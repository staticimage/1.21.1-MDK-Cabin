package net.austin.cabinmod.effect;

import net.austin.cabinmod.CabinMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, CabinMod.MOD_ID);

    // Jehoash's debuff: a much stronger mining fatigue. The jumpscare is client side (LifeOfThePartyJumpscare).
    public static final Holder<MobEffect> LIFE_OF_THE_PARTY = MOB_EFFECTS.register("life_of_the_party",
            ()-> new LifeOfThePartyEffect(MobEffectCategory.HARMFUL, 0xFF3FD2)
                    .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED,
                            ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "effect.life_of_the_party.mining"),
                            -0.99, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED,
                            ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "effect.life_of_the_party.attack"),
                            -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    // Elder guardian curse length.
    public static final int LIFE_OF_THE_PARTY_DURATION = 6000;

    public static void register(IEventBus eventBus){
        MOB_EFFECTS.register(eventBus);
    }
}
