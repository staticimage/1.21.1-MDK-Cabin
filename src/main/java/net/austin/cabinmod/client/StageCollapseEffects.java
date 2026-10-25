package net.austin.cabinmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.StageCollapse;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** World-space cinematic accents. Never replaces, steers, or freezes any player's camera. */
@EventBusSubscriber(modid=CabinMod.MOD_ID,value=Dist.CLIENT)
/*
 * VISUAL-ONLY EFFECTS
 * Draws portal rings, a teleport trail and impact shockwave from the shared phase clock.
 * Positions are camera-relative only for rendering precision; the camera itself is
 * never moved or replaced. All effects are cosmetic; gameplay cues live on the server
 * in JehoashEntity.tickStage. To remove a visual effect, edit this file safely without
 * changing damage, rewards or multiplayer synchronization.
 */
public final class StageCollapseEffects {
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null)return;
        var bosses=mc.level.getEntitiesOfClass(JehoashEntity.class,mc.player.getBoundingBox().inflate(48),JehoashEntity::isCollapsing);
        if(bosses.isEmpty())return;
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(RenderType.lightning());var pose=new PoseStack().last();
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var boss:bosses) {
            float t=boss.stageTime()+partial;var c=boss.arenaCenter();
            Vec3 source=new Vec3(c.getX()+.5,c.getY()+3.5,c.getZ()-8.5).subtract(camera);
            Vec3 arrival=boss.getPosition(partial).add(0,1.5,0).subtract(camera);
            if(t>51 && t<76) {
                float pulse=(float)Math.sin((t-51)/25*Math.PI),radius=.15f+1.1f*pulse;
                ring(out,pose,source,radius,1.6,0,.06f,.65f*pulse,1,.12f,.75f,t*.15f);
                ring(out,pose,source,radius*.8,1.7,0,.025f,.8f*pulse,.6f,.9f,1,-t*.13f);
                if(t>=StageCollapse.DODGE) {
                    ring(out,pose,arrival,radius,1.5,0,.055f,.7f*pulse,.5f,.7f,1,-t*.17f);
                    Vec3 path=arrival.subtract(source);
                    for(int i=0;i<18;i++) {
                        float along=(i/18f+(t-59)*.06f)%1;
                        Vec3 at=source.add(path.scale(along)).add(0,Math.sin(along*Math.PI)*1.5,0);
                        line(out,pose,at,at.add(path.normalize().scale(.35)),.025f,.6f*pulse,.65f,.35f,1);
                    }
                }
            }
            if(t>=StageCollapse.IMPACT && t<105) {
                float progress=(t-StageCollapse.IMPACT)/31,alpha=(1-progress)*.5f;
                Vec3 impact=new Vec3(c.getX()+.5,c.getY()+2.12,c.getZ()-9.1).subtract(camera);
                ring(out,pose,impact,.5+progress*11,1,1,.08f,alpha,1,.55f,.3f,0);
                ring(out,pose,impact.add(0,.03,0),.3+progress*8,1,1,.025f,alpha,.7f,.8f,1,0);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }
    private static void ring(VertexConsumer out,PoseStack.Pose pose,Vec3 center,double radius,double aspect,int horizontal,float width,float alpha,float r,float g,float b,float spin) {
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32+spin,z=(i+1)*Math.PI/32+spin;
            Vec3 first=horizontal==1?new Vec3(Math.cos(a)*radius,0,Math.sin(a)*radius):new Vec3(Math.cos(a)*radius,Math.sin(a)*radius*aspect,0);
            Vec3 next=horizontal==1?new Vec3(Math.cos(z)*radius,0,Math.sin(z)*radius):new Vec3(Math.cos(z)*radius,Math.sin(z)*radius*aspect,0);
            line(out,pose,center.add(first),center.add(next),width,alpha,r,g,b);
        }
    }
    private static void line(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,float w,float alpha,float r,float g,float blue) {
        Vec3 direction=b.subtract(a).normalize(),normal=direction.cross(a.add(b).scale(.5).normalize()).normalize().scale(w);
        vertex(out,pose,a.add(normal),alpha,r,g,blue);vertex(out,pose,a.subtract(normal),alpha,r,g,blue);
        vertex(out,pose,b.subtract(normal),alpha,r,g,blue);vertex(out,pose,b.add(normal),alpha,r,g,blue);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,float a,float r,float g,float b) {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,a);
    }
    private StageCollapseEffects() {}
}
