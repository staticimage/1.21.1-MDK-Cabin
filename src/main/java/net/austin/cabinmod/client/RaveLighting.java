package net.austin.cabinmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.RaveBlock;
import net.austin.cabinmod.entity.custom.ArenaLightEntity;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.worldgen.RaveArena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Frame-rate light show, independent of the server's damaging rays and block lighting.
 * Floor positions/visibility are cached per client tick; color, head motion and cone clipping
 * use partial ticks. Three concentric 48-sided cones share their ray hits with the floor pools.
 * Only arena-authored floor tiles are animated. Corrupted or covered tiles never get an overlay.
 */
@EventBusSubscriber(modid = CabinMod.MOD_ID, value = Dist.CLIENT)
public final class RaveLighting {
    private static final int SEGMENTS = 48;
    private static final double SPOT_RADIUS = 2.5;
    private static final double[] RINGS = {0, .34, .67, 1};
    private static final double[] COS = new double[SEGMENTS], SIN = new double[SEGMENTS];
    private static final Map<BlockPos, Floor> FLOORS = new HashMap<>();
    private static List<ArenaLightEntity> lights = List.of();
    private static ClientLevel lastLevel;

    static {
        for (int i = 0; i < SEGMENTS; i++) {
            COS[i] = Math.cos(i * Math.PI * 2 / SEGMENTS);
            SIN[i] = Math.sin(i * Math.PI * 2 / SEGMENTS);
        }
    }

    private static final class Floor {
        final List<BlockPos> positions = new ArrayList<>();
        final List<BlockPos> visible = new ArrayList<>();
        UUID owner;
        Floor(BlockPos center) {
            for (int x = -RaveArena.RADIUS; x <= RaveArena.RADIUS; x++)
                for (int z = -RaveArena.RADIUS; z <= RaveArena.RADIUS; z++)
                    positions.add(center.offset(x, -1, z));
        }
    }

    private RaveLighting() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel || mc.level == null) {
            FLOORS.clear(); lights = List.of(); lastLevel = mc.level;
        }
        if (mc.level == null || mc.player == null) return;
        AABB area = mc.player.getBoundingBox().inflate(96);
        lights = mc.level.getEntitiesOfClass(ArenaLightEntity.class, area);
        Map<BlockPos, UUID> anchors = new HashMap<>();
        for (var light : lights) anchors.put(light.arenaCenter(), light.ownerId());
        for (var boss : mc.level.getEntitiesOfClass(JehoashEntity.class, area))
            anchors.put(boss.arenaCenter(), boss.getUUID());
        FLOORS.keySet().retainAll(anchors.keySet());
        anchors.forEach((center, owner) -> {
            Floor floor = FLOORS.computeIfAbsent(center, Floor::new);
            floor.owner = owner;
            floor.visible.clear();
            for (BlockPos pos : floor.positions) {
                if (!mc.level.hasChunkAt(pos)) continue;
                var state = mc.level.getBlockState(pos);
                if (state.is(ModBlocks.RAVE_FLOOR.get()) && !state.getValue(RaveBlock.CORRUPTED)
                        && !mc.level.getBlockState(pos.above()).isSolidRender(mc.level, pos.above()))
                    floor.visible.add(pos);
            }
        });
    }

    public static int color(ArenaLightEntity light, float partial) {
        // Slot, rather than entity ID, keeps the palette stable after reload and on other clients.
        double time = light.level().getGameTime() + (double)partial;
        return rainbow(time * .003 + light.fixtureSlot() / 6.0);
    }

    private static int rainbow(double hue) {
        return Mth.hsvToRgb((float)(hue - Math.floor(hue)), .72f, 1);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != lastLevel) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = mc.level.getGameTime() + (double)partial;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack.Pose pose = new PoseStack().last();
        var buffers = mc.renderBuffers().bufferSource();
        var panels = buffers.getBuffer(RaveRenderTypes.FLOOR);
        for (var entry : FLOORS.entrySet()) {
            BlockPos center = entry.getKey();
            if (!event.getFrustum().isVisible(new AABB(Vec3.atLowerCornerOf(center.offset(-12, -1, -12)),
                    Vec3.atLowerCornerOf(center.offset(13, 1, 13))))) continue;
            Floor floor = entry.getValue();
            boolean playing = floor.owner != null && DiscoMusic.isPlaying(floor.owner);
            float beat = playing ? DiscoMusic.beats(floor.owner, 0) : 0;
            double pulse = playing ? Math.pow(.5 + .5 * Math.cos(beat * Math.PI * 2), 6) : 0;
            float brightness = (float)(.64 + pulse * (.12 + .16 * (playing ? DiscoMusic.energy(floor.owner) : 0)));
            for (BlockPos pos : floor.visible) {
                double x = pos.getX() - camera.x, z = pos.getZ() - camera.z, y = pos.getY() + 1.003 - camera.y;
                int rgb = rainbow(time * .0025 + (pos.getX() - center.getX()) * .045
                        + (pos.getZ() - center.getZ()) * .035);
                // Opaque dark surround prevents the old concrete colors from washing out the panels.
                floorQuad(panels, pose, x, y, z, .015, 0x0C1019, 1);
                floorQuad(panels, pose, x, y + .001, z, .075, rgb, brightness);
                floorQuad(panels, pose, x, y + .002, z, .13, rgb, brightness * .8f);
            }
        }
        buffers.endBatch(RaveRenderTypes.FLOOR);
        var glow = buffers.getBuffer(RaveRenderTypes.GLOW);
        for (var light : lights) {
            if (light.isRemoved() || light.laserPhase() != 0) continue;
            if (!event.getFrustum().isVisible(new AABB(light.position(), light.renderPoint(partial)).inflate(5))) continue;
            spotlight(glow, pose, light, partial, camera);
        }
        buffers.endBatch(RaveRenderTypes.GLOW);
    }

    private static void floorQuad(VertexConsumer out, PoseStack.Pose pose, double x, double y, double z,
                                  double inset, int rgb, float brightness) {
        vertex(out, pose, new Vec3(x + inset, y, z + inset), rgb, brightness, 1);
        vertex(out, pose, new Vec3(x + inset, y, z + 1 - inset), rgb, brightness, 1);
        vertex(out, pose, new Vec3(x + 1 - inset, y, z + 1 - inset), rgb, brightness, 1);
        vertex(out, pose, new Vec3(x + 1 - inset, y, z + inset), rgb, brightness, 1);
    }

    private static void spotlight(VertexConsumer out, PoseStack.Pose pose, ArenaLightEntity light, float partial, Vec3 camera) {
        Vec3 origin = light.renderOrigin(partial), direction = light.renderDirection(partial);
        if (direction.y >= -.05) return;
        double distance = (light.arenaCenter().getY() - .03 - origin.y) / direction.y;
        if (distance <= 0 || distance > 48) return;
        Vec3 target = origin.add(direction.scale(distance));
        Vec3 along = new Vec3(direction.x, 0, direction.z).normalize();
        if (along.lengthSqr() < .1) along = new Vec3(0, 0, 1);
        Vec3 across = new Vec3(along.z, 0, -along.x);
        Vec3 apertureUp = direction.cross(across).normalize();
        double stretch = Math.min(1.5, 1 / -direction.y);
        int rgb = color(light, partial);
        Vec3 source = origin.subtract(camera);
        BlockHitResult[] inner = new BlockHitResult[SEGMENTS];
        BlockHitResult middle = clip(light, origin, target);
        java.util.Arrays.fill(inner, middle);
        for (int ring = 1; ring < RINGS.length; ring++) {
            double radius = SPOT_RADIUS * RINGS[ring];
            BlockHitResult[] outer = new BlockHitResult[SEGMENTS];
            for (int i = 0; i < SEGMENTS; i++) {
                Vec3 end = target.add(across.scale(COS[i] * radius)).add(along.scale(SIN[i] * radius * stretch));
                outer[i] = clip(light, origin, end);
            }
            float shaftAlpha = ring == 1 ? .038f : ring == 2 ? .026f : .012f;
            for (int i = 0; i < SEGMENTS; i++) {
                int next = (i + 1) % SEGMENTS;
                Vec3 a = outer[i].getLocation().subtract(camera), b = outer[next].getLocation().subtract(camera);
                // A small aperture at the lens grows into the clipped cone shell. No depth writes.
                Vec3 apertureA = source.add(across.scale(COS[i] * .14)).add(apertureUp.scale(SIN[i] * .14));
                Vec3 apertureB = source.add(across.scale(COS[next] * .14)).add(apertureUp.scale(SIN[next] * .14));
                float alphaA = shaftAlpha * edgeFade(direction, across, apertureUp, source, a, i);
                float alphaB = shaftAlpha * edgeFade(direction, across, apertureUp, source, b, next);
                vertex(out, pose, apertureA, rgb, 1, alphaA * 2);
                vertex(out, pose, a, rgb, 1, alphaA);
                vertex(out, pose, b, rgb, 1, alphaB);
                vertex(out, pose, apertureB, rgb, 1, alphaB * 2);
                if (onFloor(light, inner[i]) && onFloor(light, inner[next]) && onFloor(light, outer[i]) && onFloor(light, outer[next])) {
                    float inside = falloff(RINGS[ring - 1]), outside = falloff(RINGS[ring]);
                    vertex(out, pose, inner[i].getLocation().subtract(camera).add(0, .009, 0), rgb, 1, inside);
                    vertex(out, pose, a.add(0, .009, 0), rgb, 1, outside);
                    vertex(out, pose, b.add(0, .009, 0), rgb, 1, outside);
                    vertex(out, pose, inner[next].getLocation().subtract(camera).add(0, .009, 0), rgb, 1, inside);
                }
            }
            inner = outer;
        }
    }

    private static float falloff(double radius) { return (float)(.48 * Math.pow(1 - radius * radius, 2)); }

    private static float edgeFade(Vec3 direction, Vec3 across, Vec3 up, Vec3 source, Vec3 end, int index) {
        Vec3 normal = across.scale(COS[index]).add(up.scale(SIN[index])).subtract(direction.scale(.18)).normalize();
        Vec3 view = source.lerp(end, .5).normalize();
        // Fade the silhouette continuously instead of showing three hard nested outlines.
        return (float)Math.pow(Math.abs(normal.dot(view)), .7);
    }

    private static BlockHitResult clip(ArenaLightEntity light, Vec3 from, Vec3 to) {
        return light.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, light));
    }

    private static boolean onFloor(ArenaLightEntity light, BlockHitResult hit) {
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
                && hit.getBlockPos().getY() == light.arenaCenter().getY() - 1
                && light.level().getBlockState(hit.getBlockPos()).getBlock() instanceof RaveBlock;
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, int rgb, float brightness, float alpha) {
        out.addVertex(pose, (float)p.x, (float)p.y, (float)p.z)
                .setColor((rgb >> 16 & 255) / 255f * brightness, (rgb >> 8 & 255) / 255f * brightness,
                        (rgb & 255) / 255f * brightness, alpha);
    }
}
