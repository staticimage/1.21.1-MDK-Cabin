package net.austin.cabinmod.client.diviner;

import net.austin.cabinmod.network.ModPayloads;
import net.austin.cabinmod.recipe.CraftingBlockRecipe;
import net.austin.cabinmod.recipe.ModRecipes;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Talking with the Cabin up in the heavens. He is a calm guide: he greets you, answers the topics you pick
 * (what he can make, what you could make from what you carry, who he is, where you are, how stars fall) and
 * reacts to what you lay in the diviner. Lines type out at ~40 characters a second with a low hum on every
 * word (diviner_cabin_hum), and the Agartha image behind the panel brightens while he speaks. Players near
 * the diviner hear the hums too (ModPayloads.CabinSpeak -> CabinHum).
 *
 * Text lives in the lang file under cabin.cabinmod.*. Drawn by CraftingBlockScreen beside the glass panel.
 */
public final class CabinDialogue {
    private static final float CHARS_PER_TICK = 2f;
    private static final int IDLE_HINT_TICKS = 20 * 40;
    private static final int TEXT_COLOUR = 0xE8F2FF, NAME_COLOUR = 0xFFD98A, TOPIC_COLOUR = 0x9CC8F0, TOPIC_HOVER = 0xFFFFFF;

    enum Topic {
        MAKE("make"), NOW("now"), WHO("who"), PLACE("place"), STARS("stars"), FAREWELL("farewell");

        final String key;

        Topic(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("cabin.cabinmod.topic." + key);
        }
    }

    /** One line he says, with item icons drawn after the text (recipe lists). `reactive` lines may be replaced. */
    private record Line(Component text, List<ItemStack> icons, boolean reactive) {
    }

    private static final Deque<Line> QUEUE = new ArrayDeque<>();
    private static final Set<BlockPos> GREETED = new HashSet<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static BlockPos pos;
    private static boolean open, dismissed, idleHinted;
    private static Line line;
    private static String plain = "";
    private static float shown;
    private static int lastHummedWord = -1;
    private static int idleTicks;
    static float voicePulse;

    // layout of the last frame, for clicks
    private static int boxX, boxY, boxW, boxH, topicsY;
    private static final List<Topic> SHOWN_TOPICS = new ArrayList<>();

    private CabinDialogue() {
    }

    // ------------------------------------------------------------------ lifecycle (DivinerCinematic)

    /** Arrived in the heavens: he greets you. */
    static void start(BlockPos at) {
        pos = at;
        open = true;
        dismissed = false;
        idleHinted = false;
        idleTicks = 0;
        QUEUE.clear();
        line = null;
        if (GREETED.add(at)) {
            say("greet.first.1");
            say("greet.first.2");
            say("greet.first.3");
        } else {
            say("greet.again." + (1 + RANDOM.nextInt(3)));
        }
    }

    static void stop() {
        open = false;
        QUEUE.clear();
        line = null;
        voicePulse = 0;
    }

    public static boolean isOpen() {
        return open;
    }

    static void tick() {
        voicePulse = Math.max(0f, voicePulse - 0.05f);
        if (!open) return;
        if (line == null && !QUEUE.isEmpty()) next();
        if (line != null && shown < plain.length()) {
            shown = Math.min(plain.length(), shown + CHARS_PER_TICK);
            humWordsUpTo((int) shown);
            voicePulse = Math.min(1f, voicePulse + 0.12f);
            idleTicks = 0;
        } else {
            idleTicks++;
            if (!QUEUE.isEmpty() && idleTicks > 50) {
                next();   // he goes on by himself after a breath; a click goes on sooner
            } else if (idleTicks == IDLE_HINT_TICKS && !idleHinted && !dismissed) {
                idleHinted = true;
                say("idle." + (1 + RANDOM.nextInt(2)));
            }
        }
    }

    // ------------------------------------------------------------------ speaking

    private static void say(String key, Object... args) {
        QUEUE.add(new Line(Component.translatable("cabin.cabinmod." + key, args), List.of(), false));
    }

    private static void sayWith(Component text, List<ItemStack> icons) {
        QUEUE.add(new Line(text, icons, false));
    }

    /** A reaction to what you just did: replaces any reaction still waiting, and follows the current line. */
    private static void react(String key, Object... args) {
        if (!open) return;
        QUEUE.removeIf(Line::reactive);
        QUEUE.addFirst(new Line(Component.translatable("cabin.cabinmod." + key, args), List.of(), true));
        if (line == null || shown >= plain.length()) next();
    }

    private static void next() {
        line = QUEUE.poll();
        shown = 0;
        lastHummedWord = -1;
        if (line == null) {
            plain = "";
            return;
        }
        plain = line.text().getString();
        int words = plain.isBlank() ? 0 : plain.trim().split("\\s+").length;
        HeavensFx.voice(pos);
        if (pos != null && words > 0 && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ModPayloads.CabinSpeak(pos, words, plain.hashCode()));
        }
    }

    /** One low hum at the start of every word the typewriter reaches. */
    private static void humWordsUpTo(int upTo) {
        int word = -1;
        for (int i = 0; i < upTo && i < plain.length(); i++) {
            boolean start = !Character.isWhitespace(plain.charAt(i)) && (i == 0 || Character.isWhitespace(plain.charAt(i - 1)));
            if (!start) continue;
            word++;
            if (word > lastHummedWord) {
                lastHummedWord = word;
                int end = i;
                while (end < plain.length() && !Character.isWhitespace(plain.charAt(end))) end++;
                hum(plain.substring(i, end).hashCode());
            }
        }
    }

    private static void hum(int seed) {
        RandomSource r = RandomSource.create(seed);
        float pitch = 0.85f + r.nextFloat() * 0.25f;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DIVINER_CABIN_HUM.get(), pitch, 0.45f));
    }

    // ------------------------------------------------------------------ topics

    private static void choose(Topic topic) {
        if (line != null && shown < plain.length()) return;
        QUEUE.clear();
        line = null;
        idleTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        List<RecipeHolder<CraftingBlockRecipe>> recipes = mc.level == null ? List.of()
                : mc.level.getRecipeManager().getAllRecipesFor(ModRecipes.CRAFTING_BLOCK_TYPE.get());
        switch (topic) {
            case MAKE -> {
                say("make.intro");
                for (RecipeHolder<CraftingBlockRecipe> holder : recipes) {
                    CraftingBlockRecipe recipe = holder.value();
                    ItemStack input = firstOf(recipe);
                    sayWith(Component.translatable("cabin.cabinmod.make.recipe", input.getHoverName(), recipe.output().getHoverName()),
                            List.of(input, recipe.output()));
                }
                say("make.outro");
            }
            case NOW -> {
                int found = 0;
                if (mc.player != null) {
                    for (RecipeHolder<CraftingBlockRecipe> holder : recipes) {
                        CraftingBlockRecipe recipe = holder.value();
                        for (ItemStack carried : mc.player.getInventory().items) {
                            if (!carried.isEmpty() && recipe.inputItem().test(carried)) {
                                sayWith(Component.translatable("cabin.cabinmod.now.some", carried.getHoverName(), recipe.output().getHoverName()),
                                        List.of(carried.copyWithCount(1), recipe.output()));
                                found++;
                                break;
                            }
                        }
                        if (found >= 3) break;
                    }
                }
                if (found == 0 && !recipes.isEmpty()) {
                    CraftingBlockRecipe recipe = recipes.get(RANDOM.nextInt(recipes.size())).value();
                    ItemStack input = firstOf(recipe);
                    sayWith(Component.translatable("cabin.cabinmod.now.none", input.getHoverName(), recipe.output().getHoverName()),
                            List.of(input, recipe.output()));
                }
            }
            case WHO, PLACE, STARS -> {
                for (int i = 1; i <= 3; i++) say(topic.key + "." + i);
            }
            case FAREWELL -> {
                say("farewell");
                dismissed = true;
            }
        }
    }

    private static ItemStack firstOf(CraftingBlockRecipe recipe) {
        ItemStack[] items = recipe.inputItem().getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copyWithCount(1);
    }

    // ------------------------------------------------------------------ reactions (CraftingBlockScreen, DivinerFx)

    /** The diviner's input slot changed item. */
    public static void onInputChanged(ItemStack now) {
        if (!open || now.isEmpty()) return;
        dismissed = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (RecipeHolder<CraftingBlockRecipe> holder : mc.level.getRecipeManager().getAllRecipesFor(ModRecipes.CRAFTING_BLOCK_TYPE.get())) {
            if (holder.value().inputItem().test(now)) {
                react("react.valid", now.getHoverName(), holder.value().output().getHoverName());
                return;
            }
        }
        react("react.invalid", now.getHoverName());
    }

    public static void onCrafted(ItemStack result) {
        react("react.crafted", result.getHoverName());
    }

    static void onLaunched(ItemStack stack) {
        react("react.launched");
    }

    // ------------------------------------------------------------------ drawing + clicks (CraftingBlockScreen)

    /** Draws the dialogue beside the panel at (x, y, w). `reveal` fades it in with the glass. */
    public static void render(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, float reveal) {
        SHOWN_TOPICS.clear();
        if (!open) return;
        int pad = 6, textW = w - pad * 2;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (line != null) {
            String typed = plain.substring(0, Math.min(plain.length(), (int) shown));
            lines.addAll(font.split(Component.literal(typed), textW));
        }
        boolean typing = line != null && shown < plain.length();
        boolean idle = !typing && QUEUE.isEmpty();
        if (idle && !dismissed) SHOWN_TOPICS.addAll(List.of(Topic.values()));
        boolean collapsed = dismissed && idle;

        int textH = Math.max(1, lines.size()) * 10;
        int iconsH = line != null && !line.icons().isEmpty() && !typing ? 18 : 0;
        int topicsH = SHOWN_TOPICS.size() * 11;
        int h = collapsed ? 20 : pad + 11 + textH + iconsH + (topicsH > 0 ? 6 + topicsH : 0) + (idle || typing ? 0 : 10) + pad;
        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;

        DivinerPanel.drawGlass(g, x, y, w, h, reveal);
        int edge = 0x6080D8FF;
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        float glow = 0.6f + 0.4f * voicePulse;
        g.drawString(font, Component.translatable("cabin.cabinmod.name"), x + pad, y + pad, scale(NAME_COLOUR, glow), false);
        if (collapsed) {
            boolean hover = inside(mouseX, mouseY, x, y, w, h);
            g.drawString(font, Component.translatable("cabin.cabinmod.topic.call"), x + pad + 52, y + pad, hover ? TOPIC_HOVER : TOPIC_COLOUR, false);
            return;
        }
        int ty = y + pad + 12;
        for (FormattedCharSequence seq : lines) {
            g.drawString(font, seq, x + pad, ty, TEXT_COLOUR, false);
            ty += 10;
        }
        if (lines.isEmpty()) ty += 10;
        if (iconsH > 0) {
            int ix = x + pad;
            for (int i = 0; i < line.icons().size(); i++) {
                if (i > 0) {
                    g.drawString(font, "→", ix + 2, ty + 5, TOPIC_COLOUR, false);
                    ix += 12;
                }
                g.renderItem(line.icons().get(i), ix, ty);
                ix += 18;
            }
            ty += iconsH;
        }
        if (!typing && !QUEUE.isEmpty()) {
            float blink = 0.5f + 0.5f * Mth.sin(DivinerCinematic.clockTime() * 0.3f);
            g.drawString(font, "▼", x + w - pad - 6, y + h - pad - 8, scale(TOPIC_COLOUR, blink), false);
        }
        if (!SHOWN_TOPICS.isEmpty()) {
            ty += 6;
            topicsY = ty;
            for (Topic topic : SHOWN_TOPICS) {
                boolean hover = inside(mouseX, mouseY, x, ty - 1, w, 11);
                g.drawString(font, (hover ? "✦ " : "  ").concat(topic.label().getString()), x + pad, ty, hover ? TOPIC_HOVER : TOPIC_COLOUR, false);
                ty += 11;
            }
        }
    }

    /** A click in the dialogue box: pick a topic, finish the typing line or go on to the next. True if used. */
    public static boolean mouseClicked(double mx, double my) {
        if (!open || !inside(mx, my, boxX, boxY, boxW, boxH)) return false;
        if (line != null && shown < plain.length()) {
            shown = plain.length();
            return true;
        }
        if (!QUEUE.isEmpty()) {
            next();
            return true;
        }
        if (dismissed) {
            dismissed = false;
            say("greet.again." + (1 + RANDOM.nextInt(3)));
            return true;
        }
        if (!SHOWN_TOPICS.isEmpty() && my >= topicsY - 1) {
            int index = (int) ((my - (topicsY - 1)) / 11);
            if (index >= 0 && index < SHOWN_TOPICS.size()) choose(SHOWN_TOPICS.get(index));
        }
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int scale(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), gg = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }
}
