package net.austin.cabinmod.block.entity;
import net.austin.cabinmod.block.custom.CraftingBlock;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.recipe.CraftingBlockRecipe;
import net.austin.cabinmod.recipe.CraftingBlockRecipeInput;
import net.austin.cabinmod.recipe.ModRecipes;
import net.austin.cabinmod.screen.custom.CraftingBlockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CraftingBlockEntity extends BlockEntity implements MenuProvider {
    // Ticks from closing the menu to the starfall landing on the diviner. The client cinematic
    // (client/diviner/DivinerCinematic) plays its return in exactly this many ticks.
    public static final int RETURN_TICKS = 80;
    /** Owner of stars launched by no one in particular (and of stars saved before stars were per player). */
    public static final UUID NOBODY = new UUID(0, 0);

    public final ItemStackHandler itemHandler = new ItemStackHandler(2){
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!level.isClientSide()){
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;

    protected final ContainerData data;
    private int progress = 0;
    private int maxProgress = 200;

    // Results taken from the output slot ride a shooting star back down instead of going to the player.
    // Each player's launches are their own stars: they fall when that player leaves the heavens.
    private final List<Star> stars = new ArrayList<>();
    private int nextStarId = 1;
    // input of the last craft, carried by the next launch so the star can take its colours
    private ItemStack lastInput = ItemStack.EMPTY;
    // players up in the heavens through this diviner (synced: everyone nearby sees them in a trance)
    private final Set<UUID> viewers = new HashSet<>();

    /** A launched result in the sky. landTick is -1 while it waits for its owner to come back down. */
    public static final class Star {
        public final int id;
        public final UUID owner;
        public final ItemStack result;
        public final ItemStack input;
        public long landTick = -1;

        Star(int id, UUID owner, ItemStack result, ItemStack input) {
            this.id = id;
            this.owner = owner;
            this.result = result;
            this.input = input;
        }

        public boolean falling() {
            return landTick >= 0;
        }

        CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putUUID("owner", owner);
            tag.put("result", result.save(registries));
            if (!input.isEmpty()) tag.put("input", input.save(registries));
            tag.putLong("land", landTick);
            return tag;
        }

        static Optional<Star> load(CompoundTag tag, HolderLookup.Provider registries) {
            return ItemStack.parse(registries, tag.getCompound("result")).map(result -> {
                ItemStack input = tag.contains("input")
                        ? ItemStack.parse(registries, tag.getCompound("input")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
                Star star = new Star(tag.getInt("id"), tag.hasUUID("owner") ? tag.getUUID("owner") : NOBODY, result, input);
                star.landTick = tag.getLong("land");
                return star;
            });
        }
    }

    public CraftingBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CRAFTING_BLOCK_BE.get(), pos, blockState);
        data = new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i){
                    case 0 -> CraftingBlockEntity.this.progress;
                    case 1 -> CraftingBlockEntity.this.maxProgress;
                    case 2 -> CraftingBlockEntity.this.hasPendingStar() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int value) {
                switch (i){
                    case 0: CraftingBlockEntity.this.progress = value; break;
                    case 1: CraftingBlockEntity.this.maxProgress = value; break;
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cabinmod.crafting_block");
    }


    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // slot 2 is this player's own waiting stars, so their close decides their own starfall
        UUID id = player.getUUID();
        ContainerData mine = new ContainerData() {
            @Override
            public int get(int i) {
                return i == 2 ? (hasPendingStar(id) ? 1 : 0) : data.get(i);
            }

            @Override
            public void set(int i, int value) {
                data.set(i, value);
            }

            @Override
            public int getCount() {
                return data.getCount();
            }
        };
        return new CraftingBlockMenu(containerId, playerInventory, this, mine);
    }

    public void drops(){
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots() + stars.size());
        for(int i=0; i<itemHandler.getSlots(); i++){
            inventory.setItem(i,itemHandler.getStackInSlot(i));
        }
        for (int i = 0; i < stars.size(); i++) {
            inventory.setItem(itemHandler.getSlots() + i, stars.get(i).result);
        }
        stars.clear();

        Containers.dropContents(this.level, this.worldPosition, inventory);
    }

    // ---------------------------------------------------------------- starfall

    /** Sends a result up to the stars with no owner; it falls on the next check. */
    public void launch(ItemStack stack) {
        launch(NOBODY, stack);
    }

    /** Sends a player's result up to the stars; it lands back on the diviner once they leave the heavens. */
    public void launch(UUID owner, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (Star star : stars) {
            ItemStack pending = star.result;
            if (!star.falling() && star.owner.equals(owner) && ItemStack.isSameItem(star.input, lastInput)
                    && ItemStack.isSameItemSameComponents(pending, stack) && pending.getCount() < pending.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), pending.getMaxStackSize() - pending.getCount());
                pending.grow(moved);
                stack.shrink(moved);
                if (stack.isEmpty()) break;
            }
        }
        if (!stack.isEmpty()) stars.add(new Star(nextStarId++, owner, stack.copy(), lastInput.copy()));
        sync();
    }

    /** Starts every waiting star falling (server). Each lands RETURN_TICKS later. */
    public void beginStarfall() {
        for (Star star : stars) startFalling(star);
        sync();
    }

    /** Starts one player's waiting stars falling (server). */
    public void beginStarfall(UUID owner) {
        for (Star star : stars) if (star.owner.equals(owner)) startFalling(star);
        sync();
    }

    private void startFalling(Star star) {
        if (level == null || level.isClientSide() || star.falling()) return;
        star.landTick = level.getGameTime() + RETURN_TICKS;
    }

    public boolean hasPendingStar() {
        return !stars.isEmpty();
    }

    /** True while this player has a star up there that hasn't started falling. */
    public boolean hasPendingStar(UUID owner) {
        for (Star star : stars) if (star.owner.equals(owner) && !star.falling()) return true;
        return false;
    }

    public List<Star> stars() {
        return stars;
    }

    /** The launched results still in the sky, in launch order. */
    public List<ItemStack> pendingStars() {
        return stars.stream().map(star -> star.result).toList();
    }

    /** Earliest landing among the falling stars, -1 if none is falling. */
    public long starfallLandTick() {
        long first = -1;
        for (Star star : stars) if (star.falling() && (first < 0 || star.landTick < first)) first = star.landTick;
        return first;
    }

    private void land(Level level, Star star) {
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.02, z = worldPosition.getZ() + 0.5;
        ItemEntity item = new ItemEntity(level, x, y, z, star.result.copy(), 0, 0, 0);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(10);
        level.addFreshEntity(item);
    }

    // ---------------------------------------------------------------- the heavens

    /** A player opened the diviner (server). */
    public void addViewer(Player player) {
        if (viewers.add(player.getUUID())) sync();
    }

    /** A player left the heavens (server): their stars come down. */
    public void removeViewer(Player player) {
        viewers.remove(player.getUUID());
        beginStarfall(player.getUUID());
    }

    public Set<UUID> viewers() {
        return viewers;
    }

    /** Whether this player is still in this diviner's menu (players not in the level count while they're viewers). */
    private boolean viewing(Level level, UUID id) {
        Player p = level.getPlayerByUUID(id);
        if (p == null) return viewers.contains(id);
        return p.containerMenu instanceof CraftingBlockMenu menu && menu.blockEntity == this;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("inventory", itemHandler.serializeNBT(registries));
        tag.putInt("crafting_block.progress", progress);
        tag.putInt("crafting_block.max_progress", maxProgress);
        ListTag list = new ListTag();
        for (Star star : stars) list.add(star.save(registries));
        tag.put("crafting_block.stars", list);
        tag.putInt("crafting_block.next_star", nextStarId);
        if (!lastInput.isEmpty()) tag.put("crafting_block.last_input", lastInput.save(registries));

        super.saveAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        itemHandler.deserializeNBT(registries,tag.getCompound("inventory"));
        progress = tag.getInt("crafting_block.progress");
        maxProgress = tag.getInt("crafting_block.max_progress");
        stars.clear();
        nextStarId = Math.max(1, tag.getInt("crafting_block.next_star"));
        for (Tag t : tag.getList("crafting_block.stars", Tag.TAG_COMPOUND)) {
            Star.load((CompoundTag) t, registries).ifPresent(stars::add);
        }
        // worlds saved before stars were per player
        long oldLand = tag.contains("crafting_block.starfall_land") ? tag.getLong("crafting_block.starfall_land") : -1;
        for (Tag t : tag.getList("crafting_block.pending_stars", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(stack -> {
                Star star = new Star(nextStarId++, NOBODY, stack, ItemStack.EMPTY);
                star.landTick = oldLand;
                stars.add(star);
            });
        }
        lastInput = tag.contains("crafting_block.last_input")
                ? ItemStack.parse(registries, tag.getCompound("crafting_block.last_input")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        viewers.clear();
        for (Tag t : tag.getList("crafting_block.viewers", Tag.TAG_INT_ARRAY)) viewers.add(NbtUtils.loadUUID(t));
    }

    // Client ticker: hands stars and viewers to the in-world effects (client-only classes, reached via the hooks).
    public static void clientTick(Level level, BlockPos pos, BlockState state, CraftingBlockEntity diviner) {
        net.austin.cabinmod.client.diviner.DivinerClientHooks.tickDiviner(diviner);
    }

    public void tick(Level level, BlockPos blockPos, BlockState blockState) {
        boolean changed = stars.removeIf(star -> {
            if (!star.falling() || level.getGameTime() < star.landTick) return false;
            land(level, star);
            return true;
        });
        if (level.getGameTime() % 20 == 0) {
            // e.g. a viewer disconnected, or the world reloaded with someone's star still up
            changed |= viewers.removeIf(id -> !viewing(level, id));
            for (Star star : stars) {
                if (!star.falling() && !viewing(level, star.owner)) {
                    startFalling(star);
                    changed = true;
                }
            }
        }
        if (changed) sync();

        boolean working = hasRecipe();
        // Show the lit front (crafting_block_front_on) while an item is being processed.
        if(blockState.getValue(CraftingBlock.LIT) != working){
            blockState = blockState.setValue(CraftingBlock.LIT, working);
            level.setBlock(blockPos, blockState, 3);
        }

        if(working){
            increaseCraftingProgress();
            setChanged(level,blockPos,blockState);

            if(hasCraftingFinished()){
                craftItem();
                resetProgress();
            }
        }else {
            resetProgress();
        }
    }

    private void craftItem() {
        Optional<RecipeHolder<CraftingBlockRecipe>> recipe = getCurrentRecipe();
        ItemStack output = recipe.get().value().output();

        lastInput = itemHandler.getStackInSlot(INPUT_SLOT).copyWithCount(1);
        itemHandler.extractItem(INPUT_SLOT, 1,false);
        itemHandler.setStackInSlot(OUTPUT_SLOT, output.copyWithCount(
                itemHandler.getStackInSlot(OUTPUT_SLOT).getCount()+output.getCount()));
    }

    private void resetProgress() {
        progress = 0;
        maxProgress = 200;
    }

    private boolean hasCraftingFinished(){
        return this.progress >= this.maxProgress;
    }

    private void increaseCraftingProgress() {
        progress++;
    }

    private boolean hasRecipe() {
        Optional<RecipeHolder<CraftingBlockRecipe>> recipe = getCurrentRecipe();
        if (recipe.isEmpty()){
            return false;
        }

        ItemStack output = recipe.get().value().output();
        return canInsertAmountIntoOutputSlot(output.getCount()) && canInsertItemIntoOutputSlot(output);
    }

    private Optional<RecipeHolder<CraftingBlockRecipe>> getCurrentRecipe() {
        return this.level.getRecipeManager()
                .getRecipeFor(ModRecipes.CRAFTING_BLOCK_TYPE.get(), new CraftingBlockRecipeInput(itemHandler.getStackInSlot(INPUT_SLOT)), level);
    }

    private boolean canInsertItemIntoOutputSlot(ItemStack output) {
        return itemHandler.getStackInSlot(OUTPUT_SLOT).isEmpty()||
                itemHandler.getStackInSlot(OUTPUT_SLOT).getItem()==output.getItem();
    }

    private boolean canInsertAmountIntoOutputSlot(int count) {
        int maxCount = itemHandler.getStackInSlot(OUTPUT_SLOT).isEmpty() ? 64 : itemHandler.getStackInSlot(OUTPUT_SLOT).getMaxStackSize();
        int currentCount = itemHandler.getStackInSlot(OUTPUT_SLOT).getCount();

        return maxCount >= currentCount +count;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        // who is up in the heavens goes to clients only: a reloaded world starts with nobody up there
        ListTag watching = new ListTag();
        for (UUID id : viewers) watching.add(NbtUtils.createUUID(id));
        tag.put("crafting_block.viewers", watching);
        return tag;
    }


    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
