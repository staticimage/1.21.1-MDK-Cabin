package net.austin.cabinmod.debug;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.RaveLighting;
import net.austin.cabinmod.entity.custom.ArenaLightEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Opt-in, disposable-world visual checks and effects-off/on frame-time comparison.
 * Run with -I tools/visual_smoke/smoke.gradle -PraveLightReview runClient.
 */
@EventBusSubscriber(modid = CabinMod.MOD_ID, value = Dist.CLIENT)
public final class RaveLightVisualSmoke {
    private static int ticks, smoothFrames;
    private static long previousFrame, previousTick;
    private static Vec3 previousAim, pausedAim;
    private static final java.util.Map<String, java.util.List<Double>> TIMES = new java.util.LinkedHashMap<>();

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("cabinmod.visualSmoke.lightReview")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();
        if (ticks == 1) {
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            mc.options.graphicsMode().set(GraphicsStatus.FANCY);
            mc.levelRenderer.allChanged();
            NeoForge.EVENT_BUS.unregister(RaveLighting.class);
        }
        if (ticks == 20) server.execute(() -> {
            var level = server.overworld();
            var area = new net.minecraft.world.phys.AABB(new BlockPos(0, 80, 0)).inflate(40);
            for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area)) {
                if (entity == RaveVisualSmoke.boss || entity instanceof net.minecraft.server.level.ServerPlayer) continue;
                if (entity instanceof net.austin.cabinmod.entity.custom.ClipMob || entity instanceof ArenaLightEntity
                        || entity instanceof net.austin.cabinmod.entity.custom.GlassShardEntity
                        || entity instanceof net.austin.cabinmod.entity.custom.FallingStageRigEntity) entity.discard();
            }
            net.austin.cabinmod.worldgen.RaveArena.installLights(level, RaveVisualSmoke.boss);
            for (var player : server.getPlayerList().getPlayers()) {
                player.removeAllEffects();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(level, .5, 83, 10, 180, 16);
            }
        });
        if (ticks == 120) NeoForge.EVENT_BUS.register(RaveLighting.class);
        if (ticks == 300) {
            mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);
            mc.levelRenderer.allChanged();
        }
        if (ticks == 420 || ticks == 480) {
            boolean wall = ticks == 420;
            server.execute(() -> {
                for (int x = -2; x <= 2; x++) for (int y = 80; y <= 85; y++)
                    server.overworld().setBlock(new BlockPos(x, y, 3), (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            });
        }
        if (ticks == 180 || ticks == 380 || ticks == 460) {
            String name = ticks == 180 ? "fancy" : ticks == 380 ? "fabulous" : "occluded";
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "lighting-" + name + ".png", mc.getMainRenderTarget(), c -> {});
        }
        if (ticks == 500) mc.pauseGame(false);
        if (ticks == 515) pausedAim = firstLight().renderPoint(mc.getTimer().getGameTimeDeltaPartialTick(false));
        if (ticks == 535) {
            Vec3 aim = firstLight().renderPoint(mc.getTimer().getGameTimeDeltaPartialTick(false));
            if (!mc.isPaused() || aim.distanceTo(pausedAim) > 1e-9) throw new AssertionError("Light clock advanced during pause");
            System.out.println("LIGHTING_SMOKE PASS pause freezes aim");
            mc.setScreen(null);
        }
        if (ticks == 545) server.execute(() -> {
            for (var player : server.getPlayerList().getPlayers()) player.teleportTo(server.overworld(), .5, 83.3, -5, 180, 0);
        });
        if (ticks == 565) net.minecraft.client.Screenshot.grab(mc.gameDirectory, "lighting-jehoash-face.png", mc.getMainRenderTarget(), c -> {});
        if (ticks == 580) {
            if (smoothFrames < 30) throw new AssertionError("No frame-rate aim interpolation: " + smoothFrames);
            System.out.println("LIGHTING_SMOKE PASS " + smoothFrames + " moving frames between game ticks");
            TIMES.forEach((name, values) -> {
                values.sort(Double::compareTo);
                System.out.printf(java.util.Locale.ROOT, "LIGHTING_TIMES %s frames=%d median=%.2fms p95=%.2fms%n",
                        name, values.size(), values.get(values.size() / 2), values.get((int)(values.size() * .95)));
            });
            mc.stop();
        }
    }

    private static ArenaLightEntity firstLight() {
        var mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(ArenaLightEntity.class, mc.player.getBoundingBox().inflate(40)).getFirst();
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!Boolean.getBoolean("cabinmod.visualSmoke.lightReview") || ticks < 10
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        long now = System.nanoTime();
        String bucket = ticks >= 40 && ticks < 110 ? "effects-off" : ticks >= 160 && ticks < 280 ? "fancy"
                : ticks >= 340 && ticks < 410 ? "fabulous" : null;
        if (bucket != null && previousFrame != 0) TIMES.computeIfAbsent(bucket, k -> new java.util.ArrayList<>()).add((now - previousFrame) / 1e6);
        previousFrame = now;
        if (ticks >= 160 && ticks < 280) {
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            var light = firstLight();
            Vec3 aim = light.renderPoint(partial);
            if (previousAim != null && previousTick == mc.level.getGameTime() && previousAim.distanceTo(aim) > 1e-7) smoothFrames++;
            previousAim = aim; previousTick = mc.level.getGameTime();
            Vec3 ray = aim.subtract(light.renderOrigin(partial)).normalize();
            if (ray.dot(light.renderDirection(partial)) < .999999) throw new AssertionError("Head and cone disagree");
        }
    }
}
