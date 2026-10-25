package net.austin.cabinmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.entity.custom.ArenaLightEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Fixed arena-fixture lasers, drawn with additive "lightning" quads (glows, no texture).
// Charging: a tracking rainbow aim line from the fixture, then a short locked dodge window. A ring focuses on the
// fixture. Firing: white-hot core in a magenta glow, wrapped by rainbow ribbons spiralling
// down the beam, fading over LASER_BEAM_TICKS, with expanding rings and a spark burst where it hits.
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LaserBeamRenderer {
    private static final int RIBBONS = 5;
    private static final float CHARGE_TICKS = 28f;
    private static final Map<Integer, Seen> SEEN = new HashMap<>();
    private static ClientLevel lastLevel;

    // Server and client tick counters differ, so each shot is timed from when this client first sees it.
    private record Seen(byte phase, int laserTick, long nanos) {
    }

    private LaserBeamRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SEEN.clear();
            lastLevel = null;
            return;
        }
        if (mc.level != lastLevel) {
            SEEN.clear();
            lastLevel = mc.level;
        }
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<ArenaLightEntity> active = mc.level.getEntitiesOfClass(ArenaLightEntity.class,
                new AABB(BlockPos.containing(cam)).inflate(96), j -> true);
        SEEN.keySet().removeIf(id -> active.stream().noneMatch(j -> j.getId() == id));
        if (active.isEmpty()) return;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = new PoseStack().last();   // the level's model-view (camera rotation) is already applied
        long now = System.nanoTime();
        for (ArenaLightEntity j : active) {
            byte phase = j.laserPhase();
            if(phase==3 && j.tickCount%6>1) continue;
            Seen seen = SEEN.get(j.getId());
            boolean fresh = seen == null || seen.phase() != phase || seen.laserTick() != j.laserTick();
            if (fresh) {
                seen = new Seen(phase, j.laserTick(), now);
                SEEN.put(j.getId(), seen);
                if (phase == JehoashEntity.LASER_FIRING) impactBurst(mc.level, j.laserPoint());
            }
            float age = (now - seen.nanos()) / 1e9f * 20f;
            Vec3 from = j.laserOrigin(pt).subtract(cam);
            Vec3 to = j.laserPoint().subtract(cam);
            if (phase == JehoashEntity.LASER_CHARGING) drawCharge(vc, pose, from, to, age);
            else if (phase == JehoashEntity.LASER_FIRING) drawBeam(vc, pose, from, to, age);
            else {
                float[] c = rainbow((j.tickCount+pt)*.008f + j.getId()*.17f);
                ribbon(vc,pose,from,to,.035f,c[0],c[1],c[2],.18f);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void drawCharge(VertexConsumer vc, PoseStack.Pose pose, Vec3 from, Vec3 to, float age) {
        float charge = Mth.clamp(age / CHARGE_TICKS, 0f, 1f);
        float flicker = 0.35f + 0.35f * Mth.sin(age * 1.7f) * Mth.sin(age * 0.9f + 1f);
        float[] c = rainbow(age * 0.05f);
        // dashed aim line, more solid as the charge builds
        Vec3 d = to.subtract(from);
        int dashes = Math.max(6, (int) (d.length() * 2));
        for (int i = 0; i < dashes; i++) {
            if ((i + (int) (age * 0.8f)) % 3 == 0 && charge < 0.85f) continue;
            Vec3 a = from.add(d.scale(i / (double) dashes)), b = from.add(d.scale((i + 0.6) / dashes));
            ribbon(vc, pose, a, b, 0.025f + 0.03f * charge, c[0], c[1], c[2], (0.25f + 0.5f * charge) * flicker + 0.15f);
        }
        // a ring of light collapsing onto the disco ball
        Vec3 axis = d.normalize();
        float r = Mth.lerp(charge, 1.4f, 0.25f);
        ring(vc, pose, from, axis, r, 0.05f + 0.05f * charge, 1f, 0.3f, 0.85f, 0.55f + 0.4f * charge);
        ring(vc, pose, from, new Vec3(0, 1, 0), r * 0.8f, 0.04f, 0.3f, 0.95f, 1f, 0.4f * charge);
    }

    private static void drawBeam(VertexConsumer vc, PoseStack.Pose pose, Vec3 from, Vec3 to, float age) {
        float life = 1f - age / JehoashEntity.LASER_BEAM_TICKS;
        if (life <= 0f) return;
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1e-3) return;
        Vec3 n = dir.scale(1 / len);
        Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize(), v = n.cross(u).normalize();

        float swell = 1f + 0.35f * (float) Math.exp(-age * 0.6f);         // fat on the first frames
        ribbon(vc, pose, from, to, (0.55f * life + 0.15f) * swell, 1f, 0.2f, 0.85f, 0.22f * life);
        ribbon(vc, pose, from, to, 0.16f * swell, 1f, 1f, 1f, 0.95f * life);

        int segs = Math.max(10, (int) (len * 3));
        for (int k = 0; k < RIBBONS; k++) {
            float[] c = rainbow(k / (float) RIBBONS + age * 0.06f);
            double radius = (0.2 + 0.07 * Math.sin(age * 0.8 + k)) * swell;
            Vec3 prev = null;
            for (int s = 0; s <= segs; s++) {
                double t = s / (double) segs;
                double ang = age * 0.6 + k * (Math.PI * 2 / RIBBONS) + t * len * 1.6;
                Vec3 p = from.add(dir.scale(t)).add(u.scale(Math.cos(ang) * radius)).add(v.scale(Math.sin(ang) * radius));
                if (prev != null) ribbon(vc, pose, prev, p, 0.055f, c[0], c[1], c[2], 0.8f * life);
                prev = p;
            }
        }
        for (int i = 0; i < 3; i++) {
            float grow = age * (0.14f + 0.05f * i);
            float[] c = rainbow(0.2f * i + age * 0.04f);
            ring(vc, pose, to, n, 0.25f + grow, 0.07f, c[0], c[1], c[2], 0.85f * life);
        }
    }

    private static void impactBurst(ClientLevel level, Vec3 at) {
        RandomSource r = level.random;
        for (int i = 0; i < 40; i++) {
            float[] c = rainbow(r.nextFloat());
            double vx = r.nextGaussian() * 0.25, vy = r.nextGaussian() * 0.25 + 0.1, vz = r.nextGaussian() * 0.25;
            if (i % 3 == 0) level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, vx, vy, vz);
            else if (i % 3 == 1) level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, vx * 4, vy * 4, vz * 4);
            else level.addParticle(new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.4f), at.x, at.y, at.z, vx, vy, vz);
        }
    }

    // Camera-facing quad from a to b (camera at the origin), drawn double-sided.
    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float width,
                               float r, float g, float bl, float alpha) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = b.subtract(a).cross(mid);
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(width / 2);
        quad(vc, pose, a.add(side), a.subtract(side), b.subtract(side), b.add(side), r, g, bl, alpha);
    }

    private static void ring(VertexConsumer vc, PoseStack.Pose pose, Vec3 centre, Vec3 normal, float radius, float thickness,
                             float r, float g, float bl, float alpha) {
        Vec3 ref = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = normal.cross(ref).normalize(), v = normal.cross(u).normalize();
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            quad(vc, pose, centre.add(d0.scale(radius - thickness)), centre.add(d0.scale(radius + thickness)),
                    centre.add(d1.scale(radius + thickness)), centre.add(d1.scale(radius - thickness)), r, g, bl, alpha);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                             float r, float g, float b, float a) {
        vertex(vc, pose, p0, r, g, b, a);
        vertex(vc, pose, p1, r, g, b, a);
        vertex(vc, pose, p2, r, g, b, a);
        vertex(vc, pose, p3, r, g, b, a);
        vertex(vc, pose, p3, r, g, b, a);
        vertex(vc, pose, p2, r, g, b, a);
        vertex(vc, pose, p1, r, g, b, a);
        vertex(vc, pose, p0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
    }

    private static float[] rainbow(float hue) {
        int rgb = Mth.hsvToRgb(((hue % 1f) + 1f) % 1f, 0.85f, 1f);
        return new float[]{(rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f};
    }
}
