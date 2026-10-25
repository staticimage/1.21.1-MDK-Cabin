package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.DiscoMusic;
import net.austin.cabinmod.entity.client.physics.ClothChain;
import net.austin.cabinmod.entity.client.physics.ModelSpace;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// Original simple silhouette, dressed in a cult robe. Limbs have no fingers or extra joints.
// Tall, lanky shrouded raver with a floating disco ball; all motion here is procedural.
/*
 * MODEL / ANIMATION EDITING MAP
 * createBodyLayer() defines native Minecraft cuboids and UV coordinates (128x128 UV
 * space, regardless of PNG resolution). Elbows, wrists and knees are new articulated
 * parts; simple mitten hands are rendered by CultDaggerLayer. There is no finger rig.
 * jam() reads measured playback beats and DjPerformance targets. reach() uses a
 * 2-link, 13+13 pixel inverse-kinematics arm, with an outward/downward elbow pole;
 * changing arm lengths requires changing both the geometry and the 169 (=13*13) math.
 * The hand is counter-rotated to lie flat on the real platter/mixer target positions.
 * conduct() is the phase-one gesture loop. collapsePose() reads saved stage time and
 * animates noticing the beam, recoiling, guarding, vanishing and landing. It never
 * moves server collision boxes or the camera. JehoashRenderer hides four blink frames.
 * applyCloth() runs Verlet chains after body posing. Mesh fabric() creates sleeve,
 * hood, cape and strand segments. ClothChain handles inertia; tuning lives in addCloth.
 * For DIRECT image prints/UV mapping, use tools/textures/pack_direct_cult_art.py.
 */
public class JehoashModel<T extends ClipMob> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "joash_placeholder"), "main");

    private final ModelPart root, torso, robe, head, armR, armL, legR, legL, ball, handR, handL, foreR, foreL, shinR, shinL;
    private record Cloth(ClothChain chain, ModelPart[] path, boolean back, float phase) {}
    private final java.util.List<Cloth> cloth = new java.util.ArrayList<>();

    public JehoashModel(ModelPart meshRoot) {
        this.root = meshRoot.getChild("root");
        this.torso = root.getChild("torso");
        this.robe = torso.getChild("robe");
        this.head = torso.getChild("head");
        this.armR = torso.getChild("arm_r");
        this.armL = torso.getChild("arm_l");
        this.legR = root.getChild("leg_r");
        this.legL = root.getChild("leg_l");
        this.ball = root.getChild("ball");
        this.foreR = armR.getChild("forearm");
        this.foreL = armL.getChild("forearm");
        this.shinR = legR.getChild("shin");this.shinL = legL.getChild("shin");
        this.handR = foreR.getChild("hand");
        this.handL = foreL.getChild("hand");
        addCloth(torso,"cape",5,7f,true,0f,root,torso);
        for (int i=0;i<5;i++) addCloth(torso,"strand_"+i,6,6f,true,i*1.4f,root,torso);
        addCloth(foreR,"sleeve_r",3,4f,false,.7f,root,torso,armR,foreR);
        addCloth(foreL,"sleeve_l",3,4f,false,2.7f,root,torso,armL,foreL);
        addCloth(head,"hood_tail",3,4f,true,1.1f,root,torso,head);
        addCloth(head,"hood_l",2,4f,false,2.1f,root,torso,head);
        addCloth(head,"hood_r",2,4f,false,3.1f,root,torso,head);
    }

    // UV offsets come from the placeholder texture generator (textures/entity/joash/joash_placeholder.png).
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0f, 24f, 0f));

        root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(24, 0).addBox(-1.5f, 0f, -1.5f, 3f, 12f, 3f), PartPose.offset(-2.5f, -24f, 0f));
        root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5f, 0f, -1.5f, 3f, 12f, 3f), PartPose.offset(2.5f, -24f, 0f));

        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(88, 0).addBox(-5f, -18f, -3f, 10f, 18f, 6f), PartPose.offset(0f, -24f, 0f));
        torso.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(48, 0).addBox(-6f, 0f, -4f, 12f, 18f, 8f), PartPose.offset(0f, -1f, 0f));

        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(40, 29).addBox(-4f, -9f, -4f, 8f, 9f, 8f), PartPose.offset(0f, -18f, 0f));
        head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 29).addBox(-5f, -10f, -5f, 10f, 11f, 10f), PartPose.ZERO);

        torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1f, -1.5f, 3f, 12f, 3f, new CubeDeformation(1.1f)), PartPose.offset(-6.5f, -17f, 0f));
        torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(12, 0).addBox(-1.5f, -1f, -1.5f, 3f, 12f, 3f, new CubeDeformation(1.1f)), PartPose.offset(6.5f, -17f, 0f));

        armHand(torso.getChild("arm_r"),0);armHand(torso.getChild("arm_l"),12);
        for(String side:new String[]{"r","l"}) root.getChild("leg_"+side).addOrReplaceChild("shin",
                CubeListBuilder.create().texOffs(side.equals("r")?24:36,12).addBox(-1.5f,0,-1.5f,3,12,3),PartPose.offset(0,12,0));
        // Elbows, wrists and knees articulate beneath the uninterrupted robe silhouette.
        fabric(torso,"cape",0,-17,4.3f,10,7,5,0,56);
        for (int i=0;i<5;i++) fabric(torso,"strand_"+i,-5.4f+i*2.7f,-15,5.2f,1.8f,6,6,20,56);
        fabric(torso.getChild("arm_r").getChild("forearm"),"sleeve_r",0,6,2.4f,5,4,3,0,0);
        fabric(torso.getChild("arm_l").getChild("forearm"),"sleeve_l",0,6,2.4f,5,4,3,12,0);
        fabric(head,"hood_tail",0,-8,5.2f,5,4,3,0,56);
        fabric(head,"hood_l",4.7f,-3,1,2,4,2,20,56);
        fabric(head,"hood_r",-4.7f,-3,1,2,4,2,40,56);

        root.addOrReplaceChild("ball", CubeListBuilder.create(), PartPose.offset(-11f, -47f, -2f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float t = ageInTicks;
        // Articulation stays under broad sleeves and the ankle-length robe; hands remain simple mittens.

        robe.yScale = 1.25f;
        robe.xScale = 1.15f;

        // idle: breathing, a little sway, ball bobbing and spinning
        torso.y += Mth.sin(t * 0.08f) * 0.6f;
        torso.zRot = Mth.sin(t * 0.03f) * 0.03f;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD + Mth.sin(t * 0.05f) * 0.05f;
        head.zRot = Mth.sin(t * 0.04f) * 0.08f;
        armR.zRot = 0.08f + Mth.sin(t * 0.06f) * 0.04f;
        armL.zRot = -0.08f - Mth.sin(t * 0.06f) * 0.04f;
        ball.y += Mth.sin(t * 0.1f) * 1.5f;
        ball.yRot = t * 0.08f;
        ball.xRot = 0.3f;

        // walk: long slow strides, arms counter-swing, robe swishes
        float swing = Mth.cos(limbSwing * 0.5f) * 1.1f * limbSwingAmount;
        legR.xRot = swing;
        legL.xRot = -swing;
        armR.xRot = -swing * 0.6f;
        armL.xRot = swing * 0.6f;
        robe.xRot = -limbSwingAmount * 0.25f + Mth.sin(limbSwing * 0.5f) * 0.06f * limbSwingAmount;
        torso.y += Math.abs(Mth.sin(limbSwing * 0.5f)) * -0.8f * limbSwingAmount;

        if (entity instanceof JehoashEntity jehoash && jehoash.isDormant()) {
            jam(jehoash, ageInTicks);
            applyCloth(entity, ageInTicks);
            return;
        }

        if(entity instanceof JehoashEntity j && j.isCollapsing()) {
            collapsePose(j,ageInTicks);applyCloth(entity,ageInTicks);return;
        }
        if(entity instanceof JehoashEntity j && j.isConducting() && !j.currentAction().equals("interrupted")) {
            conduct(j,ageInTicks);applyCloth(entity,ageInTicks);return;
        }
        foreR.xRot=-.18f;foreL.xRot=-.12f;
        String action = entity.currentAction();
        if (!action.isEmpty()) {
            entity.actionState.updateTime(ageInTicks, 1f);
            float len = Math.max(entity.clipLengthTicks(action) / 20f, 0.05f);
            float p = Mth.clamp(entity.actionState.getAccumulatedTime() / 1000f / len, 0f, 1f);
            float env = Mth.sin(p * Mth.PI);
            switch (action) {
                case "attack_melee" -> {
                    float x;
                    if (p < 0.4f) x = Mth.lerp(ease(p / 0.4f), 0f, -2.8f);                 // wind up overhead
                    else if (p < 0.55f) x = Mth.lerp(ease((p - 0.4f) / 0.15f), -2.8f, -0.3f); // slash
                    else x = Mth.lerp(ease((p - 0.55f) / 0.45f), -0.3f, 0f);                 // recover
                    armR.xRot = x;foreR.xRot=-.25f+x*.25f;handR.zRot=-.15f*env;
                    torso.yRot = x * 0.15f;
                    torso.xRot = p > 0.4f && p < 0.7f ? 0.2f * env : 0f;
                }
                case "attack_ranged" -> {
                    armR.xRot = -2.4f * env;
                    armR.zRot += 0.35f * env;
                    armL.xRot = -2.1f * env;
                    armL.zRot -= 0.5f * env;
                    float pulse = 1f + 0.7f * Mth.sin(Mth.clamp((p - 0.3f) / 0.4f, 0f, 1f) * Mth.PI);
                    ball.xScale = ball.yScale = ball.zScale = pulse;
                    ball.yRot += p * 25f;
                }
                case "teleport" -> {
                    root.yScale = 1f - 0.95f * env;
                    root.xScale = root.zScale = 1f + 0.4f * env;
                }
                case "summon_illusions" -> {
                    armR.zRot += 1.4f * env;
                    armL.zRot -= 1.4f * env;
                    head.xRot -= 0.4f * env;
                    ball.yRot += p * 15f;
                }
                case "interrupted" -> {
                    if(entity instanceof JehoashEntity j && j.isBoothAttached()) {
                        float beats=DiscoMusic.interruptedAtBeat(DiscoMusic.key(entity));
                        if(!Float.isFinite(beats)) beats=DiscoMusic.beats(DiscoMusic.key(entity),ageInTicks);
                        var motion=DjPerformance.sample(j.recordTrack(),beats,.5f);
                        float time=p*entity.clipLengthTicks(action);
                        float lift=time>=36 && time<50 ? Mth.sin((time-36)/14*Mth.PI)*10 : 0;
                        torso.xRot=.04f;head.xRot=-.12f;
                        reach(armR,handR,new org.joml.Vector3f(motion.rightHand()).add(0,-lift,0));
                        reach(armL,handL,new org.joml.Vector3f(motion.leftHand()).add(0,-lift,0));
                    }
                }
                case "welcome" -> {
                    if (p < 0.5f) {
                        float o = ease(p / 0.5f);
                        armR.zRot += 1.3f * o;
                        armL.zRot -= 1.3f * o;
                    } else {
                        float b = Mth.sin((p - 0.5f) / 0.5f * Mth.PI);
                        armR.zRot += 1.3f * (1f - b);
                        armL.zRot -= 1.3f * (1f - b);
                        torso.xRot = 0.6f * b;
                        head.xRot += 0.3f * b;
                    }
                }
                default -> {
                }
            }
        }

        if (entity.isDeadOrDying()) {
            float death = Mth.clamp((entity.deathTime + ageInTicks - entity.tickCount) / 60f,0,1);
            root.y += death * 13f;
            torso.xRot = death * .8f;
            head.xRot = death * .5f;
            armR.xRot = armL.xRot = -death * 1.5f;
        }
        if (entity.hurtTime > 0) {
            float h = entity.hurtTime / 10f;
            torso.xRot -= 0.25f * h;
            head.xRot -= 0.2f * h;
        }
        applyCloth(entity, ageInTicks);
    }

    private static void armHand(PartDefinition arm,int u) {
        var fore=arm.addOrReplaceChild("forearm",CubeListBuilder.create().texOffs(u,13)
                .addBox(-1.5f,0,-1.5f,3,11.7f,3,new CubeDeformation(1.25f)),PartPose.offset(0,13,0));
        fore.addOrReplaceChild("hand",CubeListBuilder.create(),PartPose.offset(0,13,0));
    }
    private void conduct(JehoashEntity j,float age) {
        jam(j,age);
        float t=j.stageTime()+(age-j.tickCount), cue=(t%140)/140;
        float sweep=Mth.sin(cue*Mth.TWO_PI), lift=Mth.sin(cue*Mth.PI);
        torso.xRot=.06f;torso.yRot=.1f*sweep;head.xRot=-.1f;head.yRot=.3f*sweep;
        reach(armR,handR,new org.joml.Vector3f(-19-4*sweep,-19-10*lift,-17));
        if(cue>.5f) reach(armL,handL,new org.joml.Vector3f(18,-21-8*lift,-13));
        handR.xRot+=.6f;
    }
    private void collapsePose(JehoashEntity j,float age) {
        float t=j.stageTime()+(age-j.tickCount);
        torso.y=-24;torso.zRot=0;
        if(t<net.austin.cabinmod.entity.StageCollapse.DODGE) {
            float notice=ease(Mth.clamp((t-6)/18,0,1)), fear=ease(Mth.clamp((t-28)/25,0,1));
            head.xRot=-.9f*notice;head.yRot=.22f*Mth.sin(t*.19f)*notice;
            torso.xRot=-.12f*notice;torso.yRot=-.14f*fear;root.z=3*fear;
            legR.xRot=-.25f*fear;legL.xRot=.22f*fear;shinR.xRot=.35f*fear;
            reach(armR,handR,new org.joml.Vector3f(-14-3*fear,-6-25*fear,-18+10*fear));
            reach(armL,handL,new org.joml.Vector3f(12+3*fear,-6-23*fear,-17+11*fear));
            handR.xRot+=.7f*fear;handL.xRot+=.7f*fear;
            if(t>53) { float vanish=1-ease(Mth.clamp((t-53)/6,0,1));root.xScale=root.zScale=.12f+.88f*vanish;root.yScale=.5f+.5f*vanish; }
        } else {
            float rise=ease(Mth.clamp((t-67)/32,0,1)), crouch=1-rise;
            root.y+=7*crouch;torso.xRot=.45f*crouch;torso.yRot=-.2f*crouch;
            legR.xRot=-.8f*crouch;shinR.xRot=1.35f*crouch;legL.xRot=-.65f*crouch;shinL.xRot=1.2f*crouch;
            armR.xRot=-.65f*crouch;foreR.xRot=-.7f*crouch;armR.zRot=.18f;
            armL.xRot=-1.15f*crouch;foreL.xRot=-.45f*crouch;armL.zRot=-.4f*crouch;
            head.xRot=-.3f*crouch;
            float materialize=ease(Mth.clamp((t-61)/7,0,1));root.xScale=root.zScale=.15f+.85f*materialize;
        }
        ball.yRot=t*.2f;
    }
    private void jam(JehoashEntity entity,float age) {
        float beats=DiscoMusic.beats(DiscoMusic.key(entity),age);
        DjPerformance.Pose motion=DjPerformance.sample(entity.recordTrack(),beats,DiscoMusic.energy(DiscoMusic.key(entity)));
        torso.y=-24+.45f*motion.kick(); torso.xRot=.035f+.025f*motion.kick();
        torso.zRot=.018f*Mth.sin(beats*Mth.PI*.5f);
        head.xRot=.22f+.16f*motion.kick(); head.yRot=.07f*Mth.sin(beats*Mth.PI*.25f); head.zRot=.025f*Mth.sin(beats*Mth.PI*.5f);
        legR.xRot=legL.xRot=0;
        reach(armR,handR,motion.rightHand()); reach(armL,handL,motion.leftHand());
        ball.yRot=beats*Mth.HALF_PI; ball.y-=.45f*motion.kick();
    }
    private void reach(ModelPart arm,ModelPart hand,org.joml.Vector3f target) {
        var parent=ModelSpace.of(root,torso);
        var local=new org.joml.Matrix3f(parent.rotation()).transpose().transform(new org.joml.Vector3f(target).sub(parent.origin()))
                .sub(arm.x,arm.y,arm.z);
        float length=Math.min(25.95f,local.length());
        var direction=new org.joml.Vector3f(local).normalize();local.set(direction).mul(length);
        var pole=new org.joml.Vector3f(Math.signum(arm.x)*.55f,.9f,.2f);
        pole.sub(new org.joml.Vector3f(direction).mul(pole.dot(direction))).normalize();
        var elbow=new org.joml.Vector3f(direction).mul(length/2).add(pole.mul((float)Math.sqrt(169-length*length/4)));
        pointBone(arm,elbow);
        ModelPart fore=arm==armR?foreR:foreL;
        var distal=new org.joml.Matrix3f(ModelSpace.rotationOf(arm)).transpose().transform(new org.joml.Vector3f(local).sub(elbow));
        pointBone(fore,distal);
        var rotation=new org.joml.Matrix3f(parent.rotation()).mul(ModelSpace.rotationOf(arm)).mul(ModelSpace.rotationOf(fore)).transpose();
        var angles=rotation.getEulerAnglesZYX(new org.joml.Vector3f());
        hand.xRot=angles.x;hand.yRot=angles.y;hand.zRot=angles.z;
    }
    private static void pointBone(ModelPart bone,org.joml.Vector3f direction) {
        bone.xRot=(float)Math.asin(Mth.clamp(direction.z/direction.length(),-1,1));bone.yRot=0;bone.zRot=(float)Math.atan2(-direction.x,direction.y);
    }
    public void handTransform(com.mojang.blaze3d.vertex.PoseStack pose,boolean left) {
        root.translateAndRotate(pose);torso.translateAndRotate(pose);
        (left?armL:armR).translateAndRotate(pose);(left?foreL:foreR).translateAndRotate(pose);(left?handL:handR).translateAndRotate(pose);
    }
    public void daggerTransform(com.mojang.blaze3d.vertex.PoseStack pose) { handTransform(pose,false); }

    private static void fabric(PartDefinition parent,String name,float x,float y,float z,float width,float length,int count,int u,int v) {
        for (int i=0;i<count;i++) {
            parent = parent.addOrReplaceChild(name+"_"+i, CubeListBuilder.create().texOffs(u,v)
                    .addBox(-width/2,0,-.25f,width,length+.35f,.5f),
                    PartPose.offset(i==0?x:0,i==0?y:length,i==0?z:0));
        }
    }
    private void addCloth(ModelPart parent,String name,int count,float length,boolean back,float phase,ModelPart... path) {
        ModelPart[] segments = new ModelPart[count];
        for (int i=0;i<count;i++) { parent = parent.getChild(name+"_"+i); segments[i] = parent; }
        ClothChain chain = new ClothChain(length,segments).gravity(160).damping(.91f)
                .stiffness(name.startsWith("hood") ? .14f : .035f).drag(2.6f);
        if (name.startsWith("cape") || name.startsWith("strand")) chain.bodyRadius(5.6f);
        cloth.add(new Cloth(chain,path,back,phase));
    }
    private void applyCloth(T entity,float age) {
        boolean dj = entity instanceof JehoashEntity j && j.isDormant();
        float beat = DiscoMusic.beats(DiscoMusic.key(entity),age);
        float kick = dj ? (float)Math.exp(-(beat-Mth.floor(beat))*6) * DiscoMusic.energy(DiscoMusic.key(entity)) : 0;
        float flare = entity.currentAction().equals("summon_illusions") ? 140 : 0;
        if (entity.currentAction().equals("interrupted")) {
            float seconds = entity.actionState.getAccumulatedTime()/1000f;
            flare = seconds > 2.8f && seconds < 3.6f ? 210 : 0;
        }
        if(entity instanceof JehoashEntity j && j.isCollapsing()) flare=j.stageTime()<64?180:60;
        final float burst = flare;
        for (Cloth c : cloth) {
            c.chain.apply(entity,age,ModelSpace.of(c.path),(index,pos,out) -> {
                float tip = index/(float)c.chain.size();
                float wave = Mth.sin(age*.13f + c.phase + index*.8f);
                out.add(wave*(dj?48:16)*tip, -(dj ? 110+kick*100 : 0)*tip,
                        c.back ? (dj?70:20)*tip+burst : 0);
                if (!c.back) out.add(Math.signum(pos.x)*(kick*90+burst)*tip,0,wave*18);
            });
        }
    }

    private static float ease(float x) {
        return x * x * (3f - 2f * x);
    }

    public void ballTransform(com.mojang.blaze3d.vertex.PoseStack pose) {
        root.translateAndRotate(pose); ball.translateAndRotate(pose);
    }

    @Override
    public ModelPart root() {
        return root;
    }
}
