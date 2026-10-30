package net.austin.cabinmod.debug;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.CraftingBlock;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.client.diviner.DivinerCinematic;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.stream.Collectors;

/**
 * Live two-player Transient Diviner scenario (tools/visual_smoke/diviner_mp.gradle): a dedicated server directs
 * Alice and Bob on real clients and logs the diviner's stars and viewers; each client screenshots its own view.
 *   A  Alice goes up alone, talks with the Cabin and launches a result; Bob watches from the ground: her trance,
 *      the beam, the heavens opening, the launch streak, her coloured star falling and landing (and he should
 *      hear the Cabin's hums).
 *   B  both go up together; both launch; Bob leaves first: only Bob's star may fall, Alice's waits for her.
 * Times are game ticks after START, which the server announces in chat once both have joined.
 */
@EventBusSubscriber(modid = CabinMod.MOD_ID)
public final class DivinerMultiplayerSmoke {
    static final int PORT = 25599;
    private static BlockPos diviner;
    private static long start = -1;
    private static String expectB = "";

    private static String role() {
        return System.getProperty("cabinmod.divinerMp", "");
    }

    // ------------------------------------------------------------------ server: stage, direct, check

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        if (!role().equals("server")) return;
        ServerLevel level = event.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX(), z = spawn.getZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        diviner = new BlockPos(x, y, z);
        for (int dx = -8; dx <= 12; dx++) for (int dz = -8; dz <= 30; dz++) {
            level.setBlock(diviner.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int dy = 0; dy < 8; dy++) level.setBlock(diviner.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(diviner, ModBlocks.CRAFTING_BLOCK.get().defaultBlockState().setValue(CraftingBlock.FACING, Direction.SOUTH), 3);
        refill(level);
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, event.getServer());
        level.setWeatherParameters(6000, 0, false, false);
        System.out.println("DIVINER_MP server ready, diviner at " + diviner);
    }

    private static void refill(ServerLevel level) {
        if (level.getBlockEntity(diviner) instanceof CraftingBlockEntity be) {
            be.itemHandler.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 2));
            be.itemHandler.setStackInSlot(1, new ItemStack(ModItems.MATTHEW.get(), 3));
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!role().equals("server") || diviner == null) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        ServerPlayer alice = server.getPlayerList().getPlayerByName("Alice"), bob = server.getPlayerList().getPlayerByName("Bob");
        long now = level.getGameTime();
        if (start < 0) {
            if (alice == null || bob == null) return;
            start = now + 100;   // give both clients time to load the area
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.setGameMode(GameType.SURVIVAL);
                p.getInventory().clearContent();
            }
            place(alice, 0, 3, null);
            place(bob, 7, 11, null);
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "DIVINER_MP START " + start + " " + diviner.getX() + " " + diviner.getY() + " " + diviner.getZ()), false);
            return;
        }
        long t = now - start;
        if (alice == null || bob == null) {
            if (t >= 700) {   // the clients finish on their own
                System.out.println("DIVINER_MP RESULT " + expectB);
                server.halt(false);
            } else if (t > 0 && t % 100 == 0) {
                System.out.println("DIVINER_MP waiting: a player left");
            }
            return;
        }
        // --- A: Alice alone; Bob watches
        if (t == 0) {
            place(alice, 0, 3, null);
            place(bob, 5, 6, alice);                          // close, from the side: Alice in her trance
        }
        if (t == 150) place(bob, 0, 40, null, 180, -72);    // stand back to see the sky open
        if (t == 190) refill(level);
        if (t == 245) placeLooking(bob, 10, 22, diviner.above(10));   // watch the star come down
        // --- B: both up together
        if (t == 400) {
            place(alice, -1, 3, null);
            place(bob, 2, 3, null);
            refill(level);
        }
        if (t == 525 || t == 545) refill(level);
        if (t == 590) {
            expectB = check(level, alice, bob);
            System.out.println("DIVINER_MP CHECK " + expectB);
        }
        if (t >= 0 && t % 20 == 0) log(level, t, alice, bob);
        if (t == 800) {
            System.out.println("DIVINER_MP RESULT " + expectB);
            server.halt(false);
        }
    }

    /** After Bob (but not Alice) has left the heavens in B. */
    private static String check(ServerLevel level, ServerPlayer alice, ServerPlayer bob) {
        if (!(level.getBlockEntity(diviner) instanceof CraftingBlockEntity be)) return "FAIL no diviner";
        StringBuilder sb = new StringBuilder();
        boolean ok = true;
        boolean bobFalling = be.stars().stream().anyMatch(s -> s.owner.equals(bob.getUUID()) && s.falling());
        boolean aliceWaiting = be.stars().stream().anyMatch(s -> s.owner.equals(alice.getUUID()) && !s.falling());
        boolean aliceUp = be.viewers().contains(alice.getUUID()), bobUp = be.viewers().contains(bob.getUUID());
        ok &= bobFalling;
        sb.append(bobFalling ? "PASS bob's star falls when he leaves; " : "FAIL bob's star isn't falling; ");
        ok &= aliceWaiting;
        sb.append(aliceWaiting ? "PASS alice's star waits for her; " : "FAIL alice's star isn't waiting; ");
        ok &= aliceUp && !bobUp;
        sb.append(aliceUp && !bobUp ? "PASS viewers=[alice]" : "FAIL viewers alice=" + aliceUp + " bob=" + bobUp);
        return (ok ? "ALL PASS: " : "SOME FAIL: ") + sb;
    }

    private static void log(ServerLevel level, long t, ServerPlayer alice, ServerPlayer bob) {
        if (!(level.getBlockEntity(diviner) instanceof CraftingBlockEntity be)) return;
        long now = level.getGameTime();
        String stars = be.stars().stream().map(s -> "#" + s.id + ":" + (s.owner.equals(alice.getUUID()) ? "alice" : s.owner.equals(bob.getUUID()) ? "bob" : "?")
                + ":" + s.result.getCount() + "x" + s.result.getItem() + (s.falling() ? ":lands+" + (s.landTick - now) : ":waiting"))
                .collect(Collectors.joining(", "));
        String viewers = be.viewers().stream().map(id -> id.equals(alice.getUUID()) ? "alice" : id.equals(bob.getUUID()) ? "bob" : "?")
                .collect(Collectors.joining(","));
        System.out.println("DIVINER_MP t=" + t + " viewers=[" + viewers + "] stars=[" + stars + "]");
    }

    private static void place(ServerPlayer p, int dx, int dz, ServerPlayer lookAt) {
        Vec3 at = stand(p.serverLevel(), dx, dz);
        Vec3 target = lookAt != null ? lookAt.position().add(0, 1.2, 0) : Vec3.atCenterOf(diviner);
        Vec3 d = target.subtract(at.add(0, 1.62, 0)).normalize();
        place(p, dx, dz, lookAt, (float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) Math.toDegrees(Math.asin(-d.y)));
    }

    private static void placeLooking(ServerPlayer p, int dx, int dz, BlockPos target) {
        Vec3 at = stand(p.serverLevel(), dx, dz);
        Vec3 d = Vec3.atCenterOf(target).subtract(at.add(0, 1.62, 0)).normalize();
        p.teleportTo(p.serverLevel(), at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) Math.toDegrees(Math.asin(-d.y)));
    }

    private static void place(ServerPlayer p, int dx, int dz, ServerPlayer unused, float yaw, float pitch) {
        Vec3 at = stand(p.serverLevel(), dx, dz);
        p.teleportTo(p.serverLevel(), at.x, at.y, at.z, yaw, pitch);
    }

    /** Somewhere dry to stand dx, dz from the diviner (a glass block on top of any water). */
    private static Vec3 stand(ServerLevel level, int dx, int dz) {
        int x = diviner.getX() + dx, z = diviner.getZ() + dz;
        level.getChunk(x >> 4, z >> 4);
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        if (!level.getFluidState(top.below()).isEmpty()) level.setBlock(top, Blocks.GLASS.defaultBlockState(), 3);
        else top = top.below();
        return Vec3.atBottomCenterOf(top.above());
    }

    // ------------------------------------------------------------------ clients: act and capture

    @EventBusSubscriber(modid = CabinMod.MOD_ID, value = Dist.CLIENT)
    public static class Client {
        static long clientStart = -1, lastT = -1;
        static BlockPos at;
        static int humsHeard;
        static int closedAt = -1;

        private static boolean alice() {
            return role().equals("alice");
        }

        @SubscribeEvent
        public static void chat(ClientChatReceivedEvent event) {
            String text = event.getMessage().getString();
            if (!text.startsWith("DIVINER_MP START")) return;
            String[] p = text.split(" ");
            clientStart = Long.parseLong(p[2]);
            at = new BlockPos(Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]));
            System.out.println("DIVINER_MP " + role() + " got start " + clientStart);
        }

        @SubscribeEvent
        public static void sound(PlaySoundEvent event) {
            if (event.getSound() != null && event.getSound().getLocation().getPath().equals("diviner_cabin_hum")) humsHeard++;
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            String role = role();
            if (!role.equals("alice") && !role.equals("bob")) return;
            Minecraft mc = Minecraft.getInstance();
            mc.options.pauseOnLostFocus = false;
            if (mc.level == null || mc.player == null || clientStart < 0) return;
            long t = mc.level.getGameTime() - clientStart;
            if (t == lastT) return;
            lastT = t;
            if (alice()) aliceTick(mc, (int) t);
            else bobTick(mc, (int) t);
            if (t == 790) {
                System.out.println("DIVINER_MP " + role + " done, hums heard " + humsHeard);
                mc.stop();
            }
        }

        private static void aliceTick(Minecraft mc, int t) {
            // A: alone
            if (t == 40) open(mc);
            if (t == 170) shot(mc, "alice-a170-cabin");
            if (t == 200) launch(mc);
            if (t == 230) close(mc);
            int r = t - 230;
            if (r >= 0 && r <= 80 && (r % 4 == 0 || (r >= 44 && r <= 70 && r % 2 == 0))) shot(mc, String.format("alice-a-ret%03d", r));
            // B: together
            if (t == 420) open(mc);
            if (t == 530) launch(mc);
            if (t == 600) shot(mc, "alice-b600-still-up");
            if (t == 640) close(mc);
        }

        private static void bobTick(Minecraft mc, int t) {
            // A: watching Alice
            if (t == 20) shot(mc, "bob-a020-before");
            if (t == 90 || t == 130) shot(mc, String.format("bob-a%03d-trance", t));
            if (t == 180 || t == 199 || t == 203 || t == 207 || t == 212) shot(mc, String.format("bob-a%03d-sky", t));
            if (t >= 280 && t <= 330 && t % 5 == 0) shot(mc, String.format("bob-a%03d-fall", t));
            if (t == 360) shot(mc, "bob-a360-after");
            if (t == 400) System.out.println("DIVINER_MP bob heard " + humsHeard + " cabin hums while alice was up");
            // B: together
            if (t == 440) open(mc);
            if (t == 520) shot(mc, "bob-b520-both-up");
            if (t == 550) launch(mc);
            if (t == 580) close(mc);
            int r = t - 580;
            if (r > 0 && r <= 100 && r % 10 == 0) shot(mc, String.format("bob-b-ret%03d", r));
            if (t == 700 || t == 720 || t == 740) {
                mc.player.setXRot(-30f);
                shot(mc, String.format("bob-b%03d-alice-star", t));
            }
        }

        private static void open(Minecraft mc) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        }

        private static void launch(Minecraft mc) {
            if (mc.player.containerMenu.containerId != 0)
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 37, 0, ClickType.PICKUP, mc.player);
        }

        private static void close(Minecraft mc) {
            mc.player.closeContainer();
        }

        private static void shot(Minecraft mc, String name) {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                    c -> System.out.println("DIVINER_MP " + role() + " " + c.getString() + " active=" + DivinerCinematic.isActive()));
        }
    }
}
