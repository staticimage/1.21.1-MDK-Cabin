package net.austin.cabinmod.gametest;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.ModEntities;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(CabinMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JehoashRobeGameTests {
    @GameTest(template="empty_11x6x11",timeoutTicks=30)
    public static void robeLootHasOneFixedPlayerKillRoll(GameTestHelper helper) {
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        checkLoot(helper,player,.0049f,true,1);
        checkLoot(helper,player,.005f,true,0);
        checkLoot(helper,player,.8f,true,0);
        checkLoot(helper,player,0,false,0);
        var sword=new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3);
        player.setItemInHand(InteractionHand.MAIN_HAND,sword);
        checkLoot(helper,player,.0049f,true,1);
        checkLoot(helper,player,.005f,true,0);
        var illusionKey=ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,"entities/joash_illusion"));
        var illusion=helper.getLevel().getServer().reloadableRegistries().getLootTable(illusionKey);
        helper.assertTrue(illusion.getRandomItems(params(helper,player,true),new FixedRoll(0)).isEmpty(),"illusions never drop the robe or key");
        helper.succeed();
    }

    private static LootParams params(GameTestHelper helper,Player player,boolean playerKill) {
        var boss=ModEntities.JOASH.get().create(helper.getLevel());
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY,boss)
                .withParameter(LootContextParams.ORIGIN,helper.absoluteVec(new net.minecraft.world.phys.Vec3(3,1,3)))
                .withParameter(LootContextParams.DAMAGE_SOURCE,helper.getLevel().damageSources().playerAttack(player))
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER,playerKill?player:null)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY,playerKill?player:null)
                .create(LootContextParamSets.ENTITY);
    }

    private static void checkLoot(GameTestHelper helper,Player player,float roll,boolean playerKill,int robes) {
        var key=ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID,"entities/joash"));
        var table=helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
        List<ItemStack> drops=table.getRandomItems(params(helper,player,playerKill),new FixedRoll(roll));
        helper.assertTrue(drops.stream().filter(s -> s.is(ModItems.WARDENS_SPARE_KEY.get())).mapToInt(ItemStack::getCount).sum()==1,"spare key remains guaranteed");
        helper.assertTrue(drops.stream().filter(s -> s.is(ModItems.JEHOASH_ROBE.get())).mapToInt(ItemStack::getCount).sum()==robes,
                "robe roll "+roll+" playerKill="+playerKill+" expected="+robes);
        helper.assertTrue(drops.size()==1+robes,"exactly one optional robe; no duplicate rewards");
    }

    private static final class FixedRoll extends LegacyRandomSource {
        private final float roll;
        FixedRoll(float roll) { super(17);this.roll=roll; }
        @Override public float nextFloat() { return roll; }
    }

    @GameTest(template="empty_11x6x11",timeoutTicks=30)
    public static void robePickupEquipAndSaveUsesVanillaChestSlot(GameTestHelper helper) {
        var collector=helper.makeMockServerPlayerInLevel();
        var dropped=helper.spawnItem(ModItems.JEHOASH_ROBE.get(),new BlockPos(3,1,3));
        dropped.setNoPickUpDelay();
        dropped.playerTouch(collector);
        helper.assertTrue(dropped.isRemoved() && collector.getInventory().countItem(ModItems.JEHOASH_ROBE.get())==1,"normal item pickup gives one robe");
        collector.getInventory().clearContent();
        // The server-player helper is always creative; survival consumes the
        // held stack when equipping, while creative intentionally keeps a copy.
        var player=helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.JEHOASH_ROBE.get()));
        var result=ModItems.JEHOASH_ROBE.get().use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND,result.getObject());
        var worn=player.getItemBySlot(EquipmentSlot.CHEST);
        helper.assertTrue(worn.is(ModItems.JEHOASH_ROBE.get()) && player.getMainHandItem().isEmpty(),"right click equips chest slot without duplication");
        helper.assertTrue(!worn.isDamageableItem() && worn.getMaxStackSize()==1 && worn.getRarity()==Rarity.EPIC,"cosmetic, non-stackable, no durability");
        helper.assertTrue(player.getArmorValue()==0,"cosmetic robe grants no armor");
        CompoundTag saved=player.saveWithoutId(new CompoundTag());
        var restored=helper.makeMockPlayer(GameType.SURVIVAL);
        restored.load(saved);
        helper.assertTrue(restored.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.JEHOASH_ROBE.get()),"equipment persists through save/load");
        player.inventoryMenu.quickMoveStack(player,6);
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"shift click unequips robe");
        player.getInventory().clearContent();
        player.getInventory().setItem(0,new ItemStack(ModItems.JEHOASH_ROBE.get()));
        player.inventoryMenu.quickMoveStack(player,36);
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.JEHOASH_ROBE.get()),"shift click equips non-armor Equipable item");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_CHESTPLATE));
        var swapped=Items.DIAMOND_CHESTPLATE.use(helper.getLevel(),player,InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE) && swapped.getObject().is(ModItems.JEHOASH_ROBE.get()),"robe swaps with existing chest equipment");
        helper.succeed();
    }
}
