package net.austin.cabinmod.event;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.custom.DjBoothBlock;
import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = CabinMod.MOD_ID)
public final class BoothProtection {
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof DjBoothBlock
                && event.getLevel().getBlockEntity(DjBoothBlock.centerOf(event.getState(), event.getPos())) instanceof DjBoothBlockEntity booth
                && booth.isLocked()) event.setCanceled(true);
    }
    @SubscribeEvent public static void explode(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> {
            var state = event.getLevel().getBlockState(pos);
            return state.getBlock() instanceof DjBoothBlock
                    && event.getLevel().getBlockEntity(DjBoothBlock.centerOf(state,pos)) instanceof DjBoothBlockEntity booth
                    && booth.isLocked();
        });
    }
}
