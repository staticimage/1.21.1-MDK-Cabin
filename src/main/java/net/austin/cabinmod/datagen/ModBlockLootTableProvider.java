package net.austin.cabinmod.datagen;

import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {

    protected ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.CABIN_BLOCK.get());
        dropSelf(ModBlocks.RAW_CABIN_BLOCK.get());
        dropSelf(ModBlocks.MIKE_BLOCK.get());
        dropSelf(ModBlocks.LIGHT_BLOCK.get());

        dropSelf(ModBlocks.CABIN_STAIRS.get());
        dropSelf(ModBlocks.CABIN_PRESSURE_PLATE.get());
        dropSelf(ModBlocks.CABIN_BUTTON.get());
        dropSelf(ModBlocks.CABIN_FENCE.get());
        dropSelf(ModBlocks.CABIN_FENCE_GATE.get());
        dropSelf(ModBlocks.CABIN_WALL.get());
        dropSelf(ModBlocks.CABIN_TRAPDOOR.get());
        dropSelf(ModBlocks.CHAIR.get());
        dropSelf(ModBlocks.CRAFTING_BLOCK.get());



        add(ModBlocks.CABIN_SLAB.get(),
                block -> createSlabItemTable(ModBlocks.CABIN_SLAB.get()));
        add(ModBlocks.CABIN_DOOR.get(),
                block -> createDoorTable(ModBlocks.CABIN_DOOR.get()));

        add(ModBlocks.CABIN_ORE.get(),
                block -> createOreDrop(ModBlocks.CABIN_ORE.get(), ModItems.RAW_CABIN.get()));
        add(ModBlocks.CABIN_ORE_DEEPSLATE.get(),
                block -> createMultipleOreDrops(ModBlocks.CABIN_ORE_DEEPSLATE.get(),ModItems.RAW_CABIN.get(),2,5));
    }

    protected LootTable.Builder createMultipleOreDrops(Block pBlock, Item item, float minDrops, float maxDrops) {
        HolderLookup.RegistryLookup<Enchantment> registrylookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(pBlock,
                this.applyExplosionDecay(pBlock, LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(minDrops, maxDrops)))
                        .apply(ApplyBonusCount.addOreBonusCount(registrylookup.getOrThrow(Enchantments.FORTUNE)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}
