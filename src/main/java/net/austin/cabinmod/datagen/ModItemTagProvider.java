package net.austin.cabinmod.datagen;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, CabinMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.Items.TRANSFORMABLE_ITEMS)
                .add(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE)
                .add(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);

        tag(ItemTags.SWORDS)
                .add(ModItems.EXCALIBUR.get());
        tag(ItemTags.PICKAXES)
                .add(ModItems.MATTY_MIKE.get());
        tag(ItemTags.AXES)
                .add(ModItems.CABIN_AXE.get());
        tag(ItemTags.MACE_ENCHANTABLE)
                .add(ModItems.MJOLNIR.get());

        this.tag(ItemTags.TRIM_MATERIALS)
                .add(ModItems.CABIN_STENCIL.get());

        this.tag(ItemTags.TRIM_TEMPLATES)
                .add(ModItems.CABIN_SMITHING_TEMPLATE.get());

        this.tag(ItemTags.TRIMMABLE_ARMOR)
                .add(ModItems.CABIN_HELMET.get())
                .add(ModItems.CABIN_CHESTPLATE.get())
                .add(ModItems.CABIN_LEGGINGS.get())
                .add(ModItems.CABIN_BOOTS.get());

        tag(ItemTags.HEAD_ARMOR_ENCHANTABLE)
                .add(ModItems.CABIN_HELMET.get());
        tag(ItemTags.CHEST_ARMOR_ENCHANTABLE)
                .add(ModItems.CABIN_CHESTPLATE.get());
        tag(ItemTags.LEG_ARMOR_ENCHANTABLE)
                .add(ModItems.CABIN_LEGGINGS.get());
        tag(ItemTags.FOOT_ARMOR_ENCHANTABLE)
                .add(ModItems.CABIN_BOOTS.get());
        tag(ItemTags.BOW_ENCHANTABLE)
                .add(ModItems.CABIN_BOW.get());
        tag(ItemTags.CROSSBOW_ENCHANTABLE)
                .add(ModItems.CABIN_BOW.get());

    }

}
