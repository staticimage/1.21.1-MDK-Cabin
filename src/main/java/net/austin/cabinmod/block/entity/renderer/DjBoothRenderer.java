package net.austin.cabinmod.block.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austin.cabinmod.block.custom.DjBoothBlock;
import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.austin.cabinmod.client.DiscoMusic;
import net.austin.cabinmod.entity.client.DjBoothModel;
import net.austin.cabinmod.entity.client.generated.DjBoothModelBase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// Draws the whole three-block booth from its CENTER block, with the same model and the same transform the
// entity renderer uses for Jehoash's booth (180 - yaw, flip, 1.501 down), so the hand-over is seamless.
public class DjBoothRenderer implements BlockEntityRenderer<DjBoothBlockEntity> {
    private final DjBoothModel model;

    public DjBoothRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new DjBoothModel(context.bakeLayer(DjBoothModelBase.LAYER_LOCATION));
    }

    @Override
    public void render(DjBoothBlockEntity booth, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = booth.getBlockState();
        if (!(state.getBlock() instanceof DjBoothBlock) || state.getValue(DjBoothBlock.PART) != DjBoothBlock.Part.CENTER) return;
        Direction facing = state.getValue(DjBoothBlock.FACING);
        Object key = DiscoMusic.key(booth.getBlockPos());
        if (DiscoMusic.isPlaying(key)) {
            this.model.poseJam(DiscoMusic.beats(key, 0f), DiscoMusic.platterDegrees(key), booth.recordTrack());
        } else {
            this.model.poseIdle(DiscoMusic.platterDegrees(key));
        }

        poseStack.pushPose();
        poseStack.translate(0.5f, 0f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        poseStack.scale(-1f, -1f, 1f);
        poseStack.translate(0f, -1.501f, 0f);
        this.model.renderToBuffer(poseStack, buffers.getBuffer(this.model.renderType(DjBoothModel.TEXTURE)),
                packedLight, packedOverlay, -1);
        this.model.renderDisplays(poseStack,buffers);
        this.model.renderRecords(poseStack,buffers,packedLight,booth.recordTrack(),booth.hasRecord());
        poseStack.popPose();
    }

    // The booth model is three blocks wide and about three tall (mic stand).
    @Override
    public AABB getRenderBoundingBox(DjBoothBlockEntity booth) {
        return new AABB(booth.getBlockPos()).inflate(2.0, 2.5, 2.0);
    }

    @Override
    public boolean shouldRenderOffScreen(DjBoothBlockEntity booth) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
