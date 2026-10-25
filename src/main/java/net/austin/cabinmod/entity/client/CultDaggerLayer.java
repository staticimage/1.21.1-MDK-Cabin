package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A separately shaded, bevelled ritual dagger, anchored through the right mitten's grip. */
/*
 * WEAPON EDITING MAP
 * This is a hard-surface mesh drawn in pixel units at JehoashModel.daggerTransform(),
 * which follows the right wrist/mitten. Handle, pommel, guard and blade are explicitly
 * separate geometry below. Change the edge[] polygon for blade shape; its ridge creates
 * beveled lighting. Material is neutral white_concrete tinted per face, so the blade
 * cannot accidentally sample disco-ball UVs. It appears only after booth separation.
 * This renderer creates no item and no loot: the dagger remains boss-only.
 */
public class CultDaggerLayer<T extends ClipMob> extends RenderLayer<T,JehoashModel<T>> {
    private static final ResourceLocation MATERIAL=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    public CultDaggerLayer(RenderLayerParent<T,JehoashModel<T>> parent) { super(parent); }
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float swing,float amount,float partial,float age,float yaw,float pitch) {
        for(boolean left:new boolean[]{false,true}) {
            pose.pushPose();getParentModel().handTransform(pose,left);pose.scale(1f/16,1f/16,1f/16);
            SolidGeometry.box(buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL)),pose.last(),-1.7,-1.1,-2,1.7,1.1,2,0xFF51444C,light);
            pose.popPose();
        }
        if(entity.isDeadOrDying() || (entity instanceof JehoashEntity j && (j.isDormant() || j.isBoothAttached()))) return;
        pose.pushPose();getParentModel().daggerTransform(pose);pose.scale(1f/16,1f/16,1f/16);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        SolidGeometry.box(out,pose.last(),-.65,-2.7,-.65,.65,2.3,.65,0xFF38242A,light);
        for(int i=0;i<5;i++) SolidGeometry.box(out,pose.last(),-.7,-2.2+i*.85,-.7,.7,-1.95+i*.85,.7,0xFF151318,light);
        SolidGeometry.box(out,pose.last(),-.95,-3.5,-.9,.95,-2.6,.9,0xFF8F8790,light);
        SolidGeometry.box(out,pose.last(),-.45,-3.55,-.96,.45,-2.65,-.91,0xFF960F32,0xF000F0);
        SolidGeometry.box(out,pose.last(),-3,2.2,-.65,3,3.1,.65,0xFFAAA2AC,light);
        SolidGeometry.box(out,pose.last(),-3.2,1.6,-.65,-2.35,3.1,.65,0xFF8B8491,light);
        SolidGeometry.box(out,pose.last(),2.35,1.6,-.65,3.2,3.1,.65,0xFF8B8491,light);
        Vec3[] edge={new Vec3(-1.8,3.1,0),new Vec3(-2.1,5,0),new Vec3(-1.1,10,0),new Vec3(0,14,0),new Vec3(1.1,10,0),new Vec3(2.1,5,0),new Vec3(1.8,3.1,0)};
        for(int side:new int[]{-1,1}) for(int i=0;i<edge.length;i++) {
            Vec3 a=edge[i],b=edge[(i+1)%edge.length],ridge=new Vec3(0,6.1,.7*side);
            int tint=(i<3) ^ (side<0) ? 0xFFE2E5EE : 0xFF737F92;
            SolidGeometry.face(out,pose.last(),ridge,side<0?b:a,side<0?a:b,side<0?a:b,tint,light);
        }
        pose.popPose();
    }
}
