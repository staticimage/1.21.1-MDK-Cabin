package net.austin.cabinmod.debug;

import com.mojang.authlib.GameProfile;
import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.client.JehoashRobeClient;
import net.austin.cabinmod.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Disposable world, two vanilla skin renderers and a real server-synchronized local wearer. */
@EventBusSubscriber(modid=CabinMod.MOD_ID,value=Dist.CLIENT)
public final class JehoashRobeVisualSmoke {
    private static int ticks,lastBucket=-1,frame;
    private static PreviewPlayer classic,slim;
    private static Path output;
    private static long start;
    private static final List<String> times=new ArrayList<>();
    private static final Set<Integer> stills=new HashSet<>();
    private static final int FPS=Integer.getInteger("cabinmod.visualSmoke.robeFps",60);

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("cabinmod.visualSmoke.robeReview")) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return;
        ticks++;
        var server=mc.getSingleplayerServer();
        if(ticks==1) {
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
            mc.options.enableVsync().set(false);mc.options.framerateLimit().set(FPS);
            output=mc.gameDirectory.toPath().toAbsolutePath().normalize().getParent().resolve("robe-review");
            try { Files.createDirectories(output); } catch(java.io.IOException e) { throw new AssertionError(e); }
            JehoashRobeChecks.run(mc.player,output.resolve("geometry"));
            JehoashRevealChecks.run();
            output=output.resolve("captures/"+FPS+"-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            try { Files.createDirectories(output); } catch(java.io.IOException e) { throw new AssertionError(e); }
            times.add("file,seconds");start=System.nanoTime();
            System.out.println("ROBE_CAPTURE "+output);
            classic=new PreviewPlayer(false,990001);slim=new PreviewPlayer(true,990002);
            mc.level.addEntity(classic);mc.level.addEntity(slim);
        }
        if(ticks==10) server.execute(() -> {
            clearOldPreviewEntities(server);
            var player=server.getPlayerList().getPlayers().getFirst();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().invulnerable=true;
            player.getAbilities().mayfly=true;
            player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.JEHOASH_ROBE.get()));
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            camera(server,.5,80.65,5.5,180,5);
        });
        if(ticks==35) server.execute(() -> clearOldPreviewEntities(server));
        if(ticks==40 && !JehoashRobeClient.isWearing(mc.player)) throw new AssertionError("Server equipment did not reach local client");
        if(ticks==65) server.execute(() -> camera(server,.5,80.65,-4.5,0,5));
        if(ticks==95) server.execute(() -> camera(server,5.3,80.65,.5,90,5));
        if(ticks==125) server.execute(() -> camera(server,.5,80.65,5.5,180,5));
        if(ticks==360) {
            for(var player:List.of(classic,slim)) {
                var vehicle=net.minecraft.world.entity.EntityType.PIG.create(mc.level);
                vehicle.setId(player.getId()+20);vehicle.setPos(player.getX(),80,player.getZ());
                vehicle.setNoAi(true);mc.level.addEntity(vehicle);player.startRiding(vehicle,true);
            }
        }
        if(ticks==400) for(var player:List.of(classic,slim)) {
            var vehicle=player.getVehicle();player.stopRiding();if(vehicle!=null) vehicle.discard();
            player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
        }
        if(ticks==445) {
            // F1 also hides vanilla first-person hands, including RenderArmEvent.
            mc.options.hideGui=false;
            server.execute(() -> camera(server,.5,80.5,7,180,12));
        }
        if(ticks==475) server.execute(() -> {
            var player=server.getPlayerList().getPlayers().getFirst();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
        });
        if(ticks==505) { mc.options.hideGui=false;mc.setScreen(new InventoryScreen(mc.player)); }
        if(ticks==535) { mc.setScreen(null);mc.options.hideGui=true; }
        if(ticks==540) server.execute(() -> server.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY));
        if(ticks==565 && JehoashRobeClient.isWearing(mc.player)) throw new AssertionError("Unequip did not synchronize");
        if(ticks==580) server.execute(() -> server.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.JEHOASH_ROBE.get())));
        if(ticks==610) {
            if(!JehoashRobeClient.isWearing(mc.player)) throw new AssertionError("Re-equip did not synchronize");
            if(frame<60) throw new AssertionError("Player cloth capture missing");
            try { Files.write(output.resolve("frames.csv"),times); } catch(java.io.IOException e) { throw new AssertionError(e); }
            System.out.println("ROBE_VISUAL PASS classic + slim, server equipment sync, movement, crouch, jump, bow, swim, ride, other armor, first person, inventory, unequip/re-equip; frames="+frame);
            classic.discard();slim.discard();mc.stop();
        }
        for(var player:List.of(classic,slim)) {
            boolean crouch=ticks>=180 && ticks<225,swim=ticks>=320 && ticks<355;
            player.setShiftKeyDown(crouch);player.setSwimming(swim);
            player.setPose(swim?Pose.SWIMMING:crouch?Pose.CROUCHING:Pose.STANDING);
            player.setYRot(0);player.yRotO=0;player.yBodyRot=player.yBodyRotO=0;
            player.yHeadRot=player.yHeadRotO=ticks>=420 && ticks<445?50:0;
            player.setXRot(ticks>=420 && ticks<445?25:0);
            player.walkAnimation.update(ticks>=130 && ticks<180?.65f:0,1);
            double y=80+(ticks>=230 && ticks<265?.8*Math.sin((ticks-230)/35.0*Math.PI):0);
            if(!player.isPassenger()) player.setPos(player==classic?-1:2,y,.5);
            if(ticks==270) {
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
                player.startUsingItem(InteractionHand.MAIN_HAND);
            }
            if(ticks==315) { player.stopUsingItem();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("cabinmod.visualSmoke.robeReview") || output==null) return;
        var mc=Minecraft.getInstance();
        String label=switch(ticks) {
            case 50 -> "front"; case 80 -> "back"; case 110 -> "side";
            case 160 -> "walk";case 205 -> "crouch";case 247 -> "jump";case 295 -> "bow";
            case 345 -> "swim";case 385 -> "ride";case 430 -> "armor-and-head-turn";
            case 460 -> "first-person";case 490 -> "first-person-item";case 520 -> "inventory";
            case 565 -> "unequipped";case 600 -> "re-equipped";default -> null;
        };
        if(label!=null && stills.add(ticks)) capture(mc,label+".png");
        if(ticks<130 || ticks>=400) return;
        int bucket=Mth.floor((ticks+event.getPartialTick().getGameTimeDeltaPartialTick(false))*3);
        if(bucket!=lastBucket) { lastBucket=bucket;capture(mc,String.format(java.util.Locale.ROOT,"motion-%04d.png",frame++)); }
    }

    private static void capture(Minecraft mc,String name) {
        times.add(name+","+String.format(java.util.Locale.ROOT,"%.6f",(System.nanoTime()-start)/1e9));
        Screenshot.grab(output.toFile(),name,mc.getMainRenderTarget(),message -> {});
    }

    private static void camera(net.minecraft.server.MinecraftServer server,double x,double y,double z,float yaw,float pitch) {
        var player=server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(server.overworld(),x,y,z,yaw,pitch);
        player.getAbilities().flying=true;player.onUpdateAbilities();
    }

    private static void clearOldPreviewEntities(net.minecraft.server.MinecraftServer server) {
        var area=new net.minecraft.world.phys.AABB(new net.minecraft.core.BlockPos(0,80,0)).inflate(40);
        for(var entity:server.overworld().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,area))
            if(!(entity instanceof net.minecraft.server.level.ServerPlayer)) entity.discard();
    }

    private static final class PreviewPlayer extends RemotePlayer {
        private final PlayerSkin skin;
        PreviewPlayer(boolean slim,int id) {
            super(Minecraft.getInstance().level,new GameProfile(id(slim),slim?"RobeSlim":"RobeClassic"));
            skin=DefaultPlayerSkin.get(getUUID());setId(id);setNoGravity(true);
            setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.JEHOASH_ROBE.get()));
        }
        @Override public PlayerSkin getSkin() { return skin; }
        private static UUID id(boolean slim) {
            for(int n=1;n<100;n++) {
                UUID id=new UUID(0,n);
                if((DefaultPlayerSkin.get(id).model()==PlayerSkin.Model.SLIM)==slim) return id;
            }
            throw new AssertionError("No matching vanilla skin");
        }
    }
}
