package net.austin.cabinmod.block.custom;

import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.austin.cabinmod.block.entity.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Jehoash's DJ booth as a block: three blocks wide (LEFT / CENTER / RIGHT, from the DJ's point of view),
// placed, broken and dropped as one. FACING is the way the front (the crowd side) faces; the DJ stands behind.
// The CENTER block entity's renderer draws the whole booth model (the same model Jehoash carries while he
// DJs, design/JEHOASH_V2.md section 4). Right-click toggles the phonk.
public class DjBoothBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 24, 16);

    public enum Part implements StringRepresentable {
        LEFT, CENTER, RIGHT;

        @Override
        public String getSerializedName() {
            return switch (this) {
                case LEFT -> "left";
                case CENTER -> "center";
                case RIGHT -> "right";
            };
        }
    }

    public DjBoothBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.CENTER));
    }

    // The DJ faces FACING, so his left is FACING's counter-clockwise neighbour (model +X).
    public static BlockPos partPos(BlockPos center, Direction facing, Part part) {
        return switch (part) {
            case LEFT -> center.relative(facing.getCounterClockWise());
            case CENTER -> center;
            case RIGHT -> center.relative(facing.getClockWise());
        };
    }

    public static BlockPos centerOf(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(PART)) {
            case LEFT -> pos.relative(facing.getClockWise());
            case CENTER -> pos;
            case RIGHT -> pos.relative(facing.getCounterClockWise());
        };
    }

    // Puts a whole booth down (used when Jehoash's booth splits off him). False if there's no room.
    public static boolean placeBooth(Level level, BlockPos center, Direction facing) {
        Block booth = net.austin.cabinmod.block.ModBlocks.DJ_BOOTH.get();
        for (Part part : Part.values()) {
            BlockPos at = partPos(center, facing, part);
            if (!level.isInWorldBounds(at) || !level.getBlockState(at).canBeReplaced()) return false;
        }
        BlockState base = booth.defaultBlockState().setValue(FACING, facing);
        level.setBlock(partPos(center, facing, Part.LEFT), base.setValue(PART, Part.LEFT), Block.UPDATE_CLIENTS);
        level.setBlock(partPos(center, facing, Part.RIGHT), base.setValue(PART, Part.RIGHT), Block.UPDATE_CLIENTS);
        level.setBlock(center, base.setValue(PART, Part.CENTER), Block.UPDATE_ALL);
        return true;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos center = context.getClickedPos();
        Level level = context.getLevel();
        for (Part part : new Part[]{Part.LEFT, Part.RIGHT}) {
            BlockPos at = partPos(center, facing, part);
            if (!level.getWorldBorder().isWithinBounds(at) || !level.getBlockState(at).canBeReplaced(context)) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.CENTER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            Direction facing = state.getValue(FACING);
            level.setBlock(partPos(pos, facing, Part.LEFT), state.setValue(PART, Part.LEFT), Block.UPDATE_ALL);
            level.setBlock(partPos(pos, facing, Part.RIGHT), state.setValue(PART, Part.RIGHT), Block.UPDATE_ALL);
        }
    }

    // A part whose partner went missing disappears too (no drop).
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Part part = state.getValue(PART);
        Part expected = null;
        if (part == Part.CENTER && direction == facing.getCounterClockWise()) expected = Part.LEFT;
        if (part == Part.CENTER && direction == facing.getClockWise()) expected = Part.RIGHT;
        if (part == Part.LEFT && direction == facing.getClockWise()) expected = Part.CENTER;
        if (part == Part.RIGHT && direction == facing.getCounterClockWise()) expected = Part.CENTER;
        if (expected != null && !(neighborState.is(this) && neighborState.getValue(FACING) == facing
                && neighborState.getValue(PART) == expected)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()) {
            Block.popResource(level, centerOf(state, pos), new ItemStack(this));      // one booth for the set
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockPos center = centerOf(state, pos);
            if (level.getBlockEntity(center) instanceof DjBoothBlockEntity booth) {
                boolean on = !booth.isPlaying();
                booth.setPlaying(on);
                level.playSound(null, center, on ? ModSounds.DJ_BOOTH_USE.get() : ModSounds.DJ_BOOTH_NEEDLE.get(),
                        SoundSource.RECORDS, 1.5f, 1f);
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
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.CENTER ? new DjBoothBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != ModBlockEntities.DJ_BOOTH_BE.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<DjBoothBlockEntity>) DjBoothBlockEntity::clientTick;
    }
}
