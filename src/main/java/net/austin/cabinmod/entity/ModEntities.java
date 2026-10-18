package net.austin.cabinmod.entity;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.BlueEntity;
import net.austin.cabinmod.entity.custom.ChairEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, CabinMod.MOD_ID);

    public static final Supplier<EntityType<BlueEntity>> BLUE_GUY =
            ENTITY_TYPES.register("blue_guy", () -> EntityType.Builder.of(BlueEntity::new, MobCategory.CREATURE)
                    .sized(0.5f, 1.75f).build("blue_guy"));

    public static final Supplier<EntityType<ChairEntity>> CHAIR_ENTITY =
            ENTITY_TYPES.register("chair_entity", ()-> EntityType.Builder.of(ChairEntity::new, MobCategory.MISC)
                    .sized(0.5f,0.5f).build("chair_entity"));


    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
