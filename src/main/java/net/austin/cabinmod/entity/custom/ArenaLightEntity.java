package net.austin.cabinmod.entity.custom;

import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import java.util.UUID;

/** A fixed rave fixture. Decorative sweeps are harmless; an active boss enables telegraphed shots. */
/*
 * FIXTURE LOGIC / BALANCE KNOBS
 * Server phases: 0 decorative, 1 warning, 2 damaging shot, 3 collapse strobe. The
 * 140-tick cycle tracks one selected player for 22 ticks, locks for six, fires once
 * at 28, and clears the beam at 40. Damage is currently 8; all are in tick() below.
 * The initial cycle is reset on activation, so a support hit produces an immediate
 * warning. Slot offsets stagger fixtures and UUID-sorted target selection spreads
 * shots across multiplayer participants. Aim points are synchronized RELATIVE to the
 * fixture, avoiding float precision loss millions of blocks from spawn.
 * Idle sweeps point out toward the dance floor; active targeting covers the whole
 * arena, including the support line and creative testers. Spectators are ignored.
 * Creative players see warnings/shots but retain vanilla damage immunity. Wall clips
 * stop damaging rays; StageBarrierBlock alone lets these fixture rays pass through.
 * ArenaLightRenderer draws the pan/tilt head; LaserBeamRenderer draws the beam. Neither
 * renderer deals damage. The fixtures follow StageCollapse then disappear on impact.
 */
public class ArenaLightEntity extends Entity {
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(ArenaLightEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> POINT = SynchedEntityData.defineId(ArenaLightEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SERIAL = SynchedEntityData.defineId(ArenaLightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> FACING = SynchedEntityData.defineId(ArenaLightEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockPos> CENTER = SynchedEntityData.defineId(ArenaLightEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(ArenaLightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<java.util.Optional<UUID>> OWNER = SynchedEntityData.defineId(ArenaLightEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private UUID ownerId, aimTarget;
    private BlockPos center = BlockPos.ZERO;
    private int slot, clock;
    private boolean wasActive;
    private Vec3 previousAim, currentAim, returnAim;
    private byte previousPhase;
    private int returnTick = -100;
    public ArenaLightEntity(EntityType<? extends ArenaLightEntity> type, Level level) { super(type, level); }
    public void configure(JehoashEntity boss, BlockPos center, int slot) {
        ownerId = boss.getUUID(); this.center = center.immutable(); this.slot = slot; entityData.set(FACING,(byte)boss.djFacing().get2DDataValue()); syncArena(); updateSweep();
    }
    private void syncArena() {
        entityData.set(CENTER, center); entityData.set(SLOT, slot); entityData.set(OWNER, java.util.Optional.ofNullable(ownerId));
    }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public BlockPos arenaCenter() { return entityData.get(CENTER); }
    public int fixtureSlot() { return entityData.get(SLOT); }
    public byte laserPhase() { return entityData.get(PHASE); }
    public int laserTick() { return entityData.get(SERIAL); }
    public Vec3 laserPoint() { return position().add(new Vec3(entityData.get(POINT))); }
    private void point(Vec3 world) { entityData.set(POINT,world.subtract(position()).toVector3f()); }
    public Vec3 aimDirection() { Vec3 delta=laserPoint().subtract(position()); return delta.lengthSqr()<.001 ? forward() : delta.normalize(); }
    private Vec3 forward() { var d=net.minecraft.core.Direction.from2DDataValue(entityData.get(FACING)); return new Vec3(d.getStepX(),0,d.getStepZ()); }
    public Vec3 laserOrigin(float partialTick) { return position().add(aimDirection().scale(.345)); }
    /** Continuous decorative clock, shared by all clients; no per-frame aim packets. */
    public Vec3 sweepPoint(float partialTick) {
        double t = level().getGameTime() + (double)partialTick;
        int index = fixtureSlot();
        Vec3 forward = forward(), side = new Vec3(forward.z, 0, -forward.x);
        return Vec3.atBottomCenterOf(arenaCenter()).add(0, -.02, 0)
                .add(forward.scale(4 + 3 * Math.sin(t * .011 + index)))
                .add(side.scale(Math.sin(t * .018 + index * .8) * 8));
    }
    /** Only visual tracking is interpolated. A firing ray always uses the exact server endpoint. */
    public Vec3 renderPoint(float partialTick) {
        if (laserPhase() == 0) {
            Vec3 sweep = sweepPoint(partialTick);
            float blend = net.minecraft.util.Mth.clamp((tickCount - returnTick + partialTick) / 8f, 0, 1);
            blend = blend * blend * (3 - 2 * blend);
            return returnAim == null ? sweep : returnAim.lerp(sweep, blend);
        }
        if (laserPhase() == 2 || previousAim == null || currentAim == null) return laserPoint();
        return previousAim.lerp(currentAim, partialTick);
    }
    public Vec3 renderDirection(float partialTick) {
        Vec3 delta = renderPoint(partialTick).subtract(getPosition(partialTick));
        return delta.lengthSqr() < .001 ? forward() : delta.normalize();
    }
    public Vec3 renderOrigin(float partialTick) {
        return getPosition(partialTick).add(renderDirection(partialTick).scale(.345));
    }
    private void updateSweep() {
        point(sweepPoint(0));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACING,(byte)net.minecraft.core.Direction.SOUTH.get2DDataValue()); builder.define(PHASE, (byte)0); builder.define(POINT, new Vector3f()); builder.define(SERIAL, 0);
        builder.define(CENTER, BlockPos.ZERO); builder.define(SLOT, 0); builder.define(OWNER, java.util.Optional.empty());
    }
    private void phase(byte phase) {
        entityData.set(PHASE, phase); entityData.set(SERIAL, entityData.get(SERIAL) + 1);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            if (laserPhase() == 0 && previousPhase != 0) {
                returnAim = currentAim == null ? laserPoint() : currentAim;
                returnTick = tickCount;
            }
            previousAim = currentAim == null ? laserPoint() : currentAim;
            currentAim = laserPhase() == 0 ? renderPoint(1) : laserPoint();
            previousPhase = laserPhase();
            return;
        }
        Entity owner = ownerId == null ? null : server.getEntity(ownerId);
        if(owner instanceof JehoashEntity boss && boss.isAlive() && boss.isCollapsing()) {
            int t=boss.stageTime();
            if(t>=net.austin.cabinmod.entity.StageCollapse.IMPACT) { discard();return; }
            Vec3 offset=net.austin.cabinmod.entity.StageCollapse.offset(t,-5+slot*2,-.7,0);
            setPos(center.getX()+.5+offset.x,center.getY()+7+offset.y,center.getZ()-7.5+offset.z);
            if(laserPhase()!=3)phase((byte)3);
            point(position().add(Math.sin(t*.2+slot)*4,-2,5));
            return;
        }
        boolean active = owner instanceof JehoashEntity boss && boss.isAlive() && boss.lightsAttackEnabled();
        // Start the first warning immediately when a support wakes the boss. Other fixtures
        // join at 14-tick intervals, instead of waiting for an arbitrary old decorative cycle.
        if(active && !wasActive)clock=-1-slot*37;
        wasActive=active;
        int cycle = Math.floorMod(++clock + slot * 23, 140);
        if (!active) {
            if (laserPhase() != 0) phase((byte)0);
            return;
        }
        if (cycle == 0) {
            var candidates=new java.util.ArrayList<>(server.getPlayers(this::validTarget));
            candidates.sort(java.util.Comparator.comparing(Player::getUUID));
            aimTarget=candidates.isEmpty()?null:candidates.get(Math.floorMod(slot+clock/140,candidates.size())).getUUID();
            if(aimTarget!=null)phase((byte)1);
        }
        if(cycle<28 && laserPhase()==1) {
            Player player=aimTarget==null?null:server.getPlayerByUUID(aimTarget);
            if(player==null || !validTarget(player)) { phase((byte)0);return; }
            // One chosen player per warning; six fixtures distribute their targets across the group.
            if(cycle<22)point(player.getEyePosition());
        }
        if (cycle == 28 && laserPhase() == 1) {
            Vec3 from = laserOrigin(1), to = laserPoint();
            HitResult wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, from, to, new AABB(from,to).inflate(.3),
                    e -> e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator());
            if (hit != null) {
                to = hit.getLocation();
                hit.getEntity().hurt(damageSources().indirectMagic(this, owner), 8f);
            }
            point(to);
            phase((byte)2); playSound(ModSounds.JOASH_LASER.get(), 1.3f, 1f);
        }
        if (cycle == 40) phase((byte)0);
    }
    private boolean validTarget(Player p) {
        // Creative players are valid aim targets so testing shows the attack. Damage below
        // still respects creative invulnerability. Active heads may pan toward any arena
        // player, including one beside/behind a support. Only idle sweeps are forward-only.
        return p.isAlive() && !p.isSpectator()
                && Math.abs(p.getY()-center.getY())<8 && Math.abs(p.getX()-center.getX()-.5)<11 && Math.abs(p.getZ()-center.getZ()-.5)<11;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Boss", ownerId);
        tag.putByte("Facing",entityData.get(FACING));
        tag.putLong("Center", center.asLong()); tag.putInt("Slot", slot);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Boss")) ownerId = tag.getUUID("Boss");
        center = BlockPos.of(tag.getLong("Center")); slot = tag.getInt("Slot");
        if(tag.contains("Facing")) entityData.set(FACING,tag.getByte("Facing")); syncArena(); updateSweep();
    }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
}
