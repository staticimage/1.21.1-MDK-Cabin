package net.austin.cabinmod.client.diviner;

import com.mojang.logging.LogUtils;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

/**
 * The Transient Diviner's "Cosmic Ascent / Starfall" cinematic, client side.
 *
 * INTRO   opening the diviner: the camera pulls up to a top-down view, climbs faster and faster, the world
 *         gives way to space (the Blender planet, SpaceScene), then the view tilts up into the stars and the
 *         crafting panel forms as glass (CraftingBlockScreen).
 * SPACE   idles among the stars while the menu is open; the Cabin talks with you (CabinDialogue) and the
 *         ambience swells while you work the panel (setSpaceIntensity).
 * RETURN  closing with a launched result: the camera follows the shooting star back down through the
 *         atmosphere to the diviner. Exactly CraftingBlockEntity.RETURN_TICKS long, so it lands with the server.
 * IMPACT  ink impact frames, flash, shockwave and shake, then the camera eases back to the player's eyes.
 * QUICK_RETURN / EASE_BACK  closing without a star: a faster fall, or a short ease if still near the ground.
 *
 * Over every phase's exact path sits the camera's feel (feel()): it rides a spring, so it lags and catches up,
 * banks into turns, sways like a hand-held camera, and shakes - buffeting that grows with speed and re-entry heat,
 * and jolts as it fights through the air: the liftoff, punching out of the atmosphere, the comet passing, the cut
 * back to the world, breaking through the clouds. On the way up the climb stalls against the air, then bursts.
 *
 * Time is counted in client ticks (paused with the game) plus the frame's partial tick. All the space
 * look comes from shaders/post/diviner_cosmos.json and the core diviner_* shaders.
 */
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DivinerCinematic {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "shaders/post/diviner_cosmos.json");
    // the diviner's own GUI art (Cabin in Agartha) becomes the divine image behind the glass panel
    private static final ResourceLocation GUI_ART = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "textures/gui/crafting_block/crafting_block_gui.png");
    static final float[] ART_RECT = {6f / 960f, 54f / 960f, 189f / 960f, 253f / 960f};

    enum Phase { NONE, INTRO, SPACE, RETURN, QUICK_RETURN, EASE_BACK, IMPACT }

    static final int INTRO_TICKS = 110;
    static final int RETURN_TICKS = CraftingBlockEntity.RETURN_TICKS;
    static final int QUICK_TICKS = 50;
    static final int EASE_TICKS = 16;
    static final int IMPACT_TICKS = 46;
    /** Panel slots accept clicks from this intro tick on. */
    static final int INTERACTIVE_TICK = 106;
    static final float WORLD_CEILING = 170f;       // highest the real camera climbs (blocks above the eyes)
    static final float BLOCKS_PER_RADIUS = 60000f; // world altitude -> planet radii
    static final float SPACE_ALT = 0.6f;           // idle altitude in planet radii: the limb glows at the bottom of the view
    static final float SPACE_PITCH = 22f;
    static final float INTRO_SPIN = 30f;

    private static Phase phase = Phase.NONE;
    private static int phaseTick;
    private static int clock;
    private static float partial;
    private static BlockPos pos;
    private static CameraType previousCamera;
    private static boolean wantCinematicScreen;
    private static float introYaw, introPitch;
    // pose captured when a phase starts
    private static float sYaw, sPitch, sAlt, sTakeover;
    private static Vec3 sWorld = Vec3.ZERO;

    // ---- frame state (computed once per frame in onFrame)
    static Vec3 camPos = Vec3.ZERO;
    static float camYaw, camPitch, camRoll, camFov = 70f;
    static float alt, takeover, haze, warp, reveal, cometHeat;
    static float impact = -1f, impactFrame, flash, chroma, crack, rays, apparition;
    static float shakeX, shakeY;
    static boolean cometSpaceVisible;
    static final Vector3f cometSpace = new Vector3f();
    static Vec3 cometWorld;

    // the camera's feel: springs over the exact pose (see feel())
    private static final Spring yawSpring = new Spring(), pitchSpring = new Spring(), rollSpring = new Spring(), fovSpring = new Spring();
    private static float lastFeelClock = Float.NaN, lastYaw, returnR;

    private static PostChain chain;
    private static boolean broken;
    private static int chainW, chainH;
    private static SpaceLoop loop;
    private static SwellLoop swell;
    private static float spaceIntensity;

    private DivinerCinematic() {
    }

    // ------------------------------------------------------------------ API (CraftingBlockScreen)

    /** The diviner's menu opened (also called again on every screen resize; only the first call starts the intro). */
    public static void begin(BlockPos at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || broken) return;
        if ((phase == Phase.INTRO || phase == Phase.SPACE) && at.equals(pos)) return;
        pos = at.immutable();
        if (phase == Phase.NONE) {
            previousCamera = mc.options.getCameraType();
            introYaw = mc.player.getViewYRot(1f);
            introPitch = mc.player.getViewXRot(1f);
        } else {
            introYaw = camYaw;
            introPitch = camPitch;
        }
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        setPhase(Phase.INTRO);
        clock = 0;
        lastFeelClock = Float.NaN;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DIVINER_ASCEND.get(), 1f, 0.85f));
    }

    /** The diviner's menu closed. `starfall`: a result was launched and is falling back down. */
    public static void screenClosed(boolean starfall) {
        if (phase != Phase.INTRO && phase != Phase.SPACE) return;
        Minecraft mc = Minecraft.getInstance();
        stopLoop();
        CabinDialogue.stop();
        capture();
        float t = time();
        if (starfall) {
            setPhase(Phase.RETURN);
            DivinerFx.startLocalStarfall(pos, RETURN_TICKS);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DIVINER_DESCEND.get(), 1f, 0.85f));
        } else if (phase == Phase.INTRO && t < 56) {
            setPhase(Phase.EASE_BACK);
        } else {
            setPhase(Phase.QUICK_RETURN);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DIVINER_DESCEND.get(), 1.3f, 0.5f));
        }
        wantCinematicScreen = true;
    }

    /** Esc during the return: end now and give the camera back. */
    public static void skip() {
        if (phase == Phase.NONE) return;
        finish();
    }

    /** 0..1 how much is going on at the panel (empty, an item laid in, crafting): the heavens swell with it. */
    public static void setSpaceIntensity(float intensity) {
        spaceIntensity = Mth.clamp(intensity, 0f, 1f);
    }

    /** The diviner this client is up in the heavens through, or null. */
    public static BlockPos site() {
        return phase == Phase.NONE ? null : pos;
    }

    public static boolean isActive() {
        return phase != Phase.NONE;
    }

    static boolean isReturning() {
        return phase == Phase.RETURN || phase == Phase.QUICK_RETURN || phase == Phase.EASE_BACK || phase == Phase.IMPACT;
    }

    /** 0..1 how much of the glass panel has formed. */
    public static float panelReveal() {
        if (phase == Phase.SPACE) return 1f;
        if (phase == Phase.INTRO) return reveal;
        return phase == Phase.NONE ? 1f : 0f;
    }

    public static boolean panelInteractive() {
        return phase == Phase.SPACE || phase == Phase.NONE || (phase == Phase.INTRO && time() >= INTERACTIVE_TICK);
    }

    /** Colour texture of this frame's finished cosmos, for the glass panel to refract; -1 if unavailable. */
    public static int cosmosTexture() {
        if (chain == null || phase == Phase.NONE) return -1;
        var target = chain.getTempTarget("final");
        return target == null ? -1 : target.getColorTextureId();
    }

    static float clockTime() {
        return clock + partial;
    }

    static void onResourcesReloaded() {
        if (chain != null) chain.close();
        chain = null;
        broken = false;
        SpaceScene.reset();
    }

    // ------------------------------------------------------------------ phases

    private static void setPhase(Phase next) {
        phase = next;
        phaseTick = 0;
        partial = 0;
    }

    private static float time() {
        return phaseTick + partial;
    }

    private static void capture() {
        sYaw = camYaw;
        sPitch = camPitch;
        sAlt = alt;
        sTakeover = takeover;
        sWorld = camPos;
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        stopLoop();
        CabinDialogue.stop();
        clouds(mc, true);
        cull(mc, true);
        if (previousCamera != null) mc.options.setCameraType(previousCamera);
        previousCamera = null;
        phase = Phase.NONE;
        impact = -1;
        wantCinematicScreen = false;
        if (mc.screen instanceof DivinerCinematicScreen) mc.setScreen(null);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            finish();
            return;
        }
        if (mc.isPaused()) return;
        clock++;
        phaseTick++;
        CabinDialogue.tick();
        if (wantCinematicScreen && mc.screen == null) {
            mc.setScreen(new DivinerCinematicScreen());
            wantCinematicScreen = false;
        }
        switch (phase) {
            case INTRO -> {
                if (phaseTick >= INTRO_TICKS) {
                    setPhase(Phase.SPACE);
                    startLoop();
                    CabinDialogue.start(pos);
                }
            }
            case RETURN -> {
                float rem = DivinerFx.remaining(pos, 0f);
                if ((Float.isNaN(rem) ? phaseTick >= RETURN_TICKS : rem <= 0) || phaseTick >= RETURN_TICKS + 20) {
                    capture();
                    setPhase(Phase.IMPACT);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DIVINER_IMPACT_STINGER.get(), 1f, 0.85f));
                }
            }
            case IMPACT -> {
                if (phaseTick >= IMPACT_TICKS) finish();
            }
            case QUICK_RETURN -> {
                if (phaseTick >= QUICK_TICKS) finish();
            }
            case EASE_BACK -> {
                if (phaseTick >= EASE_TICKS) finish();
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ per-frame pose

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (phase == Phase.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        partial = mc.isPaused() ? partial : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        computeFrame(mc, time());
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static float smooth(float a, float b, float x) {
        return ease((x - a) / (b - a));
    }

    private static float bell(float t, float a, float b) {
        if (t <= a || t >= b) return 0f;
        float s = Mth.sin((t - a) / (b - a) * Mth.PI);
        return s * s;
    }

    private static void computeFrame(Minecraft mc, float t) {
        float baseFov = mc.options.fov().get();
        Vec3 eye = mc.player.getEyePosition(partial);
        float playerYaw = mc.player.getViewYRot(partial), playerPitch = mc.player.getViewXRot(partial);
        Vec3 dir = DivinerFx.fallDirection(pos);
        Vec3 top = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        float spaceYaw = introYaw + INTRO_SPIN;

        alt = 0;
        takeover = haze = warp = reveal = cometHeat = 0;
        impact = -1;
        impactFrame = flash = chroma = crack = rays = apparition = 0;
        shakeX = shakeY = 0;
        camRoll = 0;
        cometSpaceVisible = false;
        cometWorld = null;
        camFov = baseFov;

        switch (phase) {
            case INTRO -> {
                float h;
                Vec3 behind = Vec3.ZERO;
                if (t < 12) {
                    // start from just behind the player's head (not inside it) and swing up overhead
                    float s = ease(t / 12f);
                    h = 0.6f + 3.4f * s;
                    float yr = introYaw * Mth.DEG_TO_RAD;
                    behind = new Vec3(Mth.sin(yr), 0, -Mth.cos(yr)).scale(2.5 * (1 - s));
                    camPitch = Mth.lerp(s, introPitch, 90f);
                } else {
                    // the climb strains against the air (falls behind), then breaks through and catches up
                    float te = t - 3.2f * bell(t, 32f, 50f);
                    h = Math.min(4f * (float) Math.exp(0.0852f * (te - 12f)), WORLD_CEILING);
                    camPitch = t < 78 ? 90f : Mth.lerp(ease((t - 78f) / 22f), 90f, SPACE_PITCH);
                }
                camYaw = introYaw + INTRO_SPIN * ease((t - 8f) / 80f);
                camPos = eye.add(0, h, 0).add(behind);
                alt = t <= 56 ? h / BLOCKS_PER_RADIUS
                        : Math.min(SPACE_ALT, (WORLD_CEILING / BLOCKS_PER_RADIUS) * (float) Math.exp(0.167f * (t - 56f)));
                takeover = smooth(40, 62, t);
                haze = smooth(14, 50, t) * (1 - takeover);
                warp = bell(t, 22, 84);
                reveal = Mth.clamp((t - 96f) / 14f, 0f, 1f);
                apparition = smooth(86, 108, t);
                camFov = baseFov + 22f * warp + 9f * jolt(t, 44f, 1f, 5f) - 4f * bell(t, 32f, 44f);
                // drifting a little as it climbs, not on a rail
                camPos = camPos.add(Mth.sin(t * 0.07f) * h * 0.03f, 0, Mth.cos(t * 0.053f) * h * 0.03f);
            }
            case SPACE -> {
                camYaw = spaceYaw + 4f * Mth.sin(t * 0.008f);
                camPitch = SPACE_PITCH + 1.5f * Mth.sin(t * 0.011f);
                camPos = eye.add(0, WORLD_CEILING, 0);
                alt = SPACE_ALT;
                takeover = 1;
                reveal = 1;
                apparition = 1;
                camFov = baseFov + 2f;
            }
            case RETURN -> {
                // run on the comet's own clock (world game time) so the camera can't drift off it
                float fxRem = DivinerFx.remaining(pos, partial);
                float r = Float.isNaN(fxRem) ? t : Mth.clamp(RETURN_TICKS - fxRem, 0f, RETURN_TICKS);
                Vec3 fwd = dir.scale(-1);
                float yawW = (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
                alt = r < 12 ? sAlt : Math.max(sAlt * (float) Math.exp(-0.15f * (r - 12f)), 0.002f);
                takeover = Math.min(sTakeover, 1f - smooth(50, 64, r));
                apparition = 1f - smooth(0, 14, r);
                warp = bell(r, 8, 60) * 0.8f;
                cometHeat = smooth(0.06f, 0.012f, alt) * (1 - smooth(54, 64, r));
                // space comet: from just behind the camera, down ahead of it toward the site (offset the same way
                // the world chase looks, -dir, so both shots face the same way)
                float ac = alt * Mth.lerp(ease(r / 16f), 1.6f, 0.45f);
                Vec3 across = new Vec3(-dir.z, 0, dir.x).normalize();
                float weave = 0.06f * Mth.sin(r * 0.32f) * smooth(10f, 20f, r);   // it weaves; the camera has to chase it
                cometSpace.set((float) (-dir.x * 0.25 + across.x * weave) * alt, 1f + ac, (float) (-dir.z * 0.25 + across.z * weave) * alt);
                cometSpaceVisible = r > 2 && r < 50;   // from the cut on, the world comet (same spot on screen) takes over
                // once it has passed, the camera follows it: the angles that keep it at the centre of the view
                float[] track = lookAt(Vec3.ZERO, new Vec3(-dir.x * 0.25 + across.x * weave, (ac - alt) / Math.max(alt, 1e-6f),
                        -dir.z * 0.25 + across.z * weave));
                returnR = r;
                // real world: the comet's actual path (DivinerFx draws it there for everyone)
                float rem = RETURN_TICKS - r;
                // chase from farther up the path (dir points away from the diviner) and to one side,
                // so the comet is between the camera and the diviner and its trail crosses the view
                double cometDist = DivinerFx.cometDistance(rem);
                Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
                cometWorld = r < 50 ? null : DivinerFx.cometPosition(pos, rem);
                Vec3 chase = cometWorld == null ? top : cometWorld.add(DivinerFx.cometBackward(pos, rem).scale(cometDist * 0.12 + 8.0))
                        .add(side.scale(4.0 + cometDist * 0.1));
                if (r < 50) {
                    // until the world fades back in, park the real camera high above the player (nothing shows)
                    camPos = eye.add(0, WORLD_CEILING, 0);
                    float down = r < 16 ? Mth.lerp(ease(r / 16f), sPitch, 90f) : 90f;
                    float yawDown = Mth.rotLerp(ease(r / 50f), sYaw, yawW);
                    float k = ease((r - 14f) / 12f);
                    camYaw = Mth.rotLerp(k, yawDown, track[0]);
                    camPitch = Mth.lerp(k, down, track[1]);
                } else {
                    // the cut to the world: start exactly behind the comet with the same angles, so it stays in the
                    // same place on screen, then glide out to the wide chase that shows the diviner beyond it
                    Vec3 view = Vec3.directionFromRotation(track[1], track[0]);
                    Vec3 hold = cometWorld.subtract(view.scale(10.0 + cometDist * 0.12));
                    float k = ease((r - 50f) / 22f);
                    camPos = hold.lerp(chase, k);
                    // never above the top of the world: chunk culling can't find the ground from up there
                    double ceiling = mc.level.getMaxBuildHeight() - 16;
                    if (camPos.y > ceiling) camPos = new Vec3(camPos.x, ceiling, camPos.z);
                    float[] look = lookAt(camPos, cometWorld.lerp(top.lerp(cometWorld, 0.5), 0.7 * k));
                    camYaw = look[0];
                    camPitch = look[1];
                }
                haze = (1 - takeover) * (1 - smooth(56, 72, r)) * 0.4f;   // light enough to keep the star in sight through it
                camFov = baseFov + 10f * cometHeat + 6f * warp;
            }
            case IMPACT -> {
                float k = t;
                float amp = 0.5f * (float) Math.exp(-k / 7f);
                RandomSource rnd = RandomSource.create(clock * 31L);
                Vec3 jitter = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).scale(amp * 0.25);
                if (k < 18) {
                    camPos = sWorld.add(jitter);
                    camYaw = sYaw;
                    camPitch = sPitch;
                } else {
                    float e = ease((k - 18f) / 28f);
                    camPos = sWorld.lerp(eye, e).add(jitter);
                    camYaw = Mth.rotLerp(e, sYaw, playerYaw);
                    camPitch = Mth.lerp(e, sPitch, playerPitch);
                }
                camYaw += (float) rnd.nextGaussian() * amp * 1.5f;
                camPitch += (float) rnd.nextGaussian() * amp * 1.5f;
                camRoll = (float) rnd.nextGaussian() * amp * 2f;
                shakeX = (float) rnd.nextGaussian() * amp * 0.006f;
                shakeY = (float) rnd.nextGaussian() * amp * 0.006f;
                // ink frames: inverted, ink, red/gold duotone, inverted - then the white flash
                impactFrame = k < 1.5f ? 1 : k < 3f ? 2 : k < 4.5f ? 3 : k < 6f ? 1 : 0;
                flash = k < 6f ? 0 : (float) Math.pow(Math.max(0, 1 - (k - 6f) / 9f), 2);
                impact = k / 26f < 1 ? k / 26f : -1;
                chroma = 3f * (float) Math.exp(-k / 10f);
                crack = k < 6f ? 0 : (float) Math.exp(-(k - 6f) / 14f);
                rays = (float) Math.exp(-k / 18f);
                camFov = baseFov - 8f * (float) Math.exp(-k / 5f) + 4f * smooth(6, 14, k) * (float) Math.exp(-(k - 6f) / 20f);
            }
            case QUICK_RETURN -> {
                float r = t;
                alt = r < 8 ? sAlt : Math.max(sAlt * (float) Math.exp(-0.19f * (r - 8f)), WORLD_CEILING / BLOCKS_PER_RADIUS);
                takeover = Math.min(sTakeover, 1 - smooth(30, 42, r));
                float h = r < 34 ? WORLD_CEILING : Mth.clamp(WORLD_CEILING * (float) Math.exp(-0.31f * (r - 34f)), 4f, WORLD_CEILING);
                Vec3 high = eye.add(0, h, 0);
                float back = ease((r - 42f) / 8f);
                camPos = high.lerp(eye, back);
                camPitch = r < 40 ? Mth.lerp(ease(r / 12f), sPitch, 90f) : Mth.lerp(ease((r - 40f) / 10f), 90f, playerPitch);
                camYaw = r < 40 ? sYaw : Mth.rotLerp(ease((r - 40f) / 10f), sYaw, playerYaw);
                warp = bell(r, 4, 44);
                haze = smooth(30, 40, r) * (1 - smooth(42, 48, r));
                camFov = baseFov + 14f * warp;
            }
            case EASE_BACK -> {
                float e = ease(t / EASE_TICKS);
                camPos = sWorld.lerp(eye, e);
                camYaw = Mth.rotLerp(e, sYaw, playerYaw);
                camPitch = Mth.lerp(e, sPitch, playerPitch);
                takeover = sTakeover * (1 - e);
                alt = sAlt;
            }
            default -> {
            }
        }
        feel(t);
        // the star falls straight through the cloud layer: flying inside it would white out the screen and lose it
        clouds(mc, phase != Phase.RETURN || returnR >= 68f);
        // the camera jumps hundreds of blocks, past the top of the world: occlusion culling would only find the
        // terrain again a few frames later (a white sky meanwhile), so draw everything in range while flying
        cull(mc, phase != Phase.INTRO && phase != Phase.RETURN && phase != Phase.QUICK_RETURN);
    }

    private static boolean savedCull;
    private static boolean cullOff;

    private static void cull(Minecraft mc, boolean on) {
        if (!on && !cullOff) {
            savedCull = mc.smartCull;
            mc.smartCull = false;
            cullOff = true;
            mc.levelRenderer.needsUpdate();
        } else if (on && cullOff) {
            mc.smartCull = savedCull;
            cullOff = false;
            mc.levelRenderer.needsUpdate();
        }
    }

    private static net.minecraft.client.CloudStatus savedClouds;

    /** Hides vanilla clouds (false) or puts the player's own setting back (true). */
    private static void clouds(Minecraft mc, boolean show) {
        var option = mc.options.cloudStatus();
        if (!show && savedClouds == null) {
            savedClouds = option.get();
            option.set(net.minecraft.client.CloudStatus.OFF);
        } else if (show && savedClouds != null) {
            option.set(savedClouds);
            savedClouds = null;
        }
    }

    // ------------------------------------------------------------------ the camera's feel

    /** A critically damped spring: follows its target with lag and no overshoot past it. Times in ticks. */
    private static final class Spring {
        float value, vel;
        boolean live;

        float update(float target, float dt, float omega, boolean angle) {
            if (!live) {
                value = target;
                vel = 0;
                live = true;
                return value;
            }
            float x = angle ? Mth.wrapDegrees(value - target) : value - target;
            int n = Math.max(1, Mth.ceil(dt / 0.2f));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                vel += (-2f * omega * vel - omega * omega * x) * h;
                x += vel * h;
            }
            value = target + x;
            return value;
        }
    }

    /** A kick at `at` that dies away over `decay` ticks. */
    private static float jolt(float t, float at, float strength, float decay) {
        return t < at ? 0f : strength * (float) Math.exp(-(t - at) / decay);
    }

    /** Smooth pseudo-noise in -1..1: three sines at unrelated rates. `rate` in cycles per tick. */
    private static float wave(float t, float rate, int seed) {
        float a = t * rate * Mth.TWO_PI;
        return 0.6f * Mth.sin(a + seed * 1.7f) + 0.3f * Mth.sin(a * 2.31f + seed * 3.1f) + 0.1f * Mth.sin(a * 5.17f + seed * 5.3f);
    }

    /**
     * Springs, banking, hand-held sway and shake over this frame's exact pose. The springs only run in the phases
     * that fly (intro, space, the starfall return); the short eases back to the eyes stay exact so they land.
     */
    private static void feel(float t) {
        float now = clockTime();
        float dt = Float.isNaN(lastFeelClock) ? 0f : Mth.clamp(now - lastFeelClock, 0f, 4f);
        lastFeelClock = now;
        float sway = 0f, buffet = 0f, bank = 0f, omega = 0.5f;
        boolean flying = true;
        switch (phase) {
            case INTRO -> {
                sway = 0.3f + 0.8f * warp;
                buffet = 0.12f * smooth(12f, 30f, t) + 0.55f * warp * warp * (1f - 0.6f * takeover)
                        + 0.35f * bell(t, 32f, 44f)                       // straining against the air
                        + jolt(t, 12f, 0.7f, 4f) + jolt(t, 44f, 1.05f, 6f) + jolt(t, 62f, 0.45f, 8f);
                bank = 0.35f;
                omega = 0.4f;
            }
            case SPACE -> {
                sway = 0.12f;
                omega = 0.2f;
            }
            case RETURN -> {
                float r = returnR;
                sway = 0.3f + 0.6f * warp;
                buffet = 0.18f + 0.9f * cometHeat + 0.5f * warp + 0.35f * smooth(64f, 78f, r)
                        + jolt(r, 14f, 0.6f, 5f) + jolt(r, 50f, 0.7f, 5f) + jolt(r, 60f, 0.6f, 6f);
                bank = 0.6f;
                omega = 0.55f;
            }
            default -> flying = false;
        }
        if (flying) {
            camYaw = yawSpring.update(camYaw, dt, omega, true);
            camPitch = pitchSpring.update(camPitch, dt, omega, false);
            camFov = fovSpring.update(camFov, dt, omega * 1.4f, false);
            float yawVel = dt > 0 ? Mth.wrapDegrees(camYaw - lastYaw) / dt : 0f;
            lastYaw = camYaw;
            camRoll = rollSpring.update(camRoll + Mth.clamp(-yawVel * bank * 3f, -14f, 14f), dt, 0.3f, false);
        } else {
            yawSpring.live = pitchSpring.live = rollSpring.live = fovSpring.live = false;
            lastYaw = camYaw;
        }
        // hand-held sway and the shake ride on top, unsmoothed
        camYaw += sway * wave(now, 0.031f, 1) * 1.6f;
        camPitch += sway * wave(now, 0.027f, 2) * 1.1f;
        camRoll += sway * wave(now, 0.023f, 3) * 1.8f;
        float k = Mth.clamp(buffet, 0f, 1.3f);
        k *= k;
        if (k > 0) {
            camYaw += k * wave(now, 0.45f, 4) * 2.2f;
            camPitch += k * wave(now, 0.53f, 5) * 2.2f;
            camRoll += k * wave(now, 0.37f, 6) * 3f;
            shakeX += k * wave(now, 0.61f, 7) * 0.004f;
            shakeY += k * wave(now, 0.71f, 8) * 0.004f;
            camFov += k * wave(now, 0.25f, 9) * 1.5f;
        }
        camPitch = Mth.clamp(camPitch, -89.9f, 89.9f);
    }

    /** Minecraft yaw/pitch (degrees) looking from `from` at `to`. */
    static float[] lookAt(Vec3 from, Vec3 to) {
        Vec3 f = to.subtract(from).normalize();
        return new float[]{(float) Math.toDegrees(Math.atan2(-f.x, f.z)), (float) Math.toDegrees(Math.asin(-f.y))};
    }

    /** Same rotation Camera#setRotation builds; its conjugate is the view matrix. */
    static Quaternionf rotation(float yaw, float pitch, float roll) {
        return new Quaternionf().rotationYXZ(Mth.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, -roll * Mth.DEG_TO_RAD);
    }

    // ------------------------------------------------------------------ camera, fog, HUD

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (phase == Phase.NONE) return;
        event.setYaw(camYaw);
        event.setPitch(camPitch);
        event.setRoll(camRoll);
    }

    // NeoForge fires ComputeCameraAngles inside Camera#setup, before setup places the camera at the entity,
    // so the position is set here instead: ComputeFov runs right after setup and before the cull frustum.
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (phase == Phase.NONE || !event.usedConfiguredFov()) return;
        event.getCamera().setPosition(camPos);
        event.setFOV(camFov);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (phase == Phase.NONE) return;
        double up = camPos.y - Minecraft.getInstance().player.getEyeY();
        if (up < 8) return;
        // high up the ground is about `up` away (more, looking down at a slant): keep the fog past it
        event.setFarPlaneDistance((float) Math.max(event.getFarPlaneDistance(), up * 2.4 + 160));
        event.setNearPlaneDistance((float) Math.max(event.getNearPlaneDistance(), up * 1.5));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (phase != Phase.NONE) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (phase != Phase.NONE) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ space + post chain

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || phase == Phase.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        PostChain post = chain(mc);
        if (post == null) return;
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float time = clockTime() / 20f;

        // the space camera sits above the diviner's site on the planet, looking the same way as the real one
        Vector3f spaceCam = new Vector3f(0, 1f + Math.max(alt, 1e-5f), 0);
        Quaternionf rot = rotation(camYaw, camPitch, camRoll);
        Matrix4f viewRot = new Matrix4f().rotation(new Quaternionf(rot).conjugate());
        Matrix4f view = new Matrix4f(viewRot).translate(-spaceCam.x, -spaceCam.y, -spaceCam.z);
        float near = Math.max(alt * 0.2f, 2e-6f);
        Matrix4f proj = new Matrix4f().setPerspective(camFov * Mth.DEG_TO_RAD, aspect, near, alt + 4f);
        Vector3f sun = sunDirection(mc);

        float cometScale = cometSpaceVisible ? Math.max(cometSpace.distance(spaceCam) * 0.045f, 1e-6f) : 0f;
        StarColors.StarPalette star = DivinerFx.localPalette(pos);
        StarColors.StarPalette beam = DivinerFx.launchedPalette(pos);
        DivinerFx.starUniforms(star);   // the space comet (SpaceScene) is drawn with the same comet shader
        if (takeover > 0.001f) {
            SpaceScene.render(post.getTempTarget("planet"), view, proj, spaceCam, sun, time,
                    cometSpaceVisible ? cometSpace : null, cometScale);
        }

        Matrix4f invViewProj = new Matrix4f(proj).mul(viewRot).invert();
        Quaternionf constRot = rotation(introYaw + INTRO_SPIN, SPACE_PITCH, 0);
        Vector3f right = constRot.transform(new Vector3f(1, 0, 0));
        Vector3f up = constRot.transform(new Vector3f(0, 1, 0));
        Vector3f fwd = constRot.transform(new Vector3f(0, 0, -1));
        Matrix4f constFrame = new Matrix4f().setColumn(0, new Vector4f(right, 0)).setColumn(1, new Vector4f(up, 0))
                .setColumn(2, new Vector4f(fwd, 0)).setColumn(3, new Vector4f(0, 0, 0, 1));

        // comet + impact positions on screen
        float[] comet = {0, 0, 0};
        float tailX = 0, tailY = 1;
        Matrix4f worldViewProj = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Vec3 cam = event.getCamera().getPosition();
        if (cometSpaceVisible && takeover > 0.05f) {
            float[] a = project(new Matrix4f(proj).mul(view), cometSpace);
            float[] b = project(new Matrix4f(proj).mul(view), new Vector3f(cometSpace).mul(1f + alt * 0.3f));
            if (a != null) {
                comet = new float[]{a[0], a[1], takeover};
                if (b != null) {
                    tailX = b[0] - a[0];
                    tailY = b[1] - a[1];
                }
            }
        } else if (cometWorld != null) {
            Vec3 rel = cometWorld.subtract(cam);
            float[] a = project(worldViewProj, new Vector3f((float) rel.x, (float) rel.y, (float) rel.z));
            float remNow = DivinerFx.remaining(pos, partial);
            Vec3 relTail = rel.add((Float.isNaN(remNow) ? DivinerFx.fallDirection(pos) : DivinerFx.cometBackward(pos, remNow)).scale(6));
            float[] b = project(worldViewProj, new Vector3f((float) relTail.x, (float) relTail.y, (float) relTail.z));
            if (a != null) {
                comet = new float[]{a[0], a[1], phase == Phase.RETURN ? 1f : Math.min(1f, (1 - takeover) * 1.2f)};
                if (b != null) {
                    tailX = b[0] - a[0];
                    tailY = b[1] - a[1];
                }
            }
        }
        float[] impactAt = {0.5f, 0.5f};
        if (pos != null) {
            Vec3 rel = Vec3.atCenterOf(pos).add(0, 0.5, 0).subtract(cam);
            float[] p = project(worldViewProj, new Vector3f((float) rel.x, (float) rel.y, (float) rel.z));
            if (p != null) impactAt = p;
        }

        var art = mc.getTextureManager().getTexture(GUI_ART);
        if (!artFiltered) {
            art.setFilter(true, false);
            artFiltered = true;
        }
        int artId = art.getId();
        long sinceLaunch = System.currentTimeMillis() - DivinerFx.lastLaunchMillis();
        float launchBeam = sinceLaunch < 1500 ? (float) Math.exp(-sinceLaunch / 350.0) : 0f;
        for (PostPass pass : passes(post)) {
            EffectInstance fx = pass.getEffect();
            if (fx.getName().endsWith("diviner_cosmos")) fx.setSampler("ApparitionSampler", () -> artId);
            fx.safeGetUniform("ArtRect").set(ART_RECT[0], ART_RECT[1], ART_RECT[2], ART_RECT[3]);
            fx.safeGetUniform("Apparition").set(apparition * (1f + 0.35f * CabinDialogue.voicePulse));
            fx.safeGetUniform("LaunchBeam").set(phase == Phase.SPACE || phase == Phase.INTRO ? launchBeam : 0f);
            fx.safeGetUniform("Crack").set(crack);
            fx.safeGetUniform("Rays").set(rays);
            fx.safeGetUniform("InvViewProj").set(invViewProj);
            fx.safeGetUniform("ConstFrame").set(constFrame);
            fx.safeGetUniform("iTime").set(time);
            fx.safeGetUniform("Takeover").set(takeover);
            fx.safeGetUniform("Haze").set(haze);
            fx.safeGetUniform("Warp").set(warp);
            fx.safeGetUniform("Reveal").set(phase == Phase.SPACE ? 1f : reveal);
            fx.safeGetUniform("SunDir").set(sun.x, sun.y, sun.z);
            fx.safeGetUniform("CometScreen").set(comet[0], comet[1], comet[2]);
            fx.safeGetUniform("CometTail").set(tailX, tailY);
            fx.safeGetUniform("CometHeat").set(cometHeat);
            fx.safeGetUniform("CometCore").set(star.core()[0], star.core()[1], star.core()[2]);
            fx.safeGetUniform("CometTrail").set(star.trail()[0], star.trail()[1], star.trail()[2]);
            float[] bc = DivinerFx.mixWhite(beam.core(), 0.25f);
            fx.safeGetUniform("BeamColor").set(bc[0], bc[1], bc[2]);
            fx.safeGetUniform("Fade").set(0f);
            fx.safeGetUniform("Impact").set(impact);
            fx.safeGetUniform("ImpactFrame").set(impactFrame);
            fx.safeGetUniform("ImpactScreen").set(impactAt[0], impactAt[1]);
            fx.safeGetUniform("Flash").set(flash);
            fx.safeGetUniform("Chroma").set(chroma);
            fx.safeGetUniform("Shake").set(shakeX, shakeY);
            fx.safeGetUniform("BloomStrength").set(0.35f + 0.3f * takeover + (impact >= 0 ? 0.5f : 0f));
        }
        DivinerFx.starUniforms(StarColors.DEFAULT);
        post.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        mc.getMainRenderTarget().bindWrite(false);
    }

    /** Screen uv (0..1, y up) of a point, or null when behind the camera. */
    private static float[] project(Matrix4f viewProj, Vector3f p) {
        Vector4f clip = viewProj.transform(new Vector4f(p, 1f));
        if (clip.w <= 1e-6f) return null;
        return new float[]{clip.x / clip.w * 0.5f + 0.5f, clip.y / clip.w * 0.5f + 0.5f};
    }

    /** Sun direction in the site's frame (y up): follows the world's time of day so night falls on the planet too. */
    static Vector3f sunDirection(Minecraft mc) {
        float a = mc.level == null ? 0.1f : mc.level.getTimeOfDay(partial);
        float ang = a * Mth.TWO_PI;
        return new Vector3f(-Mth.sin(ang), Mth.cos(ang), 0.3f).normalize();
    }

    private static PostChain chain(Minecraft mc) {
        if (broken) return null;
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
                chainW = -1;
            }
            if (w != chainW || h != chainH) {
                chain.resize(w, h);
                chainW = w;
                chainH = h;
            }
            return chain;
        } catch (Exception e) {
            LOGGER.error("Transient Diviner cinematic failed to load; using the plain menu", e);
            broken = true;
            chain = null;
            finish();
            return null;
        }
    }

    private static boolean artFiltered;
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
            LOGGER.error("Can't reach PostChain passes; diviner cinematic disabled", e);
            broken = true;
            return List.of();
        }
    }

    // ------------------------------------------------------------------ space ambience

    private static void startLoop() {
        stopLoop();
        spaceIntensity = 0;
        loop = new SpaceLoop();
        swell = new SwellLoop();
        Minecraft.getInstance().getSoundManager().play(loop);
        Minecraft.getInstance().getSoundManager().play(swell);
    }

    private static void stopLoop() {
        if (loop != null) loop.fadeOut = true;
        if (swell != null) swell.fadeOut = true;
        loop = null;
        swell = null;
    }

    private static final class SpaceLoop extends AbstractTickableSoundInstance {
        boolean fadeOut;
        private float level;

        SpaceLoop() {
            super(ModSounds.DIVINER_SPACE_LOOP.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.01f;
        }

        @Override
        public void tick() {
            level = fadeOut ? level - 0.05f : Math.min(1f, level + 0.03f);
            if (level <= 0 && fadeOut) {
                stop();
                return;
            }
            // dips a little under the swell so working the panel doesn't just get louder
            volume = Math.max(0.01f, level * 0.7f * (1f - 0.2f * (swell == null ? 0f : swell.level)));
        }
    }

    /** The brighter choir layer over the space loop, following setSpaceIntensity. */
    private static final class SwellLoop extends AbstractTickableSoundInstance {
        boolean fadeOut;
        float level;

        SwellLoop() {
            super(ModSounds.DIVINER_SPACE_SWELL.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.01f;
        }

        @Override
        public void tick() {
            float target = fadeOut ? 0f : spaceIntensity;
            level += Mth.clamp(target - level, -0.03f, 0.015f);
            if (fadeOut && level <= 0) {
                stop();
                return;
            }
            volume = Math.max(0.01f, level * 0.6f);
        }
    }
}
