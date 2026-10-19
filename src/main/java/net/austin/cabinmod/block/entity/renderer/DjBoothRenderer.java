package net.austin.cabinmod.block.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.custom.DjBoothBlock;
import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

// Spins the DJ booth's records at 33 1/3 rpm. Platter centres match tools/booth/build_dj_booth.py.
public class DjBoothRenderer implements BlockEntityRenderer<DjBoothBlockEntity> {
    private static final ResourceLocation RECORD = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/block/dj_booth/platter.png");
    private static final float DEGREES_PER_TICK = 360f * (100f / 3f) / 60f / 20f;
    private static final float RADIUS = 4.5f / 16f;
    private static final float HEIGHT = 15.56f / 16f;

    public DjBoothRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DjBoothBlockEntity booth, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = booth.getBlockState();
        if (!(state.getBlock() instanceof DjBoothBlock)) return;
        Direction facing = state.getValue(DjBoothBlock.FACING);
        boolean main = state.getValue(DjBoothBlock.PART) == DjBoothBlock.Part.MAIN;
        float spin = booth.getLevel() == null ? 0f : (booth.getLevel().getGameTime() + partialTick) * DEGREES_PER_TICK;

        poseStack.pushPose();
        // models are authored facing north; turn like the blockstate does (y = 0/90/180/270 for N/E/S/W)
        poseStack.translate(0.5f, 0f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180f)));
        poseStack.translate(-0.5f, 0f, -0.5f);
        poseStack.translate((main ? 7f : 10f) / 16f, HEIGHT, 8.5f / 16f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-spin));

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(RECORD));
        float r = RADIUS;
        vertex(vc, pose, -r, -r, 0f, 0f, packedLight, packedOverlay);
        vertex(vc, pose, -r, r, 0f, 1f, packedLight, packedOverlay);
        vertex(vc, pose, r, r, 1f, 1f, packedLight, packedOverlay);
        vertex(vc, pose, r, -r, 1f, 0f, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float v, int light, int overlay) {
        vc.addVertex(pose, x, 0f, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 1f, 0f);
    }
}
