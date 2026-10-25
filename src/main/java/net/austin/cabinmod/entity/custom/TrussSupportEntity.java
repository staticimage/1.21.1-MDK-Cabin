package net.austin.cabinmod.entity.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Pickable sleeve around a structural column. Its damage belongs to the encounter, not block mining. */
/*
 * SUPPORT TARGET CONTRACT
 * The actual truss is an unbreakable StageTrussBlock; this slightly wider 1.3x5 entity
 * wraps a column so melee/projectile rays hit it before the solid block. hurt() forwards
 * valid damage to the owning boss, including the FIRST dormant hit that starts phase
 * one. Health/scaling live on the boss, so replacing/reloading a hitbox cannot reset HP.
 * The 8-tick hit cooldown is PER ATTACKER UUID, not global. Change it here to tune hit
 * frequency. The boss caps each accepted hit and counts the hitter's stage pressure.
 * Renderer collars/labels use synchronized HP; the entity itself has no knockback/AI.
 */
public class TrussSupportEntity extends Entity {
    private static final EntityDataAccessor<Float> HEALTH=SynchedEntityData.defineId(TrussSupportEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SIDE=SynchedEntityData.defineId(TrussSupportEntity.class,EntityDataSerializers.INT);
    private UUID owner;
    private static final EntityDataAccessor<Float> MAX=SynchedEntityData.defineId(TrussSupportEntity.class,EntityDataSerializers.FLOAT);
    private final java.util.Map<UUID,Integer> lastHits=new java.util.HashMap<>();
    public float maxHealth() { return entityData.get(MAX); }
    public TrussSupportEntity(EntityType<? extends TrussSupportEntity> type,Level level) { super(type,level); }
    public void configure(JehoashEntity boss,int side) { owner=boss.getUUID();entityData.set(SIDE,side);entityData.set(MAX,boss.maxSupportHealth());entityData.set(HEALTH,boss.supportHealth(side)); }
    public UUID ownerId() { return owner; }
    public int side() { return entityData.get(SIDE); }
    public float health() { return entityData.get(HEALTH); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(MAX,60f);b.define(HEALTH,60f);b.define(SIDE,0); }
    @Override public boolean isPickable() { return health()>0; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(owner!=null && level() instanceof ServerLevel s && s.getEntity(owner) instanceof JehoashEntity boss && source.getEntity()!=null && tickCount-lastHits.getOrDefault(source.getEntity().getUUID(),-20)>=8) {
            if(boss.damageSupport(side(),source,amount)) { lastHits.put(source.getEntity().getUUID(),tickCount);entityData.set(HEALTH,boss.supportHealth(side()));return true; }
        }
        return false;
    }
    @Override public void tick() {
        super.tick();
        if(level() instanceof ServerLevel s && owner!=null && s.getEntity(owner) instanceof JehoashEntity boss) {
            entityData.set(MAX,boss.maxSupportHealth());entityData.set(HEALTH,boss.supportHealth(side()));
            if(!boss.isAlive() || boss.stagePhase()>JehoashEntity.CONDUCT) discard();
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag t) { if(owner!=null)t.putUUID("Owner",owner);t.putFloat("Max",maxHealth());t.putInt("Side",side());t.putFloat("Health",health()); }
    @Override protected void readAdditionalSaveData(CompoundTag t) { if(t.hasUUID("Owner"))owner=t.getUUID("Owner");entityData.set(MAX,t.contains("Max")?t.getFloat("Max"):60f);entityData.set(SIDE,t.getInt("Side"));entityData.set(HEALTH,t.getFloat("Health")); }
}
