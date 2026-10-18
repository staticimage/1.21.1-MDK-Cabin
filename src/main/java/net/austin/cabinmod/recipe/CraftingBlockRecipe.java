package net.austin.cabinmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record CraftingBlockRecipe(Ingredient inputItem, ItemStack output) implements Recipe<CraftingBlockRecipeInput> {



    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(inputItem);
        return list;
    }

    @Override
    public boolean matches(CraftingBlockRecipeInput input, Level level) {
        if (level.isClientSide()){
            return false;
        }

        return inputItem.test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(CraftingBlockRecipeInput input, HolderLookup.Provider registries) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return output;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CRAFTING_BLOCK_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CRAFTING_BLOCK_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<CraftingBlockRecipe>{
        public static final MapCodec<CraftingBlockRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CraftingBlockRecipe::inputItem),
                ItemStack.CODEC.fieldOf("result").forGetter(CraftingBlockRecipe::output)
        ).apply(inst,CraftingBlockRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CraftingBlockRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CraftingBlockRecipe::inputItem,
                        ItemStack.STREAM_CODEC, CraftingBlockRecipe::output,
                        CraftingBlockRecipe::new);

        @Override
        public MapCodec<CraftingBlockRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CraftingBlockRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
