package net.austin.cabinmod;

import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.block.entity.ModBlockEntities;
import net.austin.cabinmod.effect.ModEffects;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.entity.client.BlueRenderer;
import net.austin.cabinmod.entity.client.JehoashRenderer;
import net.austin.cabinmod.entity.client.ChairRenderer;
import net.austin.cabinmod.item.ModCreativeModeTabs;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.loot.ModLootModifiers;
import net.austin.cabinmod.recipe.ModRecipes;
import net.austin.cabinmod.screen.ModMenuTypes;
import net.austin.cabinmod.screen.custom.CraftingBlockScreen;
import net.austin.cabinmod.sounds.ModSounds;
import net.austin.cabinmod.util.ModItemProperties;
import net.austin.cabinmod.villager.ModVillagers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CabinMod.MOD_ID)
public class CabinMod {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "cabinmod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();


    public CabinMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        ModCreativeModeTabs.register(modEventBus);

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);

        ModSounds.register(modEventBus);
        ModEntities.register(modEventBus);

        ModVillagers.register(modEventBus);

        ModLootModifiers.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        ModMenuTypes.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModEffects.register(modEventBus);
        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            ModItemProperties.addCustomItemProperties();

            EntityRenderers.register(ModEntities.BLUE_GUY.get(), BlueRenderer::new);
            EntityRenderers.register(ModEntities.CHAIR_ENTITY.get(), ChairRenderer::new);
            EntityRenderers.register(ModEntities.GLASS_SHARD.get(), net.austin.cabinmod.entity.client.GlassShardRenderer::new);
            EntityRenderers.register(ModEntities.RAVE_SCREEN.get(), net.austin.cabinmod.entity.client.RaveScreenRenderer::new);
            EntityRenderers.register(ModEntities.TRUSS_SUPPORT.get(), net.austin.cabinmod.entity.client.TrussSupportRenderer::new);
            EntityRenderers.register(ModEntities.FALLING_STAGE_RIG.get(), net.austin.cabinmod.entity.client.FallingStageRigRenderer::new);
            EntityRenderers.register(ModEntities.ARENA_LIGHT.get(), net.austin.cabinmod.entity.client.ArenaLightRenderer::new);
            EntityRenderers.register(ModEntities.JOASH.get(), JehoashRenderer::new);
            EntityRenderers.register(ModEntities.JOASH_ILLUSION.get(), JehoashRenderer::new);
        }

//        @SubscribeEvent
//        public static void registerScreens(RegisterMenuScreensEvent event){
//            event.register(ModMenuTypes.CRAFTING_BLOCK_MENU.get(), CraftingBlockScreen::new);
//        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event){
            if (ModMenuTypes.CRAFTING_BLOCK_MENU.isBound()) {
                event.register(ModMenuTypes.CRAFTING_BLOCK_MENU.get(), CraftingBlockScreen::new);
            } else {
                LOGGER.error("TS IS NOT WORKING LMAO!!!");
            }
        }
    }
}
