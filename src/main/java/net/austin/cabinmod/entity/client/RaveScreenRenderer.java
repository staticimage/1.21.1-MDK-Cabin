package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.RaveScreenEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public class RaveScreenRenderer extends EntityRenderer<RaveScreenEntity> {
    // These are byte-for-byte copies of the user's two selected images. Static means
    // static: no animation clock, fake audio meter, pulse tint, UV warp or crossfade.
    // To replace one, change the resource here and its supplied image PNG.
    public static final ResourceLocation ART=ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,"textures/entity/rave/supplied_wings.png");
    public static final ResourceLocation ANGEL=ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,"textures/entity/rave/supplied_angel.png");
    private static final ResourceLocation METAL=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    public RaveScreenRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(RaveScreenEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        SolidGeometry.box(buffers.getBuffer(RenderType.entityCutoutNoCull(METAL)),pose.last(),-4.35,-.12,-.05,4.35,4.12,.03,0xFF27282D,light);
        // Each supplied image is square. Two 4x4 panels preserve the original proportions.
        quad(buffers.getBuffer(RenderType.eyes(ART)),pose.last(),-4.2f,0,-.2f,4,.04f,0,0,1,1,0xFFFFFFFF);
        quad(buffers.getBuffer(RenderType.eyes(ANGEL)),pose.last(),.2f,0,4.2f,4,.04f,0,0,1,1,0xFFFFFFFF);
    }
    public static void quad(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack.Pose pose,float left,float bottom,float right,float top,float z,float u0,float v0,float u1,float v1,int color) {
        vertex(out,pose,left,bottom,z,u0,v1,color);vertex(out,pose,right,bottom,z,u1,v1,color);
        vertex(out,pose,right,top,z,u1,v0,color);vertex(out,pose,left,top,z,u0,v0,color);
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,int color) {
        out.addVertex(pose,x,y,z).setColor(color).setUv(u,v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose,0,0,1);
    }
    @Override public ResourceLocation getTextureLocation(RaveScreenEntity entity) { return ART; }
}
