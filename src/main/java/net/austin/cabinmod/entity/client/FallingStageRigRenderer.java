package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.entity.StageCollapse;
import net.austin.cabinmod.entity.custom.FallingStageRigEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.Direction;

public class FallingStageRigRenderer extends EntityRenderer<FallingStageRigEntity> {
    public FallingStageRigRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(FallingStageRigEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        float t=entity.collapseTime()+partial;
        pose.pushPose();pose.translate(0,-StageCollapse.drop(t),StageCollapse.retreat(t));pose.mulPose(Axis.ZP.rotationDegrees(StageCollapse.roll(t)));
        var block=ModBlocks.STAGE_TRUSS.get().defaultBlockState();
        for(int x=-6;x<=6;x++) {
            pose.pushPose();pose.translate(x-.5,0,-.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.setValue(RotatedPillarBlock.AXIS,Direction.Axis.X),pose,buffers,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // Bent remnants hang from the crossbar, rotating outward as the connections tear.
        for(int side:new int[]{-1,1}) {
            pose.pushPose();pose.translate(side*6,0,0);pose.mulPose(Axis.ZP.rotationDegrees(side*Math.min(65,t*1.3f)));
            for(int y=-2;y<0;y++) {
                pose.pushPose();pose.translate(-.5,y,-.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block,pose,buffers,light,OverlayTexture.NO_OVERLAY);pose.popPose();
            }
            pose.popPose();
        }
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(FallingStageRigEntity entity) { return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS; }
}
