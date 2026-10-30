package net.austin.cabinmod.debug;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.CraftingBlock;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in capture of the Transient Diviner cinematic, compiled only by tools/visual_smoke/diviner_smoke.gradle. */
@EventBusSubscriber(modid = CabinMod.MOD_ID)
public final class DivinerVisualSmoke {
    static BlockPos diviner;

    private static boolean on() {
        return Boolean.getBoolean("cabinmod.divinerSmoke");
    }

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!on()) return;
        var level = event.getServer().overworld();
        int x = 240, z = 240;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        diviner = new BlockPos(x, y, z);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 8; dz++) {
            level.setBlock(diviner.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int dy = 0; dy < 6; dy++) level.setBlock(diviner.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(diviner, ModBlocks.CRAFTING_BLOCK.get().defaultBlockState().setValue(CraftingBlock.FACING, Direction.SOUTH), 3);
        if (level.getBlockEntity(diviner) instanceof CraftingBlockEntity be) {
            be.itemHandler.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 2));
            be.itemHandler.setStackInSlot(1, new ItemStack(ModItems.MATTHEW.get(), 3));
        }
        level.setDayTime(Boolean.getBoolean("cabinmod.divinerSmoke.night") ? 18000 : 5000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, event.getServer());
        level.setWeatherParameters(6000, 0, false, false);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!on() || !(event.getEntity() instanceof ServerPlayer p) || diviner == null) return;
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.getInventory().clearContent();
        p.teleportTo(p.serverLevel(), diviner.getX() + 0.5, diviner.getY(), diviner.getZ() + 3.5, 180, 25);
    }

    @EventBusSubscriber(modid = CabinMod.MOD_ID, value = Dist.CLIENT)
    public static class Client {
        static int ticks = -1, opened = -1, closed = -1;
        static final int[] INTRO = {2, 8, 14, 24, 34, 44, 52, 58, 64, 72, 80, 90, 98, 104, 112, 130};
        static final int[] RETURN = {3, 10, 18, 26, 34, 40, 42, 44, 46, 48, 50, 52, 54, 56, 58, 60, 62, 64, 66, 68, 70, 72, 76, 79, 80, 81, 82, 83, 85, 88, 92, 100, 108, 116, 124};

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            if (!on()) return;
            Minecraft mc = Minecraft.getInstance();
            mc.options.pauseOnLostFocus = false;
            if (mc.level == null || mc.player == null || diviner == null) return;
            ticks++;
            if (ticks == 100) shot(mc, "diviner-0-block");   // the animated overlay over the block, idle and crafting
            if (ticks == 120) {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(diviner), Direction.SOUTH, diviner, false));
                opened = ticks;
            }
            if (opened >= 0 && closed < 0) {
                int t = ticks - opened;
                for (int at : INTRO) if (t == at) shot(mc, String.format("diviner-a%03d", t));
                if (t == 64 || t == 90 || t == 130) planet(mc, String.format("diviner-p-a%03d", t));
                if (t == 150 && mc.player.containerMenu.containerId != 0) {
                    mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 37, 0, ClickType.PICKUP, mc.player);
                }
                if (t == 152 || t == 156 || t == 162) shot(mc, String.format("diviner-b%03d-launch", t));
                if (t == 145 || t == 170) shot(mc, String.format("diviner-b%03d-cabin", t));
                if (t == 175) {
                    mc.player.closeContainer();
                    closed = ticks;
                }
            }
            if (closed >= 0) {
                int r = ticks - closed;
                for (int at : RETURN) if (r == at) shot(mc, String.format("diviner-c%03d", r));
                if (r == 18 || r == 34 || r == 46) planet(mc, String.format("diviner-p-c%03d", r));
                if (r == 135) shot(mc, "diviner-d-landed");
                if (r == 136) heavensSeenByOthers(mc);
                if (r == 160 || r == 190) shot(mc, String.format("diviner-e%03d-heavens", r));
                if (r == 200) {
                    System.out.println("DIVINER_SMOKE done");
                    mc.stop();
                }
            }
        }

        /** Someone else (a stand-in id) goes up through the diviner: look up at the heavens opening over it. */
        private static void heavensSeenByOthers(Minecraft mc) {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            server.execute(() -> {
                // stand well back, as someone across the field would, and look up at it
                var level = server.overworld();
                int sx = diviner.getX(), sz = diviner.getZ() + 90;
                level.getChunk(sx >> 4, sz >> 4);
                BlockPos stand = new BlockPos(sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz), sz);
                level.setBlock(stand, Blocks.GLASS.defaultBlockState(), 3);   // in case it's water
                for (ServerPlayer p : server.getPlayerList().getPlayers())
                    p.teleportTo(level, sx + 0.5, stand.getY() + 1, sz + 0.5, 180, -55);
                if (server.overworld().getBlockEntity(diviner) instanceof CraftingBlockEntity be) {
                    be.viewers().add(java.util.UUID.nameUUIDFromBytes("divinerSmoke".getBytes()));
                    server.overworld().sendBlockUpdated(diviner, be.getBlockState(), be.getBlockState(), 3);
                }
            });
        }

        /** The space scene alone (the post chain's "planet" target): Blender planet, clouds, atmosphere, comet. */
        private static void planet(Minecraft mc, String name) {
            var target = net.austin.cabinmod.client.diviner.DivinerCinematic.debugTarget("planet");
            if (target != null) Screenshot.grab(mc.gameDirectory, name + ".png", target, c -> System.out.println("DIVINER_SMOKE " + c.getString()));
        }

        private static void shot(Minecraft mc, String name) {
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> System.out.println("DIVINER_SMOKE " + c.getString()));
        }
    }
}
