package net.austin.cabinmod.datagen;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        List<ItemLike> CABIN_SMELTABLES = List.of(ModItems.RAW_CABIN,
                ModBlocks.CABIN_ORE, ModBlocks.CABIN_ORE_DEEPSLATE);


        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.CABIN_BLOCK.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', ModItems.CABIN.get())
                .unlockedBy("has_cabin", has(ModItems.CABIN.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,ModItems.CABIN.get(),9)
                .requires(ModBlocks.CABIN_BLOCK.get())
                .unlockedBy("has_cabin_block",has(ModBlocks.CABIN_BLOCK.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.RAW_CABIN_BLOCK.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', ModItems.RAW_CABIN.get())
                .unlockedBy("has_raw_cabin", has(ModItems.RAW_CABIN.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,ModItems.RAW_CABIN.get(),9)
                .requires(ModBlocks.RAW_CABIN_BLOCK.get())
                .unlockedBy("has_raw_cabin_block",has(ModBlocks.RAW_CABIN_BLOCK))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MATTY_MIKE.get())
                .pattern("A")
                .pattern("B")
                .define('A', ModItems.MICHAEL.get())
                .define('B', ModItems.MATTHEW.get())
                .unlockedBy("has_matthew", has(ModItems.MATTHEW.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CABIN_BOW.get())
                .pattern(" AB")
                .pattern("A B")
                .pattern(" AB")
                .define('A', Items.BREEZE_ROD)
                .define('B', Items.STRING)
                .unlockedBy("has_raw_cabin_block", has(ModBlocks.RAW_CABIN_BLOCK.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CABIN_HELMET.get())
                .pattern("AAA")
                .pattern("ABA")
                .define('A', ModItems.COMPRESSED_CABIN.get())
                .define('B', ModItems.CABIN_CORE.get())
                .unlockedBy("has_cabin_core", has(ModItems.CABIN_CORE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CABIN_CHESTPLATE.get())
                .pattern("ABA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', ModItems.COMPRESSED_CABIN.get())
                .define('B', ModItems.CABIN_CORE.get())
                .unlockedBy("has_cabin_core", has(ModItems.CABIN_CORE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CABIN_LEGGINGS.get())
                .pattern("AAA")
                .pattern("ABA")
                .pattern("A A")
                .define('A', ModItems.COMPRESSED_CABIN.get())
                .define('B', ModItems.CABIN_CORE.get())
                .unlockedBy("has_cabin_core", has(ModItems.CABIN_CORE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CABIN_BOOTS.get())
                .pattern("ABA")
                .pattern("A A")
                .define('A', ModItems.COMPRESSED_CABIN.get())
                .define('B', ModItems.CABIN_CORE.get())
                .unlockedBy("has_cabin_core", has(ModItems.CABIN_CORE.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MATTWICH.get())
                .pattern("CCC")
                .pattern("BAB")
                .pattern("CCC")
                .define('A', Items.TOTEM_OF_UNDYING)
                .define('B', ModItems.MATTHEW.get())
                .define('C', Items.BREAD)
                .unlockedBy("has_matthew", has(ModItems.MATTHEW.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.CRAFTING_BLOCK.get())
                .pattern("DCD")
                .pattern("CAC")
                .pattern("DBD")
                .define('A', Blocks.FURNACE)
                .define('B', ModItems.CABIN_CORE.get())
                .define('C', Items.DIAMOND)
                .define('D', ModBlocks.CABIN_BLOCK.get())
                .unlockedBy("has_cabin_core", has(ModItems.CABIN_CORE.get()))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC,ModItems.EXCALIBUR.get(),1)
                .requires(ModBlocks.CHAIR.get())
                .requires(ModItems.CABIN_CORE.get())
                .requires(ModItems.DNA_DISC.get())
                .requires(ModItems.COMPRESSED_CABIN.get())
                .requires(ModItems.WAND.get())
                .requires(ModItems.MJOLNIR.get())
                .requires(ModItems.CABIN_AXE.get())
                .requires(ModItems.CABIN_BOW.get())
                .requires(ModItems.MATTY_MIKE.get())
                .unlockedBy("has_compressed_cabin",has(ModItems.COMPRESSED_CABIN.get()))
                .save(recipeOutput);

        oreSmelting(recipeOutput, CABIN_SMELTABLES, RecipeCategory.MISC, ModItems.CABIN.get(), 1, 200, "cabin");
        oreBlasting(recipeOutput, CABIN_SMELTABLES, RecipeCategory.MISC, ModItems.CABIN.get(), 1, 100, "cabin");

        stairBuilder(ModBlocks.CABIN_STAIRS.get(), Ingredient.of(ModItems.CABIN))
                .group("cabin")
                .unlockedBy("has_cabin", has(ModItems.CABIN.get()))
                .save(recipeOutput);

        slab(recipeOutput,RecipeCategory.BUILDING_BLOCKS, ModBlocks.CABIN_SLAB.get(), ModItems.CABIN.get());

        buttonBuilder(ModBlocks.CABIN_BUTTON.get(), Ingredient.of(ModItems.CABIN.get()))
                .group("cabin")
                .unlockedBy("has_cabin", has(ModItems.CABIN.get()))
                .save(recipeOutput);

        pressurePlate(recipeOutput, ModBlocks.CABIN_PRESSURE_PLATE.get(), ModItems.CABIN.get());

        fenceBuilder(ModBlocks.CABIN_FENCE.get(), Ingredient.of(ModItems.CABIN.get()))
                .group("cabin")
                .unlockedBy("has_cabin", has(ModItems.CABIN.get()))
                .save(recipeOutput);

        fenceGateBuilder(ModBlocks.CABIN_FENCE_GATE.get(), Ingredient.of(ModItems.CABIN.get()))
                .group("cabin")
                .unlockedBy("has_cabin", has(ModItems.CABIN.get()))
                .save(recipeOutput);

        wall(recipeOutput, RecipeCategory.BUILDING_BLOCKS, ModBlocks.CABIN_WALL.get(), ModItems.CABIN.get());

        doorBuilder(ModBlocks.CABIN_DOOR.get(), Ingredient.of(ModItems.CABIN.get()))
                .group("cabin")
                .unlockedBy("has_cabin", has(ModItems.CABIN.get()))
                .save(recipeOutput);

        trapdoorBuilder(ModBlocks.CABIN_TRAPDOOR.get(), Ingredient.of(ModItems.CABIN.get()))
                .group("cabin")
                .unlockedBy("has_cabin", has(ModItems.CABIN.get()))
                .save(recipeOutput);

        trimSmithing(recipeOutput, ModItems.CABIN_SMITHING_TEMPLATE.get(), ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "cabin_path"));




    }
}
