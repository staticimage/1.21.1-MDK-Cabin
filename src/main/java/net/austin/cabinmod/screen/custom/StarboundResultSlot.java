package net.austin.cabinmod.screen.custom;

import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Optional;

// The diviner's output. Nothing can be put in, and taking a result never puts it on the cursor:
// the stack is launched into the sky (CraftingBlockEntity#launch) and comes back down as a
// shooting star when the menu closes. Click, shift-click and Q-throw all go through tryRemove/safeTake;
// the menu turns hotbar swaps into launches and keeps double-click collect away from this slot.
public class StarboundResultSlot extends SlotItemHandler {
    private final CraftingBlockEntity diviner;

    public StarboundResultSlot(IItemHandler handler, CraftingBlockEntity diviner, int index, int x, int y) {
        super(handler, index, x, y);
        this.diviner = diviner;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
        launch(player, count);
        return Optional.empty();
    }

    @Override
    public ItemStack safeTake(int count, int decrement, Player player) {
        launch(player, count);
        return ItemStack.EMPTY;
    }

    /** Launches up to `count` of the result. Returns what was launched. */
    public ItemStack launch(Player player, int count) {
        if (!hasItem()) return ItemStack.EMPTY;
        ItemStack taken = remove(Math.min(count, getItem().getCount()));
        if (taken.isEmpty()) return ItemStack.EMPTY;
        ItemStack launched = taken.copy();
        if (player.level().isClientSide()) {
            net.austin.cabinmod.client.diviner.DivinerClientHooks.onLaunched(diviner, launched);
        } else {
            diviner.launch(player.getUUID(), taken);
        }
        setChanged();
        return launched;
    }
}
