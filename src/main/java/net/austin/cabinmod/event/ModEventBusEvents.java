package net.austin.cabinmod.event;


import net.austin.cabinmod.entity.custom.JehoashIllusionEntity;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.entity.client.BlueModel;
import net.austin.cabinmod.entity.client.JehoashPlaceholderModel;
import net.austin.cabinmod.entity.custom.BlueEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEventBusEvents {

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(BlueModel.LAYER_LOCATION, BlueModel::createBodyLayer);
        event.registerLayerDefinition(JehoashPlaceholderModel.LAYER_LOCATION, JehoashPlaceholderModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event){
        event.put(ModEntities.BLUE_GUY.get(), BlueEntity.createAttributes().build());
        event.put(ModEntities.JOASH.get(), JehoashEntity.createAttributes().build());
        event.put(ModEntities.JOASH_ILLUSION.get(), JehoashIllusionEntity.createAttributes().build());
    }
}
