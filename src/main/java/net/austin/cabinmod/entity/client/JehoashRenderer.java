package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.client.generated.JehoashModelBase;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

// Jehoash and his illusions (the decoys must look exactly like him): the Blender rig, a full-bright
// emissive layer for the neon trims, and his booth while it is still part of him.
public class JehoashRenderer<T extends ClipMob> extends MobRenderer<T, JehoashModel<T>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/entity/joash/joash.png");
    private static final RenderType EMISSIVE = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/entity/joash/joash_emissive.png"));

    public JehoashRenderer(EntityRendererProvider.Context context) {
        super(context, new JehoashModel<>(context.bakeLayer(JehoashModelBase.LAYER_LOCATION)), 1.0f);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EMISSIVE;
            }
        });
        this.addLayer(new DjBoothLayer<>(this, context));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }

    // His death is a clip (kneel, arms to the sky), not the vanilla tip-over.
    @Override
    protected float getFlipDegrees(T entity) {
        return 0f;
    }
}
