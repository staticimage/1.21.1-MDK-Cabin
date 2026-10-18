package net.austin.cabinmod.datagen;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.loot.AddItemModifier;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;

import java.util.concurrent.CompletableFuture;

public class ModGlobalLootModifierProvider extends GlobalLootModifierProvider {
    public ModGlobalLootModifierProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, CabinMod.MOD_ID);
    }

    @Override
    protected void start() {
//        this.add("loot_from_block",
//                new AddItemModifier(new LootItemCondition[]{
//                        LootItemBlockStatePropertyCondition.hasBlockStateProperties(Blocks.BLOCK_NAME).build(),
//                        LootItemRandomChanceCondition.randomChance(value).build()
//                }, ModItems.CABIN.get()));
//
//        this.add("loot_from_place",
//                new AddItemModifier(new LootItemCondition[]{
//                        new LootTableIdCondition.Builder(ResourceLocation.withDefaultNamespace("chests/thing")).build()
//                }, ModItems.CABIN.get()));
//
//        this.add("loot_from_entity",
//                new AddItemModifier(new LootItemCondition[]{
//                        new LootTableIdCondition.Builder(ResourceLocation.withDefaultNamespace("entities/thing")).build(),
//                        LootItemRandomChanceCondition.randomChance(value).build()
//                }, ModItems.CABIN.get()));

        this.add("cabin_core_from_warden",
                new AddItemModifier(new LootItemCondition[]{
                        new LootTableIdCondition.Builder(ResourceLocation.withDefaultNamespace("entities/warden")).build()
                }, ModItems.CABIN_CORE.get()));


    }
}
