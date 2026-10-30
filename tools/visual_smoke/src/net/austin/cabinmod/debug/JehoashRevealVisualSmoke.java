package net.austin.cabinmod.debug;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Stage-time-based capture: -I tools/visual_smoke/smoke.gradle -PraveRevealReview runClient. */
@EventBusSubscriber(modid=CabinMod.MOD_ID,value=Dist.CLIENT)
public final class JehoashRevealVisualSmoke {
    private static int ticks, shot, lastBucket=-1;
    private static boolean cameraPlaced, teleportStarted, teleportCameraPlaced;
    private static java.io.File captureRoot;
    private static JehoashEntity garmentPreview;
    private static int teleportFrames, motionFrames;
    private static net.minecraft.world.phys.Vec3 beforeTeleport;
    private static final int FPS=Integer.getInteger("cabinmod.visualSmoke.revealFps",60);
    private static final String SIDE=System.getProperty("cabinmod.visualSmoke.revealSide","front");
    private static final java.util.List<Double> frameTimes=new java.util.ArrayList<>();
    private static long lastFrame, captureStart;
    private static final java.util.List<String> captureTimes=new java.util.ArrayList<>();

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("cabinmod.visualSmoke.revealReview")) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) return;
        ticks++;
        if(ticks==1) {
            mc.options.pauseOnLostFocus=false; mc.options.hideGui=true;
            var path=mc.gameDirectory.toPath().resolve("brg-review/"+SIDE+"-"+FPS+"-"+
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            try {
                java.nio.file.Files.createDirectories(path);
                try(var texture=mc.getResourceManager().getResource(net.austin.cabinmod.entity.client.JehoashModel.BODY_TEXTURE).orElseThrow().open()) {
                    var digest=java.security.MessageDigest.getInstance("SHA-256").digest(texture.readAllBytes());
                    java.nio.file.Files.writeString(path.resolve("body.sha256"),java.util.HexFormat.of().formatHex(digest));
                }
            } catch(java.io.IOException | java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
            captureRoot=path.toFile(); captureStart=System.nanoTime();
            captureTimes.add("file,seconds");
            System.out.println("BRG_CAPTURE "+captureRoot.getAbsolutePath());
            mc.options.enableVsync().set(false); mc.options.framerateLimit().set(FPS);
            JehoashRevealChecks.run();
        }
        var server=mc.getSingleplayerServer();
        if(ticks==2) server.execute(() -> {
            // Chunks may finish loading saved entities after ServerStartedEvent.
            var area=new net.minecraft.world.phys.AABB(new net.minecraft.core.BlockPos(0,80,0)).inflate(40);
            for(var old:server.overworld().getEntitiesOfClass(net.austin.cabinmod.entity.custom.ClipMob.class,area))
                if(old!=RaveVisualSmoke.boss) old.discard();
        });
        if(ticks==10) server.execute(() -> {
            var entity=net.austin.cabinmod.entity.ModEntities.JOASH.get().create(server.overworld());
            entity.setPos(.5,80,.5); entity.setNoAi(true);
            previewData(entity,"DORMANT",false); previewData(entity,"BOOTH_ATTACHED",false);
            previewData(entity,"STAGE_PHASE",JehoashEntity.WAITING);
            server.overworld().addFreshEntity(entity); garmentPreview=entity;
            placeCamera(server,entity);
        });
        if(ticks==60) server.execute(() -> { garmentPreview.discard(); garmentPreview=null; });
        if(ticks==80) server.execute(() -> {
            var boss=RaveVisualSmoke.boss;
            var player=server.getPlayerList().getPlayers().getFirst();
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,2400,0,false,false));
            player.teleportTo(server.overworld(),.5,81,5,180,0);
            player.getAbilities().flying=true; player.onUpdateAbilities();
            // A previous interrupted preview may have saved a falling rig.
            net.austin.cabinmod.worldgen.RaveArena.restoreRig(server.overworld(),boss);
            boss.setNoAi(true);
            for(int side=0;side<2;side++) for(int hit=0;hit<3;hit++)
                boss.damageSupport(side,server.overworld().damageSources().playerAttack(player),20);
            if(!boss.isCollapsing()) throw new AssertionError("Preview supports did not start collapse: phase="+boss.stagePhase()
                    +" rig="+net.austin.cabinmod.worldgen.RaveArena.hasRig(server.overworld(),boss.arenaCenter()));
        });
        if(!cameraPlaced && RaveVisualSmoke.boss!=null && RaveVisualSmoke.boss.isCollapsing()
                && RaveVisualSmoke.boss.stageTime()>=65) {
            cameraPlaced=true;
            server.execute(() -> {
                var boss=RaveVisualSmoke.boss;
                var player=server.getPlayerList().getPlayers().getFirst();
                // Stable inspection angle in this disposable world. Gameplay runs are unchanged.
                boss.setNoAi(true); boss.setYRot(0); boss.setYBodyRot(0); boss.setYHeadRot(0);
                placeCamera(server,boss);
            });
        }
        if(ticks==230) server.execute(() -> {
            var boss=RaveVisualSmoke.boss;
            beforeTeleport=boss.position();
            try {
                var method=net.austin.cabinmod.entity.custom.ClipMob.class.getDeclaredMethod("startAction",String.class);
                method.setAccessible(true); method.invoke(boss,"teleport");
            } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
            teleportStarted=true;
        });
        if(teleportStarted && !teleportCameraPlaced && beforeTeleport.distanceTo(RaveVisualSmoke.boss.position())>.5) {
            teleportCameraPlaced=true;
            server.execute(() -> placeCamera(server,RaveVisualSmoke.boss));
        }
        if(ticks==240) server.execute(() -> {
            var boss=RaveVisualSmoke.boss;
            System.out.println("TELEPORT_REVIEW action="+boss.currentAction()+" from="+beforeTeleport+" to="+boss.position());
        });
        if(ticks==285) server.execute(() -> {
            var boss=RaveVisualSmoke.boss;
            if(beforeTeleport.distanceTo(boss.position())<.5) throw new AssertionError("Teleport marker did not move boss");
            System.out.println("TELEPORT_REVIEW PASS actual blink moved "+beforeTeleport.distanceTo(boss.position())+" blocks");
        });
        if(ticks==300) server.execute(() -> {
            var area=RaveVisualSmoke.boss.getBoundingBox().inflate(40);
            for(var illusion:server.overworld().getEntitiesOfClass(net.austin.cabinmod.entity.custom.JehoashIllusionEntity.class,area)) illusion.discard();
            // Keep subsequent outfit inspection inside the room after a real blink.
            RaveVisualSmoke.boss.teleportTo(.5,80,.5);
            RaveVisualSmoke.boss.setYRot(0); RaveVisualSmoke.boss.setYBodyRot(0); RaveVisualSmoke.boss.setYHeadRot(0);
            placeCamera(server,RaveVisualSmoke.boss);
        });
        if(ticks>=305 && ticks<340) server.execute(() -> {
            var boss=RaveVisualSmoke.boss;
            boss.setDeltaMovement(.075,boss.getDeltaMovement().y,0);
        });
        if(ticks==340) server.execute(() -> {
            RaveVisualSmoke.boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            startClip(RaveVisualSmoke.boss,"attack_melee"); placeCamera(server,RaveVisualSmoke.boss);
        });
        if(ticks==380) server.execute(() -> startClip(RaveVisualSmoke.boss,"attack_ranged"));
        if(ticks==445) server.execute(() -> RaveVisualSmoke.boss.kill());
        if(ticks==550) {
            if(!RaveVisualSmoke.boss.isRemoved()) throw new AssertionError("Death clip did not finish");
            if(motionFrames<90) throw new AssertionError("Outfit motion capture missing: "+motionFrames);
            if(teleportFrames<10) throw new AssertionError("Teleport capture missing: "+teleportFrames);
            if(shot<30) throw new AssertionError("Reveal capture missing: "+shot+" frames");
            frameTimes.sort(Double::compareTo);
            System.out.printf(java.util.Locale.ROOT,"REVEAL_VISUAL PASS %s cap=%d frames=%d median=%.2fms p95=%.2fms%n",
                    SIDE,FPS,shot,frameTimes.get(frameTimes.size()/2),frameTimes.get((int)(frameTimes.size()*.95)));
            try { java.nio.file.Files.write(captureRoot.toPath().resolve("frames.csv"),captureTimes); }
            catch(java.io.IOException e) { throw new AssertionError(e); }
            mc.stop();
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!Boolean.getBoolean("cabinmod.visualSmoke.revealReview")) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || RaveVisualSmoke.boss==null) return;
        if(ticks==45) capture(mc,"cloak-"+SIDE+".png");
        if(!cameraPlaced) return;
        var entity=mc.level.getEntity(RaveVisualSmoke.boss.getId());
        if(!(entity instanceof JehoashEntity boss)) return;
        float time=boss.stageTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean teleport=teleportStarted && ticks<286;
        String motion=ticks>=305 && ticks<340 ? "walk" : ticks>=340 && ticks<380 ? "melee"
                : ticks>=380 && ticks<435 ? "ranged" : ticks>=445 && ticks<540 ? "death" : null;
        if(!teleport && motion==null && (time<76 || time>=128)) return;
        long now=System.nanoTime();
        if(lastFrame!=0) frameTimes.add((now-lastFrame)/1e6);
        lastFrame=now;
        int bucket=(teleport || motion!=null) ? Mth.floor((ticks+event.getPartialTick().getGameTimeDeltaPartialTick(false))*3)+10000 : Mth.floor(time*3); // Up to 60 samples/second, including fractional tick boundaries.
        if(bucket==lastBucket) return;
        lastBucket=bucket;
        if(motion!=null) {
            capture(mc,String.format(java.util.Locale.ROOT,"%s-%s-%d-%04d.png",motion,SIDE,FPS,motionFrames++));
        } else if(teleport) {
            capture(mc,String.format(java.util.Locale.ROOT,"teleport-%s-%d-%04d.png",SIDE,FPS,teleportFrames++));
        } else capture(mc,String.format(java.util.Locale.ROOT,"reveal-%s-%d-%04d-t%06.2f.png",SIDE,FPS,shot++,time));
    }
    private static void capture(Minecraft mc,String file) {
        captureTimes.add(file+","+String.format(java.util.Locale.ROOT,"%.6f",(System.nanoTime()-captureStart)/1e9));
        Screenshot.grab(captureRoot,file,mc.getMainRenderTarget(),c -> {});
    }
    private static void placeCamera(net.minecraft.server.MinecraftServer server,JehoashEntity boss) {
        var player=server.getPlayerList().getPlayers().getFirst();
        boolean side=SIDE.equals("side"), back=SIDE.equals("back"), quarter=SIDE.equals("three-quarter");
        player.teleportTo(server.overworld(),boss.getX()+(side?5.5:quarter?3.9:0),boss.getY()+.7,
                boss.getZ()+(side?0:back?-2.8:quarter?3.9:5.5),side?90:back?0:quarter?135:180,7);
        player.getAbilities().flying=true; player.onUpdateAbilities();
    }
    private static void startClip(JehoashEntity boss,String clip) {
        try {
            var method=net.austin.cabinmod.entity.custom.ClipMob.class.getDeclaredMethod("startAction",String.class);
            method.setAccessible(true); method.invoke(boss,clip);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @SuppressWarnings("unchecked")
    private static <T> void previewData(JehoashEntity entity,String name,T value) {
        try {
            var field=JehoashEntity.class.getDeclaredField(name); field.setAccessible(true);
            entity.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<T>)field.get(null),value);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }

}
