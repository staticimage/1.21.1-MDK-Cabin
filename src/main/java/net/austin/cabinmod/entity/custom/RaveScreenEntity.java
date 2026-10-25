package net.austin.cabinmod.entity.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Static image-board anchor. No server animation clock and no combat/collision behavior.
 * RaveScreenRenderer draws the two exact user-provided still images. Owner UUID is saved
 * only to identify the board when rebuilding a disposable preview; it never drives art.
 * The entity remains after the boss dies, and /summon can also place a standalone board. */
public class RaveScreenEntity extends Entity {
    private UUID owner;
    public RaveScreenEntity(EntityType<? extends RaveScreenEntity> type,Level level) { super(type,level); }
    public void configure(JehoashEntity boss) { owner=boss.getUUID(); }
    public UUID ownerId() { return owner; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) { if(owner!=null)tag.putUUID("Owner",owner); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { if(tag.hasUUID("Owner"))owner=tag.getUUID("Owner"); }
}
