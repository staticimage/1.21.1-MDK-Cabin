package net.austin.cabinmod.entity.custom;

import net.austin.cabinmod.effect.ModEffects;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Jehoash the Disco Destroyer. Waits dormant in The Disco until a player uses the DJ booth, then:
// melee up close, a dodgeable disco laser at range, and every so often blinks around the arena
// leaving decoy illusions behind. Players near him get Life of the Party, elder-guardian style.
public class JehoashEntity extends ClipMob {
    public static final String MOB = "joash";

    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int ARENA_RADIUS = 11;           // 24x24 dance floor
    private static final double MELEE_RANGE = 3.5;
    private static final double LASER_RANGE = 32;
    private static final float LASER_DAMAGE = 8f;
    private static final int ILLUSION_COUNT = 3;

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private BlockPos home;
    private int meleeCooldown = 0;
    private int laserCooldown = 60;
    private int blinkCooldown = 240;
    @Nullable
    private Vec3 laserAim;
    private final List<UUID> illusions = new ArrayList<>();

    public JehoashEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 150;
        this.bossEvent.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 250)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.30)          // a creeper running from a cat
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    public String mobId() {
        return MOB;
    }

    @Override
    protected Fallback fallback(String clip) {
        return switch (clip) {
            case "welcome" -> Fallback.of(60, "sfx", 0);
            case "attack_melee" -> Fallback.of(20, "hit", 10);
            case "attack_ranged" -> Fallback.of(30, "fire", 18);
            case "teleport" -> Fallback.of(20, "move:blink", 10);
            case "summon_illusions" -> Fallback.of(24, "summon", 12);
            case "death" -> Fallback.of(60);
            default -> Fallback.of(20);
        };
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DORMANT, true);
    }

    public boolean isDormant() {
        return this.entityData.get(DORMANT);
    }

    // Called by the DJ booth.
    public void wake(Player player) {
        if (!isDormant()) return;
        this.entityData.set(DORMANT, false);
        if (this.home == null) this.home = this.blockPosition();
        this.setTarget(player);
        this.bossEvent.setVisible(true);
        startAction("welcome");
        playSound(ModSounds.JOASH_WELCOME.get(), 3f, 1f);
        cursePlayersNearby();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (isDormant()) {
            this.getNavigation().stop();
            this.setTarget(null);
            return;
        }
        if (this.tickCount % 20 == 0) cursePlayersNearby();
        if (meleeCooldown > 0) meleeCooldown--;
        if (laserCooldown > 0) laserCooldown--;
        if (blinkCooldown > 0) blinkCooldown--;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        this.getLookControl().setLookAt(target, 30f, 30f);

        if (isActing()) {
            if (!currentAction().equals("attack_melee")) this.getNavigation().stop();
            return;
        }

        double dist = this.distanceTo(target);
        if (blinkCooldown == 0) {
            startAction("teleport");
            blinkCooldown = 200 + this.random.nextInt(120);
        } else if (dist <= MELEE_RANGE && meleeCooldown == 0) {
            startAction("attack_melee");
            meleeCooldown = 25;
        } else if (dist > 5 && laserCooldown == 0 && this.hasLineOfSight(target)) {
            this.laserAim = target.getEyePosition();       // locked now, fired on the "fire" marker
            startAction("attack_ranged");
            laserCooldown = 50 + this.random.nextInt(30);
        } else if (dist > 2.5) {
            this.getNavigation().moveTo(target, 1.0);
        } else {
            this.getNavigation().stop();
        }
    }

    @Override
    protected void onClipEvent(String clip, String type, String arg) {
        LivingEntity target = this.getTarget();
        switch (type) {
            case "hit" -> {
                if (target != null && this.distanceTo(target) <= MELEE_RANGE + 0.5) this.doHurtTarget(target);
            }
            case "fire" -> fireLaser();
            case "move" -> {
                if (arg.equals("blink")) blink();
            }
            case "summon" -> summonIllusions();
            case "sfx" -> {
            }
            default -> {
            }
        }
    }

    @Override
    protected void onClipEnd(String clip) {
        if (clip.equals("teleport")) startAction("summon_illusions");
    }

    private void fireLaser() {
        if (this.laserAim == null || !(this.level() instanceof ServerLevel level)) return;
        Vec3 from = this.position().add(0, 3.0, 0);        // the floating disco ball
        Vec3 dir = this.laserAim.subtract(from).normalize();
        Vec3 to = from.add(dir.scale(LASER_RANGE));
        HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, from, to,
                this.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof JehoashEntity) && !(e instanceof JehoashIllusionEntity));
        if (hit != null) {
            to = hit.getLocation();
            hit.getEntity().hurt(this.damageSources().mobAttack(this), LASER_DAMAGE);
        }
        // rainbow beam
        double len = from.distanceTo(to);
        for (double d = 0; d < len; d += 0.3) {
            float hue = (float) ((d / 6 + this.tickCount * 0.05) % 1.0);
            int rgb = Mth.hsvToRgb(hue, 0.9f, 1f);
            Vector3f color = new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(new DustParticleOptions(color, 1.6f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        playSound(ModSounds.JOASH_LASER.get(), 2f, 1f);
        this.laserAim = null;
    }

    private void blink() {
        BlockPos centre = this.home != null ? this.home : this.blockPosition();
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1.5, getZ(), 40, 0.6, 1.2, 0.6, 0.05);
        }
        for (int i = 0; i < 16; i++) {
            Vec3 spot = randomArenaSpot(centre);
            if (this.randomTeleport(spot.x, spot.y, spot.z, true)) break;
        }
        playSound(ModSounds.JOASH_TELEPORT.get(), 2f, 1f);
    }

    private void summonIllusions() {
        if (!(this.level() instanceof ServerLevel level)) return;
        clearIllusions(level);
        BlockPos centre = this.home != null ? this.home : this.blockPosition();
        for (int i = 0; i < ILLUSION_COUNT; i++) {
            JehoashIllusionEntity illusion = ModEntities.JOASH_ILLUSION.get().create(level);
            if (illusion == null) continue;
            illusion.moveTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, this.random.nextFloat() * 360f, 0f);
            Vec3 spot = randomArenaSpot(centre);
            if (!illusion.randomTeleport(spot.x, spot.y, spot.z, false)) continue;
            illusion.setTarget(this.getTarget());
            level.addFreshEntity(illusion);
            level.sendParticles(ParticleTypes.WITCH, illusion.getX(), illusion.getY() + 1.5, illusion.getZ(), 30, 0.5, 1.2, 0.5, 0.05);
            this.illusions.add(illusion.getUUID());
        }
        playSound(ModSounds.JOASH_ILLUSIONS.get(), 2f, 1f);
    }

    private void clearIllusions(ServerLevel level) {
        for (UUID id : this.illusions) {
            if (level.getEntity(id) instanceof JehoashIllusionEntity illusion) illusion.pop();
        }
        this.illusions.clear();
    }

    private Vec3 randomArenaSpot(BlockPos centre) {
        double x = centre.getX() + 0.5 + (this.random.nextDouble() * 2 - 1) * ARENA_RADIUS;
        double z = centre.getZ() + 0.5 + (this.random.nextDouble() * 2 - 1) * ARENA_RADIUS;
        return new Vec3(x, centre.getY() + 2, z);
    }

    private void cursePlayersNearby() {
        if (!(this.level() instanceof ServerLevel level)) return;
        BlockPos centre = this.home != null ? this.home : this.blockPosition();
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(centre)) < 24 * 24
                && !p.isCreative() && !p.isSpectator())) {
            MobEffectInstance current = player.getEffect(ModEffects.LIFE_OF_THE_PARTY);
            if (current == null || current.getDuration() < 1200) {
                player.addEffect(new MobEffectInstance(ModEffects.LIFE_OF_THE_PARTY, ModEffects.LIFE_OF_THE_PARTY_DURATION), this);
            }
        }
    }

    private boolean strikeStarted;

    @Override
    public void tick() {
        super.tick();
        // "the party ends": the orbital strike opens the sky where he falls (client-side visual)
        if (this.level().isClientSide() && this.isDeadOrDying() && !this.strikeStarted) {
            this.strikeStarted = true;
            net.austin.cabinmod.client.JehoashClientHooks.onDeathStarted(this.position());
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDormant() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) clearIllusions(level);
        startDeathClip();
        super.die(source);
    }

    private void startDeathClip() {
        stopAction();
        this.bossEvent.setProgress(0f);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        this.home = this.blockPosition();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        if (this.home != null) tag.put("Home", NbtUtils.writeBlockPos(this.home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormant")) this.entityData.set(DORMANT, tag.getBoolean("Dormant"));
        NbtUtils.readBlockPos(tag, "Home").ifPresent(pos -> this.home = pos);
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setVisible(!isDormant());
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isDormant();
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : ModSounds.JOASH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.JOASH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.JOASH_DEATH.get();
    }
}
