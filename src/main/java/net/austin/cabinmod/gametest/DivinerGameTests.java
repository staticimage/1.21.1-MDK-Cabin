package net.austin.cabinmod.gametest;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.block.entity.CraftingBlockEntity;
import net.austin.cabinmod.screen.custom.CraftingBlockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

// Headless checks of the Transient Diviner's starfall: results taken from the output launch into the sky,
// fall back down after the menu closes, and are never lost. Each player's stars are their own: they fall
// when that player leaves, not when someone else does.
// Run with: gradlew runGameTestServer
@GameTestHolder(CabinMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class DivinerGameTests {
    private static final String EMPTY = "empty_11x6x11";
    private static final int OUTPUT = 37;     // 36 player slots, then input (36) and output (37)
    private static final BlockPos AT = new BlockPos(5, 1, 5);

    private static CraftingBlockEntity diviner(GameTestHelper helper) {
        helper.setBlock(AT, ModBlocks.CRAFTING_BLOCK.get());
        return (CraftingBlockEntity) helper.getBlockEntity(AT);
    }

    private static CraftingBlockMenu open(CraftingBlockEntity be, Player player) {
        CraftingBlockMenu menu = (CraftingBlockMenu) be.createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    private static void check(GameTestHelper helper, boolean ok, String what) {
        if (!ok) helper.fail(what);
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void takingTheResultLaunchesIt(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingBlockMenu menu = open(be, player);
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.DIAMOND, 3));
        menu.clicked(OUTPUT, 0, ClickType.PICKUP, player);
        check(helper, menu.getCarried().isEmpty(), "the cursor stays empty, got " + menu.getCarried());
        check(helper, be.itemHandler.getStackInSlot(1).isEmpty(), "the output slot is cleared");
        check(helper, be.hasPendingStar() && be.pendingStars().getFirst().getCount() == 3, "the stack waits in the sky");
        check(helper, menu.hasPendingStar(), "the menu tells the client a star is pending");
        check(helper, be.starfallLandTick() < 0, "nothing falls while the menu is still open");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void everyTakePathLaunches(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingBlockMenu menu = open(be, player);
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.EMERALD, 2));
        menu.clicked(OUTPUT, 0, ClickType.QUICK_MOVE, player);
        check(helper, !player.getInventory().contains(new ItemStack(Items.EMERALD)), "shift-click doesn't reach the inventory");
        check(helper, be.pendingStars().size() == 1 && be.pendingStars().getFirst().getCount() == 2, "shift-click launches the stack");

        be.itemHandler.setStackInSlot(1, new ItemStack(Items.EMERALD, 1));
        menu.clicked(OUTPUT, 0, ClickType.SWAP, player);
        check(helper, !player.getInventory().contains(new ItemStack(Items.EMERALD)) && be.pendingStars().getFirst().getCount() == 3, "hotbar swap launches instead of taking the result");
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.EMERALD, 1));
        menu.clicked(OUTPUT, 0, ClickType.THROW, player);
        check(helper, be.pendingStars().getFirst().getCount() == 4, "throwing launches instead of dropping, and stacks merge");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void outputRejectsPlacement(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingBlockMenu menu = open(be, player);
        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 4));
        menu.clicked(OUTPUT, 0, ClickType.PICKUP, player);
        check(helper, be.itemHandler.getStackInSlot(1).isEmpty(), "nothing can be put into the output");
        check(helper, menu.getCarried().getCount() == 4, "the carried stack is kept");
        check(helper, !menu.slots.get(OUTPUT).mayPlace(new ItemStack(Items.GOLD_INGOT)), "output slot refuses items");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = CraftingBlockEntity.RETURN_TICKS + 40)
    public static void closingTheMenuBringsTheStarDown(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingBlockMenu menu = open(be, player);
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.DIAMOND, 2));
        menu.clicked(OUTPUT, 0, ClickType.PICKUP, player);
        menu.removed(player);
        long land = be.starfallLandTick();
        check(helper, land == helper.getLevel().getGameTime() + CraftingBlockEntity.RETURN_TICKS, "landing is RETURN_TICKS away, got " + land);
        helper.runAfterDelay(CraftingBlockEntity.RETURN_TICKS - 5, () ->
                check(helper, landed(helper).isEmpty() && be.hasPendingStar(), "still falling just before the landing"));
        helper.runAfterDelay(CraftingBlockEntity.RETURN_TICKS + 3, () -> {
            List<ItemEntity> items = landed(helper);
            check(helper, items.size() == 1 && items.getFirst().getItem().is(Items.DIAMOND) && items.getFirst().getItem().getCount() == 2,
                    "the result lands on the diviner, got " + items);
            ItemEntity item = items.getFirst();
            BlockPos abs = helper.absolutePos(AT);
            check(helper, Math.abs(item.getX() - (abs.getX() + 0.5)) < 0.3 && Math.abs(item.getZ() - (abs.getZ() + 0.5)) < 0.3
                    && item.getY() >= abs.getY() + 0.9, "it lands on top of the block, at " + item.position());
            check(helper, !be.hasPendingStar() && be.starfallLandTick() < 0, "the star is used up");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void abandonedStarStillFalls(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        be.launch(new ItemStack(Items.NETHER_STAR));   // e.g. the viewer disconnected without closing
        helper.succeedWhen(() -> check(helper, be.starfallLandTick() >= 0, "an unwatched pending star starts falling"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void breakingTheDivinerDropsPendingStars(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        be.launch(new ItemStack(Items.DIAMOND, 5));
        be.beginStarfall();
        helper.destroyBlock(AT);
        helper.runAfterDelay(2, () -> {
            int diamonds = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(3))
                    .stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, diamonds == 5, "the falling result drops when the diviner breaks, got " + diamonds);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void eachPlayersLaunchIsTheirOwnStar(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        Player alice = helper.makeMockPlayer(GameType.SURVIVAL), bob = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingBlockMenu aliceMenu = open(be, alice), bobMenu = open(be, bob);
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.DIAMOND, 2));
        aliceMenu.clicked(OUTPUT, 0, ClickType.PICKUP, alice);
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.DIAMOND, 1));
        bobMenu.clicked(OUTPUT, 0, ClickType.PICKUP, bob);
        check(helper, be.stars().size() == 2, "same item, different players: two stars, got " + be.stars().size());
        check(helper, be.stars().get(0).owner.equals(alice.getUUID()) && be.stars().get(1).owner.equals(bob.getUUID()), "each star knows its owner");
        check(helper, aliceMenu.hasPendingStar() && bobMenu.hasPendingStar(), "each menu sees its own star waiting");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void anotherPlayerLeavingDoesNotDropYourStar(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        Player alice = helper.makeMockPlayer(GameType.SURVIVAL), bob = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingBlockMenu aliceMenu = open(be, alice), bobMenu = open(be, bob);
        check(helper, be.viewers().contains(alice.getUUID()) && be.viewers().contains(bob.getUUID()), "both are up in the heavens");
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.EMERALD, 1));
        aliceMenu.clicked(OUTPUT, 0, ClickType.PICKUP, alice);
        bobMenu.removed(bob);
        check(helper, !be.viewers().contains(bob.getUUID()), "bob left the heavens");
        check(helper, be.starfallLandTick() < 0 && !be.stars().getFirst().falling(), "alice's star waits for alice");
        aliceMenu.removed(alice);
        check(helper, be.stars().getFirst().falling() && be.viewers().isEmpty(), "and falls when she leaves");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = CraftingBlockEntity.RETURN_TICKS + 40)
    public static void starsLandOneByOne(GameTestHelper helper) {
        CraftingBlockEntity be = diviner(helper);
        Player alice = helper.makeMockPlayer(GameType.SURVIVAL), bob = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingBlockMenu aliceMenu = open(be, alice), bobMenu = open(be, bob);
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.DIAMOND, 1));
        aliceMenu.clicked(OUTPUT, 0, ClickType.PICKUP, alice);
        be.itemHandler.setStackInSlot(1, new ItemStack(Items.EMERALD, 1));
        bobMenu.clicked(OUTPUT, 0, ClickType.PICKUP, bob);
        aliceMenu.removed(alice);
        helper.runAfterDelay(10, () -> bobMenu.removed(bob));
        helper.runAfterDelay(CraftingBlockEntity.RETURN_TICKS + 3, () -> {
            List<ItemEntity> items = landed(helper);
            check(helper, items.size() == 1 && items.getFirst().getItem().is(Items.DIAMOND), "alice's star lands first, got " + items);
            check(helper, be.stars().size() == 1, "bob's is still falling");
        });
        helper.runAfterDelay(CraftingBlockEntity.RETURN_TICKS + 14, () -> {
            check(helper, landed(helper).size() == 2 && be.stars().isEmpty(), "then bob's lands");
            helper.succeed();
        });
    }

    private static List<ItemEntity> landed(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(1.5, 2, 1.5));
    }
}
