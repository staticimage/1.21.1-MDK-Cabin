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
        int selectedRecord = jehoash.recordTrack();

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
            jehoash.wake(player);
            check(helper, !jehoash.isDormant(), "server activation interrupts the set");
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
            DjBoothBlockEntity locked = (DjBoothBlockEntity)level.getBlockEntity(center[0]);
            check(helper, locked.isLocked() && locked.recordTrack() == selectedRecord, "the selected record transfers into the locked desk");
            check(helper, locked.isPlaying() && jehoash.getUUID().equals(locked.bossId()), "combat booth keeps playing with the boss audio identity");
            check(helper, locked.takeRecord().isEmpty(), "record cannot be taken during combat");
            CompoundTag saved = locked.saveWithoutMetadata(level.registryAccess());
            DjBoothBlockEntity loaded = new DjBoothBlockEntity(center[0],locked.getBlockState());
            loaded.loadWithComponents(saved,level.registryAccess());
            check(helper, loaded.isLocked() && loaded.recordTrack() == selectedRecord, "lock and selection survive booth reload");
            check(helper, loaded.isPlaying() && locked.bossId().equals(loaded.bossId()), "playback and stable audio identity survive reload");
            saved.putBoolean("Playing",false);
            loaded.loadWithComponents(saved,level.registryAccess());
            check(helper, loaded.isPlaying(), "older silent locked booths resume music on load");
            jehoash.kill();
        });

        // death clip is 4 s; afterwards the booth is a jukebox for anyone
        helper.runAfterDelay(5 + 75 + 100, () -> {
            check(helper, jehoash.isRemoved(), "he is gone after the death clip");
            check(helper, level.getBlockState(center[0]).is(ModBlocks.DJ_BOOTH.get()), "the booth survives him");
            BlockPos left = DjBoothBlock.partPos(center[0], facing[0], DjBoothBlock.Part.LEFT);
            BlockState leftState = level.getBlockState(left);
            leftState.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(left), Direction.UP, left, false));
            check(helper, level.getBlockEntity(center[0]) instanceof DjBoothBlockEntity booth && !booth.hasRecord() && !booth.isLocked(),
                    "right-clicking any part extracts the record after victory");
            check(helper, !((DjBoothBlockEntity)level.getBlockEntity(center[0])).isPlaying(), "extracting the record stops playback");
            check(helper, player.getInventory().countItem(net.austin.cabinmod.entity.RecordTracks.stack(selectedRecord).getItem()) == 1,
                    "exactly the chosen record is delivered");
            leftState.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(left), Direction.UP, left, false));
            check(helper, player.getInventory().countItem(net.austin.cabinmod.entity.RecordTracks.stack(selectedRecord).getItem()) == 1,
                    "second interaction cannot duplicate the record");
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
        int selectedRecord = jehoash.recordTrack();

        helper.runAfterDelay(5, () -> {
            center[0] = jehoash.blockPosition().relative(jehoash.djFacing());
            jehoash.wake(player);
        });
        // 20 ticks into the clip, well before the booth marker (53 ticks): save and load him like a chunk reload would
        helper.runAfterDelay(25, () -> {
            check(helper, !jehoash.isDormant() && jehoash.isBoothAttached(), "mid-interruption before the reload");
            CompoundTag tag = jehoash.saveWithoutId(new CompoundTag());
            jehoash.discard();
            JehoashEntity copy = ModEntities.JOASH.get().create(level);
            check(helper, copy != null, "entity type creates a Jehoash");
            copy.load(tag);

            level.addFreshEntity(copy);
            reloaded[0] = copy;
        });
        helper.runAfterDelay(35, () -> {
            JehoashEntity copy = reloaded[0];
            check(helper, !copy.isDormant() && !copy.isBoothAttached(), "the reloaded Jehoash let go of the booth");
            check(helper, level.getBlockState(center[0]).is(ModBlocks.DJ_BOOTH.get()), "the booth stands at " + center[0]);
            check(helper, copy.getBbWidth() < 2f, "fighting hitbox after the reload");
            check(helper, copy.recordTrack() == selectedRecord, "the selected song survives reload");
            copy.discard();
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void arenaRestoresAndShardsRespectWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(5,1,5));
        JehoashEntity boss = helper.spawn(ModEntities.JOASH.get(), new BlockPos(5,1,2));
        boss.configureArena(center);
        BlockPos surface = center.below();
        level.setBlock(surface,ModBlocks.RAVE_FLOOR.get().defaultBlockState(),3);
        BlockPos untouched = center.offset(1,-1,0);
        level.setBlock(untouched,net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),3);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(5,() -> boss.wake(player));
        helper.runAfterDelay(65,() -> {
            check(helper,level.getBlockState(surface).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"arena becomes flesh during fight");
            check(helper,level.getBlockState(untouched).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK),"unrelated blocks are never transformed");
            boss.kill();
            check(helper,!level.getBlockState(surface).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"victory restores original arena surface");
            BlockPos wall = helper.absolutePos(new BlockPos(4,2,8));
            level.setBlock(wall,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            var shard = ModEntities.GLASS_SHARD.get().create(level);
            shard.setPos(Vec3.atCenterOf(wall).add(-1,0,0));
            shard.setDeltaMovement(.78,0,0); level.addFreshEntity(shard);
            helper.runAfterDelay(5,() -> {
                check(helper,shard.isRemoved(),"glass collides with solid walls instead of passing through them");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 440, batch = "rave_combat")
    public static void lightsWarnTrackAndDamageOnlyDuringFight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(5,1,5));
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"rave-test-player"),false);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level,center.getX()+.5,center.getY(),center.getZ()+.5,0,0);
        player.getFoodData().setFoodLevel(18);
        JehoashEntity boss = helper.spawn(ModEntities.JOASH.get(),new BlockPos(2,1,2));
        boss.configureArena(center); boss.setNoAi(true);
        net.austin.cabinmod.entity.custom.ArenaLightEntity[] fixture = new net.austin.cabinmod.entity.custom.ArenaLightEntity[1];
        boolean[] charging = {false}, fired = {false};
        float[] lowestHealth = {20};
        helper.runAfterDelay(5,() -> {
            for(var light: level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.ArenaLightEntity.class,boss.getBoundingBox().inflate(20)))
                if(boss.getUUID().equals(light.ownerId())) light.discard();
            fixture[0] = ModEntities.ARENA_LIGHT.get().create(level);
            fixture[0].configure(boss,center,0);
            fixture[0].setPos(Vec3.atCenterOf(center).add(-4,2,-4));
            level.addFreshEntity(fixture[0]);
        });
        helper.onEachTick(() -> {
            if (fixture[0] != null) {
                charging[0] |= fixture[0].laserPhase()==1;
                fired[0] |= fixture[0].laserPhase()==2;
                lowestHealth[0] = Math.min(lowestHealth[0],player.getHealth());
            }
        });
        helper.runAfterDelay(145,() -> {
            check(helper, player.getHealth()==20 && !charging[0] && !fired[0], "dormant light show is harmless for a full firing cycle");
            boss.wake(player);
        });
        helper.runAfterDelay(350,() -> {
            check(helper, charging[0] && fired[0], "active fixtures telegraph before firing");
            check(helper, lowestHealth[0] < 20, "active beam actually damages a survival player");
            var shard = ModEntities.GLASS_SHARD.get().create(level);
            shard.setOwner(boss); shard.aim(player);
            shard.setPos(player.position().add(-4,1.5,-3)); shard.setDeltaMovement(.78,0,0);
            level.addFreshEntity(shard);
            helper.runAfterDelay(4,() -> {
                check(helper,shard.getDeltaMovement().z > .05,"glass projectile curves toward its moving target");
                boss.kill();
                check(helper,shard.isRemoved(),"victory clears in-flight shards");
                level.getServer().getPlayerList().remove(player);
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void portapottyOccupiesTwoBlocks(GameTestHelper helper) {
        BlockPos pos=helper.absolutePos(new BlockPos(4,1,4));
        var block=ModBlocks.BURNING_PORTAPOTTY.get();
        var state=block.defaultBlockState();
        helper.getLevel().setBlock(pos,state,3);
        block.setPlacedBy(helper.getLevel(),pos,state,null,net.minecraft.world.item.ItemStack.EMPTY);
        check(helper,helper.getLevel().getBlockState(pos.above()).is(block),"upper half reserves the roof space");
        helper.getLevel().destroyBlock(pos.above(),false);
        check(helper,helper.getLevel().getBlockState(pos).isAir(),"breaking the upper half removes the entire prop");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void fleshStartsAtTheStand(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(5,1,8)), stand=center.offset(0,1,-6), floor=center.below();
        level.setBlock(stand,ModBlocks.RAVE_WALL.get().defaultBlockState(),3);
        level.setBlock(floor,ModBlocks.RAVE_FLOOR.get().defaultBlockState(),3);
        net.austin.cabinmod.worldgen.RaveArena.growFromStand(level,center,stand,1);
        check(helper,level.getBlockState(stand).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"flesh begins inside the stand");
        check(helper,!level.getBlockState(floor).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"dance-floor center stays normal until the wave reaches it");
        net.austin.cabinmod.worldgen.RaveArena.growFromStand(level,center,stand,28);
        check(helper,level.getBlockState(floor).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"wave reaches the dance floor");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void sixRecordPerformancesLoopAndStayOnTheDecks(GameTestHelper helper) {
        for(int track=0;track<6;track++) for(int step=0;step<160;step++) {
            float beat=step*.1f;
            var a=net.austin.cabinmod.entity.client.DjPerformance.sample(track,beat,1);
            var b=net.austin.cabinmod.entity.client.DjPerformance.sample(track,beat+16,1);
            check(helper,a.rightHand().distance(b.rightHand())<.001 && a.leftHand().distance(b.leftHand())<.001,"every song's hand loop closes smoothly");
            check(helper,Math.abs(a.crossfader())<=2.11,"crossfader stays inside its slot");
            float dx=a.rightHand().x+14, dz=a.rightHand().z+14.5f;
            check(helper,dx*dx+dz*dz<16,"scratch hand stays over the record");
            if(a.mixerWeight()>.999) check(helper,Math.abs(a.leftHand().x-a.crossfader())<.01,"mixer hand follows the crossfader");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "rave_multiplayer")
    public static void multiplayerSupportsCollapseReloadAndRestore(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos center=helper.absolutePos(new BlockPos(40,1,40));
        for(int x=(center.getX()-12)>>4;x<=(center.getX()+12)>>4;x++) for(int z=(center.getZ()-12)>>4;z<=(center.getZ()+12)>>4;z++) level.setChunkForced(x,z,true);
        JehoashEntity[] boss={net.austin.cabinmod.worldgen.RaveArena.build(level,center)};
        boss[0].setNoAi(true);Vec3 start=boss[0].position();
        var first=arenaPlayer(level,"rave-first",Vec3.atBottomCenterOf(center).add(-2,0,5));
        var second=arenaPlayer(level,"rave-second",Vec3.atBottomCenterOf(center).add(2,0,5));
        var supports=new net.austin.cabinmod.entity.custom.TrussSupportEntity[2];
        // The full arena crosses the template's chunk boundary. Headless tests run
        // much faster than real time; wait for asynchronous entity-chunk loading
        // before starting the measured encounter timeline instead of racing disk IO.
        helper.startSequence().thenWaitUntil(() -> {
            for(var support:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20)))
                if(boss[0].getUUID().equals(support.ownerId()))supports[support.side()]=support;
            check(helper,supports[0]!=null && supports[1]!=null && supports[0].tickCount>2 && supports[1].tickCount>2 && boss[0].tickCount>2,"arena entity chunks are ready");
        }).thenExecute(() -> {
        boolean[] targeted={false,false};
        helper.onEachTick(() -> {
            for(var light:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.ArenaLightEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20))) {
                if(!boss[0].getUUID().equals(light.ownerId()) || light.laserPhase()!=1)continue;
                if(light.laserPoint().distanceTo(first.getEyePosition())<.1)targeted[0]=true;
                if(light.laserPoint().distanceTo(second.getEyePosition())<.1)targeted[1]=true;
            }
        });
        helper.runAfterDelay(5,() -> {
            for(var support:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20)))
                if(boss[0].getUUID().equals(support.ownerId()))supports[support.side()]=support;
            boss[0].interact(first,InteractionHand.MAIN_HAND);
            check(helper,boss[0].isDormant(),"right-clicking no longer activates the fight");
            check(helper,supports[0].hurt(level.damageSources().playerAttack(first),1),"first hit on a dormant support activates phase one");
        });
        helper.runAfterDelay(30,() -> {
            check(helper,boss[0].isConducting() && boss[0].maxSupportHealth()==84 && boss[0].getMaxHealth()==400,"two-player encounter scales once at activation");
            for(var support:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20)))
                if(boss[0].getUUID().equals(support.ownerId()))supports[support.side()]=support;
            check(helper,supports[0]!=null && supports[1]!=null,"both columns expose hittable support entities");
        });
        helper.runAfterDelay(125,() -> {
            check(helper,!level.getBlockState(center.offset(0,1,-8)).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"stage stays normal throughout phase one");
            check(helper,boss[0].position().distanceTo(start)<.01 && boss[0].isBoothAttached(),"intro finishes without leaving the decks");
            check(helper,!boss[0].hurt(level.damageSources().playerAttack(first),100),"phase-one boss is protected while the supports are targets");
        });
        for(int time:new int[]{150,160}) helper.runAfterDelay(time,() -> {
            float before=boss[0].supportHealth(0);
            check(helper,supports[0].hurt(level.damageSources().playerAttack(first),20),"first player's support hit counts at "+time+" supportTick="+supports[0].tickCount+" player="+first.position()+" phase="+boss[0].stagePhase());
            check(helper,supports[0].hurt(level.damageSources().playerAttack(second),20),"simultaneous second player's hit also counts");
            check(helper,boss[0].supportHealth(0)==before-40,"concurrent damage is combined exactly once");
        });
        helper.runAfterDelay(170,() -> {
            supports[0].hurt(level.damageSources().playerAttack(first),20);
            check(helper,boss[0].isConducting() && boss[0].supportHealth(0)==0,"one broken support does not skip the phase");
            check(helper,targeted[0] && targeted[1],"fixtures distribute warned attacks across both players");
        });
        helper.runAfterDelay(175,() -> boss[0]=reloadStageBoss(level,boss[0]));
        for(int time:new int[]{180,190}) helper.runAfterDelay(time,() -> {
            supports[1].hurt(level.damageSources().playerAttack(first),20);supports[1].hurt(level.damageSources().playerAttack(second),20);
        });
        helper.runAfterDelay(200,() -> {
            supports[1].hurt(level.damageSources().playerAttack(first),20);
            check(helper,boss[0].isCollapsing() && boss[0].isBoothAttached(),"both supports trigger one shared collapse");
        });
        helper.runAfterDelay(225,() -> {
            boss[0]=reloadStageBoss(level,boss[0]);
            check(helper,boss[0].isCollapsing() && boss[0].stageTime()>=24,"collapse timeline survives reload");
        });
        helper.runAfterDelay(254,() -> check(helper,boss[0].isBoothAttached(),"boss stays beneath the falling beam until the dodge cue"));
        helper.runAfterDelay(270,() -> {
            check(helper,!level.getBlockState(center.offset(0,1,-8)).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"flesh waits for the beam to hit");
            check(helper,!boss[0].isBoothAttached() && boss[0].position().distanceTo(start)>4,"last-second teleport leaves the booth behind");
            check(helper,boss[0].isCollapsing(),"landing animation finishes before combat resumes");
            var booth=(DjBoothBlockEntity)level.getBlockEntity(boss[0].boothCenter());
            check(helper,booth!=null && booth.isLocked() && booth.isPlaying(),"music continues through the collapse while the record stays locked");
        });
        helper.runAfterDelay(320,() -> {
            check(helper,level.getBlockState(center.offset(0,1,-8)).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"flesh spreads from the stand after impact");
            check(helper,boss[0].stagePhase()==JehoashEntity.ON_FOOT,"second phase begins after the cinematic");
            var at=boss[0].boothCenter();check(helper,level.getBlockEntity(at) instanceof DjBoothBlockEntity,"record booth survives the falling beam");
            check(helper,((DjBoothBlockEntity)level.getBlockEntity(at)).isPlaying(),"music continues in the on-foot phase");
            boss[0].kill();
            check(helper,net.austin.cabinmod.worldgen.RaveArena.hasRig(level,center),"victory restores the stage truss");
            check(helper,!level.getBlockState(center.below()).getValue(net.austin.cabinmod.block.custom.RaveBlock.CORRUPTED),"victory restores the dance floor");
            var booth=(DjBoothBlockEntity)level.getBlockEntity(at);
            check(helper,!booth.takeRecord().isEmpty() && booth.takeRecord().isEmpty(),"multiplayer victory still yields exactly one shared booth record");
            level.getServer().getPlayerList().remove(first);level.getServer().getPlayerList().remove(second);
            for(int x=(center.getX()-12)>>4;x<=(center.getX()+12)>>4;x++) for(int z=(center.getZ()-12)>>4;z<=(center.getZ()+12)>>4;z++) level.setChunkForced(x,z,false);
            helper.succeed();
        });
        });
    }
    @GameTest(template = EMPTY, timeoutTicks = 600, batch = "rave_access")
    public static void supportPressureFieldAndCreativeLightTargets(GameTestHelper helper) {
        var level=helper.getLevel();var center=helper.absolutePos(new BlockPos(40,1,40));
        for(int x=(center.getX()-12)>>4;x<=(center.getX()+12)>>4;x++)for(int z=(center.getZ()-12)>>4;z<=(center.getZ()+12)>>4;z++)level.setChunkForced(x,z,true);
        var boss=net.austin.cabinmod.worldgen.RaveArena.build(level,center);boss.setNoAi(true);
        var player=arenaPlayer(level,"stage-access-test",new Vec3(center.getX()-4.3,center.getY()+2,center.getZ()-7.5));
        player.setGameMode(GameType.CREATIVE);
        net.austin.cabinmod.entity.custom.TrussSupportEntity[] support={null};boolean[] warned={false},fired={false};
        // As above, wait for the remote arena chunk to tick before measuring hit cooldowns.
        helper.startSequence().thenWaitUntil(() -> {
            for(var e:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20)))
                if(boss.getUUID().equals(e.ownerId()) && e.side()==0)support[0]=e;
            check(helper,support[0]!=null && support[0].tickCount>2 && boss.tickCount>2,"stage-access entity chunk is ready");
        }).thenExecute(() -> {
        helper.onEachTick(() -> {
            for(var light:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.ArenaLightEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20))) {
                if(!boss.getUUID().equals(light.ownerId()))continue;
                if(light.laserPhase()==1 && light.laserPoint().distanceTo(player.getEyePosition())<.1)warned[0]=true;
                if(light.laserPhase()==2)fired[0]=true;
            }
        });
        helper.runAfterDelay(35,() -> {
            for(var e:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20)))
                if(boss.getUUID().equals(e.ownerId()) && e.side()==0)support[0]=e;
            check(helper,support[0]!=null,"support exists before the encounter is activated");
        });
        for(int time:new int[]{40,50,60})helper.runAfterDelay(time,() -> check(helper,support[0].hurt(level.damageSources().playerAttack(player),1),"accepted stage pressure hit"));
        helper.runAfterDelay(61,() -> {
            check(helper,warned[0],"first hit starts warnings at creative players beside the support line");
            check(helper,player.getDeltaMovement().z>.3,"third support hit launches the player toward the dance floor");
            player.teleportTo(level,center.getX()+.5,center.getY(),center.getZ()+5.5,0,0);
        });
        helper.runAfterDelay(80,() -> {
            check(helper,fired[0],"fixtures actually fire after their first-hit warning");
            check(helper,player.getHealth()==20,"creative testers retain damage immunity");
            check(helper,boss.stageFieldClosed() && level.getBlockState(center.offset(0,1,-2)).is(ModBlocks.STAGE_BARRIER.get()),"ejection closes access after the clearance delay");
            var light=level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.ArenaLightEntity.class,new net.minecraft.world.phys.AABB(center).inflate(20)).getFirst();
            var block=level.getBlockState(center.offset(0,1,-2));
            check(helper,block.getCollisionShape(level,center.offset(0,1,-2),net.minecraft.world.phys.shapes.CollisionContext.of(light)).isEmpty(),"the closed field lets fixture lasers pass through");
            check(helper,!block.getCollisionShape(level,center.offset(0,1,-2),net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(),"the same field blocks players");
        });
        helper.runAfterDelay(140,() -> check(helper,!boss.stageFieldClosed(),"temporary field opens again"));
        helper.runAfterDelay(160,() -> check(helper,boss.stageFieldClosed(),"periodic stage closure works independently of hit pressure"));
        helper.runAfterDelay(220,() -> {
            check(helper,!boss.stageFieldClosed(),"periodic field reopens");boss.kill();
            check(helper,level.getBlockState(center.offset(0,1,-2)).isAir(),"victory leaves no stage barrier behind");
            level.getServer().getPlayerList().remove(player);
            for(int x=(center.getX()-12)>>4;x<=(center.getX()+12)>>4;x++)for(int z=(center.getZ()-12)>>4;z<=(center.getZ()+12)>>4;z++)level.setChunkForced(x,z,false);
            helper.succeed();
        });
        });
    }

    private static JehoashEntity reloadStageBoss(ServerLevel level,JehoashEntity old) {
        CompoundTag saved=old.saveWithoutId(new CompoundTag());old.discard();
        JehoashEntity copy=ModEntities.JOASH.get().create(level);copy.load(saved);level.addFreshEntity(copy);return copy;
    }
    private static net.minecraft.server.level.ServerPlayer arenaPlayer(ServerLevel level,String name,Vec3 at) {
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),name),false);
        var player=new net.minecraft.server.level.ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);player.teleportTo(level,at.x,at.y,at.z,0,0);player.getAbilities().invulnerable=true;return player;
    }

    private static void check(GameTestHelper helper, boolean ok, String what) {
        if (!ok) helper.fail(what);
    }
}
