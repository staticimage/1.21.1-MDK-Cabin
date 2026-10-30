package net.austin.cabinmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;

/** Untextured light meshes. Transparent light must not write depth and hide later cones. */
final class RaveRenderTypes extends RenderType {
    // AFTER_PARTICLES has the particles framebuffer bound in Fabulous mode. Draw on the
    // scene's real depth buffer, then restore the caller's target for other event handlers.
    private static final OutputStateShard SCENE = new OutputStateShard("cabinmod_rave_scene",
            () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false),
            () -> {
                if (Minecraft.useShaderTransparency())
                    Minecraft.getInstance().levelRenderer.getParticlesTarget().bindWrite(false);
            });
    static final RenderType GLOW = create("cabinmod_rave_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
                    .setOutputState(SCENE).createCompositeState(false));
    static final RenderType FLOOR = create("cabinmod_rave_floor", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 65536, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL)
                    .setOutputState(SCENE)
                    .createCompositeState(false));

    private RaveRenderTypes() {
        super("cabinmod_rave", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
}
