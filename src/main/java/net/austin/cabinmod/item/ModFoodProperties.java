package net.austin.cabinmod.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ModFoodProperties {
    public static final FoodProperties MATTHEW = new FoodProperties.Builder()
            .nutrition(1)
            .saturationModifier(1.5f)
            .usingConvertsTo(Items.BONE)
            .alwaysEdible()
            .effect(()-> new MobEffectInstance(MobEffects.DIG_SPEED, 400, 200),0.25f)
            .effect(()-> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 200),0.25f)
            .effect(()-> new MobEffectInstance(MobEffects.LUCK, 400, 200),0.25f)
            .effect(()-> new MobEffectInstance(MobEffects.HARM, 1, 200),0.25f)
            .build();

    public static final FoodProperties MATTWICH = new FoodProperties.Builder()
            .nutrition(20)
            .saturationModifier(1.2f)
            .effect(()-> new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 400, 200),0.5f)
            .build();
}
