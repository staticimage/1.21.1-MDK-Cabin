package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Small hard-surface meshes with explicit normals and a neutral material. */
public final class SolidGeometry {
    public static void box(VertexConsumer out,PoseStack.Pose pose,double x0,double y0,double z0,double x1,double y1,double z1,int color,int light) {
        Vec3 a=new Vec3(x0,y0,z0), b=new Vec3(x1,y0,z0), c=new Vec3(x1,y1,z0), d=new Vec3(x0,y1,z0);
        Vec3 e=new Vec3(x0,y0,z1), f=new Vec3(x1,y0,z1), g=new Vec3(x1,y1,z1), h=new Vec3(x0,y1,z1);
        face(out,pose,a,d,c,b,color,light);face(out,pose,e,f,g,h,color,light);
        face(out,pose,a,e,h,d,color,light);face(out,pose,b,c,g,f,color,light);
        face(out,pose,a,b,f,e,color,light);face(out,pose,d,h,g,c,color,light);
    }
    public static void face(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,int light) {
        Vec3 n=b.subtract(a).cross(c.subtract(a)).normalize();
        vertex(out,pose,a,0,0,n,color,light);vertex(out,pose,b,0,1,n,color,light);
        vertex(out,pose,c,1,1,n,color,light);vertex(out,pose,d,1,0,n,color,light);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,float u,float v,Vec3 n,int color,int light) {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }
    private SolidGeometry() {}
}
