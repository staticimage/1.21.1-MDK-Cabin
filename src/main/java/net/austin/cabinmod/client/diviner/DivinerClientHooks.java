package net.austin.cabinmod.client.diviner;

import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

// Client-only calls made from common code (CraftingBlockEntity's client ticker, StarboundResultSlot on the
// client, network/ModPayloads' client handler). Only reached inside client-side branches, so a dedicated server never loads this class.
public final class DivinerClientHooks {
    private DivinerClientHooks() {
    }

    public static void tickDiviner(CraftingBlockEntity diviner) {
        DivinerFx.track(diviner);
    }

    public static void onLaunched(CraftingBlockEntity diviner, ItemStack launched) {
        DivinerFx.onLaunched(diviner, launched);
    }

    /** The Cabin is speaking to `listener` at the diviner `pos`: hum along for everyone else around it. */
    public static void onCabinHum(BlockPos pos, int words, int seed, UUID listener) {
        HeavensFx.remoteHum(pos, words, seed, listener);
    }
}
