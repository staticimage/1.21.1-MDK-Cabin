package net.austin.cabinmod.debug;

import com.google.gson.GsonBuilder;
import net.austin.cabinmod.entity.client.physics.ClothSheet;
import net.austin.cabinmod.entity.client.physics.PlayerRobePose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native mesh and simulation checks. This entire package is excluded from release builds. */
final class JehoashRobeChecks {
    static void run(AbstractClientPlayer wearer,Path output) {
        for(boolean slim:new boolean[]{false,true}) {
            PlayerModel<AbstractClientPlayer> model=new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(
                    slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
            for(int fps:new int[]{30,60,120}) {
                ClothSheet cloth=ClothSheet.player(slim);
                float age=0;
                for(String action:List.of("stand","walk","crouch","bow","swim","ride","look","stand")) {
                    for(int frame=0;frame<fps/3;frame++) {
                        age+=20f/fps;
                        animate(model,wearer,action,age);
                        var pose=PlayerRobePose.of(model,frame(new Vec3(0,90,0),action.equals("swim")),false);
                        cloth.updatePlayer(wearer,age,pose,true);
                        checkState(cloth,wearer);
                    }
                    if(List.of("stand","crouch","bow").contains(action)) checkFrontCoverage(cloth,wearer,model);
                }
                float end=age;
                var pose=PlayerRobePose.of(model,frame(new Vec3(30000000,90,-30000000),false),false);
                cloth.updatePlayer(wearer,end+1,pose,true);
                checkState(cloth,wearer);
                cloth.forget(wearer);
                check(((Map<?,?>)field(cloth,"states")).isEmpty(),"unequip clears simulation state");
                cloth.updatePlayer(wearer,end+2,pose,true);
                checkState(cloth,wearer);
                System.out.println("ROBE_CHECK PASS "+(slim?"slim":"classic")+" "+fps+" FPS: poses, torso/leg coverage, teleport, re-equip, finite vertices");
            }
            for(String action:List.of("stand","walk","crouch","bow","swim","ride","look")) {
                var cloth=ClothSheet.player(slim);
                animate(model,wearer,action,10);
                var pose=PlayerRobePose.of(model,frame(new Vec3(0,90,0),action.equals("swim")),false);
                cloth.updatePlayer(wearer,10,pose,false);
                export(cloth,wearer,output.resolve((slim?"slim":"classic")+"-"+action+".json"));
            }
            for(boolean right:new boolean[]{false,true}) {
                var sleeve=ClothSheet.playerSleeve(slim,right);
                animate(model,wearer,"stand",0);
                sleeve.updatePlayer(wearer,0,PlayerRobePose.of(model,frame(Vec3.ZERO,false),false),true);
                checkState(sleeve,wearer);
                Object[] vertices=(Object[])field(sleeve,"vertices");
                for(Object vertex:vertices) check(field(vertex,"part").toString().equals(right?"SLEEVE_R":"SLEEVE_L"),"view model contains only requested sleeve");
            }
            var rest=ClothSheet.player(slim);
            for(Object vertex:(Object[])field(rest,"vertices")) {
                if(field(vertex,"part").toString().equals("HOOD")) {
                    Vector3f p=(Vector3f)field(vertex,"rest");
                    check(!(p.y> -7.5f && p.z< -3.9f && Math.abs(p.x)<4.1f),"hood opening leaves player's face clear");
                }
            }
        }
        System.out.println("ROBE_CHECK PASS open hood, isolated first-person sleeves, posed previews, both model profiles");
    }

    static ClothSheet.PlayerFrame frame(Vec3 origin,boolean horizontal) {
        var matrix=new Matrix4f().rotationY((float)Math.PI);
        if(horizontal) matrix.rotateX(-(float)Math.PI/2);
        matrix.scale(-.9375f,-.9375f,.9375f).translate(0,-1.501f,0).scale(1f/16);
        return new ClothSheet.PlayerFrame(origin,matrix);
    }

    private static void animate(PlayerModel<AbstractClientPlayer> model,AbstractClientPlayer wearer,String action,float age) {
        model.attackTime=0;model.crouching=action.equals("crouch");model.riding=action.equals("ride");
        model.swimAmount=action.equals("swim")?1:0;
        model.rightArmPose=action.equals("bow")?HumanoidModel.ArmPose.BOW_AND_ARROW:HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose=HumanoidModel.ArmPose.EMPTY;
        model.setupAnim(wearer,age*.5f,action.equals("walk")?.8f:0,age,action.equals("look")?65:0,action.equals("look")?35:0);
    }

    private static void checkState(ClothSheet cloth,AbstractClientPlayer wearer) {
        Object state=((Map<?,?>)field(cloth,"states")).get(wearer);
        check(state!=null && !(boolean)field(state,"detached") && !(boolean)field(state,"expired"),"worn robe never detaches or expires");
        for(Vector3f point:(Vector3f[])field(state,"renderPosition"))
            check(point.isFinite() && point.lengthSquared()<65536,"finite local cloth positions");
    }

    /** The front fabric must still cover the skin after a full sequence of poses. */
    private static void checkFrontCoverage(ClothSheet cloth,AbstractClientPlayer wearer,PlayerModel<?> model) {
        Object state=((Map<?,?>)field(cloth,"states")).get(wearer);
        Vector3f[] points=(Vector3f[])field(state,"renderPosition");
        Object[] vertices=(Object[])field(cloth,"vertices"),quads=(Object[])field(cloth,"quads");
        List<Vector3f> samples=new ArrayList<>();
        for(float y:new float[]{3,7}) for(float x:new float[]{-3,-1,1,3})
            samples.add(PlayerRobePose.transform(model.body).apply(new Vector3f(x,y,-2.1f)));
        for(var leg:List.of(model.rightLeg,model.leftLeg))
            for(float y:new float[]{1,4,8}) for(float x:new float[]{-.8f,.8f})
                samples.add(PlayerRobePose.transform(leg).apply(new Vector3f(x,y,-2.1f)));
        for(Vector3f sample:samples) {
            boolean covered=false;
            for(Object quad:quads) {
                int a=(int)field(quad,"a"),b=(int)field(quad,"b"),c=(int)field(quad,"c"),d=(int)field(quad,"d");
                if(!field(vertices[a],"part").toString().equals("ROBE")) continue;
                if(inFront(points[a],points[b],points[c],sample) || inFront(points[a],points[c],points[d],sample)) {
                    covered=true;break;
                }
            }
            check(covered,"front robe covers torso/legs at "+sample);
        }
    }

    private static boolean inFront(Vector3f a,Vector3f b,Vector3f c,Vector3f sample) {
        float determinant=(b.y-c.y)*(a.x-c.x)+(c.x-b.x)*(a.y-c.y);
        if(Math.abs(determinant)<1e-6f) return false;
        float u=((b.y-c.y)*(sample.x-c.x)+(c.x-b.x)*(sample.y-c.y))/determinant;
        float v=((c.y-a.y)*(sample.x-c.x)+(a.x-c.x)*(sample.y-c.y))/determinant;
        return u>=0 && v>=0 && u+v<=1 && u*a.z+v*b.z+(1-u-v)*c.z<sample.z;
    }

    private static void export(ClothSheet cloth,AbstractClientPlayer wearer,Path path) {
        Object state=((Map<?,?>)field(cloth,"states")).get(wearer);
        Vector3f[] points=(Vector3f[])field(state,"renderPosition");
        Object[] vertices=(Object[])field(cloth,"vertices");
        List<Object> data=new ArrayList<>();
        for(int i=0;i<vertices.length;i++) data.add(List.of(points[i].x,points[i].y,points[i].z,
                field(vertices[i],"u"),field(vertices[i],"v"),field(vertices[i],"part").toString()));
        List<Object> faces=new ArrayList<>();
        for(Object quad:(Object[])field(cloth,"quads")) faces.add(List.of(field(quad,"a"),field(quad,"b"),field(quad,"c"),field(quad,"d")));
        var json=new LinkedHashMap<String,Object>();
        json.put("vertices",data);json.put("quads",faces);
        try { Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().create().toJson(json)); }
        catch(java.io.IOException e) { throw new AssertionError(e); }
    }

    private static Object field(Object owner,String name) {
        try { var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
