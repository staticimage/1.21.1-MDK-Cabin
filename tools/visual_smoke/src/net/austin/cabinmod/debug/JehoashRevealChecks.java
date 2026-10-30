package net.austin.cabinmod.debug;

import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.entity.client.JehoashModel;
import net.austin.cabinmod.entity.client.physics.ClothSheet;
import net.austin.cabinmod.entity.client.physics.ModelSpace;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/** Client-only regression checks, excluded from the release jar. Reflection avoids test APIs in the mod. */
final class JehoashRevealChecks {
    static void run() {
        var entity = ModEntities.JOASH.get().create(Minecraft.getInstance().level);
        entity.setPos(.5, 90, .5);
        entity.xo=entity.getX(); entity.yo=entity.getY(); entity.zo=entity.getZ();
        data(entity,"DORMANT",false);
        data(entity,"BOOTH_ATTACHED",false);
        var model = new JehoashModel<JehoashEntity>(JehoashModel.createBodyLayer().bakeRoot());
        comparePoses(sample(model,entity,JehoashEntity.COLLAPSE,109.999f),
                sample(model,entity,JehoashEntity.ON_FOOT,110.001f), .02f, "phase boundary");
        comparePoses(sample(model,entity,JehoashEntity.ON_FOOT,127.999f),
                sample(model,entity,JehoashEntity.ON_FOOT,128.001f), .02f, "recovery end");
        sample(model,entity,JehoashEntity.COLLAPSE,81.9f);
        check(!model.root().getChild("torso").getChild("revealed_core").visible,"outfit stays covered before reveal");
        sample(model,entity,JehoashEntity.COLLAPSE,82f);
        check(!model.root().getChild("torso").getChild("arm_r").visible,"sleeves cover the fuller shoulders until release");
        check(model.root().getChild("torso").getChild("revealed_core").visible,"native cuboid body appears beneath cloth");
        var coveredGloves=new Capture();
        model.renderCoveredHands(entity,82,new com.mojang.blaze3d.vertex.PoseStack(),type -> coveredGloves,0,0);
        check(coveredGloves.points.size()==48,"both fitted gloves render while sleeve hierarchy is covered");
        sample(model,entity,JehoashEntity.COLLAPSE,88f);
        check(model.root().getChild("torso").getChild("arm_r").visible,"fuller shoulders become visible at release");
        var exposedGloves=new Capture();
        model.renderCoveredHands(entity,88,new com.mojang.blaze3d.vertex.PoseStack(),type -> exposedGloves,0,0);
        check(exposedGloves.points.isEmpty(),"exposed gloves render once in the native hierarchy");
        checkTeleportGeometry(model,entity);
        var illusion=ModEntities.JOASH_ILLUSION.get().create(Minecraft.getInstance().level);
        var illusionModel=new JehoashModel<net.austin.cabinmod.entity.custom.JehoashIllusionEntity>(JehoashModel.createBodyLayer().bakeRoot());
        illusionModel.setupAnim(illusion,0,0,10,0,0);
        check(illusionModel.root().getChild("torso").getChild("revealed_core").visible
                && illusionModel.root().getChild("torso").getChild("arm_r").visible,"illusions use exposed cuboid body");

        var transform = new ModelSpace.Transform(new Vector3f(),new Matrix3f());
        var pose = new ClothSheet.Pose(transform,transform,transform,transform,transform,transform);
        entity.tickCount=0;
        Vector3f[] reference=null;
        for (int fps : new int[]{30,60,120}) {
            var cloth=prepared(entity,pose);
            Object state=state(cloth,entity);
            Vector3f[] release=copy((Vector3f[])field(state,"position"));
            cloth.update(entity,4,pose,0,true);
            comparePoints(release,(Vector3f[])field(state,"position"),0,"release preserves positions");
            Vector3f[] velocityHistory=copy((Vector3f[])field(state,"previous"));
            cloth.update(entity,4,pose,0,true);
            comparePoints(velocityHistory,(Vector3f[])field(state,"previous"),0,"release applied once");
            for (int frame=1;frame<=fps/2;frame++) cloth.update(entity,4+frame*20f/fps,pose,0,true);
            check((float)field(state,"detachedAge")==4,"release clock stays fixed");
            Vector3f[] points=copy((Vector3f[])field(state,"position"));
            if(reference==null) reference=points;
            else comparePoints(reference,points,.003f,"same half-second flight at "+fps+" FPS");
            for (Vector3f point:points) check(point.isFinite(),"finite cloth positions");
            System.out.println("REVEAL_CHECK PASS detached trajectory at "+fps+" FPS");
        }
        var cloth=prepared(entity,pose);
        cloth.update(entity,4,pose,0,true);
        Object state=state(cloth,entity);
        entity.yBodyRot=entity.yBodyRotO=170;
        cloth.update(entity,4.5f,pose,0,true);
        check(!(boolean)field(state,"expired"),"abrupt turn keeps detached cloth");
        cloth.update(entity,5,pose,0,false);
        check((boolean)field(state,"detached"),"stale phase data cannot reattach cloth");
        cloth.update(entity,25,pose,0,true);
        check((boolean)field(state,"expired"),"render gap expires instead of resetting onto body");
        cloth.update(entity,26,pose,0,true);
        check((boolean)field(state,"expired"),"expired cloth stays hidden");
        var late=new ClothSheet(false);
        late.update(entity,100,pose,0,true);
        check((boolean)field(state(late,entity),"expired"),"late tracking does not replay release");
        entity.yBodyRot=entity.yBodyRotO=0;
        var fading=prepared(entity,pose);
        fading.update(entity,4,pose,0,true);
        for(int frame=1;frame<=204;frame++) fading.update(entity,4+frame/6f,pose,0,true);
        int[] colors={0,0};
        var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                JehoashRevealChecks.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
                (proxy,method,args) -> {
                    if(method.getName().equals("setColor") && args.length==1) { colors[0]=(int)args[0]>>>24; colors[1]++; }
                    return proxy;
                });
        var texture=net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
        fading.render(entity,new com.mojang.blaze3d.vertex.PoseStack(),type -> consumer,texture,0,0);
        check(colors[1]>0 && colors[0]>=126 && colors[0]<=128,"cloth is half-transparent midway through fade");
        fading.update(entity,42,pose,0,true);
        colors[1]=0;
        fading.render(entity,new com.mojang.blaze3d.vertex.PoseStack(),type -> consumer,texture,0,0);
        check(colors[1]==0,"fully faded cloth emits no vertices");
        System.out.println("REVEAL_CHECK PASS pose continuity, covered body swap, one-time release, turn, render gap, late tracking, fade");
    }

    private static ClothSheet prepared(JehoashEntity entity, ClothSheet.Pose pose) {
        var cloth=new ClothSheet(false);
        for(int i=0;i<24;i++) cloth.update(entity,i/6f,pose,0,false);
        return cloth;
    }

    private static void checkTeleportGeometry(JehoashModel<JehoashEntity> model,JehoashEntity entity) {
        data(entity,"STAGE_PHASE",JehoashEntity.ON_FOOT); data(entity,"STAGE_TIME",128);
        entity.tickCount=0;
        model.setupAnim(entity,0,0,10,0,0);
        var before=new Capture();
        model.renderToBuffer(new com.mojang.blaze3d.vertex.PoseStack(),before,0,0,0xFFFFFFFF);
        var normalHead=new com.mojang.blaze3d.vertex.PoseStack(); model.headTransform(normalHead);
        var normalHand=new com.mojang.blaze3d.vertex.PoseStack(); model.handTransform(normalHand,false);
        var normalBall=new com.mojang.blaze3d.vertex.PoseStack(); model.ballTransform(normalBall);
        data(entity,"ACTION","teleport"); entity.actionState.start(0);
        model.setupAnim(entity,0,0,10,0,0);
        check(Math.abs(model.root().yScale-.05f)<.001f,"teleport reaches its midpoint compression");
        var after=new Capture();
        model.renderToBuffer(new com.mojang.blaze3d.vertex.PoseStack(),after,0,0,0xFFFFFFFF);
        check(before.points.size()==after.points.size() && after.points.size()>100,"teleport renders the full cuboid hierarchy");
        float anchor=model.root().y/16f;
        for(int i=0;i<before.points.size();i++) compareScaled(before.points.get(i),after.points.get(i),anchor);
        var teleHead=new com.mojang.blaze3d.vertex.PoseStack(); model.headTransform(teleHead);
        var teleHand=new com.mojang.blaze3d.vertex.PoseStack(); model.handTransform(teleHand,false);
        var teleBall=new com.mojang.blaze3d.vertex.PoseStack(); model.ballTransform(teleBall);
        compareScaled(new Vector3f().mulPosition(normalHead.last().pose()),new Vector3f().mulPosition(teleHead.last().pose()),anchor);
        compareScaled(new Vector3f().mulPosition(normalHand.last().pose()),new Vector3f().mulPosition(teleHand.last().pose()),anchor);
        compareScaled(new Vector3f().mulPosition(normalBall.last().pose()),new Vector3f().mulPosition(teleBall.last().pose()),anchor);
        data(entity,"ACTION",""); entity.actionState.stop();
        System.out.println("TELEPORT_CHECK PASS all body vertices, head, hand and ball share midpoint scaling");
    }

    private static void compareScaled(Vector3f before,Vector3f after,float anchor) {
        var expected=new Vector3f(before.x*1.4f,anchor+(before.y-anchor)*.05f,before.z*1.4f);
        check(expected.distance(after)<.0001f,"body/accessory transform mismatch: "+expected+" vs "+after);
    }

    private static final class Capture implements com.mojang.blaze3d.vertex.VertexConsumer {
        final java.util.List<Vector3f> points=new java.util.ArrayList<>();
        public Capture addVertex(float x,float y,float z) { points.add(new Vector3f(x,y,z)); return this; }
        public Capture setColor(int r,int g,int b,int a) { return this; }
        public Capture setUv(float u,float v) { return this; }
        public Capture setUv1(int u,int v) { return this; }
        public Capture setUv2(int u,int v) { return this; }
        public Capture setNormal(float x,float y,float z) { return this; }
    }

    private static List<PartPose> sample(JehoashModel<JehoashEntity> model, JehoashEntity entity, int phase, float time) {
        entity.tickCount=(int)time;
        data(entity,"STAGE_PHASE",phase); data(entity,"STAGE_TIME",(int)time);
        model.setupAnim(entity,2,.35f,time,23,7);
        return model.root().getAllParts().map(p -> p.storePose()).toList();
    }

    private static void comparePoses(List<PartPose> a,List<PartPose> b,float tolerance,String label) {
        for(int i=0;i<a.size();i++) {
            PartPose x=a.get(i), y=b.get(i);
            check(Math.abs(x.x-y.x)<tolerance && Math.abs(x.y-y.y)<tolerance && Math.abs(x.z-y.z)<tolerance
                    && angle(x.xRot,y.xRot)<tolerance && angle(x.yRot,y.yRot)<tolerance && angle(x.zRot,y.zRot)<tolerance,
                    label+" joint "+i);
        }
    }
    private static float angle(float a,float b) { return Math.abs(net.minecraft.util.Mth.wrapDegrees((a-b)*net.minecraft.util.Mth.RAD_TO_DEG))*net.minecraft.util.Mth.DEG_TO_RAD; }
    private static Vector3f[] copy(Vector3f[] points) { return java.util.Arrays.stream(points).map(Vector3f::new).toArray(Vector3f[]::new); }
    private static void comparePoints(Vector3f[] a,Vector3f[] b,float tolerance,String label) {
        float max=0;
        for(int i=0;i<a.length;i++) max=Math.max(max,a[i].distance(b[i]));
        check(max<=tolerance,label+" max distance="+max);
    }
    private static Object state(ClothSheet cloth,JehoashEntity entity) { return ((Map<?,?>)field(cloth,"states")).get(entity); }
    private static Object field(Object object,String name) {
        try { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @SuppressWarnings("unchecked")
    private static <T> void data(JehoashEntity entity,String name,T value) {
        try {
            Class<?> owner=entity.getClass(); java.lang.reflect.Field f=null;
            while(owner!=null && f==null) {
                try { f=owner.getDeclaredField(name); } catch(NoSuchFieldException ignored) { owner=owner.getSuperclass(); }
            }
            if(f==null) throw new NoSuchFieldException(name);
            f.setAccessible(true);
            entity.getEntityData().set((EntityDataAccessor<T>)f.get(null),value);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean pass,String message) { if(!pass) throw new AssertionError(message); }
}
