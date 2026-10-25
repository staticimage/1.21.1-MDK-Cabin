package net.austin.cabinmod.worldgen;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.custom.RaveBlock;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CabinMod.MOD_ID)
/*
 * ARENA LAYOUT AND TRANSFORMATION MAP
 * All coordinates are relative to center c, the dance floor's standing height. The
 * floor is y=-1; walls are x/z=+-12; ceiling is y=8. Negative Z is the rear stage.
 * The podium fills x=-7..7, z=-11..-4, y=0..1. Jehoash stands at y=2. The gantry
 * crossbar is y=7,z=-8, with columns at x=+-6. trussMount() places six fixture pivots.
 * Change these layout constants AND the support/field positions together if resizing.
 * build() writes authored RaveBlock surfaces and vanilla stairs/speakers, then creates
 * boss, support targets and static image screen. canPlace() protects occupied terrain.
 * growFromStand() only flips CORRUPTED within the arena; see RaveBlock's detailed guide.
 * The origin and expansion radius are saved on JehoashEntity. CORRUPTION_REACH bounds
 * the wave, not the room size. transform(false) restores flags while preserving TONE.
 * Stage rig blocks become FallingStageRigEntity only during collapse; restoreRig()
 * rebuilds those authored truss positions on victory. stageBarrier() is a separate
 * reversible U-shaped gate and deliberately does not overwrite player-built blocks.
 */
public final class RaveArena {
    public static final int RADIUS = 12, HEIGHT = 8;
    public static final int PODIUM_HEIGHT = 2, STAGE_Z = -8, CORRUPTION_REACH = 28;
    public static BlockPos trussMount(BlockPos center, int slot) { return center.offset(-5+slot*2,7,STAGE_Z); }
    public static BlockPos stageOrigin(BlockPos center) { return center.offset(0,1,STAGE_Z); }
    public static int installLights(ServerLevel level,JehoashEntity boss) {
        int installed = 0;
        for (int i=0;i<6;i++) {
            BlockPos mount = trussMount(boss.arenaCenter(),i);
            if (!level.getBlockState(mount).is(ModBlocks.STAGE_TRUSS.get())) continue;
            var light=ModEntities.ARENA_LIGHT.get().create(level);
            if (light == null) continue;
            // The clamp touches the lower chord; the moving head hangs beneath it.
            light.setPos(mount.getX()+.5,mount.getY()-.7,mount.getZ()+.5);
            light.configure(boss,boss.arenaCenter(),i);
            level.addFreshEntity(light); installed++;
        }
        return installed;
    }
    public static boolean hasRig(ServerLevel level,BlockPos center) {
        return level.getBlockState(trussMount(center,0)).is(ModBlocks.STAGE_TRUSS.get())
                && level.getBlockState(trussMount(center,5)).is(ModBlocks.STAGE_TRUSS.get());
    }
    public static void ensureSupports(ServerLevel level,JehoashEntity boss) {
        for(int side=0;side<2;side++) {
            final int index=side;
            var existing=level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,
                    new net.minecraft.world.phys.AABB(boss.arenaCenter()).inflate(18),e -> boss.getUUID().equals(e.ownerId()) && e.side()==index);
            if(!existing.isEmpty()) continue;
            var support=ModEntities.TRUSS_SUPPORT.get().create(level);
            if(support==null) continue;
            var at=boss.arenaCenter().offset(side==0?-6:6,2,STAGE_Z);
            support.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);support.configure(boss,side);level.addFreshEntity(support);
        }
    }
    public static void ensureFallingRig(ServerLevel level,JehoashEntity boss) {
        var c=boss.arenaCenter();
        for(int x=-6;x<=6;x++) {
            var p=c.offset(x,7,STAGE_Z);
            if(level.getBlockState(p).is(ModBlocks.STAGE_TRUSS.get())) level.removeBlock(p,false);
        }
        for(int x:new int[]{-6,6}) for(int y=5;y<7;y++) {
            var p=c.offset(x,y,STAGE_Z);
            if(level.getBlockState(p).is(ModBlocks.STAGE_TRUSS.get())) level.removeBlock(p,false);
        }
        if(!level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.FallingStageRigEntity.class,
                new net.minecraft.world.phys.AABB(c).inflate(18),e -> boss.getUUID().equals(e.ownerId())).isEmpty()) return;
        var rig=ModEntities.FALLING_STAGE_RIG.get().create(level);
        if(rig!=null) { rig.setPos(c.getX()+.5,c.getY()+7,c.getZ()+STAGE_Z+.5);rig.configure(boss);level.addFreshEntity(rig); }
    }
    /** The stage field forms a U: front z=-2 and sides x=+-8, five blocks tall.
     * Only air is replaced; opening removes ONLY our own barrier blocks. Player-built blocks
     * are never erased by this toggle. The back is already closed by the arena wall. */
    public static void stageBarrier(ServerLevel level,BlockPos center,boolean closed) {
        for(int x=-8;x<=8;x++) for(int z=-11;z<=-2;z++) {
            if(z!=-2 && Math.abs(x)!=8)continue;
            for(int y=0;y<5;y++) {
                var pos=center.offset(x,y,z);var state=level.getBlockState(pos);
                if(closed && state.isAir())level.setBlock(pos,ModBlocks.STAGE_BARRIER.get().defaultBlockState(),3);
                else if(!closed && state.is(ModBlocks.STAGE_BARRIER.get()))level.removeBlock(pos,false);
            }
        }
    }

    public static void restoreRig(ServerLevel level,JehoashEntity boss) {
        stageBarrier(level,boss.arenaCenter(),false);
        var c=boss.arenaCenter();var area=new net.minecraft.world.phys.AABB(c).inflate(20);
        for(var e:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.FallingStageRigEntity.class,area)) if(boss.getUUID().equals(e.ownerId()))e.discard();
        for(var e:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,area)) if(boss.getUUID().equals(e.ownerId()))e.discard();
        for(var e:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.ArenaLightEntity.class,area)) if(boss.getUUID().equals(e.ownerId()))e.discard();
        for(int x:new int[]{-6,6}) for(int y=2;y<7;y++) level.setBlock(c.offset(x,y,STAGE_Z),ModBlocks.STAGE_TRUSS.get().defaultBlockState(),3);
        for(int x=-6;x<=6;x++) level.setBlock(c.offset(x,7,STAGE_Z),ModBlocks.STAGE_TRUSS.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X),3);
        installLights(level,boss);
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cabinmod").requires(s -> s.hasPermission(2))
            .then(Commands.literal("rave_arena").then(Commands.argument("center", BlockPosArgument.blockPos())
                .executes(ctx -> {
                    BlockPos center = BlockPosArgument.getLoadedBlockPos(ctx,"center");
                    ServerLevel level = ctx.getSource().getLevel();
                    if (!canPlace(level, center)) {
                        ctx.getSource().sendFailure(Component.literal("Needs a clear 25 x 9 x 25 space above the floor, inside loaded chunks and the world border."));
                        return 0;
                    }
                    build(level, center);
                    ctx.getSource().sendSuccess(() -> Component.literal("Rave arena placed. Strike a truss support to start the fight."), true);
                    return 1;
                }))));
    }
    public static boolean canPlace(ServerLevel level, BlockPos c) {
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-12,-1,-12), c.offset(12,8,12))) {
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) return false;
            if (pos.getY() >= c.getY() && !level.getBlockState(pos).canBeReplaced()) return false;
            if (level.getBlockEntity(pos) != null) return false;
        }
        return true;
    }
    public static JehoashEntity build(ServerLevel level, BlockPos c) {
        for (int x=-12;x<=12;x++) for (int z=-12;z<=12;z++) {
            boolean edge = Math.abs(x)==12 || Math.abs(z)==12;
            level.setBlock(c.offset(x,-1,z), (Math.floorMod(x+z,2)==0 ? ModBlocks.RAVE_FLOOR.get() : ModBlocks.RAVE_WALL.get()).defaultBlockState().setValue(RaveBlock.TONE,Math.floorMod(Math.floorDiv(x,2)+Math.floorDiv(z,2),4)), 3);
            for (int y=0;y<=8;y++) {
                BlockState state = Blocks.AIR.defaultBlockState();
                if (edge || y==8) state = ModBlocks.RAVE_WALL.get().defaultBlockState();
                // Wide entrance opposite the DJ; remains an escape route during combat.
                if (z==12 && Math.abs(x)<=2 && y<4) state = Blocks.AIR.defaultBlockState();
                // Strips of colored club lighting, corrupted alongside the walls.
                if (edge && y==5 && (x+z)%3==0) state = ModBlocks.RAVE_FLOOR.get().defaultBlockState().setValue(RaveBlock.TONE,Math.floorMod(x+z,4));
                level.setBlock(c.offset(x,y,z),state,3);
            }
        }
        // Two-block podium, with a centered gantry rooted in the stage itself.
        for (int x=-7;x<=7;x++) for(int z=-11;z<=-4;z++) for(int y=0;y<PODIUM_HEIGHT;y++)
            level.setBlock(c.offset(x,y,z),ModBlocks.RAVE_WALL.get().defaultBlockState(),3);
        for(int x=-2;x<=2;x++) {
            level.setBlock(c.offset(x,0,-3),Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.NORTH),3);
            level.setBlock(c.offset(x,1,-4),Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.NORTH),3);
        }
        for(int x : new int[]{-6,6}) for(int y=PODIUM_HEIGHT;y<7;y++)
            level.setBlock(c.offset(x,y,STAGE_Z),ModBlocks.STAGE_TRUSS.get().defaultBlockState(),3);
        for(int x=-6;x<=6;x++) level.setBlock(c.offset(x,7,STAGE_Z),ModBlocks.STAGE_TRUSS.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X),3);
        // Speakers flank the raised stand, leaving the gantry and dance floor unobstructed.
        for (int x : new int[]{-9,9}) for (int y=0;y<4;y++) {
            level.setBlock(c.offset(x,y,-9),Blocks.POLISHED_BLACKSTONE.defaultBlockState(),3);
            level.setBlock(c.offset(x,y,-8), y%2==0 ? Blocks.NOTE_BLOCK.defaultBlockState() : Blocks.BLACK_CONCRETE.defaultBlockState(),3);
        }
        JehoashEntity boss = ModEntities.JOASH.get().create(level);
        if (boss == null) throw new IllegalStateException("Missing Jehoash entity type");
        boss.moveTo(c.getX()+.5,c.getY()+PODIUM_HEIGHT,c.getZ()+STAGE_Z-.5,0,0);
        boss.configureArena(c);
        level.addFreshEntity(boss);
        ensureSupports(level,boss);
        var screen=ModEntities.RAVE_SCREEN.get().create(level);
        if(screen!=null) { screen.setPos(c.getX()+.5,c.getY()+3,c.getZ()-10.96);screen.configure(boss);level.addFreshEntity(screen); }
        return boss;
    }
    /** Grow only through this arena, starting at the DJ stand rather than the dance-floor center. */
    public static void growFromStand(ServerLevel level,BlockPos center,BlockPos origin,int progress) {
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-RADIUS,-1,-RADIUS),center.offset(RADIUS,HEIGHT,RADIUS))) {
            double dx=pos.getX()-origin.getX(), dz=pos.getZ()-origin.getZ();
            double distance=Math.sqrt(dx*dx+dz*dz)+Math.abs(pos.getY()-origin.getY())*.3;
            if(distance>progress || !level.hasChunkAt(pos)) continue;
            BlockState state=level.getBlockState(pos);
            if(state.getBlock() instanceof RaveBlock && !state.getValue(RaveBlock.CORRUPTED))
                level.setBlock(pos,state.setValue(RaveBlock.CORRUPTED,true),3);
        }
    }

    public static void transform(ServerLevel level, BlockPos center, boolean corrupted, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius,-1,-radius), center.offset(radius,8,radius))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RaveBlock && state.getValue(RaveBlock.CORRUPTED) != corrupted) {
                level.setBlock(pos, state.setValue(RaveBlock.CORRUPTED,corrupted),3);
            }
        }
    }
    private RaveArena() {}
}
