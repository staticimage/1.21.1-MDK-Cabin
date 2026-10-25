package net.austin.cabinmod.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austin.cabinmod.entity.custom.TrussSupportEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Exposed bolted collars mark the hittable part of each support. */
public class TrussSupportRenderer extends EntityRenderer<TrussSupportEntity> {
    private static final ResourceLocation MATERIAL=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    public TrussSupportRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(TrussSupportEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(MATERIAL));
        float damage=1-e.health()/e.maxHealth();
        int steel=e.health()<=0?0xFF541E20:0xFF9B7244;
        for(double y:new double[]{.35,1.15}) {
            SolidGeometry.box(out,pose.last(),-.58,y,-.58,.58,y+.15,.58,steel,light);
            for(double x:new double[]{-.42,.42}) SolidGeometry.box(out,pose.last(),x-.06,y+.035,.585,x+.06,y+.12,.63,0xFFD9D6B6,light);
        }
        SolidGeometry.box(out,pose.last(),-.35,.5,.51,.35,1.15,.57,0xFF33353A,light);
        for(int i=0;i<5;i++) {
            double x=-.29+i*.12;
            SolidGeometry.box(out,pose.last(),x,.61,.575,x+.08,.98,.59,i<Math.ceil(5*e.health()/e.maxHealth())?0xFFFF8030:0xFF351114,0xF000F0);
        }
        if(damage>0) for(int i=0;i<4;i++) {
            double x=-.45+i*.25;
            SolidGeometry.box(out,pose.last(),x,.55+i*.12,.59,x+.04,.8+i*.12,.61,0xFFFF381C,0xF000F0);
        }
        if(e.health()>0) {
            pose.pushPose();pose.translate(0,1.65,.7);pose.mulPose(entityRenderDispatcher.cameraOrientation());pose.scale(-.018f,-.018f,.018f);
            String label=(e.side()==0?"LEFT":"RIGHT")+" SUPPORT  "+(int)e.health()+" / "+(int)e.maxHealth();
            var font=getFont();font.drawInBatch(label,-font.width(label)/2f,0,0xFFFFBD66,false,pose.last().pose(),buffers,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL,0xA0000000,0xF000F0);pose.popPose();
        }
    }
    @Override public ResourceLocation getTextureLocation(TrussSupportEntity entity) { return MATERIAL; }
}
