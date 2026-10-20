package net.austin.cabinmod.entity.custom;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.DjBoothBlock;
import net.austin.cabinmod.effect.ModEffects;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Jehoash the Disco Destroyer (design/JEHOASH_V2.md).
// Dormant: DJing behind his booth, which is part of his model; snapped to the block grid facing a cardinal
// direction, invulnerable, solid. A player right-clicking interrupts the set: the music tape-stops, he
// snarls "WELCOME", slams the booth (which splits off and becomes a real block), roars, and blinks into the
// arena. Then: melee, a dodgeable disco laser, blinks with decoy illusions, Life of the Party.
public class JehoashEntity extends ClipMob {
    public static final String MOB = "joash";

    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BOOTH_ATTACHED = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> LASER_PHASE = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> LASER_POINT = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> LASER_TICK = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.INT);

    public static final byte LASER_IDLE = 0, LASER_CHARGING = 1, LASER_FIRING = 2;
    public static final int LASER_BEAM_TICKS = 12;
    public static final int ARENA_RADIUS = 11;           // 24x24 dance floor
    private static final double MELEE_RANGE = 3.5;
    private static final double LASER_RANGE = 32;
    private static final float LASER_DAMAGE = 8f;
    private static final int ILLUSION_COUNT = 3;
    private static final EntityDimensions DJING = EntityDimensions.scalable(3.2f, 3.5f);   // covers him + the booth

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private BlockPos home;
    @Nullable
    private BlockPos boothPos;                           // where the booth is / will be (centre block)
    private Direction boothFacing = Direction.SOUTH;     // the way he and the booth's front face
    private boolean snapped;
    private boolean roared;
    private int meleeCooldown = 0;
    private int laserCooldown = 60;
    private int blinkCooldown = 240;
    @Nullable
    private Vec3 laserAim;
    private final List<UUID> illusions = new ArrayList<>();
    private boolean strikeStarted;

    public JehoashEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 150;
        this.bossEvent.setVisible(false);
        // the type's size is applied in the Entity constructor; switch to the DJing size (he spawns dormant)
        this.refreshDimensions();
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

    // Stand-in timings until the Blender clips are exported (design section 5).
    @Override
    protected Fallback fallback(String clip) {
        return switch (clip) {
            case "interrupted" -> Fallback.of(92, "voice", 24, "slam", 50, "booth", 53, "roar", 58, "move:blink", 86);
            case "attack_melee" -> Fallback.of(20, "hit", 10);
            case "attack_ranged" -> Fallback.of(32, "fire", 20);
            case "teleport" -> Fallback.of(20, "move:blink", 10);
            case "summon_illusions" -> Fallback.of(24, "summon", 12);
            case "hurt" -> Fallback.of(10);
            case "death" -> Fallback.of(80);
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
        builder.define(BOOTH_ATTACHED, true);
        builder.define(LASER_PHASE, LASER_IDLE);
        builder.define(LASER_POINT, new Vector3f());
        builder.define(LASER_TICK, 0);
    }

    public boolean isDormant() {
        return this.entityData.get(DORMANT);
    }

    public boolean isBoothAttached() {
        return this.entityData.get(BOOTH_ATTACHED);
    }

    public byte laserPhase() {
        return this.entityData.get(LASER_PHASE);
    }

    public Vec3 laserPoint() {
        return new Vec3(this.entityData.get(LASER_POINT));
    }

    public int laserTick() {
        return this.entityData.get(LASER_TICK);
    }

    // The way he faces while the booth is his (snapped to a cardinal direction).
    public Direction djFacing() {
        return Direction.fromYRot(this.getYRot());
    }

    // Booth centre block: one block in front of him.
    public BlockPos boothCenter() {
        return this.boothPos != null ? this.boothPos : this.blockPosition().relative(djFacing());
    }

    // Where the music comes from while the booth is part of him (client and server).
    public Vec3 boothSoundPosition() {
        return Vec3.atBottomCenterOf(this.blockPosition().relative(djFacing())).add(0, 1.6, 0);
    }

    // Approximate disco-ball position in the attack pose (above and in front of his head).
    public Vec3 laserOrigin(float partialTick) {
        float yaw = this.yBodyRot * ((float) Math.PI / 180f);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        return this.getPosition(partialTick).add(fwd.scale(0.5)).add(0, 3.75, 0);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return isDormant() ? DJING : super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DORMANT.equals(key)) this.refreshDimensions();
    }

    // While the booth is still drawn as part of him (until the slam) don't let him be culled when only the booth is in view.
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return isBoothAttached() ? super.getBoundingBoxForCulling().inflate(1.6, 0.2, 1.6) : super.getBoundingBoxForCulling();
    }

    // Solid while DJing, so players bump into him and his booth.
    @Override
    public boolean canBeCollidedWith() {
        return isDormant() && this.isAlive();
    }

    @Override
    public boolean isPushable() {
        return !isDormant() && !isBoothAttached();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isDormant() && this.isAlive()) {
            if (!this.level().isClientSide()) wake(player);
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return super.mobInteract(player, hand);
    }

    // The music gets interrupted.
    public void wake(Player player) {
        if (!isDormant()) return;
        this.boothFacing = djFacing();
        this.boothPos = this.blockPosition().relative(this.boothFacing);
        this.home = this.boothPos.relative(this.boothFacing, 6);
        this.entityData.set(DORMANT, false);
        this.setTarget(player);
        this.bossEvent.setVisible(true);
        this.level().playSound(null, this.boothPos, ModSounds.DJ_BOOTH_NEEDLE.get(), SoundSource.HOSTILE, 2.5f, 1f);
        startAction("interrupted");
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!isBoothAttached()) super.knockback(strength, x, z);
    }

    @Override
    public void tick() {
        if (isBoothAttached()) {
            Vec3 v = this.getDeltaMovement();                     // explosions etc. set motion directly
            if (v.x != 0 || v.z != 0) this.setDeltaMovement(0, v.y, 0);
        }
        super.tick();
        if (isBoothAttached()) {
            if (!this.level().isClientSide() && !this.snapped) snapToGrid();
            lockFacing();
        }
        if (!this.level().isClientSide() && laserPhase() != LASER_IDLE) {
            // firing fades after the beam; a charge that never fired (interrupted clip) gives up after the clip length
            int limit = laserPhase() == LASER_FIRING ? LASER_BEAM_TICKS : clipLengthTicks("attack_ranged") + 10;
            if (this.tickCount - laserTick() > limit) this.entityData.set(LASER_PHASE, LASER_IDLE);
        }
        // "the party ends": the orbital strike opens the sky where he falls (client-side visual + sound)
        if (this.level().isClientSide() && this.isDeadOrDying() && !this.strikeStarted) {
            this.strikeStarted = true;
            net.austin.cabinmod.client.JehoashClientHooks.onDeathStarted(this.position());
        }
    }

    // Stand in the middle of a block facing a cardinal direction, so the booth lines up with the block grid.
    private void snapToGrid() {
        Direction facing = Direction.fromYRot(this.getYRot());
        Player near = this.level().getNearestPlayer(this, 16);
        if (near != null) {
            Vec3 d = near.position().subtract(this.position());
            facing = Direction.getNearest(d.x, 0, d.z);
            if (facing.getAxis().isVertical()) facing = Direction.SOUTH;
        }
        BlockPos at = this.blockPosition();
        this.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, facing.toYRot(), 0f);
        this.snapped = true;
    }

    private void lockFacing() {
        float yaw = Direction.fromYRot(this.getYRot()).toYRot();
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
        this.setXRot(0f);
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
        // reloaded mid-interruption (the running clip isn't saved): finish the booth hand-over and the roar
        if (!isActing() && (isBoothAttached() || !this.roared)) {
            separateBooth();
            if (!this.roared) roar();
        }
        if (this.roared && this.tickCount % 20 == 0) cursePlayersNearby();
        if (meleeCooldown > 0) meleeCooldown--;
        if (laserCooldown > 0) laserCooldown--;
        if (blinkCooldown > 0) blinkCooldown--;

        LivingEntity target = this.getTarget();
        if (isActing() || isBoothAttached()) {
            if (!currentAction().equals("attack_melee")) this.getNavigation().stop();
            if (target != null && !isBoothAttached()) this.getLookControl().setLookAt(target, 30f, 30f);
            return;
        }
        if (target == null || !target.isAlive()) return;
        this.getLookControl().setLookAt(target, 30f, 30f);

        double dist = this.distanceTo(target);
        if (blinkCooldown == 0) {
            startAction("teleport");
            blinkCooldown = 200 + this.random.nextInt(120);
        } else if (dist <= MELEE_RANGE && meleeCooldown == 0) {
            startAction("attack_melee");
            meleeCooldown = 25;
        } else if (dist > 5 && laserCooldown == 0 && this.hasLineOfSight(target)) {
            this.laserAim = target.getEyePosition();       // locked now, fired on the "fire" marker
            this.entityData.set(LASER_POINT, this.laserAim.toVector3f());
            this.entityData.set(LASER_TICK, this.tickCount);
            this.entityData.set(LASER_PHASE, LASER_CHARGING);
            playSound(ModSounds.JOASH_LASER_CHARGE.get(), 2f, 1f);
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
                if (arg.equals("blink")) {
                    if (clip.equals("interrupted")) blinkIntoArena();
                    else blink();
                }
            }
            case "summon" -> summonIllusions();
            case "voice" -> playSound(ModSounds.JOASH_WELCOME_VOICE.get(), 3f, 1f);
            case "slam" -> {
                BlockPos at = boothCenter();
                this.level().playSound(null, at, ModSounds.DJ_BOOTH_SLAM.get(), SoundSource.HOSTILE, 2.5f, 1f);
                // the cabin scream (the mod's own cabin_block_hit) - the user's pick for this moment
                this.level().playSound(null, at, ModSounds.CABIN_BLOCK_HIT.get(), SoundSource.HOSTILE, 3f, 1f);
                if (this.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.CRIT, at.getX() + 0.5, at.getY() + 1.7, at.getZ() + 0.5, 30, 1.2, 0.2, 0.4, 0.3);
                }
            }
            case "booth" -> separateBooth();
            case "roar" -> roar();
            case "sfx" -> {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, arg));
                if (sound != null) playSound(sound, 2f, 1f);
            }
            default -> {
            }
        }
    }

    @Override
    protected void onClipEnd(String clip) {
        if (clip.equals("teleport")) startAction("summon_illusions");
        if (clip.equals("interrupted")) {
            separateBooth();                 // in case a marker was missed
            if (!this.roared) roar();
        }
    }

    private void roar() {
        this.roared = true;
        playSound(ModSounds.JOASH_ROAR.get(), 3f, 1f);
        cursePlayersNearby();
    }

    // The booth leaves his model and becomes a real block, exactly where it stood.
    private void separateBooth() {
        if (!isBoothAttached()) return;
        this.entityData.set(BOOTH_ATTACHED, false);
        BlockPos at = boothCenter();
        Direction facing = this.boothPos != null ? this.boothFacing : djFacing();
        if (!DjBoothBlock.placeBooth(this.level(), at, facing)) {
            this.spawnAtLocation(new ItemStack(ModBlocks.DJ_BOOTH.get()));
        }
    }

    private void fireLaser() {
        if (this.laserAim == null || !(this.level() instanceof ServerLevel level)) return;
        Vec3 from = laserOrigin(1f);
        Vec3 dir = this.laserAim.subtract(from).normalize();
        Vec3 to = from.add(dir.scale(LASER_RANGE));
        HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, from, to,
                this.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof JehoashEntity) && !(e instanceof JehoashIllusionEntity));
        if (hit != null) {
            AABB struck = hit.getEntity().getBoundingBox().inflate(0.3);   // same margin the pick used
            to = struck.clip(from, to).orElse(struck.getCenter());
            hit.getEntity().hurt(this.damageSources().mobAttack(this), LASER_DAMAGE);
        }
        this.entityData.set(LASER_POINT, to.toVector3f());
        this.entityData.set(LASER_TICK, this.tickCount);
        this.entityData.set(LASER_PHASE, LASER_FIRING);
        playSound(ModSounds.JOASH_LASER.get(), 2.5f, 1f);
        this.laserAim = null;
    }

    private void blink() {
        BlockPos centre = this.home != null ? this.home : this.blockPosition();
        puff();
        for (int i = 0; i < 16; i++) {
            Vec3 spot = randomArenaSpot(centre, ARENA_RADIUS);
            if (this.randomTeleport(spot.x, spot.y, spot.z, true)) break;
        }
        playSound(ModSounds.JOASH_TELEPORT.get(), 2f, 1f);
    }

    // End of the interruption: over the booth and into the arena in front of it.
    private void blinkIntoArena() {
        BlockPos centre = this.home != null ? this.home : this.blockPosition();
        puff();
        for (int i = 0; i < 16; i++) {
            Vec3 spot = randomArenaSpot(centre, 3);
            if (this.randomTeleport(spot.x, spot.y, spot.z, true)) break;
        }
        playSound(ModSounds.JOASH_TELEPORT.get(), 2f, 1f);
    }

    private void puff() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1.5, getZ(), 40, 0.6, 1.2, 0.6, 0.05);
        }
    }

    private void summonIllusions() {
        if (!(this.level() instanceof ServerLevel level)) return;
        clearIllusions(level);
        BlockPos centre = this.home != null ? this.home : this.blockPosition();
        for (int i = 0; i < ILLUSION_COUNT; i++) {
            JehoashIllusionEntity illusion = ModEntities.JOASH_ILLUSION.get().create(level);
            if (illusion == null) continue;
            illusion.moveTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, this.random.nextFloat() * 360f, 0f);
            Vec3 spot = randomArenaSpot(centre, ARENA_RADIUS);
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

    private Vec3 randomArenaSpot(BlockPos centre, int radius) {
        double x = centre.getX() + 0.5 + (this.random.nextDouble() * 2 - 1) * radius;
        double z = centre.getZ() + 0.5 + (this.random.nextDouble() * 2 - 1) * radius;
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

    // While DJing he can't be hurt; hits just bounce off (only a right-click interrupts him).
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDormant() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            separateBooth();                                      // the booth always survives him
            this.entityData.set(LASER_PHASE, LASER_IDLE);
        }
        if (this.level() instanceof ServerLevel level) clearIllusions(level);
        stopAction();
        this.bossEvent.setProgress(0f);
        super.die(source);
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
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (isDormant()) snapToGrid();
        return data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Dormant", isDormant());
        tag.putBoolean("BoothAttached", isBoothAttached());
        tag.putBoolean("Snapped", this.snapped);
        tag.putBoolean("Roared", this.roared);
        tag.putString("BoothFacing", this.boothFacing.getSerializedName());
        if (this.home != null) tag.put("Home", NbtUtils.writeBlockPos(this.home));
        if (this.boothPos != null) tag.put("BoothPos", NbtUtils.writeBlockPos(this.boothPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Dormant")) this.entityData.set(DORMANT, tag.getBoolean("Dormant"));
        if (tag.contains("BoothAttached")) this.entityData.set(BOOTH_ATTACHED, tag.getBoolean("BoothAttached"));
        this.snapped = tag.getBoolean("Snapped");
        this.roared = tag.getBoolean("Roared");
        Direction facing = Direction.byName(tag.getString("BoothFacing"));
        if (facing != null && facing.getAxis().isHorizontal()) this.boothFacing = facing;
        NbtUtils.readBlockPos(tag, "Home").ifPresent(pos -> this.home = pos);
        NbtUtils.readBlockPos(tag, "BoothPos").ifPresent(pos -> this.boothPos = pos);
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setVisible(!isDormant());
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
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
