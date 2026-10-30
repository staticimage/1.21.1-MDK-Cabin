package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austin.cabinmod.entity.custom.ArenaLightEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Hanging moving-head fixture: clamp, motor base, U-yoke, finned barrel and recessed lens. */
public class ArenaLightRenderer extends EntityRenderer<ArenaLightEntity> {
    private static final ResourceLocation MATERIAL=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    public ArenaLightRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(ArenaLightEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        int housing=0xFF252932, metal=0xFF9FA9B5;
        SolidGeometry.box(out,pose.last(),-.08,.62,-.09,.08,.96,.09,metal,light);
        SolidGeometry.box(out,pose.last(),-.16,.88,-.17,.16,.98,.17,metal,light);
        SolidGeometry.box(out,pose.last(),-.34,.42,-.22,.34,.63,.22,housing,light);
        Vec3 direction=entity.renderDirection(partial);
        float pan=(float)Math.atan2(direction.x,direction.z), tilt=-(float)Math.asin(direction.y);
        pose.pushPose();pose.mulPose(Axis.YP.rotation(pan));
        SolidGeometry.box(out,pose.last(),-.32,-.04,-.09,-.24,.47,.09,metal,light);
        SolidGeometry.box(out,pose.last(),.24,-.04,-.09,.32,.47,.09,metal,light);
        SolidGeometry.box(out,pose.last(),-.32,.38,-.09,.32,.47,.09,metal,light);
        SolidGeometry.box(out,pose.last(),-.36,-.085,-.085,-.23,.085,.085,housing,light);
        SolidGeometry.box(out,pose.last(),.23,-.085,-.085,.36,.085,.085,housing,light);
        pose.mulPose(Axis.XP.rotation(tilt));
        SolidGeometry.box(out,pose.last(),-.215,-.205,-.26,.215,.205,.15,housing,light);
        for(int i=0;i<5;i++) {
            double z=-.25+i*.065;
            SolidGeometry.box(out,pose.last(),-.235,.19,z,.235,.225,z+.025,0xFF444C58,light);
        }
        int lens=entity.laserPhase()==2 ? 0xFFF5F6FF : entity.laserPhase()==1 ? 0xFFEF54B7
                : 0xFF000000 | net.austin.cabinmod.client.RaveLighting.color(entity, partial);
        for(int i=0;i<48;i++) {
            double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
            Vec3 backA=new Vec3(Math.cos(a)*.215,Math.sin(a)*.215,.12),backB=new Vec3(Math.cos(b)*.215,Math.sin(b)*.215,.12);
            Vec3 frontA=new Vec3(Math.cos(a)*.205,Math.sin(a)*.205,.34),frontB=new Vec3(Math.cos(b)*.205,Math.sin(b)*.205,.34);
            SolidGeometry.face(out,pose.last(),backA,frontA,frontB,backB,0xFF555D69,light);
            Vec3 innerA=new Vec3(Math.cos(a)*.155,Math.sin(a)*.155,.341),innerB=new Vec3(Math.cos(b)*.155,Math.sin(b)*.155,.341);
            SolidGeometry.face(out,pose.last(),frontA,innerA,innerB,frontB,0xFF15191F,light);
            SolidGeometry.face(out,pose.last(),new Vec3(0,0,.326),innerA,innerB,innerB,lens,0xF000F0);
        }
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(ArenaLightEntity entity) { return MATERIAL; }
}
