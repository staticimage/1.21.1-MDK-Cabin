package net.austin.cabinmod.recipe;

import net.austin.cabinmod.CabinMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CabinMod.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, CabinMod.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CraftingBlockRecipe>> CRAFTING_BLOCK_SERIALIZER =
            SERIALIZERS.register("crafting_block", CraftingBlockRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<CraftingBlockRecipe>> CRAFTING_BLOCK_TYPE =
            TYPES.register("crafting_block", ()-> new RecipeType<CraftingBlockRecipe>() {
                @Override
                public String toString() {
                    return "crafting_block";
                }
            });


    public static void register(IEventBus eventBus){
        SERIALIZERS.register(eventBus);
        TYPES.register(eventBus);
    }
}