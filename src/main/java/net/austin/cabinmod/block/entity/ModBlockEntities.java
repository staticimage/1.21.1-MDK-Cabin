package net.austin.cabinmod.block.entity;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.CraftingBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CabinMod.MOD_ID);

    public static final Supplier<BlockEntityType<CraftingBlockEntity>> CRAFTING_BLOCK_BE =
            BLOCK_ENTITIES.register("crafting_block_be", ()-> BlockEntityType.Builder.of(
                    CraftingBlockEntity::new, ModBlocks.CRAFTING_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus){
        BLOCK_ENTITIES.register(eventBus);
    }
}
