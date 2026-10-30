package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** The supplied photograph is stored unchanged. UVs select just the face, under the hood.
 * Crop coordinates refer to the original 366x378 photo: [158,80] through [302,242].
 * This 8:9 region matches the head's front face without stretching its proportions.
 */
public final class JehoashFaceLayer<T extends ClipMob> extends RenderLayer<T, JehoashModel<T>> {
    private static final ResourceLocation FACE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
            "textures/entity/joash/jehoash_face.png");
    private static final float U0 = 158f / 366, U1 = 302f / 366, V0 = 80f / 378, V1 = 242f / 378;

    public JehoashFaceLayer(RenderLayerParent<T, JehoashModel<T>> parent) { super(parent); }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                                 float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (entity.isInvisible()) return;
        pose.pushPose();
        getParentModel().headTransform(pose);
        pose.scale(1f / 16, 1f / 16, 1f / 16);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(FACE));
        int overlay = net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(entity, 0);
        vertex(out, pose.last(), -4, -9, U0, V0, light, overlay);
        vertex(out, pose.last(), -4, 0, U0, V1, light, overlay);
        vertex(out, pose.last(), 4, 0, U1, V1, light, overlay);
        vertex(out, pose.last(), 4, -9, U1, V0, light, overlay);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y,
                               float u, float v, int light, int overlay) {
        // Just in front of the original head, still behind the outer hood's transparent opening.
        out.addVertex(pose, x, y, -4.02f).setColor(0xFFFFFFFF).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
    }
}
