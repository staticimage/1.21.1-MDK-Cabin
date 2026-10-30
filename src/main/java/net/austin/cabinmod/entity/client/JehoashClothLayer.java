package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws Jehoash's simulated robe, cape, and face-covering hood on their posed bones. */
public final class JehoashClothLayer<T extends ClipMob> extends RenderLayer<T, JehoashModel<T>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
            "textures/entity/joash/jehoash_brg_cloak.png");

    public JehoashClothLayer(RenderLayerParent<T, JehoashModel<T>> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (entity.isInvisible()) return;
        getParentModel().renderCloth(entity, pose, buffers, TEXTURE, light,
                LivingEntityRenderer.getOverlayCoords(entity, 0));
    }
}
