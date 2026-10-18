package net.austin.cabinmod.villager;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModVillagers {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, CabinMod.MOD_ID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS =
            DeferredRegister.create(BuiltInRegistries.VILLAGER_PROFESSION, CabinMod.MOD_ID);


    public static final Holder<PoiType> CABIN_POI = POI_TYPES.register("cabin_poi",
            ()-> new PoiType(ImmutableSet.copyOf(ModBlocks.MIKE_BLOCK.get().getStateDefinition().getPossibleStates()), 1,1));

    public static final Holder<VillagerProfession> CABINGER = VILLAGER_PROFESSIONS.register("cabinger",
            ()-> new VillagerProfession("cabinger", holder -> holder.value()==CABIN_POI.value(),
                    poiTypeHolder -> poiTypeHolder.value()==CABIN_POI.value(), ImmutableSet.of(), ImmutableSet.of(),
                    ModSounds.CABIN_BLOCK_HIT.get()));

    public static void register(IEventBus eventBus){
        POI_TYPES.register(eventBus);
        VILLAGER_PROFESSIONS.register(eventBus);
    }
}
