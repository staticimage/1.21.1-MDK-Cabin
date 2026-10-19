package net.austin.cabinmod.block.custom;

import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

// The Disco's DJ booth: using it starts the party - wakes the dormant Jehoash in the arena.
public class DjBoothBlock extends Block {
    private static final double SEARCH_RADIUS = 32;

    public DjBoothBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            level.playSound(null, pos, ModSounds.DJ_BOOTH_USE.get(), SoundSource.BLOCKS, 1f, 1f);
            for (JehoashEntity jehoash : level.getEntitiesOfClass(JehoashEntity.class, new AABB(pos).inflate(SEARCH_RADIUS), JehoashEntity::isDormant)) {
                jehoash.wake(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
