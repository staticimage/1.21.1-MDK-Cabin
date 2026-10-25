package net.austin.cabinmod.entity;

import net.austin.cabinmod.entity.custom.JehoashIllusionEntity;
import net.austin.cabinmod.entity.custom.JehoashEntity;
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


    public static final Supplier<EntityType<JehoashEntity>> JOASH =
            ENTITY_TYPES.register("joash", () -> EntityType.Builder.of(JehoashEntity::new, MobCategory.MONSTER)
                    .sized(1.5f, 3.5f).fireImmune().clientTrackingRange(10).build("joash"));

    public static final Supplier<EntityType<JehoashIllusionEntity>> JOASH_ILLUSION =
            ENTITY_TYPES.register("joash_illusion", () -> EntityType.Builder.of(JehoashIllusionEntity::new, MobCategory.MONSTER)
                    .sized(1.5f, 3.5f).fireImmune().clientTrackingRange(10).build("joash_illusion"));

    public static final Supplier<EntityType<net.austin.cabinmod.entity.custom.GlassShardEntity>> GLASS_SHARD =
            ENTITY_TYPES.register("glass_shard", () -> EntityType.Builder.<net.austin.cabinmod.entity.custom.GlassShardEntity>of(net.austin.cabinmod.entity.custom.GlassShardEntity::new, MobCategory.MISC)
                    .sized(.2f,.2f).clientTrackingRange(8).updateInterval(1).build("glass_shard"));
    public static final Supplier<EntityType<net.austin.cabinmod.entity.custom.ArenaLightEntity>> ARENA_LIGHT =
            ENTITY_TYPES.register("arena_light", () -> EntityType.Builder.<net.austin.cabinmod.entity.custom.ArenaLightEntity>of(net.austin.cabinmod.entity.custom.ArenaLightEntity::new, MobCategory.MISC)
                    .sized(.4f,.4f).fireImmune().clientTrackingRange(10).updateInterval(1).build("arena_light"));

    public static final Supplier<EntityType<net.austin.cabinmod.entity.custom.TrussSupportEntity>> TRUSS_SUPPORT =
            ENTITY_TYPES.register("truss_support", () -> EntityType.Builder.<net.austin.cabinmod.entity.custom.TrussSupportEntity>of(net.austin.cabinmod.entity.custom.TrussSupportEntity::new, MobCategory.MISC)
                    .sized(1.3f,5f).fireImmune().clientTrackingRange(10).updateInterval(1).build("truss_support"));
    public static final Supplier<EntityType<net.austin.cabinmod.entity.custom.FallingStageRigEntity>> FALLING_STAGE_RIG =
            ENTITY_TYPES.register("falling_stage_rig", () -> EntityType.Builder.<net.austin.cabinmod.entity.custom.FallingStageRigEntity>of(net.austin.cabinmod.entity.custom.FallingStageRigEntity::new, MobCategory.MISC)
                    .sized(13f,1f).fireImmune().clientTrackingRange(12).updateInterval(1).build("falling_stage_rig"));

    public static final Supplier<EntityType<net.austin.cabinmod.entity.custom.RaveScreenEntity>> RAVE_SCREEN =
            ENTITY_TYPES.register("rave_screen", () -> EntityType.Builder.<net.austin.cabinmod.entity.custom.RaveScreenEntity>of(net.austin.cabinmod.entity.custom.RaveScreenEntity::new, MobCategory.MISC)
                    .sized(8.7f,4.3f).fireImmune().clientTrackingRange(12).build("rave_screen"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
