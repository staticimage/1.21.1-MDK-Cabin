package net.austin.cabinmod.item.custom;

import net.austin.cabinmod.block.ModBlocks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

public class WandItem extends Item {
    private static final Map<Block, Block> WAND_MAP =
            Map.of(
                    ModBlocks.RAW_CABIN_BLOCK.get(), Blocks.SPONGE,
                    ModBlocks.CABIN_BLOCK.get(), ModBlocks.MIKE_BLOCK.get(),
                    Blocks.STONE, ModBlocks.CABIN_ORE.get(),
                    Blocks.DEEPSLATE, ModBlocks.CABIN_ORE_DEEPSLATE.get()
            );


    public WandItem(Properties properties) {
        super(properties);
    }


    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Block clickedBlock = level.getBlockState(context.getClickedPos()).getBlock();

        if(WAND_MAP.containsKey(clickedBlock)){
            if(!level.isClientSide()){
                level.setBlockAndUpdate(context.getClickedPos(),WAND_MAP.get(clickedBlock).defaultBlockState());

                context.getItemInHand().hurtAndBreak(1,((ServerLevel) level),context.getPlayer(),
                        item -> context.getPlayer().onEquippedItemBroken(item, EquipmentSlot.MAINHAND));

                level.playSound(null,context.getClickedPos(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if(Screen.hasShiftDown()){
            tooltipComponents.add(Component.translatable("tooltip.cabinmod.wand.shift_down"));
        }else {
            tooltipComponents.add(Component.translatable("tooltip.cabinmod.wand"));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
