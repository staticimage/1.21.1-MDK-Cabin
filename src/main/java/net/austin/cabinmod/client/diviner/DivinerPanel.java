package net.austin.cabinmod.client.diviner;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.austin.cabinmod.CabinMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** GUI-side pieces of the diviner's glass panel (used by screen/custom/CraftingBlockScreen). */
public final class DivinerPanel {
    private static final ResourceLocation STARBURST = sheet("starburst_8x8");
    private static final ResourceLocation SPARK = sheet("spark_4x4");
    // constellation that lights star by star as a craft progresses, in the 24x16 arrow area
    private static final int[][] STARS = {{0, 9}, {5, 3}, {10, 11}, {14, 4}, {19, 10}, {24, 6}};

    private DivinerPanel() {
    }

    private static ResourceLocation sheet(String name) {
        return ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/vfx/diviner/" + name + ".png");
    }

    /** The see-through panel itself. Falls back to a dark translucent card if the cosmos isn't available. */
    public static void drawGlass(GuiGraphics g, int x, int y, int w, int h, float reveal) {
        int tex = DivinerCinematic.cosmosTexture();
        var shader = DivinerShaders.glass;
        if (tex < 0 || shader == null) {
            g.fill(x, y, x + w, y + h, 0xD0101830);
            return;
        }
        g.flush();
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, tex);
        shader.safeGetUniform("PanelSize").set((float) (w * scale), (float) (h * scale));
        shader.safeGetUniform("Reveal").set(reveal);
        shader.safeGetUniform("iTime").set(DivinerCinematic.clockTime() / 20f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x, y, 0).setUv(0, 1);
        b.addVertex(m, x, y + h, 0).setUv(0, 0);
        b.addVertex(m, x + w, y + h, 0).setUv(1, 0);
        b.addVertex(m, x + w, y, 0).setUv(1, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** Dark recessed slot with a faint cyan edge; `hot` slots (the diviner's own) glow brighter. */
    public static void drawSlot(GuiGraphics g, int x, int y, boolean hot) {
        g.fill(x, y, x + 18, y + 18, 0x70000814);
        int edge = hot ? 0xC080F0FF : 0x5550C8FF;
        g.fill(x, y, x + 18, y + 1, edge);
        g.fill(x, y + 17, x + 18, y + 18, edge);
        g.fill(x, y, x + 1, y + 18, edge);
        g.fill(x + 17, y, x + 18, y + 18, edge);
    }

    /** Star-by-star progress constellation (replaces the arrow). progress 0..1. */
    public static void drawConstellation(GuiGraphics g, int x, int y, float progress, float time) {
        float lit = progress * STARS.length;
        for (int i = 1; i < STARS.length; i++) {
            float seg = lit - i;
            if (seg <= 0) break;
            line(g, x + STARS[i - 1][0], y + STARS[i - 1][1], x + STARS[i][0], y + STARS[i][1], Math.min(1f, seg), 0xB060D8FF);
        }
        for (int i = 0; i < STARS.length; i++) {
            int sx = x + STARS[i][0], sy = y + STARS[i][1];
            boolean on = lit > i;
            float tw = 0.75f + 0.25f * (float) Math.sin(time * 4 + i * 1.7);
            if (on) {
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, ((int) (0x70 * tw) << 24) | 0x80E8FF);
                g.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
            } else {
                g.fill(sx, sy, sx + 1, sy + 1, 0x50FFFFFF);
            }
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, float amount, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int n = Math.round(steps * amount);
        for (int s = 0; s <= n; s++) {
            int px = x0 + Math.round((x1 - x0) * (s / (float) Math.max(1, steps)));
            int py = y0 + Math.round((y1 - y0) * (s / (float) Math.max(1, steps)));
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** Supernova over a slot when a craft completes, in the colours of its star. age in seconds. */
    public static void drawSupernova(GuiGraphics g, int cx, int cy, float age, ItemStack result) {
        float[] tint = DivinerFx.mixWhite(StarColors.resolve(Minecraft.getInstance().level, ItemStack.EMPTY, result).core(), 0.35f);
        float life = 0.9f;
        if (age < 0 || age > life) return;
        int frame = Math.min(63, (int) (age / life * 64));
        int size = 56;
        additive(() -> g.blit(STARBURST, cx - size / 2, cy - size / 2, size, size,
                (frame % 8) * 128f, (frame / 8) * 128f, 128, 128, 1024, 1024), tint);
    }

    /** The launched result streaking up out of the panel toward the stars. age in seconds. */
    public static void drawLaunch(GuiGraphics g, BlockPos pos, int sx, int sy, float age, int screenTop) {
        float life = 0.75f;
        if (age < 0 || age > life) return;
        float t = age / life;
        float y = sy - (sy - screenTop + 40) * t * t;
        ItemStack stack = DivinerFx.launched(pos);
        float[] tint = DivinerFx.mixWhite(DivinerFx.launchedPalette(pos).core(), 0.3f);
        for (int i = 0; i < 8; i++) {
            float ty = y + 6 + i * 7 * (0.4f + t);
            int frame = (i + (int) (age * 30)) % 16;
            int s = Math.max(4, 22 - i * 2);
            additive(() -> g.blit(SPARK, sx + 8 - s / 2, (int) ty + 8 - s / 2, s, s, (frame % 4) * 128f, (frame / 4) * 128f,
                    128, 128, 512, 512), tint);
        }
        if (!stack.isEmpty()) g.renderItem(stack, sx, (int) y);
    }

    private static void additive(Runnable draw, float[] tint) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1f);
        draw.run();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
