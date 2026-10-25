package net.austin.cabinmod.block.custom;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * RAVE WALL / FLOOR IMPLEMENTATION GUIDE
 * ====================================
 * One Java block class is registered twice in ModBlocks: RAVE_WALL and RAVE_FLOOR.
 * A wall is NOT a block entity and does not run a per-block tick or custom renderer.
 * It is an ordinary saved BlockState with two properties:
 *
 *   CORRUPTED=false -> normal black wall / colored dance-floor model.
 *   CORRUPTED=true  -> the living_flesh model for BOTH registrations.
 *   TONE=0..3      -> normal floor color. It is retained during corruption, so
 *                    resetting CORRUPTED restores the exact checkerboard colors.
 *
 * Follow the resource chain (all paths beneath assets/cabinmod):
 *   blockstates/rave_wall.json and rave_floor.json choose models from these flags.
 *   models/block/living_flesh.json is a normal cube_all model, shared by both blocks.
 *   textures/block/living_flesh.png is a VERTICAL strip: sixteen 64x64 frames.
 *   living_flesh.png.mcmeta lists frames 0..15, frametime=3, interpolate=true.
 * Minecraft's client texture atlas advances those frames; 16*3/20 = 2.4 seconds.
 * This animates the entire face material, NOT block geometry or server block states.
 * You can change animation speed in .mcmeta without changing Java code. If changing
 * frame dimensions/count, also update .mcmeta and tools/validate_rave_assets.py.
 *
 * How blocks become flesh:
 * JehoashEntity.tick waits until StageCollapse.IMPACT, then calls
 * RaveArena.growFromStand every four server ticks. The saved origin is the block
 * BELOW THE BOOTH, not the dance-floor center. That method expands a radial wave
 * through this arena's bounded volume and changes only instances of RaveBlock.
 * It uses distance, not flood-fill adjacency: the wave can reach another surface
 * across air. To make growth follow connected flesh, replace that distance test
 * with a queue-based surface flood fill in growFromStand; leave this class alone.
 *
 * How restoration works:
 * JehoashEntity.die calls RaveArena.transform(...,false,12). That method clears only
 * CORRUPTED, preserving TONE, block identity and all unrelated player blocks.
 * There is no snapshot of the world and no replacement with guessed vanilla blocks.
 * Truss restoration and the temporary stage barriers have SEPARATE code paths.
 *
 * Lighting/collision/loot:
 * ModBlocks supplies properties: walls emit no light, floors emit 11 normally and
 * 5 when corrupted. Both retain full-cube collision; flesh is visual, not passable.
 * Their authored arena blocks are unbreakable and use noDrop loot tables. Change
 * those properties in ModBlocks/ModBlockLootTableProvider, not in the texture JSON.
 */
public class RaveBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty TONE = net.minecraft.world.level.block.state.properties.IntegerProperty.create("tone",0,3);
    public static final BooleanProperty CORRUPTED = BooleanProperty.create("corrupted");
    public RaveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CORRUPTED, false).setValue(TONE,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CORRUPTED,TONE);
    }
}
