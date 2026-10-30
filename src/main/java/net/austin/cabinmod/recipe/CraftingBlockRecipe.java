package net.austin.cabinmod.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

// The Transient Diviner's recipe: one input becomes `result`. The optional "star" object picks the colours of the
// shooting star the result rides back down on ({"core": "#RRGGBB", "trail": "#RRGGBB"}); without it the client
// takes them from the items' own textures (client/diviner/StarColors).
public record CraftingBlockRecipe(Ingredient inputItem, ItemStack output, Optional<StarColor> star) implements Recipe<CraftingBlockRecipeInput> {

    public CraftingBlockRecipe(Ingredient inputItem, ItemStack output) {
        this(inputItem, output, Optional.empty());
    }

    /** 0xRRGGBB colours of a result's shooting star. */
    public record StarColor(int core, int trail) {
        private static final Codec<Integer> HEX = Codec.STRING.comapFlatMap(
                text -> {
                    try {
                        return com.mojang.serialization.DataResult.success(Integer.parseInt(text.replace("#", ""), 16) & 0xFFFFFF);
                    } catch (NumberFormatException e) {
                        return com.mojang.serialization.DataResult.error(() -> "Not a #RRGGBB colour: " + text);
                    }
                },
                rgb -> String.format("#%06X", rgb));
        public static final Codec<StarColor> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                HEX.fieldOf("core").forGetter(StarColor::core),
                HEX.fieldOf("trail").forGetter(StarColor::trail)
        ).apply(inst, StarColor::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, StarColor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, StarColor::core, ByteBufCodecs.INT, StarColor::trail, StarColor::new);
    }

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
                ItemStack.CODEC.fieldOf("result").forGetter(CraftingBlockRecipe::output),
                StarColor.CODEC.optionalFieldOf("star").forGetter(CraftingBlockRecipe::star)
        ).apply(inst,CraftingBlockRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CraftingBlockRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CraftingBlockRecipe::inputItem,
                        ItemStack.STREAM_CODEC, CraftingBlockRecipe::output,
                        ByteBufCodecs.optional(StarColor.STREAM_CODEC), CraftingBlockRecipe::star,
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
