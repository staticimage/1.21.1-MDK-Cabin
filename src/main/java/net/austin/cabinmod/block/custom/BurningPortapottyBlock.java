package net.austin.cabinmod.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorative only. The bowl's fire uses Minecraft's animated fire atlas, never fire blocks. */
/*
 * DECORATIVE PROP MAP
 * Two HALF states reserve both occupied blocks; the lower half renders the full model
 * and owns the drop. Removing either half removes its partner. The JSON model uses
 * vanilla animated fire_0 planes inside the toilet bowl; animateTick adds smoke/embers.
 * There is intentionally no use interaction or actual spreading fire. Change model UVs
 * in models/block/burning_portapotty.json; do not add a block-entity tick just to animate
 * fire, since the vanilla atlas already animates that material.
 */
public class BurningPortapottyBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.world.level.block.state.properties.DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final net.minecraft.world.level.block.state.properties.DoubleBlockHalf LOWER = net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
    private static final net.minecraft.world.level.block.state.properties.DoubleBlockHalf UPPER = net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public BurningPortapottyBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,LOWER));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,HALF); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedPos().getY() >= context.getLevel().getMaxBuildHeight()-1) return null;
        if (!context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced()) return null;
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return Block.box(0,0,0,16,16,16);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack) {
        level.setBlock(pos.above(),state.setValue(HALF,UPPER),3);
    }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        Direction partner = state.getValue(HALF)==LOWER ? Direction.UP : Direction.DOWN;
        if (direction==partner && !(neighbor.is(this) && neighbor.getValue(HALF)!=state.getValue(HALF)
                && neighbor.getValue(FACING)==state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,direction,neighbor,level,pos,neighborPos);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide() && !player.isCreative() && state.getValue(HALF)==UPPER)
            Block.popResource(level,pos.below(),new net.minecraft.world.item.ItemStack(this));
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random) {
        if (state.getValue(HALF)==UPPER) return;
        double x=pos.getX()+.5+(random.nextDouble()-.5)*.18;
        double z=pos.getZ()+.5+(random.nextDouble()-.5)*.18;
        level.addParticle(ParticleTypes.FLAME,x,pos.getY()+.83,z,0,.015,0);
        if (random.nextBoolean()) level.addParticle(ParticleTypes.SMOKE,x,pos.getY()+1.25,z,0,.035,0);
        if (random.nextInt(32)==0) level.playLocalSound(x,pos.getY()+1,z,SoundEvents.FIRE_AMBIENT,SoundSource.BLOCKS,.45f,.8f+random.nextFloat()*.2f,false);
    }
}
