package net.austin.cabinmod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Holds no data - exists so DjBoothRenderer can draw the spinning records.
public class DjBoothBlockEntity extends BlockEntity {
    public DjBoothBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DJ_BOOTH_BE.get(), pos, state);
    }
}
