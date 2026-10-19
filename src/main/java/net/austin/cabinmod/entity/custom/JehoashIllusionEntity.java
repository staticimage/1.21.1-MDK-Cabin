package net.austin.cabinmod.entity.custom;

import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// A decoy Jehoash: looks and moves like him (same clips), but does no damage and pops when hit.
public class JehoashIllusionEntity extends ClipMob {
    private static final int LIFETIME = 300;

    private int mimicCooldown = 20;

    public JehoashIllusionEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    public String mobId() {
        return JehoashEntity.MOB;
    }

    @Override
    protected Fallback fallback(String clip) {
        return switch (clip) {
            case "attack_melee" -> Fallback.of(20);
            case "attack_ranged" -> Fallback.of(30);
            default -> Fallback.of(20);
        };
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.tickCount > LIFETIME) {
            pop();
            return;
        }
        if (mimicCooldown > 0) mimicCooldown--;
        LivingEntity target = this.getTarget();
        if (target == null || isActing()) return;
        this.getLookControl().setLookAt(target, 30f, 30f);
        double dist = this.distanceTo(target);
        if (mimicCooldown == 0 && dist < 3.5) {
            startAction("attack_melee");                 // same swing as the real one, no damage
            mimicCooldown = 25;
        } else if (mimicCooldown == 0 && dist > 6 && this.random.nextInt(40) == 0) {
            startAction("attack_ranged");                // fake laser wind-up
            mimicCooldown = 60;
        } else if (dist > 2.5) {
            this.getNavigation().moveTo(target, 1.0);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved()) return false;
        if (source.getEntity() != null || source.getDirectEntity() != null) {
            pop();
            return true;
        }
        return false;
    }

    public void pop() {
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1.5, getZ(), 40, 0.5, 1.2, 0.5, 0.1);
            this.playSound(ModSounds.JOASH_ILLUSION_POP.get(), 1.5f, 1.2f);
            this.discard();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }
}
