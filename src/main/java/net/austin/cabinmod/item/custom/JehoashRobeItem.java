package net.austin.cabinmod.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Cosmetic equipment: vanilla chest-slot behavior without armor stats or durability. */
public final class JehoashRobeItem extends Item implements Equipable {
    public static final float DROP_CHANCE = .005f;

    public JehoashRobeItem(Properties properties) { super(properties); }

    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.CHEST; }
    @Override public Holder<SoundEvent> getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.cabinmod.jehoash_robe").withStyle(ChatFormatting.GRAY));
    }
}
