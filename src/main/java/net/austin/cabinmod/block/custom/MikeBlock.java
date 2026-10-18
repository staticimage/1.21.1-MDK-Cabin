package net.austin.cabinmod.block.custom;

import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class MikeBlock extends Block {
    public MikeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        level.playSound(player,pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS,1f,1f);
        return InteractionResult.SUCCESS;
    }


    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if(entity instanceof ItemEntity itemEntity){
            if(isValidItem(itemEntity.getItem())){
                itemEntity.setItem(new ItemStack(ModItems.CABIN_SMITHING_TEMPLATE.get(), itemEntity.getItem().getCount()));
            }
            if(itemEntity.getItem().getItem()== ModBlocks.CABIN_STAIRS.asItem()){
                itemEntity.setItem(new ItemStack(ModBlocks.CHAIR.get(), itemEntity.getItem().getCount()));
            }
            if(itemEntity.getItem().getItem()== Blocks.COAL_BLOCK.asItem()){
                itemEntity.setItem(new ItemStack(ModItems.MICHAEL.get(), itemEntity.getItem().getCount()));
            }
            if(itemEntity.getItem().getItem()== Items.POISONOUS_POTATO){
                itemEntity.setItem(new ItemStack(ModItems.BLUE_SPAWN_EGG.get(), itemEntity.getItem().getCount()));
            }
        }

        super.stepOn(level, pos, state, entity);
    }

    private boolean isValidItem(ItemStack item) {
        return item.is(ModTags.Items.TRANSFORMABLE_ITEMS);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.cabinmod.mike_block.tooltip"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
