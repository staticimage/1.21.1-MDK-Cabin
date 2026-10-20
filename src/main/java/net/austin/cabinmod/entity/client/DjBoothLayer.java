package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.client.DiscoMusic;
import net.austin.cabinmod.entity.client.generated.DjBoothModelBase;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

// While the booth is part of Jehoash, draw it one block in front of him (his booth centre block).
public class DjBoothLayer<T extends ClipMob, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final DjBoothModel booth;

    public DjBoothLayer(RenderLayerParent<T, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.booth = new DjBoothModel(context.bakeLayer(DjBoothModelBase.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(entity instanceof JehoashEntity jehoash) || !jehoash.isBoothAttached()) return;
        Object key = DiscoMusic.key(jehoash);
        if (jehoash.isDormant()) {
            this.booth.poseJam(DiscoMusic.beats(key, ageInTicks), DiscoMusic.platterDegrees(key));
        } else {
            this.booth.poseClip("interrupted", entity.actionState.getAccumulatedTime(), DiscoMusic.platterDegrees(key));
        }
        poseStack.pushPose();
        poseStack.translate(0f, 0f, -1f);              // model space: -Z is forward, one block = the booth centre
        this.booth.renderToBuffer(poseStack, buffers.getBuffer(this.booth.renderType(DjBoothModel.TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY, -1);
        this.booth.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(DjBoothModel.EMISSIVE)),
                0xF000F0, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();
    }
}
