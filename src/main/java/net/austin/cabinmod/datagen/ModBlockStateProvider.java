package net.austin.cabinmod.datagen;

import net.minecraft.server.packs.PackType;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.LightBlock;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.util.ModTags;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, CabinMod.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {

        blockWithItem(ModBlocks.CABIN_BLOCK);
        blockWithItem(ModBlocks.RAW_CABIN_BLOCK);
        blockWithItem(ModBlocks.CABIN_ORE);
        blockWithItem(ModBlocks.CABIN_ORE_DEEPSLATE);
        blockWithItem(ModBlocks.MIKE_BLOCK);
        // dj_booth: hand-made two-part model + blockstate (tools/booth/build_dj_booth.py), not generated here.



        stairsBlock(ModBlocks.CABIN_STAIRS.get(), blockTexture(ModBlocks.CABIN_BLOCK.get()));
        slabBlock(ModBlocks.CABIN_SLAB.get(), blockTexture(ModBlocks.CABIN_BLOCK.get()), blockTexture(ModBlocks.CABIN_BLOCK.get()));
        buttonBlock(ModBlocks.CABIN_BUTTON.get(), blockTexture(ModBlocks.CABIN_BLOCK.get()));
        pressurePlateBlock(ModBlocks.CABIN_PRESSURE_PLATE.get(), blockTexture(ModBlocks.CABIN_BLOCK.get()));
        fenceBlock(ModBlocks.CABIN_FENCE.get(), blockTexture(ModBlocks.CABIN_BLOCK.get()));
        fenceGateBlock(ModBlocks.CABIN_FENCE_GATE.get(), blockTexture(ModBlocks.CABIN_BLOCK.get()));
        wallBlock(ModBlocks.CABIN_WALL.get(), blockTexture(ModBlocks.CABIN_BLOCK.get()));

        doorBlockWithRenderType(ModBlocks.CABIN_DOOR.get(),modLoc("block/cabin_door_bottom"),modLoc("block/cabin_door_top"),"cutout");
        trapdoorBlockWithRenderType(ModBlocks.CABIN_TRAPDOOR.get(),modLoc("block/cabin_trapdoor"),true,"cutout");

        blockItem(ModBlocks.CABIN_STAIRS);
        blockItem(ModBlocks.CABIN_SLAB);
        blockItem(ModBlocks.CABIN_PRESSURE_PLATE);
        blockItem(ModBlocks.CABIN_FENCE_GATE);
        blockItem(ModBlocks.CABIN_TRAPDOOR, "_bottom");

        customLight();
    }

    private void customLight() {
        getVariantBuilder(ModBlocks.LIGHT_BLOCK.get()).forAllStates(state -> {
            if(state.getValue(LightBlock.CLICKED)) {
                return new ConfiguredModel[]{new ConfiguredModel(models().cubeAll("light_block_on",
                        ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "block/" + "light_block_on")))};
            } else {
                return new ConfiguredModel[]{new ConfiguredModel(models().cubeAll("light_block_off",
                        ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "block/" + "light_block_off")))};
            }
        });

        simpleBlockItem(ModBlocks.LIGHT_BLOCK.get(), models().cubeAll("light_block_off",
                ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "block/" + "light_block_off")));
    }

    private void blockWithItem(DeferredBlock<?> deferredBlock){
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }

    private void blockItem(DeferredBlock<?> deferredBlock) {
        simpleBlockItem(deferredBlock.get(), new ModelFile.UncheckedModelFile("cabinmod:block/" + deferredBlock.getId().getPath()));
    }

    private void blockItem(DeferredBlock<?> deferredBlock, String appendix) {
        simpleBlockItem(deferredBlock.get(), new ModelFile.UncheckedModelFile("cabinmod:block/" + deferredBlock.getId().getPath() + appendix));
    }

    // Texture the user hasn't painted yet: declare it so the model still generates.
    private void expectTexture(String path) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, path);
        if (!models().existingFileHelper.exists(texture, PackType.CLIENT_RESOURCES, ".png", "textures")) {
            models().existingFileHelper.trackGenerated(texture, PackType.CLIENT_RESOURCES, ".png", "textures");
        }
    }
}
