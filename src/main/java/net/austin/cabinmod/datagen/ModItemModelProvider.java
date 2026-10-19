package net.austin.cabinmod.datagen;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.LinkedHashMap;

public class ModItemModelProvider extends ItemModelProvider {

    private static LinkedHashMap<ResourceKey<TrimMaterial>, Float> trimMaterials = new LinkedHashMap<>();
    static {
        trimMaterials.put(TrimMaterials.QUARTZ, 0.1F);
        trimMaterials.put(TrimMaterials.IRON, 0.2F);
        trimMaterials.put(TrimMaterials.NETHERITE, 0.3F);
        trimMaterials.put(TrimMaterials.REDSTONE, 0.4F);
        trimMaterials.put(TrimMaterials.COPPER, 0.5F);
        trimMaterials.put(TrimMaterials.GOLD, 0.6F);
        trimMaterials.put(TrimMaterials.EMERALD, 0.7F);
        trimMaterials.put(TrimMaterials.DIAMOND, 0.8F);
        trimMaterials.put(TrimMaterials.LAPIS, 0.9F);
        trimMaterials.put(TrimMaterials.AMETHYST, 1.0F);
    }


    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CabinMod.MOD_ID, existingFileHelper);
    }


    @Override
    protected void registerModels() {
        basicItem(ModItems.CABIN.get());
        basicItem(ModItems.RAW_CABIN.get());
        basicItem(ModItems.MICHAEL.get());
        handheldItem(ModItems.WAND.get());
        basicItem(ModItems.MATTHEW.get());
        basicItem(ModItems.MATTWICH.get());
        basicItem(ModItems.COMPRESSED_CABIN.get());
        basicItem(ModItems.CABIN_CORE.get());
        // Textures the user hasn't painted yet: declare them so the models still generate.
        // In game they show the missing-texture pattern until the PNG lands at that path.
        expectTexture("item/wardens_spare_key");
        basicItem(ModItems.WARDENS_SPARE_KEY.get());
        withExistingParent(ModItems.JOASH_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        // dead_excalibur uses a hand-written model (src/main/resources/.../models/item/dead_excalibur.json)
        // with Excalibur's oversized display transforms - don't generate one here or the build sees a duplicate.

        basicItem(ModItems.CABIN_SMITHING_TEMPLATE.get());
        basicItem(ModItems.CABIN_STENCIL.get());

        buttonItem(ModBlocks.CABIN_BUTTON, ModBlocks.CABIN_BLOCK);
        fenceItem(ModBlocks.CABIN_FENCE, ModBlocks.CABIN_BLOCK);
        wallItem(ModBlocks.CABIN_WALL, ModBlocks.CABIN_BLOCK);

        basicItem(ModBlocks.CABIN_DOOR.asItem());



        handheldItem(ModItems.MATTY_MIKE.get());
        handheldItem(ModItems.CABIN_AXE.get());
        handheldItem(ModItems.MJOLNIR.get());

        trimmedArmorItem(ModItems.CABIN_HELMET);
        trimmedArmorItem(ModItems.CABIN_CHESTPLATE);
        trimmedArmorItem(ModItems.CABIN_LEGGINGS);
        trimmedArmorItem(ModItems.CABIN_BOOTS);

        basicItem(ModItems.MO2_DISC.get());
        basicItem(ModItems.BRG_DISC.get());
        basicItem(ModItems.HYG_DISC.get());
        basicItem(ModItems.DNA_DISC.get());
        basicItem(ModItems.BLA_DISC.get());
        basicItem(ModItems.SOS_DISC.get());

        withExistingParent(ModItems.BLUE_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
    }

    public void buttonItem(DeferredBlock<?> block, DeferredBlock<Block> baseBlock) {
        this.withExistingParent(block.getId().getPath(), mcLoc("block/button_inventory"))
                .texture("texture",  ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
                        "block/" + baseBlock.getId().getPath()));
    }

    public void fenceItem(DeferredBlock<?> block, DeferredBlock<Block> baseBlock) {
        this.withExistingParent(block.getId().getPath(), mcLoc("block/fence_inventory"))
                .texture("texture",  ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
                        "block/" + baseBlock.getId().getPath()));
    }

    public void wallItem(DeferredBlock<?> block, DeferredBlock<Block> baseBlock) {
        this.withExistingParent(block.getId().getPath(), mcLoc("block/wall_inventory"))
                .texture("wall",  ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,
                        "block/" + baseBlock.getId().getPath()));
    }

    private void trimmedArmorItem(DeferredItem<ArmorItem> itemDeferredItem) {
        final String MOD_ID = CabinMod.MOD_ID; // Change this to your mod id

        if(itemDeferredItem.get() instanceof ArmorItem armorItem) {
            trimMaterials.forEach((trimMaterial, value) -> {
                float trimValue = value;

                String armorType = switch (armorItem.getEquipmentSlot()) {
                    case HEAD -> "helmet";
                    case CHEST -> "chestplate";
                    case LEGS -> "leggings";
                    case FEET -> "boots";
                    default -> "";
                };

                String armorItemPath = armorItem.toString();
                String trimPath = "trims/items/" + armorType + "_trim_" + trimMaterial.location().getPath();
                String currentTrimName = armorItemPath + "_" + trimMaterial.location().getPath() + "_trim";
                ResourceLocation armorItemResLoc = ResourceLocation.parse(armorItemPath);
                ResourceLocation trimResLoc = ResourceLocation.parse(trimPath); // minecraft namespace
                ResourceLocation trimNameResLoc = ResourceLocation.parse(currentTrimName);

                existingFileHelper.trackGenerated(trimResLoc, PackType.CLIENT_RESOURCES, ".png", "textures");

                getBuilder(currentTrimName)
                        .parent(new ModelFile.UncheckedModelFile("item/generated"))
                        .texture("layer0", armorItemResLoc.getNamespace() + ":item/" + armorItemResLoc.getPath())
                        .texture("layer1", trimResLoc);

                this.withExistingParent(itemDeferredItem.getId().getPath(),
                                mcLoc("item/generated"))
                        .override()
                        .model(new ModelFile.UncheckedModelFile(trimNameResLoc.getNamespace()  + ":item/" + trimNameResLoc.getPath()))
                        .predicate(mcLoc("trim_type"), trimValue).end()
                        .texture("layer0",
                                ResourceLocation.fromNamespaceAndPath(MOD_ID,
                                        "item/" + itemDeferredItem.getId().getPath()));
            });
        }
    }

    private ItemModelBuilder handheldItem(DeferredItem<?> item){
        return withExistingParent(item.getId().getPath(),
                ResourceLocation.parse("item/handheld")).texture("layer0",
                ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "item/" + item.getId().getPath()));
    }


    private void expectTexture(String path) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, path);
        if (!existingFileHelper.exists(texture, PackType.CLIENT_RESOURCES, ".png", "textures")) {
            existingFileHelper.trackGenerated(texture, PackType.CLIENT_RESOURCES, ".png", "textures");
        }
    }
}
