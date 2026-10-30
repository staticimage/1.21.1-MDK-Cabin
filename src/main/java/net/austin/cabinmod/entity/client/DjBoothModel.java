package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.client.generated.DjBoothModelBase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

// The attached booth and detached block share this mesh. Hands and controls use DjPerformance.
public class DjBoothModel extends DjBoothModelBase<Entity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/entity/dj_booth/dj_booth_direct.png");

    public DjBoothModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    public void poseJam(float beats, float platterDegrees, int track) {
        reset();
        var motion=DjPerformance.sample(track,beats,1);
        spin(platterDegrees);
        parts().get("platter_r").yRot=-motion.scratchDegrees()*Mth.DEG_TO_RAD;
        parts().get("xfader").x+=motion.crossfader();
    }

    public void poseClip(String clip, long millis, float platterDegrees) {
        reset();
        spin(platterDegrees);
    }

    public void poseIdle(float platterDegrees) {
        reset();
        spin(platterDegrees);
    }

    public void renderDisplays(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers) {
        pose.pushPose();rootPart.translateAndRotate(pose);cabinet.translateAndRotate(pose);
        pose.pushPose();pose.scale(1f/16,1f/16,1f/16);
        RaveScreenRenderer.quad(buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(TEXTURE)),pose.last(),-22,-1,22,-24,-6.02f,
                15f/128,15f/128,59f/128,38f/128,0xFFFFFFFF);
        pose.popPose();
        parts().get("laptop").translateAndRotate(pose);pose.scale(1f/16,1f/16,1f/16);
        RaveScreenRenderer.quad(buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(RaveScreenRenderer.ART)),pose.last(),-3.8f,-.3f,3.8f,-5.5f,1.72f,
                .25f,0,.75f,1,0xFFFFFFFF);
        pose.popPose();
    }

    /** Draw the actual mod item texture on both decks; the right deck is a visual cue copy, not extra loot. */
    public void renderRecords(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, int track, boolean present) {
        if (!present) return;
        for (String side : new String[]{"l", "r"}) {
            pose.pushPose();
            rootPart.translateAndRotate(pose); cabinet.translateAndRotate(pose);
            parts().get("deck_"+side).translateAndRotate(pose);
            parts().get("platter_"+side).translateAndRotate(pose);
            pose.translate(0,-.061,0);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            pose.scale(.72f,.72f,.72f);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                    net.austin.cabinmod.entity.RecordTracks.stack(track), net.minecraft.world.item.ItemDisplayContext.FIXED,
                    light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,
                    net.minecraft.client.Minecraft.getInstance().level,0);
            pose.popPose();
        }
    }

    private void reset() {
        this.root().getAllParts().forEach(ModelPart::resetPose);
    }

    private void spin(float degrees) {
        for (String name : new String[]{"platter_l", "platter_r"}) {
            ModelPart platter = parts().get(name);
            if (platter != null) platter.yRot -= degrees * Mth.DEG_TO_RAD;
        }
    }
}
