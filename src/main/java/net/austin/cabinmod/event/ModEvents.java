package net.austin.cabinmod.event;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.item.ModItems;
import net.austin.cabinmod.item.custom.HammerItem;
import net.austin.cabinmod.villager.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ModEvents {
    private static final Set<BlockPos> HARVESTED_BLOCKS = new HashSet<>();

    // Done with the help of https://github.com/CoFH/CoFHCore/blob/1.19.x/src/main/java/cofh/core/event/AreaEffectEvents.java
    // Don't be a jerk License
    @SubscribeEvent
    public static void onHammerUsage(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getMainHandItem();

        if(mainHandItem.getItem() instanceof HammerItem hammer && player instanceof ServerPlayer serverPlayer) {
            BlockPos initialBlockPos = event.getPos();
            if(HARVESTED_BLOCKS.contains(initialBlockPos)) {
                return;
            }

            for(BlockPos pos : HammerItem.getBlocksToBeDestroyed(2, initialBlockPos, serverPlayer)) {
                if(pos == initialBlockPos || !hammer.isCorrectToolForDrops(mainHandItem, event.getLevel().getBlockState(pos))) {
                    continue;
                }

                HARVESTED_BLOCKS.add(pos);
                serverPlayer.gameMode.destroyBlock(pos);
                HARVESTED_BLOCKS.remove(pos);
            }
        }
    }

    @SubscribeEvent
    public static void addCustomTrades(VillagerTradesEvent event){
        if(event.getType() == ModVillagers.CABINGER.value()){
            Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();


//            trades.get(VILLAGER PROFESSION LEVEL).add((trader, randomSource) -> new MerchantOffer(
//                    new ItemCost(ITEM, AMOUNT),  <-INPUT
//                    new ItemStack(ITEM, AMOUNT), # OF TIMES ABLE TO TRADE, XP, PRICE MULT   <-OUTPUT
//            ));

            trades.get(1).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(ModItems.CABIN.get(), 1),
                    new ItemStack(Items.POISONOUS_POTATO, 1), 1, 100, 0.05f
            ));

            trades.get(1).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(ModItems.CABIN.get(), 32),
                    new ItemStack(Items.SEA_LANTERN, 1), 6, 3, 0.05f
            ));

            trades.get(1).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(ModItems.CABIN.get(), 2),
                    new ItemStack(Items.WHITE_CANDLE, 8), 8, 2, 0.05f
            ));

            trades.get(2).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(ModItems.CABIN.get(), 32),
                    new ItemStack(ModItems.CABIN_BOW.get(), 1), 5, 3, 0.05f
            ));

            trades.get(2).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(Items.EMERALD, 2),
                    new ItemStack(ModItems.RAW_CABIN.get(), 4), 9, 3, 0.05f
            ));

            trades.get(2).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(ModItems.CABIN.get(), 16),
                    new ItemStack(ModItems.CABIN_SMITHING_TEMPLATE.get(), 1), 2, 6, 0.05f
            ));

            trades.get(3).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(Items.DIAMOND, 1),
                    new ItemStack(ModItems.MO2_DISC.get(), 1), 1, 3, 0.05f
            ));

            trades.get(3).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(Items.REDSTONE, 1),
                    new ItemStack(ModItems.BLA_DISC.get(), 1), 1, 3, 0.05f
            ));

            trades.get(3).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(Items.EMERALD, 1),
                    new ItemStack(ModItems.HYG_DISC.get(), 1), 1, 3, 0.05f
            ));

            trades.get(3).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(Items.COAL, 1),
                    new ItemStack(ModItems.DNA_DISC.get(), 1), 1, 3, 0.05f
            ));

            trades.get(3).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(Items.LAPIS_LAZULI, 1),
                    new ItemStack(ModItems.BRG_DISC.get(), 1), 1, 3, 0.05f
            ));

            trades.get(3).add((trader, randomSource) -> new MerchantOffer(
                    new ItemCost(Items.IRON_INGOT, 1),
                    new ItemStack(ModItems.SOS_DISC.get(), 1), 1, 3, 0.05f
            ));


        }
    }
}
