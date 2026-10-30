package net.austin.cabinmod.debug;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.ModBlocks;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.worldgen.RaveArena;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in visual harness, compiled only by tools/visual_smoke/smoke.gradle. */
@EventBusSubscriber(modid=CabinMod.MOD_ID)
public final class RaveVisualSmoke {
    static JehoashEntity boss;
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("cabinmod.visualSmoke")) return;
        var level=event.getServer().overworld();
        var center=new BlockPos(0,80,0);
        for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) level.getChunk(x,z);
        if(Boolean.getBoolean("cabinmod.visualSmoke.robeReview")) {
            var area=new net.minecraft.world.phys.AABB(center).inflate(40);
            for(var old:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,area))
                if(!(old instanceof ServerPlayer)) old.discard();
            for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) {
                level.setBlock(center.offset(x,-1,z),net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                for(int y=0;y<=10;y++) level.setBlock(center.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
            }
            level.setDayTime(6000);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
            return;
        }
        if(Boolean.getBoolean("cabinmod.visualSmoke.revealReview")) {
            var area=new net.minecraft.world.phys.AABB(center).inflate(40);
            for(var old:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,area))
                if(!(old instanceof ServerPlayer)) old.discard();
        }
        boss=RaveArena.build(level,center);
        level.setBlock(center.offset(9,0,-4),ModBlocks.BURNING_PORTAPOTTY.get().defaultBlockState()
                .setValue(net.austin.cabinmod.block.custom.BurningPortapottyBlock.FACING,net.minecraft.core.Direction.SOUTH),3);
        var propPos = center.offset(9,0,-4);
        ModBlocks.BURNING_PORTAPOTTY.get().setPlacedBy(level,propPos,level.getBlockState(propPos),null,net.minecraft.world.item.ItemStack.EMPTY);
        level.setDefaultSpawnPos(center.offset(0,0,7),180);
        level.setDayTime(6000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("cabinmod.visualSmoke") || !(event.getEntity() instanceof ServerPlayer p)) return;
        p.setGameMode(GameType.CREATIVE);p.setHealth(p.getMaxHealth());p.removeAllEffects();
        p.teleportTo(p.serverLevel(),0.5,83,10,180,16);
    }
    @EventBusSubscriber(modid=CabinMod.MOD_ID,value=net.neoforged.api.distmarker.Dist.CLIENT)
    public static class Client {
        static int ticks, waiting;
        static Object originalPlayback;
        @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (!Boolean.getBoolean("cabinmod.visualSmoke")) return;
            if (Boolean.getBoolean("cabinmod.visualSmoke.lightReview")
                    || Boolean.getBoolean("cabinmod.visualSmoke.revealReview")
                    || Boolean.getBoolean("cabinmod.visualSmoke.robeReview")) return;
            var mc=net.minecraft.client.Minecraft.getInstance();
            mc.options.pauseOnLostFocus = false;
            if(mc.level==null || mc.player==null) {
                if (++waiting % 100 == 0) System.out.println("VISUAL_SMOKE waiting: "+mc.screen);
                if (mc.screen != null && (mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                        || mc.screen.getClass().getSimpleName().contains("Backup"))) {
                    for(var child : mc.screen.children()) {
                        if(child instanceof net.minecraft.client.gui.components.Button button &&
                                java.util.List.of("Proceed","Yes","I know what I'm doing!","Continue").contains(button.getMessage().getString())) {
                            button.onPress();break;
                        }
                    }
                }
                return;
            }
            if(mc.player.isDeadOrDying()) { mc.player.respawn();mc.setScreen(null);return; }
            if (mc.isPaused()) return;
            if(ticks>=780)return;
            mc.options.hideGui=true;ticks++;
            var server=mc.getSingleplayerServer();
            // Verify actual client playback survives every phase. Reflection stays in
            // this opt-in harness, so no test-only hooks enter the production mod.
            if(ticks==80 || ticks==240 || ticks==488 || ticks==540 || ticks==660) {
                try {
                    var music=net.austin.cabinmod.client.DiscoMusic.class;
                    var field=music.getDeclaredField("PLAYING");field.setAccessible(true);
                    Object playing=((java.util.Map<?,?>)field.get(null)).get(boss.getUUID());
                    if(playing==null)throw new AssertionError("No fight music at tick "+ticks);
                    var trackField=playing.getClass().getDeclaredField("track");trackField.setAccessible(true);
                    if(trackField.getInt(playing)!=boss.recordTrack())throw new AssertionError("Client/server record mismatch at tick "+ticks);
                    if(originalPlayback==null)originalPlayback=playing;
                    if(playing!=originalPlayback)throw new AssertionError("Fight music restarted at tick "+ticks);
                    var soundField=playing.getClass().getDeclaredField("sound");soundField.setAccessible(true);
                    var sound=(net.minecraft.client.resources.sounds.SoundInstance)soundField.get(playing);
                    if(!mc.getSoundManager().isActive(sound))throw new AssertionError("Fight music channel inactive at tick "+ticks);
                    System.out.println("MUSIC_SMOKE PASS tick="+ticks+" same active sound, seconds="+net.austin.cabinmod.client.DiscoMusic.trackSeconds(boss.getUUID()));
                } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
            }
            // Capture-only protection. This harness is excluded from the release JAR.
            if(ticks%10==0 && ticks<570)server.execute(() -> {
                for(var p:server.getPlayerList().getPlayers()) { p.setHealth(p.getMaxHealth());p.getAbilities().invulnerable=true; }
            });
            if(ticks==40) server.execute(() -> {
                var level=server.overworld();var area=new net.minecraft.world.phys.AABB(new BlockPos(0,80,0)).inflate(35);
                for(var old:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,area)) {
                    if(old==boss || old instanceof ServerPlayer)continue;
                    if(old instanceof net.austin.cabinmod.entity.custom.RaveScreenEntity screen && !boss.getUUID().equals(screen.ownerId()))old.discard();
                    if(old instanceof net.austin.cabinmod.entity.custom.ClipMob || old instanceof net.austin.cabinmod.entity.custom.ArenaLightEntity
                            || old instanceof net.austin.cabinmod.entity.custom.TrussSupportEntity support && !boss.getUUID().equals(support.ownerId()) || old instanceof net.austin.cabinmod.entity.custom.FallingStageRigEntity
                            || old instanceof net.minecraft.world.entity.item.ItemEntity)old.discard();
                }
                net.austin.cabinmod.worldgen.RaveArena.transform(level,new BlockPos(0,80,0),false,12);
                net.austin.cabinmod.worldgen.RaveArena.installLights(level,boss);
            });
            if(ticks==90) server.execute(() -> camera(server,2.8,84.2,-5,145,24));
            if(ticks==115) server.execute(() -> camera(server,2.5,83,-10.8,50,8));
            if(ticks==140) server.execute(() -> {
                var p=server.getPlayerList().getPlayers().getFirst();camera(server,.5,81,5,180,-6);
                p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();
                for(var support:server.overworld().getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,new net.minecraft.world.phys.AABB(new BlockPos(0,80,0)).inflate(20)))
                    if(boss.getUUID().equals(support.ownerId()) && support.side()==0)support.hurt(server.overworld().damageSources().playerAttack(p),1);
            });
            if(ticks==170)server.execute(() -> { camera(server,-4.3,82,-6.2,137,20);var p=server.getPlayerList().getPlayers().getFirst();p.getAbilities().flying=false;p.onUpdateAbilities(); });
            if(ticks==220)server.execute(() -> camera(server,.5,81,5,180,-6));
            if(ticks==350)server.execute(() -> { camera(server,5.3,82,-6.2,223,20);var p=server.getPlayerList().getPlayers().getFirst();p.getAbilities().flying=false;p.onUpdateAbilities(); });
            if(ticks==378)server.execute(() -> camera(server,.5,81,5,180,-6));
            if(ticks==500 || ticks==520 || ticks==540)server.execute(() ->
                    camera(server,boss.getX(),boss.getY()+1.8,boss.getZ()+5,180,7));
            if(ticks==488 || ticks==520 || ticks==540 || ticks==565 || ticks==585)
                server.execute(() -> System.out.println("PHASE_SMOKE tick="+ticks+" phase="+boss.stagePhase()
                        +" stageTime="+boss.stageTime()+" attached="+boss.isBoothAttached()));
            if(ticks==180 || ticks==190 || ticks==200 || ticks==360 || ticks==370 || ticks==380) {
                int side=ticks<350?0:1;
                server.execute(() -> {
                    var level=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();
                    for(var support:level.getEntitiesOfClass(net.austin.cabinmod.entity.custom.TrussSupportEntity.class,new net.minecraft.world.phys.AABB(new BlockPos(0,80,0)).inflate(20)))
                        if(boss.getUUID().equals(support.ownerId()) && support.side()==side)support.hurt(level.damageSources().playerAttack(p),20);
                });
            }
            if((ticks>=92 && ticks<=112 || ticks>=184 && ticks<=218 || ticks>=220 && ticks<=250 || ticks>=382 && ticks<=490 || ticks>=510 && ticks<=550) && ticks%2==0)
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,String.format("review-anim-%03d.png",ticks),mc.getMainRenderTarget(),c -> {});
            String shot=switch(ticks) {
                case 80 -> "01-stage";case 110 -> "02-dj-hands";case 130 -> "03-cloth";case 240 -> "04-conductor-phase";
                case 198 -> "14-knockback";case 280 -> "13-stage-field";case 400 -> "05-buckling";case 432 -> "06-falling";case 444 -> "07-dodge";case 458 -> "08-impact";
                case 488 -> "09-landing";case 540 -> "10-on-foot";
                case 565 -> "15-face-on-foot";case 585 -> "16-face-close";
                case 610 -> "17-death";case 630 -> "18-death-late";
                case 660 -> "11-restored";case 730 -> "12-portapotty";default -> null;
            };
            if(shot!=null)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"review-"+shot+".png",mc.getMainRenderTarget(),c -> System.out.println("VISUAL_SMOKE "+c.getString()));
            if(ticks==550)server.execute(() -> boss.setNoAi(true));
            if(ticks==570)server.execute(() -> boss.kill());
            if(ticks==130 || ticks==540 || ticks==660)
                System.out.println("FABRIC_SMOKE " + net.austin.cabinmod.entity.client.physics.ClothSheet.drainPerformanceSample());
            if(ticks==690)server.execute(() -> { var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);camera(server,9.5,80,-.5,180,10); });
            if(ticks==780) {
                mc.options.hideGui=false;
                server.execute(() -> {
                    var level=server.overworld();var area=new net.minecraft.world.phys.AABB(new BlockPos(0,80,0)).inflate(35);
                    for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,area))if(!(e instanceof ServerPlayer))e.discard();
                    boss=RaveArena.build(level,new BlockPos(0,80,0));
                    var prop=new BlockPos(9,80,-4);var state=ModBlocks.BURNING_PORTAPOTTY.get().defaultBlockState().setValue(net.austin.cabinmod.block.custom.BurningPortapottyBlock.FACING,net.minecraft.core.Direction.SOUTH);
                    level.setBlock(prop,state,3);ModBlocks.BURNING_PORTAPOTTY.get().setPlacedBy(level,prop,state,null,net.minecraft.world.item.ItemStack.EMPTY);
                    var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.CREATIVE);camera(server,.5,80,1,180,-15);
                    p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD));
                    p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Multistage preview ready. Attack the orange support collars to start and advance the fight. Your camera stays yours during the collapse."));
                });
            }
        }
        private static void camera(net.minecraft.server.MinecraftServer server,double x,double y,double z,float yaw,float pitch) {
            var p=server.getPlayerList().getPlayers().getFirst();p.teleportTo(p.serverLevel(),x,y,z,yaw,pitch);
            p.getAbilities().flying=true;p.onUpdateAbilities();
        }
    }
}
