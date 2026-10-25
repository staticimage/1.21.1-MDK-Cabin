package net.austin.cabinmod.entity.custom;

import net.austin.cabinmod.CabinMod;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Shared encounter: first support hit -> conductor -> collapsing truss -> on-foot combat.
// The booth stays attached until the scripted dodge, and flesh begins only after impact.
/*
 * ENCOUNTER EDITING MAP
 * This entity is the SERVER authority. WAITING -> CONDUCT occurs inside damageSupport
 * on the first accepted player-owned hit; mobInteract deliberately does not wake him.
 * CONDUCT keeps the attached booth and disables ordinary AI/health damage. The boss
 * bar reads exactly "supports" and measures combined remaining support durability.
 * Both supports at zero -> COLLAPSE. tickStage owns the saved shared tick counter;
 * StageCollapse owns DODGE/IMPACT/END timings and the rig's fall curve. The client model
 * only draws those states; it cannot advance the fight or deal damage.
 * At DODGE, separateBooth transfers the one saved record and blinkIntoArena moves him.
 * At IMPACT the particles play and the flesh-growth gate in tick() opens. At END the
 * existing melee/shard/teleport/illusion AI resumes. Death unlocks one booth record,
 * restores authored surfaces/truss, clears access fields and cleans projectiles.
 * Party scaling is sampled ONCE in wake(), then saved. Support damage cooldowns live
 * on TrussSupportEntity and are per attacker, allowing simultaneous teammates' hits.
 * SUPPORT_HITS_BEFORE_EJECT and FIELD_* tune knockback/access timing. supportPressure
 * is a short-lived hit streak (resets on reload); phase, HP and field timers persist.
 * Rendering/art locations: JehoashModel (bones/IK/cloth), CultDaggerLayer (blade mesh),
 * DjPerformance (song choreography), and tools/textures/pack_direct_cult_art.py (UVs).
 */
public class JehoashEntity extends ClipMob {
    public static final String MOB = "joash";

    public static final int WAITING=0, CONDUCT=1, COLLAPSE=2, ON_FOOT=3;
    private static final EntityDataAccessor<Integer> STAGE_PHASE=SynchedEntityData.defineId(JehoashEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE_TIME=SynchedEntityData.defineId(JehoashEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SUPPORT_LEFT=SynchedEntityData.defineId(JehoashEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SUPPORT_RIGHT=SynchedEntityData.defineId(JehoashEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SUPPORT_MAX=SynchedEntityData.defineId(JehoashEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> ARENA_CENTER=SynchedEntityData.defineId(JehoashEntity.class,EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private boolean stagedEncounter;
    // Stage access tuning: three accepted hits per player trigger an ejection, and a
    // periodic 10-second cycle closes the field for three seconds. All timings are ticks.
    // The 12-tick lead-in lets launched players clear the field before it becomes solid.
    private static final int SUPPORT_HITS_BEFORE_EJECT=3, FIELD_CYCLE=200, FIELD_WARNING=100, FIELD_CLOSE=112, FIELD_OPEN=172;
    private final java.util.Map<UUID,Integer> supportPressure=new java.util.HashMap<>();
    private int forcedFieldStart=-1,forcedFieldEnd=-1;
    private boolean fieldClosed;
    public boolean stageFieldClosed() { return fieldClosed; }
    private boolean onStage(Player p) {
        BlockPos c=arenaCenter();return inArena(p) && p.getY()>=c.getY()+1 && p.getZ()<c.getZ()-2 && Math.abs(p.getX()-c.getX()-.5)<8;
    }
    private void ejectFromStage(Player p) {
        var c=arenaCenter();Vec3 direction=new Vec3(c.getX()+.5-p.getX(),0,c.getZ()+5.5-p.getZ()).normalize();
        p.setDeltaMovement(direction.scale(1.35).add(0,.7,0));p.hurtMarked=true;p.hasImpulse=true;p.fallDistance=0;
        if(p instanceof ServerPlayer serverPlayer)serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
    }
    private void stagePressure(ServerLevel server,Player hitter) {
        int hits=supportPressure.merge(hitter.getUUID(),1,Integer::sum);
        if(hits<SUPPORT_HITS_BEFORE_EJECT)return;
        supportPressure.put(hitter.getUUID(),0);
        if(onStage(hitter)) {
            ejectFromStage(hitter);forcedFieldStart=stageTime()+12;forcedFieldEnd=stageTime()+72;
            playSound(net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,2,.7f);
        }
    }
    private void tickStageAccess(ServerLevel server) {
        int cycle=stageTime()%FIELD_CYCLE;
        if(cycle==FIELD_WARNING) {
            for(var player:server.getPlayers(p -> !p.isSpectator() && onStage(p)))ejectFromStage(player);
            playSound(net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,2,1.4f);
        }
        boolean closed=cycle>=FIELD_CLOSE && cycle<FIELD_OPEN || stageTime()>=forcedFieldStart && stageTime()<forcedFieldEnd;
        if(closed!=fieldClosed || stageTime()%20==0) {
            net.austin.cabinmod.worldgen.RaveArena.stageBarrier(server,arenaCenter(),closed);fieldClosed=closed;
        }
    }
    private int encounterPlayers=1;
    public float maxSupportHealth() { return entityData.get(SUPPORT_MAX); }
    public int stagePhase() { return entityData.get(STAGE_PHASE); }
    public int stageTime() { return entityData.get(STAGE_TIME); }
    public float supportHealth(int side) { return entityData.get(side==0?SUPPORT_LEFT:SUPPORT_RIGHT); }
    public boolean isConducting() { return stagePhase()==CONDUCT; }
    public boolean isCollapsing() { return stagePhase()==COLLAPSE; }
    public boolean lightsAttackEnabled() { return !isDormant() && !isCollapsing() && (isConducting() || !isBoothAttached()); }
    public boolean damageSupport(int side,DamageSource source,float amount) {
        if(!(level() instanceof ServerLevel server) || (!isDormant() && !isConducting()) || !isAlive() || !(source.getEntity() instanceof Player player)
                || !inArena(player) || amount<=0 || supportHealth(side)<=0) return false;
        if(isDormant())wake(player);
        if(!isConducting())return false;
        float hp=Math.max(0,supportHealth(side)-Math.min(20,amount));
        entityData.set(side==0?SUPPORT_LEFT:SUPPORT_RIGHT,hp);
        BlockPos at=arenaCenter().offset(side==0?-6:6,3,-8);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK,at.getX()+.5,at.getY(),at.getZ()+.5,18,.4,.7,.4,.15);
        server.playSound(null,at,hp==0?net.minecraft.sounds.SoundEvents.ANVIL_BREAK:net.minecraft.sounds.SoundEvents.ANVIL_HIT,SoundSource.HOSTILE,1.5f,hp==0?.65f:1.4f);
        stagePressure(server,player);
        if(supportHealth(0)<=0 && supportHealth(1)<=0) {
            net.austin.cabinmod.worldgen.RaveArena.stageBarrier(server,arenaCenter(),false);fieldClosed=false;
            stopAction();getNavigation().stop();entityData.set(STAGE_PHASE,COLLAPSE);entityData.set(STAGE_TIME,0);
            net.austin.cabinmod.worldgen.RaveArena.ensureFallingRig(server,this);
            bossEvent.setName(Component.literal("supports"));
        }
        return true;
    }
    private void tickStage(ServerLevel server) {
        if(isDormant() && tickCount%20==0 && net.austin.cabinmod.worldgen.RaveArena.hasRig(server,arenaCenter()))
            net.austin.cabinmod.worldgen.RaveArena.ensureSupports(server,this);
        if(isConducting()) {
            entityData.set(STAGE_TIME,stageTime()+1);
            tickStageAccess(server);
            if(tickCount%20==0) net.austin.cabinmod.worldgen.RaveArena.ensureSupports(server,this);
        } else if(isCollapsing()) {
            int t=stageTime()+1;entityData.set(STAGE_TIME,t);
            if(t==1 || tickCount%20==0) net.austin.cabinmod.worldgen.RaveArena.ensureFallingRig(server,this);
            if(t==1 || t==20 || t==42) playSound(net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,3,.5f+t*.005f);
            if(t<74 && t%4==0) {
                var c=arenaCenter();
                for(int side:new int[]{-1,1})server.sendParticles(ParticleTypes.ELECTRIC_SPARK,c.getX()+.5+side*6,c.getY()+6,c.getZ()-7.5,12,.3,.8,.3,.1);
            }
            if(t>=net.austin.cabinmod.entity.StageCollapse.DODGE && isBoothAttached()) {
                separateBooth();
                if(!isBoothAttached()) { blinkIntoArena();laserCooldown=50;meleeCooldown=30;blinkCooldown=240; }
            }
            if(t==net.austin.cabinmod.entity.StageCollapse.IMPACT) {
                var c=arenaCenter();
                server.playSound(null,c.offset(0,2,-9),net.minecraft.sounds.SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,4,.55f);
                server.playSound(null,c.offset(0,2,-9),net.minecraft.sounds.SoundEvents.GLASS_BREAK,SoundSource.HOSTILE,3,.7f);
                server.sendParticles(ParticleTypes.CLOUD,c.getX()+.5,c.getY()+2.4,c.getZ()-9,160,5,.35,.6,.08);
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,net.austin.cabinmod.block.ModBlocks.STAGE_TRUSS.get().defaultBlockState()),
                        c.getX()+.5,c.getY()+2.7,c.getZ()-9,110,5,.5,.6,.3);
            }
            if(t>=net.austin.cabinmod.entity.StageCollapse.END && !isBoothAttached()) {
                entityData.set(STAGE_PHASE,ON_FOOT);bossEvent.setName(getDisplayName());bossEvent.setColor(BossEvent.BossBarColor.RED);
                if(!roared) roar();
            }
        }
    }

    private static final EntityDataAccessor<Integer> RECORD = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.INT);
    private boolean lightsCreated;
    private int corruptionRadius;
    private BlockPos corruptionOrigin;
    public int recordTrack() { return this.entityData.get(RECORD); }
    public void configureArena(BlockPos center) {
        home = center.immutable();entityData.set(ARENA_CENTER,java.util.Optional.of(home)); snapped = true; boothFacing = Direction.SOUTH;
        boothPos = blockPosition().south();
        corruptionOrigin = boothPos.below();
    }
    public BlockPos arenaCenter() { return entityData.get(ARENA_CENTER).orElseGet(() -> home != null ? home : boothCenter().relative(djFacing(), 6)); }
    public boolean inArena(LivingEntity entity) {
        BlockPos c = arenaCenter();
        return Math.abs(entity.getX()-c.getX()-.5) <= 11 && Math.abs(entity.getZ()-c.getZ()-.5) <= 11
                && Math.abs(entity.getY()-c.getY()) < 8;
    }

    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BOOTH_ATTACHED = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> LASER_PHASE = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> LASER_POINT = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> LASER_TICK = SynchedEntityData.defineId(JehoashEntity.class, EntityDataSerializers.INT);

    public static final byte LASER_IDLE = 0, LASER_CHARGING = 1, LASER_FIRING = 2;
    public static final int LASER_BEAM_TICKS = 12;
    public static final int ARENA_RADIUS = 11;           // 24x24 dance floor
    private static final double MELEE_RANGE = 3.5;
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

    public JehoashEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // Defaults must match on client/server. A random builder default is never
        // sent in spawn metadata, so each listener would choose a different song
        // and then restart when the booth revealed the server's actual selection.
        // Select on the server AFTER defining the shared zero default instead.
        if (!level.isClientSide()) this.entityData.set(RECORD, this.random.nextInt(net.austin.cabinmod.entity.RecordTracks.COUNT));
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
        builder.define(ARENA_CENTER,java.util.Optional.empty());builder.define(SUPPORT_MAX,60f);builder.define(STAGE_PHASE,WAITING);builder.define(STAGE_TIME,0);builder.define(SUPPORT_LEFT,60f);builder.define(SUPPORT_RIGHT,60f);
        builder.define(DORMANT, true);
        builder.define(RECORD, 0);
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
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        return this.getPosition(partialTick).add(right.scale(-.6875)).add(fwd.scale(.125)).add(0, 2.94, 0);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return isBoothAttached() ? DJING : super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DORMANT.equals(key) || BOOTH_ATTACHED.equals(key)) this.refreshDimensions();
    }

    // While the booth is still drawn as part of him (until the slam) don't let him be culled when only the booth is in view.
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return isBoothAttached() ? super.getBoundingBoxForCulling().inflate(1.6, 0.2, 1.6) : super.getBoundingBoxForCulling();
    }

    // Solid while DJing, so players bump into him and his booth.
    @Override
    public boolean canBeCollidedWith() {
        return isBoothAttached() && this.isAlive();
    }

    @Override
    public boolean isPushable() {
        return !isDormant() && !isBoothAttached();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return super.mobInteract(player, hand);
    }

    // Server transition, normally called by the first valid support hit.
    public void wake(Player player) {
        if (!isDormant()) return;
        this.boothFacing = djFacing();
        this.boothPos = this.blockPosition().relative(this.boothFacing);
        if (this.home == null) this.home = this.boothPos.relative(this.boothFacing, 6);
        this.entityData.set(DORMANT, false);
        stagedEncounter=level() instanceof ServerLevel server && net.austin.cabinmod.worldgen.RaveArena.hasRig(server,arenaCenter());
        entityData.set(STAGE_PHASE,stagedEncounter?CONDUCT:ON_FOOT);entityData.set(STAGE_TIME,0);
        if(stagedEncounter && level() instanceof ServerLevel server) {
            encounterPlayers=Math.max(1,server.getPlayers(p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && inArena(p)).size());
            float supportMaximum=60f*(1+.4f*(encounterPlayers-1));
            entityData.set(SUPPORT_MAX,supportMaximum);entityData.set(SUPPORT_LEFT,supportMaximum);entityData.set(SUPPORT_RIGHT,supportMaximum);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(250*(1+.6*(encounterPlayers-1)));setHealth(getMaxHealth());
            net.austin.cabinmod.worldgen.RaveArena.ensureSupports(server,this);
            bossEvent.setName(Component.literal("supports"));
        }
        this.corruptionRadius = 0;
        if (corruptionOrigin == null) corruptionOrigin = boothCenter().below();
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
        if (this.level() instanceof ServerLevel server && isAlive()) {
            tickStage(server);
            if (!lightsCreated && this.snapped) {
                this.home = arenaCenter();entityData.set(ARENA_CENTER,java.util.Optional.of(home));
                net.austin.cabinmod.worldgen.RaveArena.installLights(server,this);
                lightsCreated = true;
            }
            if (!isDormant() && (!stagedEncounter || stagePhase()==ON_FOOT || isCollapsing() && stageTime()>=net.austin.cabinmod.entity.StageCollapse.IMPACT) && corruptionRadius < net.austin.cabinmod.worldgen.RaveArena.CORRUPTION_REACH && tickCount % 4 == 0) {
                if (corruptionOrigin == null) corruptionOrigin = boothCenter().below();
                net.austin.cabinmod.worldgen.RaveArena.growFromStand(server,arenaCenter(),corruptionOrigin,++corruptionRadius);
            }
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
        this.bossEvent.setProgress(isConducting() ? (supportHealth(0)+supportHealth(1))/(2*maxSupportHealth()) : this.getHealth() / this.getMaxHealth());
        if(isConducting() || isCollapsing()) {
            getNavigation().stop();
            if(isConducting()) bossEvent.setName(Component.literal("supports"));
            return;
        }

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

        if(stagedEncounter && tickCount%120==0 && level() instanceof ServerLevel server) {
            var players=new java.util.ArrayList<>(server.getPlayers(p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && inArena(p)));
            players.sort(java.util.Comparator.comparing(Player::getUUID));
            if(!players.isEmpty()) setTarget(players.get(Math.floorMod(tickCount/120,players.size())));
        }
        LivingEntity target = this.getTarget();
        if (isActing() || isBoothAttached()) {
            if (!currentAction().equals("attack_melee")) this.getNavigation().stop();
            if (target != null && !isBoothAttached()) this.getLookControl().setLookAt(target, 30f, 30f);
            return;
        }
        if (target == null || !target.isAlive() || !inArena(target)) {
            this.setTarget(null); this.getNavigation().stop();
            if (this.level() instanceof ServerLevel server) {
                Player nearby = server.getNearestPlayer(arenaCenter().getX()+.5,arenaCenter().getY()+1,arenaCenter().getZ()+.5,18,
                        e -> e instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive() && inArena(p));
                this.setTarget(nearby);
            }
            return;
        }
        this.getLookControl().setLookAt(target, 30f, 30f);

        double dist = this.distanceTo(target);
        if (blinkCooldown == 0) {
            startAction("teleport");
            blinkCooldown = 200 + this.random.nextInt(120);
        } else if (dist <= MELEE_RANGE && meleeCooldown == 0) {
            startAction("attack_melee");
            meleeCooldown = 25;
        } else if (dist > 5 && laserCooldown == 0 && this.hasLineOfSight(target)) {
            this.laserAim = target.getEyePosition();       // charge pose; shards acquire the current target on release
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
        if((isConducting() || isCollapsing()) && clip.equals("interrupted") && (type.equals("booth") || type.equals("move"))) return;
        LivingEntity target = this.getTarget();
        switch (type) {
            case "hit" -> {
                if (target != null && this.distanceTo(target) <= MELEE_RANGE + 0.5 && this.hasLineOfSight(target)) this.doHurtTarget(target);
            }
            case "fire" -> fireShards();
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
        if (clip.equals("interrupted") && !isConducting() && !isCollapsing()) {
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
        BlockPos at = boothCenter();
        Direction facing = this.boothPos != null ? this.boothFacing : djFacing();
        boolean placed = DjBoothBlock.placeBooth(this.level(), at, facing);
        // If a player built into the desk while it was attached, keep the reward in a nearby booth.
        if (!placed) for (int radius=1; radius<=4 && !placed; radius++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = at.relative(direction, radius);
                if (DjBoothBlock.placeBooth(this.level(), candidate, facing)) {
                    at = candidate; placed = true; break;
                }
            }
        }
        if (!placed) return; // retry; never turn a locked record into an item drop
        this.boothPos = at;
        if (this.level().getBlockEntity(at) instanceof net.austin.cabinmod.block.entity.DjBoothBlockEntity booth) {
            booth.bindBoss(getUUID(),recordTrack());
        }
        this.entityData.set(BOOTH_ATTACHED, false);
    }

    private void fireShards() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !inArena(target) || !(level() instanceof ServerLevel server)) return;
        Vec3 from = laserOrigin(1f);
        for (int i=0;i<3;i++) {
            GlassShardEntity shard = ModEntities.GLASS_SHARD.get().create(server);
            if (shard == null) continue;
            double angle = i * Math.PI * 2 / 3;
            Vec3 origin = from.add(Math.cos(angle)*.5, Math.sin(angle)*.35, Math.sin(angle)*.3);
            shard.setPos(origin); shard.setOwner(this); shard.aim(target);
            shard.setDeltaMovement(target.getEyePosition().subtract(origin).normalize().scale(.78));
            server.addFreshEntity(shard);
        }
        entityData.set(LASER_TICK,tickCount); entityData.set(LASER_PHASE,LASER_FIRING);
        playSound(net.minecraft.sounds.SoundEvents.GLASS_BREAK, 1.7f, .8f);
        laserAim = null;
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
        for (ServerPlayer player : level.getPlayers(p -> inArena(p)
                && !p.isCreative() && !p.isSpectator())) {
            MobEffectInstance current = player.getEffect(ModEffects.LIFE_OF_THE_PARTY);
            if (current == null || current.getDuration() < 1200) {
                player.addEffect(new MobEffectInstance(ModEffects.LIFE_OF_THE_PARTY, ModEffects.LIFE_OF_THE_PARTY_DURATION), this);
            }
        }
    }

    // The supports are the only damage targets before the on-foot phase.
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if ((isDormant() || isConducting() || isCollapsing()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            separateBooth();                                      // the booth always survives him
            this.entityData.set(LASER_PHASE, LASER_IDLE);
        }
        if (this.level() instanceof ServerLevel level) {
            clearIllusions(level);
            if(stagedEncounter) net.austin.cabinmod.worldgen.RaveArena.restoreRig(level,this);
            net.austin.cabinmod.worldgen.RaveArena.transform(level,arenaCenter(),false,12);
            if (level.getBlockEntity(boothCenter()) instanceof net.austin.cabinmod.block.entity.DjBoothBlockEntity booth) booth.unlock(getUUID());
            for (ServerPlayer player : level.getPlayers(this::inArena)) player.removeEffect(ModEffects.LIFE_OF_THE_PARTY);
            for (GlassShardEntity shard : level.getEntitiesOfClass(GlassShardEntity.class,getBoundingBox().inflate(48))) {
                if (shard.getOwner() == this) shard.discard();
            }
        }
        stopAction();
        this.bossEvent.setProgress(0f);
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        if (!level().isClientSide() && isBoothAttached()) {
            separateBooth();
            if (isBoothAttached()) return;
            if (level().getBlockEntity(boothCenter()) instanceof net.austin.cabinmod.block.entity.DjBoothBlockEntity booth) booth.unlock(getUUID());
        }
        super.tickDeath();
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
        tag.putInt("ForcedFieldStart",forcedFieldStart);tag.putInt("ForcedFieldEnd",forcedFieldEnd);tag.putBoolean("FieldClosed",fieldClosed);
        tag.putInt("EncounterPlayers",encounterPlayers);tag.putFloat("SupportMax",maxSupportHealth());
        tag.putInt("StagePhase",stagePhase());tag.putInt("StageTime",stageTime());tag.putBoolean("StagedEncounter",stagedEncounter);
        tag.putFloat("SupportLeft",supportHealth(0));tag.putFloat("SupportRight",supportHealth(1));
        tag.putBoolean("Dormant", isDormant());
        tag.putInt("RecordTrack",recordTrack()); tag.putBoolean("LightsCreated",lightsCreated);
        tag.putInt("CorruptionRadius",corruptionRadius);
        if (corruptionOrigin != null) tag.put("CorruptionOrigin",NbtUtils.writeBlockPos(corruptionOrigin));
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
        if (tag.contains("RecordTrack")) this.entityData.set(RECORD, net.austin.cabinmod.entity.RecordTracks.valid(tag.getInt("RecordTrack")));
        forcedFieldStart=tag.contains("ForcedFieldStart")?tag.getInt("ForcedFieldStart"):-1;forcedFieldEnd=tag.contains("ForcedFieldEnd")?tag.getInt("ForcedFieldEnd"):-1;fieldClosed=tag.getBoolean("FieldClosed");
        stagedEncounter=tag.getBoolean("StagedEncounter");encounterPlayers=Math.max(1,tag.getInt("EncounterPlayers"));
        if(tag.contains("SupportMax"))entityData.set(SUPPORT_MAX,tag.getFloat("SupportMax"));
        entityData.set(STAGE_PHASE,tag.contains("StagePhase")?tag.getInt("StagePhase"):(tag.getBoolean("Dormant")?WAITING:ON_FOOT));
        entityData.set(STAGE_TIME,tag.getInt("StageTime"));
        if(tag.contains("SupportLeft"))entityData.set(SUPPORT_LEFT,tag.getFloat("SupportLeft"));
        if(tag.contains("SupportRight"))entityData.set(SUPPORT_RIGHT,tag.getFloat("SupportRight"));
        lightsCreated = tag.getBoolean("LightsCreated"); corruptionRadius = tag.getInt("CorruptionRadius");
        NbtUtils.readBlockPos(tag,"CorruptionOrigin").ifPresent(pos -> corruptionOrigin=pos);
        if (tag.contains("Dormant")) this.entityData.set(DORMANT, tag.getBoolean("Dormant"));
        if (tag.contains("BoothAttached")) this.entityData.set(BOOTH_ATTACHED, tag.getBoolean("BoothAttached"));
        this.snapped = tag.getBoolean("Snapped");
        this.roared = tag.getBoolean("Roared");
        Direction facing = Direction.byName(tag.getString("BoothFacing"));
        if (facing != null && facing.getAxis().isHorizontal()) this.boothFacing = facing;
        NbtUtils.readBlockPos(tag, "Home").ifPresent(pos -> { this.home = pos;entityData.set(ARENA_CENTER,java.util.Optional.of(pos)); });
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
