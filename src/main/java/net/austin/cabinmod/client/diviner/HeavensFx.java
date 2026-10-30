package net.austin.cabinmod.client.diviner;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What everyone else sees while a player is up in the heavens through a diviner (CraftingBlockEntity#viewers):
 * the player floats in a trance over the ground with motes spiralling up around them, a faint beam of light
 * climbs from the diviner, and far above it the sky opens onto deep space with the Cabin in Agartha in it,
 * small and very far away. The Cabin's hums reach them from the diviner (DivinerClientHooks#onCabinHum).
 */
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HeavensFx {
    static final float RIFT_HEIGHT = 140f;
    private static final float RIFT_SIZE = 70f;
    private static final int OPEN_TICKS = 40;

    private static final Map<BlockPos, Rift> RIFTS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<RemoteHum> HUMS = new ArrayList<>();

    /** The Cabin speaking to someone else at a diviner: `words` hums, one every few ticks. */
    private static final class RemoteHum {
        final BlockPos pos;
        final RandomSource random;
        int words, wait;

        RemoteHum(BlockPos pos, int words, int seed) {
            this.pos = pos;
            this.words = words;
            this.random = RandomSource.create(seed);
        }
    }

    private static final class Rift {
        final BlockPos pos;
        final Set<UUID> viewers = new HashSet<>();
        long lastSeen;
        float open, prevOpen, voice;
        boolean announced;
        StarColors.StarPalette palette = StarColors.DEFAULT;

        Rift(BlockPos pos) {
            this.pos = pos;
        }
    }

    private HeavensFx() {
    }

    static void track(CraftingBlockEntity diviner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Rift rift = RIFTS.get(diviner.getBlockPos());
        if (rift == null) {
            if (diviner.viewers().isEmpty()) return;
            RIFTS.put(diviner.getBlockPos().immutable(), rift = new Rift(diviner.getBlockPos().immutable()));
        }
        rift.viewers.clear();
        rift.viewers.addAll(diviner.viewers());
        rift.lastSeen = mc.level.getGameTime();
        rift.palette = StarColors.forRecipeInput(mc.level, diviner.itemHandler.getStackInSlot(0));
    }

    /** True while this player is up in the heavens through some diviner, as other players see them. */
    public static boolean inTrance(Player player) {
        if (RIFTS.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && DivinerCinematic.isActive()) return false;
        for (Rift rift : RIFTS.values()) if (rift.viewers.contains(player.getUUID()) && rift.open > 0.05f) return true;
        return false;
    }

    /** The Cabin speaks at this diviner: the image in the sky brightens a little. */
    static void voice(BlockPos pos) {
        Rift rift = RIFTS.get(pos);
        if (rift != null) rift.voice = 1f;
    }

    static void remoteHum(BlockPos pos, int words, int seed, UUID listener) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getUUID().equals(listener)) return;   // they hear him up close already
        HUMS.removeIf(h -> h.pos.equals(pos));
        HUMS.add(new RemoteHum(pos.immutable(), Math.min(words, 40), seed));
        voice(pos);
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        RIFTS.clear();
        HUMS.clear();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (Iterator<RemoteHum> it = HUMS.iterator(); it.hasNext(); ) {
            RemoteHum hum = it.next();
            if (--hum.wait > 0) continue;
            // low and faint from the diviner, as if from very far away; about the typewriter's pace
            mc.level.playLocalSound(hum.pos.getX() + 0.5, hum.pos.getY() + 1.2, hum.pos.getZ() + 0.5, ModSounds.DIVINER_CABIN_HUM.get(),
                    SoundSource.NEUTRAL, 0.35f, 0.82f + hum.random.nextFloat() * 0.22f, false);
            hum.wait = 2 + hum.random.nextInt(3);
            if (--hum.words <= 0) it.remove();
        }
        if (RIFTS.isEmpty()) return;
        long now = mc.level.getGameTime();
        for (Iterator<Rift> it = RIFTS.values().iterator(); it.hasNext(); ) {
            Rift rift = it.next();
            boolean stale = now - rift.lastSeen > 40;   // the diviner unloaded or was broken
            boolean wanted = !stale && !rift.viewers.isEmpty();
            rift.prevOpen = rift.open;
            rift.open = Mth.clamp(rift.open + (wanted ? 1f : -1.5f) / OPEN_TICKS, 0f, 1f);
            rift.voice = Math.max(0f, rift.voice - 0.04f);
            if (wanted && !rift.announced && rift.open > 0.1f) {
                rift.announced = true;
                mc.level.playLocalSound(rift.pos.getX() + 0.5, rift.pos.getY() + 12, rift.pos.getZ() + 0.5,
                        ModSounds.DIVINER_HEAVENS_OPEN.get(), SoundSource.AMBIENT, 4f, 1f, false);
            }
            if (!wanted && rift.open <= 0) {
                if (stale || rift.viewers.isEmpty()) it.remove();
                continue;
            }
            if (rift.viewers.isEmpty()) rift.announced = false;
            // motes spiralling up around everyone in the trance, and up the beam
            for (UUID id : rift.viewers) {
                Player p = mc.level.getPlayerByUUID(id);
                if (p == null || (p == mc.player && DivinerCinematic.isActive())) continue;
                double ang = (now * 0.35 + id.hashCode()) % (Math.PI * 2);
                Vec3 at = p.position().add(Math.cos(ang) * 0.7, 0.2 + RANDOM.nextDouble() * 1.6 + 0.4 * rift.open, Math.sin(ang) * 0.7);
                DivinerFx.mote(at, new Vec3(-Math.sin(ang) * 0.03, 0.06, Math.cos(ang) * 0.03), DivinerFx.mixWhite(rift.palette.core(), 0.4f));
            }
            if (now % 2 == 0) {
                Vec3 base = Vec3.atCenterOf(rift.pos).add(0, 0.6, 0);
                DivinerFx.mote(base.add((RANDOM.nextDouble() - 0.5) * 0.6, RANDOM.nextDouble() * 6, (RANDOM.nextDouble() - 0.5) * 0.6),
                        new Vec3(0, 0.25 + RANDOM.nextDouble() * 0.2, 0), rift.palette.core());
            }
        }
    }

    // ------------------------------------------------------------------ the trance (pose: client/mixin/HumanoidModelMixin)

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        float open = tranceAmount(player, event.getPartialTick());
        if (open <= 0) return;
        float t = (player.tickCount + event.getPartialTick()) / 20f;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0, open * (0.45 + 0.08 * Mth.sin(t * 1.6f)), 0);
        pushed.add(player.getId());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (pushed.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    private static final Set<Integer> pushed = new HashSet<>();

    /** 0..1 how far into the trance this player is (follows the rift opening). */
    public static float tranceAmount(Player player, float partial) {
        if (!inTrance(player)) return 0f;
        float best = 0;
        for (Rift rift : RIFTS.values()) {
            if (rift.viewers.contains(player.getUUID())) best = Math.max(best, Mth.lerp(partial, rift.prevOpen, rift.open));
        }
        return best * best * (3 - 2 * best);
    }

    // ------------------------------------------------------------------ beam + sky

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RIFTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || DivinerShaders.trail == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = (float) ((mc.level.getGameTime() + partial) % 24000) / 20f;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean inSpace = DivinerCinematic.isActive();
        for (Rift rift : RIFTS.values()) {
            float open = Mth.lerp(partial, rift.prevOpen, rift.open);
            if (open <= 0.001f) continue;
            DivinerFx.starUniforms(rift.palette);
            DivinerShaders.trail.safeGetUniform("iTime").set(time);
            Vec3 base = Vec3.atCenterOf(rift.pos).add(0, 0.5, 0).subtract(cam);
            Vector3f b = new Vector3f((float) base.x, (float) base.y, (float) base.z);
            VertexConsumer beam = buffers.getBuffer(DivinerRenderTypes.TRAIL);
            float pulse = 0.85f + 0.15f * Mth.sin(time * 2.2f);
            DivinerFx.pillar(beam, b, RIFT_HEIGHT * open, 0.12f + 0.1f * open, 0.45f * open * pulse);
            DivinerFx.pillar(beam, b, RIFT_HEIGHT * 0.5f * open, 0.6f * open, 0.12f * open);
            buffers.endBatch(DivinerRenderTypes.TRAIL);

            // the crafter themselves is looking at the real heavens; their sky has no hole in it
            if (inSpace || DivinerShaders.rift == null) continue;
            var s = DivinerShaders.rift;
            float[] art = DivinerCinematic.ART_RECT;
            s.safeGetUniform("ArtRect").set(art[0], art[1], art[2], art[3]);
            s.safeGetUniform("Open").set(open);
            s.safeGetUniform("Voice").set(rift.voice);
            s.safeGetUniform("iTime").set(time);
            float[] c = rift.palette.core();
            s.safeGetUniform("StarCore").set(c[0], c[1], c[2]);
            // a disc facing the camera, centred high above the diviner (big enough to read from far away)
            Vec3 centre = Vec3.atCenterOf(rift.pos).add(0, RIFT_HEIGHT, 0).subtract(cam);
            Vector3f ctr = new Vector3f((float) centre.x, (float) centre.y, (float) centre.z);
            Vector3f toCam = new Vector3f(ctr).negate().normalize();
            Vector3f right = new Vector3f(0, 1, 0).cross(toCam);
            if (right.lengthSquared() < 1e-4f) right.set(1, 0, 0);
            right.normalize().mul(RIFT_SIZE);
            Vector3f up = new Vector3f(toCam).cross(right).normalize().mul(RIFT_SIZE);
            Vector3f p0 = new Vector3f(ctr).sub(right).sub(up), p1 = new Vector3f(ctr).add(right).sub(up);
            Vector3f p2 = new Vector3f(ctr).add(right).add(up), p3 = new Vector3f(ctr).sub(right).add(up);
            VertexConsumer out = buffers.getBuffer(DivinerRenderTypes.RIFT);
            int white = 0xFFFFFFFF;
            out.addVertex(p0.x, p0.y, p0.z).setUv(0, 0).setColor(white);
            out.addVertex(p1.x, p1.y, p1.z).setUv(1, 0).setColor(white);
            out.addVertex(p2.x, p2.y, p2.z).setUv(1, 1).setColor(white);
            out.addVertex(p0.x, p0.y, p0.z).setUv(0, 0).setColor(white);
            out.addVertex(p2.x, p2.y, p2.z).setUv(1, 1).setColor(white);
            out.addVertex(p3.x, p3.y, p3.z).setUv(0, 1).setColor(white);
            buffers.endBatch(DivinerRenderTypes.RIFT);
        }
        DivinerFx.starUniforms(StarColors.DEFAULT);
    }
}
