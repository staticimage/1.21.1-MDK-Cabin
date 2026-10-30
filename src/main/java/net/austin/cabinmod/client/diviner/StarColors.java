package net.austin.cabinmod.client.diviner;

import com.mojang.blaze3d.platform.NativeImage;
import net.austin.cabinmod.recipe.CraftingBlockRecipe;
import net.austin.cabinmod.recipe.ModRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Colours of a shooting star, from what went into the diviner and what came out. A recipe can name them
 * ("star": {"core", "trail"} in data/cabinmod/recipe); otherwise they come from the items' own textures: the
 * result gives the head of the star, the input the far end of its trail. Every client works the same colours
 * out from the synced star (CraftingBlockEntity.Star), so everyone sees the same star.
 */
public final class StarColors {
    /** The classic diviner cyan/violet, for stars with nothing to go on. */
    public static final StarPalette DEFAULT = new StarPalette(new float[]{0.4f, 0.9f, 1f}, new float[]{0.62f, 0.3f, 1f});
    private static final Map<Item, float[]> SPRITE_COLOURS = new HashMap<>();

    /** core: the star's head, halo and impact accents. trail: where its plasma tail fades to. */
    public record StarPalette(float[] core, float[] trail) {
        public int coreArgb(float alpha) {
            return argb(core, alpha);
        }

        public int trailArgb(float alpha) {
            return argb(trail, alpha);
        }

        private static int argb(float[] c, float a) {
            return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
        }
    }

    private StarColors() {
    }

    public static StarPalette resolve(Level level, ItemStack input, ItemStack result) {
        if (input.isEmpty() && result.isEmpty()) return DEFAULT;
        if (level != null) {
            for (RecipeHolder<CraftingBlockRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.CRAFTING_BLOCK_TYPE.get())) {
                CraftingBlockRecipe recipe = holder.value();
                boolean same = input.isEmpty() ? ItemStack.isSameItem(recipe.output(), result) : recipe.inputItem().test(input);
                if (same && recipe.star().isPresent()) {
                    CraftingBlockRecipe.StarColor c = recipe.star().get();
                    return new StarPalette(rgb(c.core()), rgb(c.trail()));
                }
            }
        }
        float[] core = result.isEmpty() ? null : spriteColour(result);
        float[] trail = input.isEmpty() ? null : spriteColour(input);
        if (core == null && trail == null) return DEFAULT;
        if (core == null) core = trail;
        if (trail == null) trail = shift(core);
        return new StarPalette(core, trail);
    }

    /** The colour this player's star will have: the item in the input slot and what it becomes. */
    public static StarPalette forRecipeInput(Level level, ItemStack input) {
        if (level == null || input.isEmpty()) return DEFAULT;
        for (RecipeHolder<CraftingBlockRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.CRAFTING_BLOCK_TYPE.get())) {
            if (holder.value().inputItem().test(input)) return resolve(level, input, holder.value().output());
        }
        return DEFAULT;
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    /** A darker, cooler companion for a star that only has one colour. */
    private static float[] shift(float[] c) {
        return new float[]{c[0] * 0.55f + 0.1f, c[1] * 0.4f + 0.05f, Math.min(1f, c[2] * 0.8f + 0.35f)};
    }

    /**
     * Saturation-weighted mean colour of an item's sprite, then pushed bright and saturated enough to glow
     * (a dull grey ingot would otherwise make a dull grey star). Null if the sprite can't be read.
     */
    private static float[] spriteColour(ItemStack stack) {
        return SPRITE_COLOURS.computeIfAbsent(stack.getItem(), item -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                TextureAtlasSprite sprite = mc.getItemRenderer().getModel(stack, mc.level, null, 0).getParticleIcon();
                NativeImage image = sprite.contents().getOriginalImage();
                int w = sprite.contents().width(), h = sprite.contents().height();
                double r = 0, g = 0, b = 0, weight = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = image.getPixelRGBA(x, y);
                        int a = (abgr >>> 24) & 255;
                        if (a < 128) continue;
                        float pr = (abgr & 255) / 255f, pg = ((abgr >> 8) & 255) / 255f, pb = ((abgr >> 16) & 255) / 255f;
                        float max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
                        double wgt = 0.05 + (max - min) * (max - min) * 4 + max * 0.1;
                        r += pr * wgt;
                        g += pg * wgt;
                        b += pb * wgt;
                        weight += wgt;
                    }
                }
                if (weight <= 0) return null;
                float[] hsv = rgbToHsv((float) (r / weight), (float) (g / weight), (float) (b / weight));
                int rgb = Mth.hsvToRgb(hsv[0], Math.max(0.45f, hsv[1]), Math.max(0.9f, hsv[2]));
                return rgb(rgb);
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    private static float[] rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float hue = 0;
        if (d > 1e-5f) {
            if (max == r) hue = ((g - b) / d) % 6f;
            else if (max == g) hue = (b - r) / d + 2f;
            else hue = (r - g) / d + 4f;
            hue /= 6f;
            if (hue < 0) hue += 1f;
        }
        return new float[]{hue, max <= 0 ? 0 : d / max, max};
    }

    /** Called on resource reload: sprites may have changed. */
    static void clearCache() {
        SPRITE_COLOURS.clear();
    }
}
