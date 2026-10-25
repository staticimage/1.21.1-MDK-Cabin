package net.austin.cabinmod.entity.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.UUID;

/** A persistent visual wreck; the server owns its timeline through Jehoash's saved stage state. */
public class FallingStageRigEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIME=SynchedEntityData.defineId(FallingStageRigEntity.class,EntityDataSerializers.INT);
    private UUID owner;
    public FallingStageRigEntity(EntityType<? extends FallingStageRigEntity> type,Level level) { super(type,level); }
    public void configure(JehoashEntity boss) { owner=boss.getUUID();entityData.set(TIME,boss.stageTime()); }
    public UUID ownerId() { return owner; }
    public int collapseTime() { return entityData.get(TIME); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(TIME,0); }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(8,6,3); }
    @Override public void tick() {
        super.tick();
        if(level() instanceof ServerLevel s && owner!=null && s.getEntity(owner) instanceof JehoashEntity boss) {
            if(!boss.isAlive()) discard(); else entityData.set(TIME,boss.stageTime());
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag t) { if(owner!=null)t.putUUID("Owner",owner);t.putInt("Time",collapseTime()); }
    @Override protected void readAdditionalSaveData(CompoundTag t) { if(t.hasUUID("Owner"))owner=t.getUUID("Owner");entityData.set(TIME,t.getInt("Time")); }
}
