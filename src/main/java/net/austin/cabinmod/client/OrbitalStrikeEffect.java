package net.austin.cabinmod.client;

import com.mojang.logging.LogUtils;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

// Full-screen orbital strike (shader ported from Orbital Railgun, MIT - see shaders/license_orbital_railgun.txt).
// Storm clouds roll in, the sky tears open, planets fall, then the blast and a shockwave roll out
// from `target`. Purely visual. Used for Jehoash's death ("the party ends").
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class OrbitalStrikeEffect {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "shaders/post/joash_strike.json");
    private static final float BUILDUP_SECONDS = 8f;     // shader: IsBlockHit doubles as the build-up length
    private static final float TAIL_SECONDS = 7f;        // blast + shockwave + embers fade
    private static final double MAX_DISTANCE = 160;

    private static PostChain chain;
    private static boolean broken;
    private static int width, height;
    private static Vec3 target;
    private static long startNanos;
    private static long pausedAt = -1;

    private OrbitalStrikeEffect() {
    }

    public static void start(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (broken || mc.player == null || mc.player.position().distanceTo(at) > MAX_DISTANCE) return;
        target = at;
        startNanos = System.nanoTime();
        pausedAt = -1;
        // the strike's own soundtrack (joash/strike), timed to the shader: planets hit at 3.264 + 0.384k s, blast at 8 s
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.JOASH_STRIKE.get().getLocation(), SoundSource.HOSTILE,
                1.0f, 1.0f, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    public static boolean isActive() {
        return target != null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        // the soundtrack pauses with the game: hold the shader clock too, so planets still land on their hits
        long now = System.nanoTime();
        if (mc.isPaused()) {
            if (pausedAt < 0) pausedAt = now;
            now = pausedAt;
        } else if (pausedAt >= 0) {
            startNanos += now - pausedAt;
            pausedAt = -1;
        }
        float t = (now - startNanos) / 1e9f;
        if (t > BUILDUP_SECONDS + TAIL_SECONDS) {
            target = null;
            return;
        }
        PostChain post = chain(mc);
        if (post == null) {
            target = null;
            return;
        }

        Matrix4f inverse = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
        Vec3 cam = event.getCamera().getPosition();
        for (PostPass pass : passes(post)) {
            EffectInstance fx = pass.getEffect();
            fx.safeGetUniform("InverseTransformMatrix").set(inverse);
            fx.safeGetUniform("iTime").set(t);
            fx.safeGetUniform("IsBlockHit").set(BUILDUP_SECONDS);
            fx.safeGetUniform("DestructionRange").set(24f);
            fx.safeGetUniform("CameraPosition").set((float) cam.x, (float) cam.y, (float) cam.z);
            fx.safeGetUniform("BlockPosition").set((float) target.x, (float) target.y, (float) target.z);
            fx.safeGetUniform("ShockwaveSpeed").set(48f);
            fx.safeGetUniform("ShockwaveDuration").set(3f);
            fx.safeGetUniform("ScreenShake").set(1f);
        }
        post.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        mc.getMainRenderTarget().bindWrite(false);
    }

    private static PostChain chain(Minecraft mc) {
        if (broken) return null;
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
                width = -1;
            }
            if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            return chain;
        } catch (Exception e) {
            LOGGER.error("Orbital strike effect failed to load; disabling it", e);
            broken = true;
            chain = null;
            return null;
        }
    }

    private static Field passesField;

    @SuppressWarnings("unchecked")
    private static List<PostPass> passes(PostChain post) {
        try {
            if (passesField == null) {
                passesField = PostChain.class.getDeclaredField("passes");
                passesField.setAccessible(true);
            }
            return (List<PostPass>) passesField.get(post);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Can't reach PostChain passes; orbital strike disabled", e);
            broken = true;
            return List.of();
        }
    }
}
