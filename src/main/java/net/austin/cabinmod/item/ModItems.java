package net.austin.cabinmod.item;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.item.custom.HammerItem;
import net.austin.cabinmod.item.custom.WandItem;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CabinMod.MOD_ID);

    public static final DeferredItem<Item> CABIN = ITEMS.register("cabin",
            ()-> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CABIN_STENCIL = ITEMS.register("cabin_stencil",
            ()-> new Item(new Item.Properties()));

    public static final DeferredItem<Item> RAW_CABIN = ITEMS.register("raw_cabin",
            ()-> new Item(new Item.Properties()));

    public static final DeferredItem<Item> MICHAEL = ITEMS.register("michael",
            ()-> new Item(new Item.Properties()));

    public static final DeferredItem<Item> WAND = ITEMS.register("wand",
            ()-> new WandItem(new Item.Properties().durability(16)));

    public static final DeferredItem<Item> COMPRESSED_CABIN = ITEMS.register("compressed_cabin",
            ()-> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CABIN_CORE = ITEMS.register("cabin_core",
            ()-> new Item(new Item.Properties()));

    public static final DeferredItem<Item> MATTHEW = ITEMS.register("matthew",
            ()-> new Item(new Item.Properties().food(ModFoodProperties.MATTHEW)){
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                    tooltipComponents.add(Component.translatable("tooltip.cabinmod.matthew.tooltip"));
                    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                }
            });

    public static final DeferredItem<Item> MATTWICH = ITEMS.register("mattwich",
            ()-> new Item(new Item.Properties().food(ModFoodProperties.MATTWICH)){
            });


    public static final DeferredItem<SwordItem> EXCALIBUR = ITEMS.register("excalibur",
            ()-> new SwordItem(ModToolTiers.CABIN,new Item.Properties()
                    .attributes(SwordItem.createAttributes(ModToolTiers.CABIN, 10, -2.4f))));

    public static final DeferredItem<PickaxeItem> MATTY_MIKE = ITEMS.register("matty_mike",
            ()-> new PickaxeItem(ModToolTiers.CABIN,new Item.Properties()
                    .attributes(PickaxeItem.createAttributes(ModToolTiers.CABIN, 1.0f, -2.8f))));

    public static final DeferredItem<AxeItem> CABIN_AXE = ITEMS.register("cabin_axe",
            ()-> new AxeItem(ModToolTiers.CABIN,new Item.Properties()
                    .attributes(AxeItem.createAttributes(ModToolTiers.CABIN, 6f, -3.2f))));

    public static final DeferredItem<HammerItem> MJOLNIR = ITEMS.register("mjolnir",
            ()-> new HammerItem(ModToolTiers.CABIN,new Item.Properties()
                    .attributes(PickaxeItem.createAttributes(ModToolTiers.CABIN, 7f, -3.5f))));

    public static final DeferredItem<Item> CABIN_BOW = ITEMS.register("cabin_bow",
            ()-> new BowItem(new Item.Properties()
                    .durability(1000)));


    public static final DeferredItem<Item> CABIN_SMITHING_TEMPLATE = ITEMS.register("cabin_smithing_template",
            ()-> SmithingTemplateItem.createArmorTrimTemplate(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "cabin_path")));


    public static final DeferredItem<ArmorItem> CABIN_HELMET = ITEMS.register("cabin_helmet",
            ()-> new ArmorItem(ModArmorMaterials.CABIN_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                    new Item.Properties()
                            .durability(ArmorItem.Type.HELMET.getDurability(40))));

    public static final DeferredItem<ArmorItem> CABIN_CHESTPLATE = ITEMS.register("cabin_chestplate",
            ()-> new ArmorItem(ModArmorMaterials.CABIN_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties()
                            .durability(ArmorItem.Type.CHESTPLATE.getDurability(40))));

    public static final DeferredItem<ArmorItem> CABIN_LEGGINGS = ITEMS.register("cabin_leggings",
            ()-> new ArmorItem(ModArmorMaterials.CABIN_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                    new Item.Properties()
                            .durability(ArmorItem.Type.LEGGINGS.getDurability(40))));

    public static final DeferredItem<ArmorItem> CABIN_BOOTS = ITEMS.register("cabin_boots",
            ()-> new ArmorItem(ModArmorMaterials.CABIN_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                    new Item.Properties()
                            .durability(ArmorItem.Type.BOOTS.getDurability(40))));


    public static final DeferredItem<Item> MO2_DISC = ITEMS.register("mo2_disc",
            ()-> new Item(new Item.Properties()
                    .jukeboxPlayable(ModSounds.MO2_DISC_KEY)
                    .stacksTo(1)));

    public static final DeferredItem<Item> BRG_DISC = ITEMS.register("brg_disc",
            ()-> new Item(new Item.Properties()
                    .jukeboxPlayable(ModSounds.BRG_DISC_KEY)
                    .stacksTo(1)));

    public static final DeferredItem<Item> HYG_DISC = ITEMS.register("hyg_disc",
            ()-> new Item(new Item.Properties()
                    .jukeboxPlayable(ModSounds.HYG_DISC_KEY)
                    .stacksTo(1)));

    public static final DeferredItem<Item> BLA_DISC = ITEMS.register("bla_disc",
            ()-> new Item(new Item.Properties()
                    .jukeboxPlayable(ModSounds.BLA_DISC_KEY)
                    .stacksTo(1)));

    public static final DeferredItem<Item> DNA_DISC = ITEMS.register("dna_disc",
            ()-> new Item(new Item.Properties()
                    .jukeboxPlayable(ModSounds.DNA_DISC_KEY)
                    .stacksTo(1)));

    public static final DeferredItem<Item> SOS_DISC = ITEMS.register("sos_disc",
            ()-> new Item(new Item.Properties()
                    .jukeboxPlayable(ModSounds.SOS_DISC_KEY)
                    .stacksTo(1)));


    public static final DeferredItem<Item> BLUE_SPAWN_EGG = ITEMS.register("blue_spawn_egg",
            ()-> new DeferredSpawnEggItem(ModEntities.BLUE_GUY, 0x0000ff, 0xff0000,
                    new Item.Properties()));



    public static final DeferredItem<Item> WARDENS_SPARE_KEY = ITEMS.register("wardens_spare_key",
            ()-> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredItem<SwordItem> DEAD_EXCALIBUR = ITEMS.register("dead_excalibur",
            ()-> new SwordItem(Tiers.WOOD, new Item.Properties()
                    .component(DataComponents.UNBREAKABLE, new Unbreakable(false))
                    .attributes(SwordItem.createAttributes(Tiers.WOOD, 3, -2.4f))){
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                    tooltipComponents.add(Component.translatable("tooltip.cabinmod.dead_excalibur.tooltip"));
                    super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                }
            });


    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
