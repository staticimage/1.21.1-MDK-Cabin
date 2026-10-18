package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.BlueEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BlueRenderer extends MobRenderer<BlueEntity, BlueModel<BlueEntity>> {

    public BlueRenderer(EntityRendererProvider.Context context) {
        super(context, new BlueModel<>(context.bakeLayer(BlueModel.LAYER_LOCATION)), 0.25f);
    }

    @Override
    public ResourceLocation getTextureLocation(BlueEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/entity/blue/blue_guy.png");
    }

    @Override
    public void render(BlueEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if(entity.isBaby()) {
            poseStack.scale(0.5f, 0.5f, 0.5f);
        } else {
            poseStack.scale(1f, 1f, 1f);
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
