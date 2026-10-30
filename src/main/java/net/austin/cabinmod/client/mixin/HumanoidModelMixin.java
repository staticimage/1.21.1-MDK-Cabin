package net.austin.cabinmod.client.mixin;

import net.austin.cabinmod.client.diviner.HeavensFx;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A player up in the heavens through a diviner, as everyone else sees them (client/diviner/HeavensFx): arms open
// and raised, head tipped back to the sky, legs hanging. Runs at the end of HumanoidModel#setupAnim, before
// PlayerModel copies the arms and legs onto its sleeves and trousers.
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void cabinmod$heavensTrance(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                                        float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        float k = HeavensFx.tranceAmount(player, ageInTicks - (int) ageInTicks);
        if (k <= 0) return;
        float sway = Mth.sin(ageInTicks * 0.08f) * 0.06f;
        head.xRot = Mth.lerp(k, head.xRot, -0.55f);
        head.yRot = Mth.lerp(k, head.yRot, 0f);
        hat.copyFrom(head);
        rightArm.xRot = Mth.lerp(k, rightArm.xRot, -2.4f + sway);
        rightArm.zRot = Mth.lerp(k, rightArm.zRot, 0.55f);
        rightArm.yRot = Mth.lerp(k, rightArm.yRot, 0f);
        leftArm.xRot = Mth.lerp(k, leftArm.xRot, -2.4f - sway);
        leftArm.zRot = Mth.lerp(k, leftArm.zRot, -0.55f);
        leftArm.yRot = Mth.lerp(k, leftArm.yRot, 0f);
        rightLeg.xRot = Mth.lerp(k, rightLeg.xRot, 0.12f);
        leftLeg.xRot = Mth.lerp(k, leftLeg.xRot, 0.05f);
        rightLeg.zRot = Mth.lerp(k, rightLeg.zRot, 0.04f);
        leftLeg.zRot = Mth.lerp(k, leftLeg.zRot, -0.04f);
    }
}
