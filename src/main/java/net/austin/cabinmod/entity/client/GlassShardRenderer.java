package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austin.cabinmod.entity.custom.GlassShardEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class GlassShardRenderer extends EntityRenderer<GlassShardEntity> {
    public GlassShardRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(GlassShardEntity shard,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(shard.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90-shard.getXRot()));
        pose.mulPose(Axis.YP.rotationDegrees((shard.tickCount+partial)*24));
        GlassGeometry.shard(pose,buffers);
        pose.popPose();
        super.render(shard,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(GlassShardEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    }
}
