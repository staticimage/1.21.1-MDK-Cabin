package net.austin.cabinmod.screen.custom;

import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.screen.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class CraftingBlockMenu extends AbstractContainerMenu {
    public final CraftingBlockEntity blockEntity;
    private final Level level;
    private final ContainerData data;

    public CraftingBlockMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData){
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(3));
    }

    public CraftingBlockMenu(int pContainerId, Inventory inv, BlockEntity entity, ContainerData data){
        super(ModMenuTypes.CRAFTING_BLOCK_MENU.get(), pContainerId);
        this.blockEntity = ((CraftingBlockEntity) entity);
        this.level = inv.player.level();
        this.data = data;

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        this.addSlot(new SlotItemHandler(blockEntity.itemHandler, 0, 54, 34));
        this.addSlot(new StarboundResultSlot(blockEntity.itemHandler, blockEntity, 1, 104, 34));

        addDataSlots(data);
        if (!level.isClientSide()) blockEntity.addViewer(inv.player);
    }

    /** True while this player's launched result waits to fall back down (decides the starfall return on close). */
    public boolean hasPendingStar(){
        return data.get(2) != 0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) blockEntity.removeViewer(player);   // brings this player's stars down
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // a hotbar-number swap would copy the result straight into the inventory: launch it instead
        if (clickType == ClickType.SWAP && slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof StarboundResultSlot starbound) {
            starbound.launch(player, starbound.getItem().getCount());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof StarboundResultSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    public boolean isCrafting(){
        return data.get(0)>0;
    }

    public int getScaledArrowProgress(){
        int progress = this.data.get(0);
        int maxProgress = this.data.get(1);
        int arrowPixelSize = 24;

        return maxProgress != 0 && progress != 0 ? progress * arrowPixelSize / maxProgress : 0;
    }



    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    private static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;
    private static final int TE_INVENTORY_SLOT_COUNT = 2;  // must be the number of slots you have!
    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;  //EMPTY_ITEM
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // Check if the slot clicked is one of the vanilla container slots
        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            // This is a vanilla container slot so merge the stack into the tile inventory
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + TE_INVENTORY_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;  // EMPTY_ITEM
            }
        } else if (sourceSlot instanceof StarboundResultSlot starbound) {
            // Shift-clicking the result launches the whole stack instead of moving it to the inventory.
            starbound.launch(playerIn, sourceStack.getCount());
            return ItemStack.EMPTY;
        } else if (pIndex < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            // This is a TE slot so merge the stack into the players inventory
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + pIndex);
            return ItemStack.EMPTY;
        }
        // If stack size == 0 (the entire stack was moved) set slot contents to null
        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
                player, ModBlocks.CRAFTING_BLOCK.get());
    }

    private void addPlayerInventory(Inventory playerInventory){
        for (int i=0; i<3; ++i){
            for(int l=0; l<9; ++l){
                this.addSlot(new Slot(playerInventory, l+i*9+9,8+l*18,84+i*18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory){
        for (int i=0; i<9; ++i){
            this.addSlot(new Slot(playerInventory, i, 8+i*18, 142));
        }
    }
}
