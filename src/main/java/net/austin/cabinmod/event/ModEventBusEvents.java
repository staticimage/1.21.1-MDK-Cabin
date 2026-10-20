package net.austin.cabinmod.event;

import net.austin.cabinmod.block.entity.ModBlockEntities;
import net.austin.cabinmod.block.entity.renderer.DjBoothRenderer;


import net.austin.cabinmod.entity.custom.JehoashIllusionEntity;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.entity.client.BlueModel;
import net.austin.cabinmod.entity.client.generated.DjBoothModelBase;
import net.austin.cabinmod.entity.client.generated.JehoashModelBase;
import net.austin.cabinmod.entity.custom.BlueEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEventBusEvents {

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerBlockEntityRenderer(ModBlockEntities.DJ_BOOTH_BE.get(), DjBoothRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(BlueModel.LAYER_LOCATION, BlueModel::createBodyLayer);
        event.registerLayerDefinition(JehoashModelBase.LAYER_LOCATION, JehoashModelBase::createBodyLayer);
        event.registerLayerDefinition(DjBoothModelBase.LAYER_LOCATION, DjBoothModelBase::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(ModEntities.BLUE_GUY.get(), BlueEntity.createAttributes().build());
        event.put(ModEntities.JOASH.get(), JehoashEntity.createAttributes().build());
        event.put(ModEntities.JOASH_ILLUSION.get(), JehoashIllusionEntity.createAttributes().build());
    }
}
