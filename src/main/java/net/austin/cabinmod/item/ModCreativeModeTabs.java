package net.austin.cabinmod.item;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CabinMod.MOD_ID);


    public static final Supplier<CreativeModeTab> CABIN_ITEMS_TAB = CREATIVE_MODE_TAB.register("cabin_items_tab",
            ()-> CreativeModeTab.builder().icon(()-> new ItemStack(ModItems.CABIN.get()))
                    .title(Component.translatable("creativetab.cabinmod.cabin_items"))
                    .displayItems((itemDisplayParameters,output)->{
                        output.accept(ModItems.CABIN);
                        output.accept(ModItems.RAW_CABIN);
                        output.accept(ModItems.MICHAEL);
                        output.accept(ModItems.WAND);
                        output.accept(ModItems.MATTHEW);
                        output.accept(ModItems.MATTWICH);
                        output.accept(ModItems.COMPRESSED_CABIN);
                        output.accept(ModItems.CABIN_CORE);
                        output.accept(ModItems.WARDENS_SPARE_KEY);


                        output.accept(ModItems.EXCALIBUR);
                        output.accept(ModItems.DEAD_EXCALIBUR);
                        output.accept(ModItems.MATTY_MIKE);
                        output.accept(ModItems.CABIN_AXE);
                        output.accept(ModItems.MJOLNIR);
                        output.accept(ModItems.CABIN_BOW);

                        output.accept(ModItems.CABIN_SMITHING_TEMPLATE);
                        output.accept(ModItems.CABIN_STENCIL);

                        output.accept(ModItems.CABIN_HELMET);
                        output.accept(ModItems.CABIN_CHESTPLATE);
                        output.accept(ModItems.CABIN_LEGGINGS);
                        output.accept(ModItems.CABIN_BOOTS);

                        output.accept(ModItems.MO2_DISC);
                        output.accept(ModItems.BLA_DISC);
                        output.accept(ModItems.HYG_DISC);
                        output.accept(ModItems.DNA_DISC);
                        output.accept(ModItems.BRG_DISC);
                        output.accept(ModItems.SOS_DISC);

                        output.accept(ModItems.BLUE_SPAWN_EGG);
                        output.accept(ModItems.JOASH_SPAWN_EGG);



                    }).build());

    public static final Supplier<CreativeModeTab> CABIN_BLOCKS_TAB = CREATIVE_MODE_TAB.register("cabin_blocks_tab",
            ()-> CreativeModeTab.builder().icon(()-> new ItemStack(ModBlocks.CABIN_BLOCK.get()))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,"cabin_items_tab"))
                    .title(Component.translatable("creativetab.cabinmod.cabin_blocks"))
                    .displayItems((itemDisplayParameters,output)->{
                        output.accept(ModBlocks.CABIN_BLOCK);
                        output.accept(ModBlocks.RAW_CABIN_BLOCK);
                        output.accept(ModBlocks.CABIN_ORE);
                        output.accept(ModBlocks.CABIN_ORE_DEEPSLATE);
                        output.accept(ModBlocks.MIKE_BLOCK);
                        output.accept(ModBlocks.LIGHT_BLOCK);
                        output.accept(ModBlocks.DJ_BOOTH);

                        output.accept(ModBlocks.CABIN_STAIRS);
                        output.accept(ModBlocks.CABIN_SLAB);
                        output.accept(ModBlocks.CABIN_PRESSURE_PLATE);
                        output.accept(ModBlocks.CABIN_BUTTON);
                        output.accept(ModBlocks.CABIN_FENCE);
                        output.accept(ModBlocks.CABIN_FENCE_GATE);
                        output.accept(ModBlocks.CABIN_WALL);
                        output.accept(ModBlocks.CABIN_DOOR);
                        output.accept(ModBlocks.CABIN_TRAPDOOR);

                        output.accept(ModBlocks.CHAIR);
                        output.accept(ModBlocks.CRAFTING_BLOCK);




                    }).build());


    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
