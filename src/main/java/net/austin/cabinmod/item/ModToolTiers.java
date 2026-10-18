package net.austin.cabinmod.item;

import net.austin.cabinmod.util.ModTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class ModToolTiers {
    public static final Tier CABIN = new SimpleTier(ModTags.Blocks.INCORRECT_FOR_CABIN_TOOL,
            5000, 10f, 5f, 30, ()-> Ingredient.of(ModItems.CABIN));

}
