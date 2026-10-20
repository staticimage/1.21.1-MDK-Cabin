package net.austin.cabinmod.gametest;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.DjBoothBlock;
import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

// Headless checks of Jehoash's DJ set -> interruption -> booth hand-over -> death -> booth jukebox flow.
// Run with: gradlew runGameTestServer
@GameTestHolder(CabinMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class JehoashGameTests {
    private static final String EMPTY = "empty_11x6x11";   // the holder namespace (cabinmod) is added by NeoForge

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void interruptionSplitsTheBooth(GameTestHelper helper) {
        JehoashEntity jehoash = helper.spawn(ModEntities.JOASH.get(), new BlockPos(5, 1, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        BlockPos[] center = new BlockPos[1];
        Direction[] facing = new Direction[1];
        Vec3[] stood = new Vec3[1];

        helper.runAfterDelay(5, () -> {
            check(helper, jehoash.isDormant() && jehoash.isBoothAttached(), "spawns DJing with his booth attached");
            Vec3 at = jehoash.position();
            check(helper, Math.abs(at.x - Math.floor(at.x) - 0.5) < 1e-4 && Math.abs(at.z - Math.floor(at.z) - 0.5) < 1e-4,
                    "snapped to a block centre, at " + at);
            check(helper, jehoash.getBbWidth() > 3f, "wide DJ hitbox covers the booth");
            check(helper, !jehoash.hurt(jehoash.damageSources().playerAttack(player), 10f), "hits bounce off while DJing");
            check(helper, jehoash.isDormant(), "a hit does not interrupt him");
            facing[0] = jehoash.djFacing();
            center[0] = jehoash.blockPosition().relative(facing[0]);
            jehoash.interact(player, InteractionHand.MAIN_HAND);
            check(helper, !jehoash.isDormant(), "a right-click interrupts the set");
            check(helper, jehoash.isBoothAttached(), "the booth is still his until the slam");
            stood[0] = jehoash.position();
            jehoash.knockback(2.0, 1.0, 0.0);
        });
        helper.runAfterDelay(5 + 20, () -> check(helper, jehoash.position().subtract(stood[0]).horizontalDistance() < 1e-3,
                "knockback can't drag him (and his booth) off the grid: moved to " + jehoash.position()));

        // "booth" marker at 2.65 s (53 ticks) of the interrupted clip
        helper.runAfterDelay(5 + 75, () -> {
            check(helper, !jehoash.isBoothAttached(), "the booth split off him");
            for (DjBoothBlock.Part part : DjBoothBlock.Part.values()) {
                BlockPos pos = DjBoothBlock.partPos(center[0], facing[0], part);
                BlockState state = level.getBlockState(pos);
                check(helper, state.is(ModBlocks.DJ_BOOTH.get()) && state.getValue(DjBoothBlock.PART) == part
                        && state.getValue(DjBoothBlock.FACING) == facing[0], "booth " + part + " at " + pos + " but found " + state);
            }
            check(helper, jehoash.getBbWidth() < 2f, "normal hitbox once he is fighting");
            jehoash.kill();
        });

        // death clip is 4 s; afterwards the booth is a jukebox for anyone
        helper.runAfterDelay(5 + 75 + 100, () -> {
            check(helper, jehoash.isRemoved(), "he is gone after the death clip");
            check(helper, level.getBlockState(center[0]).is(ModBlocks.DJ_BOOTH.get()), "the booth survives him");
            BlockPos left = DjBoothBlock.partPos(center[0], facing[0], DjBoothBlock.Part.LEFT);
            BlockState leftState = level.getBlockState(left);
            leftState.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(left), Direction.UP, left, false));
            check(helper, level.getBlockEntity(center[0]) instanceof DjBoothBlockEntity booth && booth.isPlaying(),
                    "right-clicking any part turns the phonk on");
            level.destroyBlock(left, false);
            check(helper, level.getBlockState(center[0]).isAir()
                            && level.getBlockState(DjBoothBlock.partPos(center[0], facing[0], DjBoothBlock.Part.RIGHT)).isAir(),
                    "breaking one part removes the whole booth");
            helper.succeed();
        });
    }

    // Killed before anyone interrupts him: the booth splits off facing the way he did (north here, not the default south).
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void deathWhileDjingKeepsTheBoothFacing(GameTestHelper helper) {
        JehoashEntity jehoash = helper.spawn(ModEntities.JOASH.get(), new BlockPos(5, 1, 6));
        jehoash.setYRot(180f);
        ServerLevel level = helper.getLevel();
        BlockPos[] center = new BlockPos[1];

        helper.runAfterDelay(5, () -> {
            check(helper, jehoash.isDormant() && jehoash.djFacing() == Direction.NORTH, "DJing facing north, not " + jehoash.djFacing());
            center[0] = jehoash.blockPosition().relative(Direction.NORTH);
            jehoash.kill();
        });
        helper.runAfterDelay(5 + 100, () -> {
            for (DjBoothBlock.Part part : DjBoothBlock.Part.values()) {
                BlockPos pos = DjBoothBlock.partPos(center[0], Direction.NORTH, part);
                BlockState state = level.getBlockState(pos);
                check(helper, state.is(ModBlocks.DJ_BOOTH.get()) && state.getValue(DjBoothBlock.PART) == part
                        && state.getValue(DjBoothBlock.FACING) == Direction.NORTH, "booth " + part + " at " + pos + " but found " + state);
            }
            helper.succeed();
        });
    }

    // The running clip isn't saved: a reload between the right-click and the booth marker must still hand the booth over.
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void reloadMidInterruptionFinishesTheHandOver(GameTestHelper helper) {
        JehoashEntity jehoash = helper.spawn(ModEntities.JOASH.get(), new BlockPos(5, 1, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        BlockPos[] center = new BlockPos[1];
        JehoashEntity[] reloaded = new JehoashEntity[1];

        helper.runAfterDelay(5, () -> {
            center[0] = jehoash.blockPosition().relative(jehoash.djFacing());
            jehoash.interact(player, InteractionHand.MAIN_HAND);
        });
        // 20 ticks into the clip, well before the booth marker (53 ticks): save and load him like a chunk reload would
        helper.runAfterDelay(25, () -> {
            check(helper, !jehoash.isDormant() && jehoash.isBoothAttached(), "mid-interruption before the reload");
            CompoundTag tag = jehoash.saveWithoutId(new CompoundTag());
            jehoash.discard();
            JehoashEntity copy = ModEntities.JOASH.get().create(level);
            check(helper, copy != null, "entity type creates a Jehoash");
            copy.load(tag);
            copy.setUUID(UUID.randomUUID());
            level.addFreshEntity(copy);
            reloaded[0] = copy;
        });
        helper.runAfterDelay(35, () -> {
            JehoashEntity copy = reloaded[0];
            check(helper, !copy.isDormant() && !copy.isBoothAttached(), "the reloaded Jehoash let go of the booth");
            check(helper, level.getBlockState(center[0]).is(ModBlocks.DJ_BOOTH.get()), "the booth stands at " + center[0]);
            check(helper, copy.getBbWidth() < 2f, "fighting hitbox after the reload");
            copy.discard();
            helper.succeed();
        });
    }

    private static void check(GameTestHelper helper, boolean ok, String what) {
        if (!ok) helper.fail(what);
    }
}
