package net.austin.cabinmod.client.diviner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.client.mesh.CabinMesh;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The starfall as everyone nearby sees it in the world: the Blender comet with its plasma trail carrying the
 * result down onto the diviner, then the impact - molten-glass crown (Blender crater mesh), energy dome, ground
 * shockwave with glowing cracks, crystal shards and Blender-rendered flipbook sprites. No vanilla particles:
 * this keeps its own small particle list, drawn with the diviner_* core shaders.
 *
 * Every star is its own fall (CraftingBlockEntity.Star, synced to everyone) in its own colours (StarColors).
 * The crafting player's own star starts locally with their cinematic (the LOCAL key) and then takes the
 * server's landing tick, so everyone sees it land together. A star someone else launches shoots up out of the
 * diviner for everyone around it.
 */
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DivinerFx {
    static final int VISIBLE_TICKS = 60;     // how long before landing the comet shows in the world
    static final int IMPACT_LIFE = 80;

    static final int STREAK_LIFE = 26;
    private static final int LOCAL_ID = -1;

    private record FallKey(BlockPos pos, int id) {
    }

    private static final Map<FallKey, Starfall> FALLS = new HashMap<>();
    private static final Map<BlockPos, Launched> LAUNCHED = new HashMap<>();
    private static final Map<BlockPos, Set<Integer>> SEEN = new HashMap<>();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long lastLaunchMillis;

    private static final class Starfall {
        final BlockPos pos;
        double landTick;
        ItemStack item;
        final boolean local;
        boolean impacted;
        StarColors.StarPalette palette;
        int starId;   // the server star this is (0 until a local fall finds its own)

        Starfall(BlockPos pos, double landTick, ItemStack item, boolean local, StarColors.StarPalette palette) {
            this.pos = pos;
            this.landTick = landTick;
            this.item = item;
            this.local = local;
            this.palette = palette;
        }
    }

    /** The last result this client launched from a diviner. */
    private record Launched(ItemStack stack, StarColors.StarPalette palette, long millis) {
    }

    /** Someone else's result shooting up out of the diviner, as everyone around sees it. */
    private record Streak(BlockPos pos, ItemStack item, StarColors.StarPalette palette, long startTick) {
    }

    private record Impact(BlockPos pos, int[] age, StarColors.StarPalette palette) {
    }

    private enum Sheet {
        STARBURST("starburst_8x8", 8), RING("shockwave_ring_8x8", 8), SHARD("star_shard_4x4", 4),
        WISP("nebula_wisp_8x8", 8), SPARK("spark_4x4", 4);

        final String file;
        final int grid;

        Sheet(String file, int grid) {
            this.file = file;
            this.grid = grid;
        }
    }

    private static final class Particle {
        final Sheet sheet;
        Vec3 pos, vel;
        int age;
        final int life;
        final float size, spin, r, g, b;
        final boolean ground, loop;
        float gravity, drag = 0.92f;

        Particle(Sheet sheet, Vec3 pos, Vec3 vel, int life, float size, float r, float g, float b, boolean ground, boolean loop) {
            this.sheet = sheet;
            this.pos = pos;
            this.vel = vel;
            this.life = life;
            this.size = size;
            this.spin = (RANDOM.nextFloat() - 0.5f) * 0.2f;
            this.r = r;
            this.g = g;
            this.b = b;
            this.ground = ground;
            this.loop = loop;
        }

        Particle(Sheet sheet, Vec3 pos, Vec3 vel, int life, float size, float[] rgb, boolean ground, boolean loop) {
            this(sheet, pos, vel, life, size, rgb[0], rgb[1], rgb[2], ground, loop);
        }
    }

    private DivinerFx() {
    }

    // ------------------------------------------------------------------ the fall path (shared with the cinematic camera)

    /** Unit direction the comet comes in from (pointing up, away from the diviner). Fixed per diviner. */
    public static Vec3 fallDirection(BlockPos pos) {
        if (pos == null) return new Vec3(0, 1, 0);
        RandomSource r = RandomSource.create(pos.asLong() * 31 + 7);
        double a = r.nextDouble() * Math.PI * 2, tilt = 0.22 + r.nextDouble() * 0.14;
        return new Vec3(Math.sin(a) * tilt, 1, Math.cos(a) * tilt).normalize();
    }

    /** Blocks between the comet and the diviner `remaining` ticks before it lands: ~135 blocks up 30 ticks out, landing at ~60 blocks/s (low enough that the chase camera stays inside the world). */
    public static double cometDistance(float remaining) {
        return remaining <= 0 ? 0 : 3.0 * remaining + 0.05 * remaining * remaining;
    }

    /**
     * Ticks (with the frame's partial) until the starfall at `pos` lands, from the same game clock the comet is
     * drawn with, or NaN if none is tracked. The cinematic camera follows this so it never drifts off the comet.
     */
    static float remaining(BlockPos pos, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Starfall f = pos == null ? null : FALLS.get(new FallKey(pos, LOCAL_ID));
        if (f == null || mc.level == null) return Float.NaN;
        return (float) (f.landTick - (mc.level.getGameTime() + partial));
    }

    public static Vec3 cometPosition(BlockPos pos, float remaining) {
        double dist = cometDistance(remaining);
        return Vec3.atCenterOf(pos).add(0, 0.9, 0).add(fallDirection(pos).scale(dist)).add(pathOffset(pos, dist));
    }

    /**
     * How far the path bends away from the straight line `dist` blocks up: a long lazy bow plus a slow corkscrew,
     * both dying out as it reaches the diviner, so the star swoops in instead of sliding down a rail.
     */
    static Vec3 pathOffset(BlockPos pos, double dist) {
        if (pos == null || dist <= 0) return Vec3.ZERO;
        Vec3 dir = fallDirection(pos);
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        Vec3 across = dir.cross(side).normalize();
        double phase = (pos.asLong() * 0.37) % (Math.PI * 2);
        double bow = dist * 0.12 * (dist / (dist + 80.0));
        double swirl = Math.min(4.0, dist * 0.05);
        double a = dist * 0.03 + phase;
        return side.scale(bow + Math.cos(a) * swirl).add(across.scale(Math.sin(a) * swirl));
    }

    /** Unit direction back along the path from the comet `remaining` ticks out (where it came from). */
    static Vec3 cometBackward(BlockPos pos, float remaining) {
        Vec3 d = cometPosition(pos, remaining + 0.5f).subtract(cometPosition(pos, remaining));
        return d.lengthSqr() < 1e-8 ? fallDirection(pos) : d.normalize();
    }

    // ------------------------------------------------------------------ hooks

    /** Wall-clock time of the last result this client launched from a diviner menu. */
    public static long lastLaunchMillis() {
        return lastLaunchMillis;
    }

    static ItemStack launched(BlockPos pos) {
        Launched l = LAUNCHED.get(pos);
        return l == null ? ItemStack.EMPTY : l.stack();
    }

    /** Colours of the star this client last launched from `pos`. */
    static StarColors.StarPalette launchedPalette(BlockPos pos) {
        Launched l = pos == null ? null : LAUNCHED.get(pos);
        return l == null ? StarColors.DEFAULT : l.palette();
    }

    /** Colours of this client's own falling star (for the cinematic), or of its last launch. */
    static StarColors.StarPalette localPalette(BlockPos pos) {
        Starfall f = pos == null ? null : FALLS.get(new FallKey(pos, LOCAL_ID));
        return f != null ? f.palette : launchedPalette(pos);
    }

    static void onLaunched(CraftingBlockEntity diviner, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        lastLaunchMillis = System.currentTimeMillis();
        LAUNCHED.put(diviner.getBlockPos().immutable(),
                new Launched(stack.copy(), StarColors.resolve(mc.level, ItemStack.EMPTY, stack), lastLaunchMillis));
        mc.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ModSounds.DIVINER_LAUNCH.get(), 0.9f, 0.6f));
        CabinDialogue.onLaunched(stack);
    }

    /** The crafting player's own fall, started by the cinematic so the comet lands exactly with its camera. */
    static void startLocalStarfall(BlockPos pos, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || pos == null) return;
        FALLS.put(new FallKey(pos, LOCAL_ID), new Starfall(pos, mc.level.getGameTime() + ticks, launched(pos), true, launchedPalette(pos)));
    }

    /** Client ticker of every loaded diviner: picks up every star, launched or falling, from the synced stars. */
    static void track(CraftingBlockEntity diviner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        UUID me = mc.player.getUUID();
        BlockPos pos = diviner.getBlockPos().immutable();
        long now = mc.level.getGameTime();
        Set<Integer> seen = SEEN.get(pos);
        boolean firstLook = seen == null;   // stars already up when the diviner loaded don't shoot up again
        if (firstLook) SEEN.put(pos, seen = new HashSet<>());
        Set<Integer> alive = new HashSet<>();
        Starfall local = FALLS.get(new FallKey(pos, LOCAL_ID));
        for (CraftingBlockEntity.Star star : diviner.stars()) {
            alive.add(star.id);
            boolean mine = star.owner.equals(me);
            StarColors.StarPalette palette = StarColors.resolve(mc.level, star.input, star.result);
            if (seen.add(star.id) && !firstLook && !mine && !star.falling()) launchStreak(pos, star.result, palette, now);
            if (!star.falling()) continue;
            if (mine && local != null && !local.impacted && (local.starId == 0 || local.starId == star.id)) {
                // my own star: keep the cinematic's fall, but land it on the server's tick, in the star's colours
                local.starId = star.id;
                local.landTick = star.landTick;
                local.palette = palette;
                if (local.item.isEmpty()) local.item = star.result.copy();
                continue;
            }
            if (local != null && local.starId == star.id) continue;
            FallKey key = new FallKey(pos, star.id);
            Starfall fall = FALLS.get(key);
            if (fall == null) {
                if (now > star.landTick) continue;
                FALLS.put(key, new Starfall(pos, star.landTick, star.result.copy(), false, palette));
                mc.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, ModSounds.DIVINER_FALL_WORLD.get(),
                        SoundSource.BLOCKS, 4f, 1f, false);
            } else {
                fall.landTick = star.landTick;
                fall.palette = palette;
            }
        }
        seen.retainAll(alive);
        HeavensFx.track(diviner);
    }

    private static void launchStreak(BlockPos pos, ItemStack item, StarColors.StarPalette palette, long now) {
        Minecraft mc = Minecraft.getInstance();
        STREAKS.add(new Streak(pos, item.copy(), palette, now));
        mc.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, ModSounds.DIVINER_LAUNCH_WORLD.get(),
                SoundSource.BLOCKS, 2.5f, 1f, false);
        Vec3 top = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        PARTICLES.add(new Particle(Sheet.STARBURST, top.add(0, 0.4, 0), Vec3.ZERO, 14, 4f, mixWhite(palette.core(), 0.5f), false, false));
        for (int i = 0; i < 18; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.12, 0.3 + RANDOM.nextDouble() * 0.4, RANDOM.nextGaussian() * 0.12);
            PARTICLES.add(new Particle(Sheet.SPARK, top, v, 14 + RANDOM.nextInt(10), 0.4f + RANDOM.nextFloat() * 0.4f, palette.core(), false, true));
        }
    }

    static float[] mixWhite(float[] c, float k) {
        return new float[]{c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k};
    }

    /** A drifting mote around a player in the heavens (HeavensFx). */
    static void mote(Vec3 at, Vec3 vel, float[] rgb) {
        PARTICLES.add(new Particle(Sheet.SPARK, at, vel, 24 + RANDOM.nextInt(16), 0.25f + RANDOM.nextFloat() * 0.25f, rgb, false, true));
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        FALLS.clear();
        IMPACTS.clear();
        PARTICLES.clear();
        LAUNCHED.clear();
        SEEN.clear();
        STREAKS.clear();
    }

    // ------------------------------------------------------------------ simulation

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (Iterator<Starfall> it = FALLS.values().iterator(); it.hasNext(); ) {
            Starfall f = it.next();
            double rem = f.landTick - now;
            if (!f.impacted && rem <= 0) {
                f.impacted = true;
                impact(f);
            } else if (!f.impacted && rem < VISIBLE_TICKS) {
                Vec3 at = cometPosition(f.pos, (float) rem);
                Vec3 dir = cometBackward(f.pos, (float) rem);
                for (int i = 0; i < 3; i++) {
                    Vec3 v = dir.scale(0.4 + RANDOM.nextDouble() * 0.6).add(rnd(0.25));
                    PARTICLES.add(new Particle(Sheet.SPARK, at.add(dir.scale(RANDOM.nextDouble() * 4)), v,
                            14 + RANDOM.nextInt(10), 0.5f + RANDOM.nextFloat() * 0.6f, mixWhite(f.palette.core(), 0.3f), false, true));
                }
                if (now % 3 == 0)
                    PARTICLES.add(new Particle(Sheet.WISP, at.add(dir.scale(3 + RANDOM.nextDouble() * 6)), rnd(0.05),
                            30, 1.6f + RANDOM.nextFloat(), f.palette.trail()[0] * 0.75f, f.palette.trail()[1] * 0.75f, f.palette.trail()[2] * 0.75f, false, false));
            }
            if (rem < -IMPACT_LIFE) it.remove();
        }
        STREAKS.removeIf(st -> now - st.startTick() > STREAK_LIFE);
        for (Iterator<Impact> it = IMPACTS.iterator(); it.hasNext(); ) {
            Impact im = it.next();
            int age = ++im.age[0];
            if (age == 4 || age == 9) {   // after-shocks: two more rings rolling out over the ground
                Vec3 c = Vec3.atCenterOf(im.pos());
                PARTICLES.add(new Particle(Sheet.RING, new Vec3(c.x, im.pos().getY() + 0.07 + age * 0.002, c.z), Vec3.ZERO, 24,
                        age == 4 ? 22f : 30f, age == 4 ? mixWhite(im.palette().core(), 0.3f) : im.palette().trail(), true, false));
                PARTICLES.add(new Particle(Sheet.STARBURST, c.add(0, 1.2, 0), Vec3.ZERO, 14, 5f, mixWhite(im.palette().core(), 0.6f), false, false));
            }
            if (age > IMPACT_LIFE) it.remove();
        }
        for (Iterator<Particle> it = PARTICLES.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            if (++p.age >= p.life) {
                it.remove();
                continue;
            }
            p.pos = p.pos.add(p.vel);
            p.vel = p.vel.scale(p.drag).add(0, -p.gravity, 0);
        }
        if (PARTICLES.size() > 1500) PARTICLES.subList(0, PARTICLES.size() - 1500).clear();
    }

    private static Vec3 rnd(double s) {
        return new Vec3(RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s, RANDOM.nextGaussian() * s);
    }

    private static void impact(Starfall f) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 top = Vec3.atCenterOf(f.pos).add(0, 0.55, 0);
        mc.level.playLocalSound(top.x, top.y, top.z, ModSounds.DIVINER_IMPACT.get(), SoundSource.BLOCKS, 3f, 1f, false);
        StarColors.StarPalette pal = f.palette;
        IMPACTS.add(new Impact(f.pos, new int[]{0}, pal));
        PARTICLES.add(new Particle(Sheet.STARBURST, top.add(0, 0.6, 0), Vec3.ZERO, 22, 9f, mixWhite(pal.core(), 0.75f), false, false));
        for (int i = 0; i < 3; i++)
            PARTICLES.add(new Particle(Sheet.STARBURST, top.add(rnd(1.2)).add(0, 1, 0), Vec3.ZERO, 16 + i * 4, 3.5f, pal.core(), false, false));
        PARTICLES.add(new Particle(Sheet.RING, new Vec3(top.x, f.pos.getY() + 0.06, top.z), Vec3.ZERO, 26, 16f, pal.core(), true, false));
        PARTICLES.add(new Particle(Sheet.RING, top.add(0, 0.3, 0), Vec3.ZERO, 18, 7f, mixWhite(pal.trail(), 0.4f), false, false));
        for (int i = 0; i < 16; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.25, 0.35 + RANDOM.nextDouble() * 0.45, RANDOM.nextGaussian() * 0.25);
            Particle shard = new Particle(Sheet.SHARD, top, v, 30 + RANDOM.nextInt(20), 0.9f + RANDOM.nextFloat() * 0.6f, mixWhite(pal.core(), 0.3f), false, true);
            shard.gravity = 0.045f;
            shard.drag = 0.97f;
            PARTICLES.add(shard);
        }
        for (int i = 0; i < 46; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.45, RANDOM.nextDouble() * 0.6, RANDOM.nextGaussian() * 0.45);
            Particle spark = new Particle(Sheet.SPARK, top, v, 20 + RANDOM.nextInt(25), 0.4f + RANDOM.nextFloat() * 0.4f,
                    i % 3 == 0 ? pal.trail() : mixWhite(pal.core(), 0.4f), false, true);
            spark.gravity = 0.02f;
            PARTICLES.add(spark);
        }
        for (int i = 0; i < 8; i++)
            PARTICLES.add(new Particle(Sheet.WISP, top.add(rnd(1.0)), new Vec3(RANDOM.nextGaussian() * 0.04, 0.03 + RANDOM.nextDouble() * 0.04, RANDOM.nextGaussian() * 0.04),
                    40 + RANDOM.nextInt(30), 1.4f + RANDOM.nextFloat(), pal.trail()[0] * 0.7f, pal.trail()[1] * 0.7f, pal.trail()[2] * 0.7f, false, false));
    }

    /** Nearby players who aren't in the cinematic still feel the landing. */
    @SubscribeEvent
    public static void onCameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (IMPACTS.isEmpty() || DivinerCinematic.isActive()) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float amp = 0;
        for (Impact im : IMPACTS) {
            float age = im.age()[0] + (float) event.getPartialTick();
            if (age > 16) continue;
            double dist = cam.distanceTo(Vec3.atCenterOf(im.pos()));
            amp = Math.max(amp, (float) ((1 - age / 16f) * Math.max(0, 1 - dist / 40.0)));
        }
        if (amp <= 0) return;
        RandomSource r = RandomSource.create(System.nanoTime() / 16_000_000L);
        event.setYaw(event.getYaw() + (float) r.nextGaussian() * amp * 1.6f);
        event.setPitch(event.getPitch() + (float) r.nextGaussian() * amp * 1.6f);
        event.setRoll(event.getRoll() + (float) r.nextGaussian() * amp * 2f);
    }

    // ------------------------------------------------------------------ rendering

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (FALLS.isEmpty() && IMPACTS.isEmpty() && PARTICLES.isEmpty() && STREAKS.isEmpty())) return;
        if (DivinerShaders.comet == null || DivinerShaders.fx == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        float time = (float) (now % 24000) / 20f;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        cometUniforms(1f, 1f, 1f, 1f, 1.4f, time);
        if (DivinerShaders.trail != null) DivinerShaders.trail.safeGetUniform("iTime").set(time);
        CabinMesh comet = CabinMesh.get("comet");
        PoseStack items = new PoseStack();
        for (Starfall f : FALLS.values()) {
            double rem = f.landTick - now;
            if (f.impacted || rem <= 0 || rem >= VISIBLE_TICKS) continue;
            starUniforms(f.palette);
            Vec3 dir = cometBackward(f.pos, (float) rem);
            Vec3 head = cometPosition(f.pos, (float) rem).subtract(cam);
            Vector3f h = new Vector3f((float) head.x, (float) head.y, (float) head.z);
            Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
            if (comet != null) {
                // tumbling as it burns in, with a little shudder against the air
                float shudder = 0.12f * Mth.sin(time * 37f) + 0.08f * Mth.sin(time * 23f);
                Matrix4f m = new Matrix4f().translate(h).rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d))
                        .rotateY(time * 5f).rotateX(shudder).scale(1.6f * (1f + 0.06f * Mth.sin(time * 19f)));
                comet.emit(buffers.getBuffer(DivinerRenderTypes.COMET), m, 0, 1, 1, 1, 1);
            }
            pathTrail(buffers.getBuffer(DivinerRenderTypes.TRAIL), f.pos, (float) rem, cam, (float) Math.min(cometDistance((float) rem), 70.0));
            if (rem < 14) {   // the falling star arcs lightning down onto the ground around the diviner
                VertexConsumer arcs = buffers.getBuffer(DivinerRenderTypes.TRAIL);
                Vec3 groundC = Vec3.atCenterOf(f.pos).subtract(cam).add(0, -0.45, 0);
                long tick = mc.level.getGameTime();
                for (int i = 0; i < 3; i++) {
                    RandomSource r = RandomSource.create(tick * 17 + i * 101);
                    double ang = r.nextDouble() * Math.PI * 2, rad = 1.5 + r.nextDouble() * 5;
                    Vector3f end = new Vector3f((float) (groundC.x + Math.cos(ang) * rad), (float) groundC.y, (float) (groundC.z + Math.sin(ang) * rad));
                    arc(arcs, h, end, r, 0.2f);
                }
            }
            if (!f.item.isEmpty()) {
                items.pushPose();
                items.translate(h.x, h.y, h.z);
                items.mulPose(new Quaternionf().rotationY(time * 6f));
                items.scale(0.9f, 0.9f, 0.9f);
                mc.getItemRenderer().renderStatic(f.item, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        items, buffers, mc.level, 0);
                items.popPose();
            }
            buffers.endBatch(DivinerRenderTypes.TRAIL);   // uniforms are per star
            buffers.endBatch(DivinerRenderTypes.COMET);
        }
        renderStreaks(buffers, comet, items, cam, now, time);
        buffers.endBatch();
        starUniforms(StarColors.DEFAULT);

        renderCircles(buffers, cam, now, time);
        for (Impact im : IMPACTS) renderImpact(buffers, im, cam, partial, time);
        renderParticles(buffers, camera, cam, partial);
    }

    /** The star's colours on every shader that draws it. */
    static void starUniforms(StarColors.StarPalette p) {
        float[] c = p.core(), t = p.trail();
        for (var shader : new net.minecraft.client.renderer.ShaderInstance[]{DivinerShaders.comet, DivinerShaders.trail,
                DivinerShaders.circle, DivinerShaders.crater, DivinerShaders.ground}) {
            if (shader == null) continue;
            shader.safeGetUniform("StarCore").set(c[0], c[1], c[2]);
            shader.safeGetUniform("StarTrail").set(t[0], t[1], t[2]);
        }
    }

    /** Someone else's result shooting up out of the diviner, trail streaming back down to it. */
    private static void renderStreaks(MultiBufferSource.BufferSource buffers, CabinMesh comet, PoseStack items, Vec3 cam, double now, float time) {
        Minecraft mc = Minecraft.getInstance();
        for (Streak st : STREAKS) {
            float age = (float) (now - st.startTick());
            if (age < 0 || age > STREAK_LIFE) continue;
            starUniforms(st.palette());
            Vec3 dir = fallDirection(st.pos());
            double dist = cometDistance(age * 0.9f) + 1.0;
            Vec3 head = Vec3.atCenterOf(st.pos()).add(0, 0.9, 0).add(dir.scale(dist)).subtract(cam);
            Vector3f h = new Vector3f((float) head.x, (float) head.y, (float) head.z);
            Vector3f down = new Vector3f((float) -dir.x, (float) -dir.y, (float) -dir.z);
            float fade = 1f - Mth.clamp((age - STREAK_LIFE + 8) / 8f, 0f, 1f);
            if (comet != null) {
                Matrix4f m = new Matrix4f().translate(h).rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), down)).scale(1.2f);
                comet.emit(buffers.getBuffer(DivinerRenderTypes.COMET), m, 0, 1, 1, 1, fade);
            }
            trail(buffers.getBuffer(DivinerRenderTypes.TRAIL), h, down, (float) Math.min(dist - 0.5, 40.0) * fade);
            if (!st.item().isEmpty() && age < 10) {
                items.pushPose();
                items.translate(h.x, h.y, h.z);
                items.mulPose(new Quaternionf().rotationY(time * 6f));
                items.scale(0.7f, 0.7f, 0.7f);
                mc.getItemRenderer().renderStatic(st.item(), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        items, buffers, mc.level, 0);
                items.popPose();
            }
            buffers.endBatch(DivinerRenderTypes.TRAIL);
            buffers.endBatch(DivinerRenderTypes.COMET);
        }
    }

    private static void cometUniforms(float r, float g, float b, float a, float glow, float time) {
        var s = DivinerShaders.comet;
        s.safeGetUniform("ModelMat").set(new Matrix4f());
        s.safeGetUniform("CameraPos").set(0f, 0f, 0f);
        s.safeGetUniform("Tint").set(r, g, b, a);
        s.safeGetUniform("Glow").set(glow);
        s.safeGetUniform("iTime").set(time);
    }

    /** The plasma ribbon behind a falling comet, laid along the curve it actually flew (cometPosition). */
    private static void pathTrail(VertexConsumer out, BlockPos pos, float rem, Vec3 cam, float length) {
        int n = 24;
        float speed = 3f + 0.1f * rem;   // blocks per tick at this point of the fall (d/drem of cometDistance)
        float back = length / speed;
        Vector3f[] pts = new Vector3f[n + 1];
        for (int i = 0; i <= n; i++) {
            float v = i / (float) n;
            Vec3 p = cometPosition(pos, rem + back * v * v).subtract(cam);
            pts[i] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
        }
        ribbon(out, pts);
    }

    /** Camera-facing plasma ribbon from the comet head back along `dir`. */
    private static void trail(VertexConsumer out, Vector3f head, Vector3f dir, float length) {
        int n = 20;
        Vector3f[] pts = new Vector3f[n + 1];
        for (int i = 0; i <= n; i++) {
            float v = i / (float) n;
            pts[i] = new Vector3f(dir).mul(length * v * v).add(head);
        }
        ribbon(out, pts);
    }

    /** Ribbon through camera-relative points, head first, widening toward the tail. */
    private static void ribbon(VertexConsumer out, Vector3f[] pts) {
        int n = pts.length - 1;
        Vector3f prevL = null, prevR = null;
        float prevV = 0;
        for (int i = 0; i <= n; i++) {
            float v = i / (float) n;
            Vector3f p = pts[i];
            Vector3f dir = new Vector3f(pts[Math.min(n, i + 1)]).sub(pts[Math.max(0, i - 1)]);
            Vector3f side = new Vector3f(dir).cross(p);
            if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
            side.normalize().mul(0.5f + 3.2f * v);
            Vector3f l = new Vector3f(p).sub(side), r = new Vector3f(p).add(side);
            if (prevL != null) {
                quad(out, prevL, prevR, r, l, prevV, v);
            }
            prevL = l;
            prevR = r;
            prevV = v;
        }
    }

    /** A jagged, camera-facing lightning bolt from a to b (drawn with the trail shader's white core). */
    private static void arc(VertexConsumer out, Vector3f a, Vector3f b, RandomSource r, float width) {
        int n = 9;
        Vector3f prev = new Vector3f(a);
        Vector3f span = new Vector3f(b).sub(a);
        float len = span.length();
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            Vector3f p = new Vector3f(span).mul(t).add(a);
            if (i < n) p.add((r.nextFloat() - 0.5f) * len * 0.12f, (r.nextFloat() - 0.5f) * len * 0.12f, (r.nextFloat() - 0.5f) * len * 0.12f);
            Vector3f dir = new Vector3f(p).sub(prev);
            Vector3f side = new Vector3f(dir).cross(new Vector3f(p).add(prev).mul(0.5f));
            if (side.lengthSquared() < 1e-8f) side.set(1, 0, 0);
            side.normalize().mul(width);
            quad(out, new Vector3f(prev).sub(side), new Vector3f(prev).add(side), new Vector3f(p).add(side), new Vector3f(p).sub(side), 0.05f, 0.05f);
            prev = p;
        }
    }

    /** Summoning circle on the ground: builds up while the star falls, flares at the impact, then burns out. */
    private static void renderCircles(MultiBufferSource.BufferSource buffers, Vec3 cam, double now, float time) {
        if (DivinerShaders.circle == null) return;
        for (Starfall f : FALLS.values()) {
            double rem = f.landTick - now;
            if (rem > 30) continue;
            float charge = (float) Mth.clamp(1 - rem / 30.0, 0, 1);
            float flare = rem > 0 ? 0 : (float) Math.min(1, -rem / 3.0);
            float fade = rem > 0 ? 1 : (float) Mth.clamp(1 + rem / 45.0, 0, 1);
            if (fade <= 0) continue;
            starUniforms(f.palette);
            DivinerShaders.circle.safeGetUniform("Charge").set(charge);
            DivinerShaders.circle.safeGetUniform("Flare").set(flare * fade);
            DivinerShaders.circle.safeGetUniform("iTime").set(time);
            Vec3 c = Vec3.atCenterOf(f.pos).subtract(cam);
            float y = (float) (c.y - 0.47), s = 7f + 2f * flare;
            int col = ((int) (fade * 255) << 24) | 0xFFFFFF;
            VertexConsumer out = buffers.getBuffer(DivinerRenderTypes.CIRCLE);
            float x0 = (float) c.x - s, x1 = (float) c.x + s, z0 = (float) c.z - s, z1 = (float) c.z + s;
            out.addVertex(x0, y, z0).setUv(0, 0).setColor(col);
            out.addVertex(x1, y, z0).setUv(1, 0).setColor(col);
            out.addVertex(x1, y, z1).setUv(1, 1).setColor(col);
            out.addVertex(x0, y, z0).setUv(0, 0).setColor(col);
            out.addVertex(x1, y, z1).setUv(1, 1).setColor(col);
            out.addVertex(x0, y, z1).setUv(0, 1).setColor(col);
            buffers.endBatch(DivinerRenderTypes.CIRCLE);
        }
    }

    /** Pillar of light standing on the diviner after the impact: a camera-facing ribbon around the vertical. */
    static void pillar(VertexConsumer out, Vector3f base, float height, float width, float brightness) {
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f side = new Vector3f(up).cross(base);
        if (side.lengthSquared() < 1e-6f) side.set(1, 0, 0);
        side.normalize().mul(width);
        int n = 12;
        int c = ((int) (Mth.clamp(brightness, 0, 1) * 255) << 24) | 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            float v0 = i / (float) n, v1 = (i + 1) / (float) n;
            Vector3f a = new Vector3f(base).add(0, height * v0 * v0, 0), b = new Vector3f(base).add(0, height * v1 * v1, 0);
            Vector3f la = new Vector3f(a).sub(side), ra = new Vector3f(a).add(side), lb = new Vector3f(b).sub(side), rb = new Vector3f(b).add(side);
            out.addVertex(la.x, la.y, la.z).setUv(0, v0).setColor(c);
            out.addVertex(ra.x, ra.y, ra.z).setUv(1, v0).setColor(c);
            out.addVertex(rb.x, rb.y, rb.z).setUv(1, v1).setColor(c);
            out.addVertex(la.x, la.y, la.z).setUv(0, v0).setColor(c);
            out.addVertex(rb.x, rb.y, rb.z).setUv(1, v1).setColor(c);
            out.addVertex(lb.x, lb.y, lb.z).setUv(0, v1).setColor(c);
        }
    }

    private static void quad(VertexConsumer out, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float v0, float v1) {
        int white = 0xFFFFFFFF;
        out.addVertex(a.x, a.y, a.z).setUv(0, v0).setColor(white);
        out.addVertex(b.x, b.y, b.z).setUv(1, v0).setColor(white);
        out.addVertex(c.x, c.y, c.z).setUv(1, v1).setColor(white);
        out.addVertex(a.x, a.y, a.z).setUv(0, v0).setColor(white);
        out.addVertex(c.x, c.y, c.z).setUv(1, v1).setColor(white);
        out.addVertex(d.x, d.y, d.z).setUv(0, v1).setColor(white);
    }

    private static void renderImpact(MultiBufferSource.BufferSource buffers, Impact im, Vec3 cam, float partial, float time) {
        float a = im.age[0] + partial;
        Vec3 top = Vec3.atCenterOf(im.pos()).add(0, 0.5, 0).subtract(cam);
        Vector3f t = new Vector3f((float) top.x, (float) top.y, (float) top.z);
        starUniforms(im.palette());

        if (DivinerShaders.trail != null && a < 46) {
            VertexConsumer beams = buffers.getBuffer(DivinerRenderTypes.TRAIL);
            float life = 1 - a / 46f;
            pillar(beams, new Vector3f(t).add(0, -0.4f, 0), 90f, 0.35f + 2.4f * life * life, life);
            pillar(beams, new Vector3f(t).add(0, -0.4f, 0), 60f, 4f * life * life * life, life * 0.5f);
            if (a < 10) {   // lightning skittering out over the ground
                Vector3f ground = new Vector3f(t).add(0, -0.95f, 0);
                for (int i = 0; i < 6; i++) {
                    RandomSource r = RandomSource.create((long) (im.age()[0] * 13 + i * 7) + im.pos().asLong());
                    double ang = r.nextDouble() * Math.PI * 2, rad = 3 + r.nextDouble() * 6 * (a / 10f + 0.3f);
                    arc(beams, ground, new Vector3f((float) (ground.x + Math.cos(ang) * rad), ground.y, (float) (ground.z + Math.sin(ang) * rad)), r, 0.15f);
                }
            }
            buffers.endBatch(DivinerRenderTypes.TRAIL);
        }

        CabinMesh crater = CabinMesh.get("crater");
        if (crater != null && DivinerShaders.crater != null && a < 72) {
            float frame = a < 6 ? a / 6f : a < 30 ? 1 + (a - 6) / 24f : 2;
            var s = DivinerShaders.crater;
            s.safeGetUniform("ModelMat").set(new Matrix4f());
            s.safeGetUniform("CameraPos").set(0f, 0f, 0f);
            s.safeGetUniform("Heat").set(1f - Mth.clamp(a / 42f, 0f, 1f));
            s.safeGetUniform("Fade").set(a < 42 ? 1f : 1f - (a - 42) / 30f);
            s.safeGetUniform("iTime").set(time);
            crater.emit(buffers.getBuffer(DivinerRenderTypes.CRATER), new Matrix4f().translate(t), frame, 1, 1, 1, 1);
            buffers.endBatch(DivinerRenderTypes.CRATER);
        }

        CabinMesh dome = CabinMesh.get("planet_atmo");
        if (dome != null && a < 16) {
            float e = 1 - (float) Math.pow(1 - a / 16f, 3);
            float[] c = im.palette().core();
            cometUniforms(0.4f + 0.6f * c[0], 0.4f + 0.6f * c[1], 0.4f + 0.6f * c[2], 1f - a / 16f, 0.7f, time);
            dome.emit(buffers.getBuffer(DivinerRenderTypes.COMET), new Matrix4f().translate(t).scale(0.3f + 6.5f * e), 0, 1, 1, 1, 1);
            buffers.endBatch(DivinerRenderTypes.COMET);
        }

        if (DivinerShaders.ground != null && a < 30) {
            DivinerShaders.ground.safeGetUniform("Progress").set(a / 30f);
            DivinerShaders.ground.safeGetUniform("iTime").set(time);
            float y = t.y - 0.98f, s = 10f;
            Vector3f c0 = new Vector3f(t.x - s, y, t.z - s), c1 = new Vector3f(t.x + s, y, t.z - s);
            Vector3f c2 = new Vector3f(t.x + s, y, t.z + s), c3 = new Vector3f(t.x - s, y, t.z + s);
            VertexConsumer out = buffers.getBuffer(DivinerRenderTypes.GROUND);
            int white = 0xFFFFFFFF;
            out.addVertex(c0.x, c0.y, c0.z).setUv(0, 0).setColor(white);
            out.addVertex(c1.x, c1.y, c1.z).setUv(1, 0).setColor(white);
            out.addVertex(c2.x, c2.y, c2.z).setUv(1, 1).setColor(white);
            out.addVertex(c0.x, c0.y, c0.z).setUv(0, 0).setColor(white);
            out.addVertex(c2.x, c2.y, c2.z).setUv(1, 1).setColor(white);
            out.addVertex(c3.x, c3.y, c3.z).setUv(0, 1).setColor(white);
            buffers.endBatch(DivinerRenderTypes.GROUND);
        }
    }

    private static void renderParticles(MultiBufferSource.BufferSource buffers, Camera camera, Vec3 cam, float partial) {
        if (PARTICLES.isEmpty()) return;
        Vector3f left = new Vector3f(camera.getLeftVector()), up = new Vector3f(camera.getUpVector());
        CabinMesh shardMesh = CabinMesh.get("shard");
        cometUniforms(1f, 1f, 1f, 1f, 1.5f, 0);
        starUniforms(new StarColors.StarPalette(new float[]{1f, 1f, 1f}, new float[]{0.75f, 0.75f, 0.9f}));
        if (shardMesh != null) {
            VertexConsumer shards = buffers.getBuffer(DivinerRenderTypes.COMET);
            for (Particle p : PARTICLES) {
                if (p.sheet != Sheet.SHARD) continue;
                float age = p.age + partial;
                float life01 = Mth.clamp(age / p.life, 0f, 1f);
                Vec3 at = p.pos.add(p.vel.scale(partial)).subtract(cam);
                Matrix4f m = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z)
                        .rotateXYZ(age * 0.3f, age * 0.41f + p.spin * 40, age * 0.23f).scale(p.size * 2.2f * (1 - life01 * 0.6f));
                shardMesh.emit(shards, m, 0, p.r, p.g, p.b, 1 - life01);
            }
            buffers.endBatch(DivinerRenderTypes.COMET);
        }
        starUniforms(StarColors.DEFAULT);
        for (Sheet sheet : Sheet.values()) {
            if (sheet == Sheet.SHARD && shardMesh != null) continue;
            RenderType type = DivinerRenderTypes.sprite(sheet.file);
            VertexConsumer out = null;
            for (Particle p : PARTICLES) {
                if (p.sheet != sheet) continue;
                float age = p.age + partial;
                float life01 = Mth.clamp(age / p.life, 0f, 1f);
                Vec3 at = p.pos.add(p.vel.scale(partial)).subtract(cam);
                if (out == null) out = buffers.getBuffer(type);
                int frames = sheet.grid * sheet.grid;
                int frame = p.loop ? (int) (age * 0.8f) % frames : Math.min(frames - 1, (int) (life01 * frames));
                float u0 = (frame % sheet.grid) / (float) sheet.grid, v0 = (frame / sheet.grid) / (float) sheet.grid;
                float du = 1f / sheet.grid;
                float alpha = p.loop ? (1 - life01) : 1f;
                int color = ((int) (alpha * 255) << 24) | ((int) (p.r * 255) << 16) | ((int) (p.g * 255) << 8) | (int) (p.b * 255);
                float s = p.size * 0.5f;
                Vector3f a, b, c, d;
                Vector3f centre = new Vector3f((float) at.x, (float) at.y, (float) at.z);
                if (p.ground) {
                    a = new Vector3f(centre).add(-s, 0, -s);
                    b = new Vector3f(centre).add(s, 0, -s);
                    c = new Vector3f(centre).add(s, 0, s);
                    d = new Vector3f(centre).add(-s, 0, s);
                } else {
                    float rot = p.spin * age;
                    Vector3f l = new Vector3f(left).mul(Mth.cos(rot)).add(new Vector3f(up).mul(Mth.sin(rot))).mul(s);
                    Vector3f u = new Vector3f(up).mul(Mth.cos(rot)).sub(new Vector3f(left).mul(Mth.sin(rot))).mul(s);
                    a = new Vector3f(centre).add(l).add(u);
                    b = new Vector3f(centre).sub(l).add(u);
                    c = new Vector3f(centre).sub(l).sub(u);
                    d = new Vector3f(centre).add(l).sub(u);
                }
                out.addVertex(a.x, a.y, a.z).setUv(u0, v0).setColor(color);
                out.addVertex(b.x, b.y, b.z).setUv(u0 + du, v0).setColor(color);
                out.addVertex(c.x, c.y, c.z).setUv(u0 + du, v0 + du).setColor(color);
                out.addVertex(a.x, a.y, a.z).setUv(u0, v0).setColor(color);
                out.addVertex(c.x, c.y, c.z).setUv(u0 + du, v0 + du).setColor(color);
                out.addVertex(d.x, d.y, d.z).setUv(u0, v0 + du).setColor(color);
            }
            if (out != null) buffers.endBatch(type);
        }
    }
}
