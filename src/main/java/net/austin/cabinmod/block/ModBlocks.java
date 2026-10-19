package net.austin.cabinmod.block;

import net.austin.cabinmod.block.custom.DjBoothBlock;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.custom.ChairBlock;
import net.austin.cabinmod.block.custom.CraftingBlock;
import net.austin.cabinmod.block.custom.LightBlock;
import net.austin.cabinmod.block.custom.MikeBlock;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CabinMod.MOD_ID);

    public static final DeferredBlock<Block> CABIN_BLOCK = registerBlock("cabin_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                    .strength(4f)
                    .requiresCorrectToolForDrops()
                    .sound(ModSounds.CABIN_BLOCK_SOUNDS)
            ));

    public static final DeferredBlock<Block> RAW_CABIN_BLOCK = registerBlock("raw_cabin_block",
            ()-> new Block(BlockBehaviour.Properties.of()
                            .strength(4f)
                            .requiresCorrectToolForDrops()
                            .sound(ModSounds.CABIN_BLOCK_SOUNDS)
            ));

    public static final DeferredBlock<Block> CABIN_ORE = registerBlock("cabin_ore",
            ()-> new DropExperienceBlock(UniformInt.of(2,4), BlockBehaviour.Properties.of()
                    .strength(3f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            ));

    public static final DeferredBlock<Block> CABIN_ORE_DEEPSLATE = registerBlock("cabin_ore_deepslate",
            ()-> new DropExperienceBlock(UniformInt.of(3,6), BlockBehaviour.Properties.of()
                    .strength(4f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)
            ));

    public static final DeferredBlock<Block> MIKE_BLOCK = registerBlock("mike_block",
            ()-> new MikeBlock(BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()));



    public static final DeferredBlock<StairBlock> CABIN_STAIRS = registerBlock("cabin_stairs",
            () -> new StairBlock(ModBlocks.CABIN_BLOCK.get().defaultBlockState(), BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<SlabBlock> CABIN_SLAB = registerBlock("cabin_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<PressurePlateBlock> CABIN_PRESSURE_PLATE = registerBlock("cabin_pressure_plate",
            () -> new PressurePlateBlock(BlockSetType.IRON, BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<ButtonBlock> CABIN_BUTTON = registerBlock("cabin_button",
            () -> new ButtonBlock(BlockSetType.IRON, 20, BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()
                    .noCollission()));

    public static final DeferredBlock<FenceBlock> CABIN_FENCE = registerBlock("cabin_fence",
            () -> new FenceBlock(BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<FenceGateBlock> CABIN_FENCE_GATE = registerBlock("cabin_fence_gate",
            () -> new FenceGateBlock(WoodType.ACACIA, BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<WallBlock> CABIN_WALL = registerBlock("cabin_wall",
            () -> new WallBlock(BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<DoorBlock> CABIN_DOOR = registerBlock("cabin_door",
            () -> new DoorBlock(BlockSetType.COPPER, BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<TrapDoorBlock> CABIN_TRAPDOOR = registerBlock("cabin_trapdoor",
            () -> new TrapDoorBlock(BlockSetType.COPPER, BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> LIGHT_BLOCK = registerBlock("light_block",
            ()-> new LightBlock(BlockBehaviour.Properties.of()
                    .strength(2f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(LightBlock.CLICKED) ? 15:0)
            ));

    public static final DeferredBlock<Block> CHAIR = registerBlock("chair",
            ()-> new ChairBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(1f)
            ));

    public static final DeferredBlock<Block> CRAFTING_BLOCK = registerBlock("crafting_block",
            ()-> new CraftingBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(2f)
                    .requiresCorrectToolForDrops()
            ));





    public static final DeferredBlock<Block> DJ_BOOTH = registerBlock("dj_booth",
            ()-> new DjBoothBlock(BlockBehaviour.Properties.of()
                    .strength(5f, 1200f)
                    .noOcclusion()
                    .lightLevel(state -> 7)
            ));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block){
        ModItems.ITEMS.register(name, ()-> new BlockItem(block.get(), new Item.Properties()));
    }


    public static void register(IEventBus eventBus){
        BLOCKS.register(eventBus);
    }
}
