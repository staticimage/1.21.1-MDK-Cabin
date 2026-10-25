package net.austin.cabinmod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// The center owns one persistent record. The boss locks it until defeat; all three parts share this state.
/*
 * RECORD OWNERSHIP MAP
 * The selected record index, owner UUID and combat lock are saved and synchronized.
 * Jehoash transfers its chosen track here when the booth separates at the dodge cue.
 * Only victory unlocks extraction. takeRecord clears the stored record atomically, so
 * multiple players clicking cannot duplicate it. Two platter images are visual copies
 * of ONE record. BoothProtection handles mining/explosion protection while locked.
 * The combat lock controls extraction ONLY. Playback continues while locked, and
 * bossId gives the client the same audio identity before/after the booth separates.
 */
public class DjBoothBlockEntity extends BlockEntity {
    private boolean playing;
    private int record = -1;
    private boolean locked;
    private java.util.UUID bossId;

    public boolean hasRecord() { return record >= 0; }
    public int recordTrack() { return Math.max(0, record); }
    public boolean isLocked() { return locked; }
    public java.util.UUID bossId() { return bossId; }
    public void bindBoss(java.util.UUID id, int track) {
        bossId = id; record = net.austin.cabinmod.entity.RecordTracks.valid(track); locked = true; setPlaying(true);
    }
    public void unlock(java.util.UUID id) {
        if (id.equals(bossId)) { locked = false; setPlaying(hasRecord()); }
    }
    public boolean insert(net.minecraft.world.item.ItemStack stack) {
        int index = net.austin.cabinmod.entity.RecordTracks.index(stack);
        if (locked || hasRecord() || index < 0) return false;
        bossId = null; record = index; setPlaying(true); return true;
    }
    public net.minecraft.world.item.ItemStack takeRecord() {
        if (locked || !hasRecord()) return net.minecraft.world.item.ItemStack.EMPTY;
        var stack = net.austin.cabinmod.entity.RecordTracks.stack(record);
        record = -1; setPlaying(false); return stack;
    }

    public DjBoothBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DJ_BOOTH_BE.get(), pos, state);
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing && hasRecord();
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // Client ticker: lets the music manager know this booth is on (client-only class, reached via the hooks).
    public static void clientTick(Level level, BlockPos pos, BlockState state, DjBoothBlockEntity booth) {
        net.austin.cabinmod.client.JehoashClientHooks.tickBooth(booth);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Playing", this.playing);
        tag.putInt("Record", record); tag.putBoolean("Locked", locked);
        if (bossId != null) tag.putUUID("Boss", bossId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        record = tag.contains("Record") ? tag.getInt("Record") : -1;
        if (record >= net.austin.cabinmod.entity.RecordTracks.COUNT) record = -1;
        locked = tag.getBoolean("Locked");
        bossId = tag.hasUUID("Boss") ? tag.getUUID("Boss") : null;
        // Also migrate older combat saves, which incorrectly silenced locked booths.
        this.playing = (tag.getBoolean("Playing") || locked) && hasRecord();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
