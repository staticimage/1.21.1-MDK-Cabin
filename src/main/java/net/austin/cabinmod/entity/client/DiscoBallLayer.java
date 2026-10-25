package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class DiscoBallLayer<T extends ClipMob> extends RenderLayer<T,JehoashModel<T>> {
    public DiscoBallLayer(RenderLayerParent<T,JehoashModel<T>> parent) { super(parent); }
    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light,T entity,float swing,
            float amount,float partial,float age,float yaw,float pitch) {
        pose.pushPose();
        getParentModel().ballTransform(pose);
        GlassGeometry.sphere(pose,buffers);
        boolean released = entity instanceof JehoashEntity j && j.laserPhase() == JehoashEntity.LASER_FIRING;
        for (int i=0;i<6;i++) {
            if (released && i%2==0) continue;
            double angle = age*.06 + i*Math.PI/3;
            pose.pushPose();
            pose.translate(Math.cos(angle)*.48,Math.sin(angle*1.5+i)*.22,Math.sin(angle)*.48);
            pose.mulPose(Axis.ZP.rotation((float)angle));
            pose.mulPose(Axis.XP.rotation(age*.04f));
            pose.scale(.6f,.6f,.6f);
            GlassGeometry.shard(pose,buffers);
            pose.popPose();
        }
        pose.popPose();
    }
}
