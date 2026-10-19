package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// TEMPORARY stand-in for playtesting until the Blender rig is exported (then JehoashModelBase replaces it).
// Tall, lanky shrouded raver with a floating disco ball; all motion here is procedural.
public class JehoashPlaceholderModel<T extends ClipMob> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "joash_placeholder"), "main");

    private final ModelPart root, torso, robe, head, armR, armL, legR, legL, ball;

    public JehoashPlaceholderModel(ModelPart meshRoot) {
        this.root = meshRoot.getChild("root");
        this.torso = root.getChild("torso");
        this.robe = torso.getChild("robe");
        this.head = torso.getChild("head");
        this.armR = torso.getChild("arm_r");
        this.armL = torso.getChild("arm_l");
        this.legR = root.getChild("leg_r");
        this.legL = root.getChild("leg_l");
        this.ball = root.getChild("ball");
    }

    // UV offsets come from the placeholder texture generator (textures/entity/joash/joash_placeholder.png).
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0f, 24f, 0f));

        root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(24, 0).addBox(-1.5f, 0f, -1.5f, 3f, 24f, 3f), PartPose.offset(-2.5f, -24f, 0f));
        root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5f, 0f, -1.5f, 3f, 24f, 3f), PartPose.offset(2.5f, -24f, 0f));

        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(88, 0).addBox(-5f, -18f, -3f, 10f, 18f, 6f), PartPose.offset(0f, -24f, 0f));
        torso.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(48, 0).addBox(-6f, 0f, -4f, 12f, 18f, 8f), PartPose.offset(0f, -1f, 0f));

        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(40, 29).addBox(-4f, -9f, -4f, 8f, 9f, 8f), PartPose.offset(0f, -18f, 0f));
        head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 29).addBox(-5f, -10f, -5f, 10f, 11f, 10f), PartPose.ZERO);

        torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1f, -1.5f, 3f, 26f, 3f), PartPose.offset(-6.5f, -17f, 0f));
        torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(12, 0).addBox(-1.5f, -1f, -1.5f, 3f, 26f, 3f), PartPose.offset(6.5f, -17f, 0f));

        root.addOrReplaceChild("ball", CubeListBuilder.create().texOffs(72, 29).addBox(-3f, -3f, -3f, 6f, 6f, 6f), PartPose.offset(-11f, -47f, -2f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float t = ageInTicks;

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
            torso.xRot = 0.25f;
            head.xRot = 0.6f;
            armR.xRot = armL.xRot = 0.1f;
            ball.y += 6f;
            return;
        }

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
                    armR.xRot = x;
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

        if (entity.hurtTime > 0) {
            float h = entity.hurtTime / 10f;
            torso.xRot -= 0.25f * h;
            head.xRot -= 0.2f * h;
        }
    }

    private static float ease(float x) {
        return x * x * (3f - 2f * x);
    }

    @Override
    public ModelPart root() {
        return root;
    }
}
