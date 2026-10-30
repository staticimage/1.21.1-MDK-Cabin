package net.austin.cabinmod.client.diviner;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.austin.cabinmod.CabinMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** World-space render types for the starfall: all additive (ONE, ONE - shaders output premultiplied colour), depth-tested, never writing depth. */
final class DivinerRenderTypes extends RenderType {
    // AFTER_PARTICLES has the particles framebuffer bound in Fabulous mode. Draw on the
    // scene's real depth buffer, then restore the caller's target (same as RaveRenderTypes).
    private static final OutputStateShard SCENE = new OutputStateShard("cabinmod_diviner_scene",
            () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false),
            () -> {
                if (Minecraft.useShaderTransparency())
                    Minecraft.getInstance().levelRenderer.getParticlesTarget().bindWrite(false);
            });

    private static RenderType additive(String name, VertexFormat format, ShaderStateShard shader, EmptyTextureStateShard texture) {
        return create("cabinmod_diviner_" + name, format, VertexFormat.Mode.TRIANGLES, 1 << 17, false, false,
                CompositeState.builder()
                        .setShaderState(shader)
                        .setTextureState(texture)
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setOutputState(SCENE)
                        .createCompositeState(false));
    }

    /** The Blender comet, impact shards and the impact dome (diviner_comet.fsh). */
    static final RenderType COMET = additive("comet", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
            new ShaderStateShard(() -> DivinerShaders.comet), NO_TEXTURE);
    /** The molten-glass crown (diviner_crater.fsh). */
    static final RenderType CRATER = additive("crater", DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
            new ShaderStateShard(() -> DivinerShaders.crater), NO_TEXTURE);
    /** Plasma ribbon behind the comet (diviner_trail.fsh). Quads as triangle pairs. */
    static final RenderType TRAIL = additive("trail", DefaultVertexFormat.POSITION_TEX_COLOR,
            new ShaderStateShard(() -> DivinerShaders.trail), NO_TEXTURE);
    /** Ground shockwave + cracks decal (diviner_ground.fsh). */
    static final RenderType GROUND = additive("ground", DefaultVertexFormat.POSITION_TEX_COLOR,
            new ShaderStateShard(() -> DivinerShaders.ground), NO_TEXTURE);

    /** Summoning circle under the diviner (diviner_circle.fsh). */
    static final RenderType CIRCLE = additive("circle", DefaultVertexFormat.POSITION_TEX_COLOR,
            new ShaderStateShard(() -> DivinerShaders.circle), NO_TEXTURE);

    // Premultiplied alpha (ONE, ONE_MINUS_SRC_ALPHA): alpha 0 adds light like the others, alpha > 0 also darkens what
    // is behind - so the hole in the sky can be deep space even at noon.
    private static final TransparencyStateShard PREMULTIPLIED = new TransparencyStateShard("cabinmod_diviner_premultiplied", () -> {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    });

    /** The heavens opening above a diviner, with the faint Cabin in Agartha inside (diviner_rift.fsh). */
    static final RenderType RIFT = create("cabinmod_diviner_rift", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES,
            1 << 12, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> DivinerShaders.rift))
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
                            "textures/gui/crafting_block/crafting_block_gui.png"), true, false))
                    .setTransparencyState(PREMULTIPLIED)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(SCENE)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> SPRITES = new HashMap<>();

    /** Flipbook sprites from a Blender-rendered sheet in textures/vfx/diviner (diviner_fx.fsh). */
    static RenderType sprite(String sheet) {
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/vfx/diviner/" + sheet + ".png");
        return SPRITES.computeIfAbsent(tex, t -> additive("sprite_" + sheet, DefaultVertexFormat.POSITION_TEX_COLOR,
                new ShaderStateShard(() -> DivinerShaders.fx), new TextureStateShard(t, true, false)));
    }

    private DivinerRenderTypes() {
        super("cabinmod_diviner", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
}
