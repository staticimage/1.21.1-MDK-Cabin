package net.austin.cabinmod.datagen;

import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.List;
import java.util.stream.Stream;

public class ModEntityLootTableProvider extends EntityLootSubProvider {
    public ModEntityLootTableProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    public void generate() {
        // Jehoash: always the Warden's Spare Key; one cabinmod disc at 10% (+10% per Looting level).
        LootPool.Builder disc = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(this.registries, 0.10f, 0.10f));
        for (Item item : List.of(ModItems.MO2_DISC.get(), ModItems.BRG_DISC.get(), ModItems.HYG_DISC.get(),
                ModItems.BLA_DISC.get(), ModItems.DNA_DISC.get(), ModItems.SOS_DISC.get())) {
            disc.add(LootItem.lootTableItem(item));
        }
        this.add(ModEntities.JOASH.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(ModItems.WARDENS_SPARE_KEY.get())))
                .withPool(disc));

        this.add(ModEntities.JOASH_ILLUSION.get(), LootTable.lootTable());
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return Stream.of(ModEntities.JOASH.get(), ModEntities.JOASH_ILLUSION.get());
    }
}
