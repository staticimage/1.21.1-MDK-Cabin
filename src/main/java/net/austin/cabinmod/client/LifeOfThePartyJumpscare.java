package net.austin.cabinmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.effect.ModEffects;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

// Full-screen jumpscare the moment Life of the Party lands (like the elder guardian curse).
// Fires on a fresh application or a re-application that raises the remaining duration.
public class LifeOfThePartyJumpscare {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "life_of_the_party_jumpscare");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/gui/life_of_the_party_jumpscare.png");
    private static final int LENGTH = 40;

    private static int lastDuration = 0;
    private static int ticksLeft = 0;

    @EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (ticksLeft > 0) ticksLeft--;
            if (mc.player == null || mc.isPaused()) return;

            MobEffectInstance effect = mc.player.getEffect(ModEffects.LIFE_OF_THE_PARTY);
            int duration = effect == null ? 0 : effect.getDuration();
            if (duration > lastDuration + 1) {
                ticksLeft = LENGTH;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LIFE_OF_THE_PARTY_JUMPSCARE.get(), 1.0f));
            }
            lastDuration = duration;
        }
    }

    @EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(LAYER_ID, (guiGraphics, deltaTracker) -> {
                if (ticksLeft <= 0) return;
                float t = (ticksLeft - deltaTracker.getGameTimeDeltaPartialTick(false)) / LENGTH;
                float alpha = t > 0.75f ? (1f - t) * 4f : t / 0.75f;   // fast in, slow fade out
                int w = guiGraphics.guiWidth(), h = guiGraphics.guiHeight();
                RenderSystem.enableBlend();
                guiGraphics.setColor(1f, 1f, 1f, alpha);
                guiGraphics.blit(TEXTURE, 0, 0, w, h, 0f, 0f, 256, 256, 256, 256);
                guiGraphics.setColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            });
        }
    }
}
