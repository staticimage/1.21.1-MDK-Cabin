package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.DiscoMusic;
import net.austin.cabinmod.entity.StageCollapse;
import net.austin.cabinmod.entity.client.physics.ClothSheet;
import net.austin.cabinmod.entity.client.generated.JehoashOutfitGeometry;
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

// A BRG-print cloak over a broad cuboid body painted from the supplied wing artwork.
/*
 * MODEL / ANIMATION EDITING MAP
 * createBodyLayer() defines the skeleton; JehoashOutfitGeometry supplies fitted cubes (128x128 UV
 * space, regardless of PNG resolution). Elbows, wrists and knees are articulated
 * parts; fitted gloves follow the wrist bones. There is no finger rig.
 * jam() reads measured playback beats and DjPerformance targets. reach() uses a
 * 2-link, 13+13 pixel inverse-kinematics arm, with an outward/downward elbow pole;
 * changing arm lengths requires changing both the geometry and the 169 (=13*13) math.
 * The hand is counter-rotated to lie flat on the real platter/mixer target positions.
 * conduct() is the phase-one gesture loop. collapsePose() reads saved stage time and
 * animates noticing the beam, recoiling, guarding, vanishing and landing. It never
 * moves server collision boxes or the camera. JehoashRenderer hides four blink frames.
 * applyCloth() pins the complete robe, cape, hood and sleeves to animated bones,
 * then advances their common fabric simulation in model space.
 * The active painted atlas and editable XCF source are listed in README.md.
 */
public class JehoashModel<T extends ClipMob> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "joash"), "main");

    private final ModelPart root, torso, head, armR, armL, legR, legL, ball, handR, handL, foreR, foreL, shinR, shinL;
    private final ClothSheet fullFabric = new ClothSheet(true);

    public JehoashModel(ModelPart meshRoot) {
        this.root = meshRoot.getChild("root");
        this.torso = root.getChild("torso");
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
    }

    // Outfit cubes/UVs are generated together; the skeleton remains the cloth/IK contract.
    public static final ResourceLocation BODY_TEXTURE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
            "textures/entity/joash/jehoash_phase_two.png");

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root",CubeListBuilder.create(),PartPose.offset(0,24,0));
        for(String side:new String[]{"r","l"}) {
            var leg=root.addOrReplaceChild("leg_"+side,CubeListBuilder.create(),PartPose.offset(side.equals("r")?-2.5f:2.5f,-24,0));
            leg.addOrReplaceChild("shin",CubeListBuilder.create(),PartPose.offset(0,12,0));
        }
        var torso=root.addOrReplaceChild("torso",CubeListBuilder.create(),PartPose.offset(0,-24,0));
        torso.addOrReplaceChild("revealed_core",CubeListBuilder.create(),PartPose.ZERO);
        torso.addOrReplaceChild("head",CubeListBuilder.create(),PartPose.offset(0,-18,0));
        for(String side:new String[]{"r","l"}) {
            var arm=torso.addOrReplaceChild("arm_"+side,CubeListBuilder.create(),PartPose.offset(side.equals("r")?-7:7,-16.5f,0));
            var fore=arm.addOrReplaceChild("forearm",CubeListBuilder.create(),PartPose.offset(0,13,0));
            fore.addOrReplaceChild("hand",CubeListBuilder.create(),PartPose.offset(0,13,0));
        }
        root.addOrReplaceChild("ball",CubeListBuilder.create(),PartPose.offset(-11,-47,-2));
        JehoashOutfitGeometry.add(root);
        return LayerDefinition.create(mesh,128,128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float t = ageInTicks;
        // The same articulated skeleton carries the covered and revealed appearances.

        boolean shed = usesRevealedBody(entity, ageInTicks);
        torso.getChild("revealed_core").visible = shed;
        // The fuller shoulders stay covered until sleeve pins release. Gloves are
        // drawn at the wrist by the layer while the arm hierarchy is hidden.
        armR.visible = armL.visible = hasShedCloth(entity,ageInTicks);
        legR.visible = legL.visible = true;

        idleAndWalk(limbSwing, limbSwingAmount, t, netHeadYaw, headPitch);

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

        if (entity instanceof JehoashEntity j && j.stagePhase()==JehoashEntity.ON_FOOT
                && j.stageTime()>=StageCollapse.END && j.stageTime()<StageCollapse.REVEAL_RECOVERY_END) {
            blendRevealRecovery(j, ageInTicks, limbSwing, limbSwingAmount, netHeadYaw, headPitch);
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

    private void idleAndWalk(float limbSwing, float limbSwingAmount, float t, float netHeadYaw, float headPitch) {
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
        torso.y += Math.abs(Mth.sin(limbSwing * 0.5f)) * -0.8f * limbSwingAmount;

    }

    private void blendRevealRecovery(JehoashEntity j, float age, float swing, float amount, float yaw, float pitch) {
        float blend = StageCollapse.revealRecovery(j.stageTime()+age-j.tickCount);
        // Capture the normal locomotion/action pose, then blend every joint from the
        // same cinematic pose used before END. Death/hit reactions are applied later.
        var parts = root.getAllParts().toList();
        var normal = parts.stream().map(ModelPart::storePose).toList();
        float sx=root.xScale, sy=root.yScale, sz=root.zScale;
        parts.forEach(ModelPart::resetPose);
        idleAndWalk(swing,amount,age,yaw,pitch);
        collapsePose(j,age);
        for (int i=0;i<parts.size();i++) {
            ModelPart part=parts.get(i);
            PartPose target=normal.get(i);
            part.x=Mth.lerp(blend,part.x,target.x); part.y=Mth.lerp(blend,part.y,target.y); part.z=Mth.lerp(blend,part.z,target.z);
            part.xRot=blendRadians(blend,part.xRot,target.xRot);
            part.yRot=blendRadians(blend,part.yRot,target.yRot);
            part.zRot=blendRadians(blend,part.zRot,target.zRot);
        }
        root.xScale=Mth.lerp(blend,root.xScale,sx);
        root.yScale=Mth.lerp(blend,root.yScale,sy);
        root.zScale=Mth.lerp(blend,root.zScale,sz);
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
            // Both arms tear outward as the last pins release. Follow-through carries
            // through the first on-foot frames rather than snapping to the idle pose.
            float throwAway=StageCollapse.garmentThrow(t);
            armR.zRot-=1.35f*throwAway;armL.zRot+=1.35f*throwAway;
            armR.xRot-=.45f*throwAway;armL.xRot-=.45f*throwAway;
            torso.xRot-=.18f*throwAway;head.xRot-=.22f*throwAway;
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
    public void renderCoveredHands(T entity,float age,com.mojang.blaze3d.vertex.PoseStack pose,
                                   net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay) {
        if (hasShedCloth(entity,age)) return; // Exposed gloves render in the native hierarchy.
        for(boolean left:new boolean[]{false,true}) {
            pose.pushPose();
            handTransform(pose,left);
            (left?handL:handR).getChild("outfit").render(pose,
                    buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(BODY_TEXTURE)),light,overlay);
            pose.popPose();
        }
    }

    public void daggerTransform(com.mojang.blaze3d.vertex.PoseStack pose) { handTransform(pose,false); }

    public void headTransform(com.mojang.blaze3d.vertex.PoseStack pose) {
        root.translateAndRotate(pose); torso.translateAndRotate(pose); head.translateAndRotate(pose);
    }

    public void torsoTransform(com.mojang.blaze3d.vertex.PoseStack pose) {
        root.translateAndRotate(pose); torso.translateAndRotate(pose);
    }

    public void renderCloth(T entity, com.mojang.blaze3d.vertex.PoseStack pose,
                            net.minecraft.client.renderer.MultiBufferSource buffers,
                            net.minecraft.resources.ResourceLocation texture, int light, int overlay) {
        if (entity instanceof net.austin.cabinmod.entity.custom.JehoashIllusionEntity
                || entity instanceof JehoashEntity j && j.stagePhase()==JehoashEntity.ON_FOOT
                && j.stageTime()<StageCollapse.END) return;
        pose.pushPose();
        if (root.xScale != 1f || root.yScale != 1f || root.zScale != 1f) {
            pose.translate(0, 24f / 16f, 0);
            pose.scale(root.xScale, root.yScale, root.zScale);
            pose.translate(0, -24f / 16f, 0);
        }
        fullFabric.render(entity, pose, buffers, texture, light, overlay);
        pose.popPose();
    }

    private void applyCloth(T entity,float age) {
        if (entity instanceof net.austin.cabinmod.entity.custom.JehoashIllusionEntity
                || entity instanceof JehoashEntity j && j.stagePhase()==JehoashEntity.ON_FOOT
                && j.stageTime()<StageCollapse.END) return;
        boolean dj = entity instanceof JehoashEntity j && j.isDormant();
        float beat = DiscoMusic.beats(DiscoMusic.key(entity),age);
        float kick = dj ? (float)Math.exp(-(beat-Mth.floor(beat))*6) * DiscoMusic.energy(DiscoMusic.key(entity)) : 0;
        float flare = entity.currentAction().equals("summon_illusions") ? 140 : 0;
        if (entity.currentAction().equals("interrupted")) {
            float seconds = entity.actionState.getAccumulatedTime()/1000f;
            flare = seconds > 2.8f && seconds < 3.6f ? 210 : 0;
        }
        if(entity instanceof JehoashEntity j && j.isCollapsing()) flare=j.stageTime()<64?180:60;
        float wind = Mth.sin(age * .12f) * (dj ? 135f : 55f) + flare;
        var posed = new ClothSheet.Pose(ModelSpace.of(root,torso), ModelSpace.of(root,torso,head),
                ModelSpace.of(root,torso,armR), ModelSpace.of(root,torso,armL),
                ModelSpace.of(root,torso,armR,foreR), ModelSpace.of(root,torso,armL,foreL));
        boolean detach = hasShedCloth(entity, age)
                || entity.isDeadOrDying() && entity.deathTime + age - entity.tickCount >= 22f;
        fullFabric.update(entity, age, posed, wind + kick * 100f, detach);
    }

    private static boolean usesRevealedBody(ClipMob entity, float age) {
        return hasShedCloth(entity,age) || entity instanceof JehoashEntity j && j.isCollapsing()
                && j.stageTime()+age-j.tickCount >= StageCollapse.REVEAL_START;
    }

    private static boolean hasShedCloth(ClipMob entity, float age) {
        if (entity instanceof net.austin.cabinmod.entity.custom.JehoashIllusionEntity) return true;
        if (!(entity instanceof JehoashEntity j)) return false;
        return j.isCollapsing() && j.stageTime() + age - entity.tickCount >= StageCollapse.CLOTH_RELEASE
                || j.stagePhase() == JehoashEntity.ON_FOOT && !j.isBoothAttached();
    }

    private static float blendRadians(float blend, float from, float to) {
        return from + blend * Mth.wrapDegrees((to-from)*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD;
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
