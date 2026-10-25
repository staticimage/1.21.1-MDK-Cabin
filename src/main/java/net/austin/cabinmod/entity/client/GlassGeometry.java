package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Faceted sphere and pointed glass triangles, independent of the humanoid's cuboid rig. */
public final class GlassGeometry {
    private static final ResourceLocation SILVER = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    public static void sphere(PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(SILVER));
        for (int lat=0;lat<12;lat++) for (int lon=0;lon<24;lon++) {
            double a = Math.PI*lat/12, b = Math.PI*(lat+1)/12;
            double c = Math.PI*2*lon/24, d = Math.PI*2*(lon+1)/24;
            int color = (lat+lon)%3==0 ? 0xFFDAECF3 : (lon%2==0 ? 0xFF718FAD : 0xFFA7B6C9);
            face(out,pose.last(),point(a,c),point(b,c),point(b,d),point(a,d),color);
        }
    }
    private static Vec3 point(double latitude,double longitude) {
        return new Vec3(Math.sin(latitude)*Math.cos(longitude),Math.cos(latitude),Math.sin(latitude)*Math.sin(longitude)).scale(.24);
    }
    public static void shard(PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(SILVER));
        Vec3 tip = new Vec3(0,.28,0), a = new Vec3(-.13,-.14,0), b = new Vec3(.1,-.1,.07), c = new Vec3(.03,-.12,-.08);
        face(out,pose.last(),tip,a,b,b,0xDDADEBFA);
        face(out,pose.last(),tip,b,c,c,0xEEDDF9FF);
        face(out,pose.last(),tip,c,a,a,0xBB7285B9);
        face(out,pose.last(),a,c,b,b,0xBBBD85CE);
    }
    private static void face(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
        vertex(out,pose,a,0,0,normal,color);vertex(out,pose,b,0,1,normal,color);
        vertex(out,pose,c,1,1,normal,color);vertex(out,pose,d,1,0,normal,color);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,float u,float v,Vec3 n,int color) {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(color).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose,(float)n.x,(float)n.y,(float)n.z);
    }
    private GlassGeometry() {}
}
