package net.austin.cabinmod.block.custom;

import net.austin.cabinmod.entity.custom.ArenaLightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Temporary stage-access field. It blocks players and incoming projectiles, but not fixture
 * laser raycasts: ClipContext supplies the ArenaLightEntity as its collision context. This is
 * why closing the gate does not accidentally shield the audience from the light show. */
public class StageBarrierBlock extends Block {
    public StageBarrierBlock(Properties properties) { super(properties); }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if(context instanceof EntityCollisionContext entity && entity.getEntity() instanceof ArenaLightEntity)return Shapes.empty();
        return Shapes.block();
    }
    @Override protected boolean skipRendering(BlockState state,BlockState adjacent,Direction side) {
        return adjacent.is(this) || super.skipRendering(state,adjacent,side);
    }
}
