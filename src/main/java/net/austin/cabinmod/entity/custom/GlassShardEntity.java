package net.austin.cabinmod.entity.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import java.util.UUID;

/** Steers briefly after release, then commits so a strafing player can dodge it. */
public class GlassShardEntity extends Projectile {
    private UUID targetId;
    private int life;
    public GlassShardEntity(EntityType<? extends GlassShardEntity> type, Level level) { super(type, level); }
    public void aim(LivingEntity target) { targetId = target.getUUID(); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (++life > 70 || (getOwner() != null && !getOwner().isAlive())) { discard(); return; }
            Entity target = targetId == null ? null : ((ServerLevel)level()).getEntity(targetId);
            if (life < 18 && target instanceof LivingEntity living && living.isAlive()
                    && (!(target instanceof Player p) || (!p.isCreative() && !p.isSpectator()))) {
                Vec3 desired = living.getEyePosition().subtract(position()).normalize().scale(.78);
                setDeltaMovement(getDeltaMovement().scale(.88).add(desired.scale(.12)).normalize().scale(.78));
            }
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (!level().isClientSide() && hit.getType() != HitResult.Type.MISS) {
            if (hit instanceof EntityHitResult entityHit) {
                entityHit.getEntity().hurt(damageSources().thrown(this, getOwner()), 6f);
            }
            ((ServerLevel)level()).sendParticles(ParticleTypes.END_ROD, hit.getLocation().x,
                    hit.getLocation().y, hit.getLocation().z, 8, .12, .12, .12, .03);
            playSound(SoundEvents.GLASS_BREAK, .8f, 1.5f);
            discard();
            return;
        }
        Vec3 next = position().add(getDeltaMovement());
        setPos(next.x, next.y, next.z);
        updateRotation();
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity
                && !(entity instanceof JehoashEntity) && !(entity instanceof JehoashIllusionEntity)
                && (!(entity instanceof Player p) || (!p.isCreative() && !p.isSpectator()));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Life", life);
        if (targetId != null) tag.putUUID("Target", targetId);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getInt("Life");
        if (tag.hasUUID("Target")) targetId = tag.getUUID("Target");
    }
}
