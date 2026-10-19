package net.austin.cabinmod.block.custom;

import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// The Disco's DJ booth: two blocks wide (MAIN = deck 1 + mixer, SIDE = mixer + deck 2 + boom mic),
// placed, broken and dropped as one, like a bed. FACING is the side the front panel (the crowd) faces;
// the DJ stands behind. Using it starts the party - wakes the dormant Jehoash in the arena.
public class DjBoothBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15.5, 16);
    private static final double SEARCH_RADIUS = 32;

    public enum Part implements StringRepresentable {
        MAIN, SIDE;

        @Override
        public String getSerializedName() {
            return this == MAIN ? "main" : "side";
        }
    }

    public DjBoothBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.MAIN));
    }

    // Direction from this half to the other one.
    public static Direction toOtherHalf(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == Part.MAIN ? facing.getClockWise() : facing.getCounterClockWise();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos side = context.getClickedPos().relative(facing.getClockWise());
        Level level = context.getLevel();
        if (!level.getWorldBorder().isWithinBounds(side) || !level.getBlockState(side).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.MAIN);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            level.setBlock(pos.relative(toOtherHalf(state)), state.setValue(PART, Part.SIDE), Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == toOtherHalf(state)
                && !(neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            if (!player.isCreative()) {
                Block.popResource(level, pos, new ItemStack(this));     // one booth per pair; halves drop nothing
            }
            BlockPos other = pos.relative(toOtherHalf(state));
            if (level.getBlockState(other).is(this)) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            level.playSound(null, pos, ModSounds.DJ_BOOTH_USE.get(), SoundSource.BLOCKS, 1f, 1f);
            for (JehoashEntity jehoash : level.getEntitiesOfClass(JehoashEntity.class, new AABB(pos).inflate(SEARCH_RADIUS), JehoashEntity::isDormant)) {
                jehoash.wake(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DjBoothBlockEntity(pos, state);
    }
}
