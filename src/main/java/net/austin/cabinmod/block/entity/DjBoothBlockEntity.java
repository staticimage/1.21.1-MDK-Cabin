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

// The booth's CENTER block: whether the phonk is on (synced to clients, who play it and animate the booth).
public class DjBoothBlockEntity extends BlockEntity {
    private boolean playing;

    public DjBoothBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DJ_BOOTH_BE.get(), pos, state);
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.playing = tag.getBoolean("Playing");
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
