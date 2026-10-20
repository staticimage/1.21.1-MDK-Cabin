package net.austin.cabinmod.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

// The Disco's phonk always plays from a DJ booth: the one built into a dormant Jehoash, or a placed booth
// block someone switched on. Each source has its own beat clock (started when this client starts its
// playback), which drives Jehoash's jam and the booth's moving parts. When Jehoash is interrupted his
// music tape-stops: pitch drags down while the volume dies.
// Track facts come from tools/audio/build_music.py (design/disco_music.json).
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DiscoMusic {
    public static final float BPM = 114.0f;
    public static final float FIRST_BEAT_SECONDS = 0.0058f;
    public static final float TRACK_SECONDS = 147.41f;
    public static final float KICK_ENTERS_SECONDS = 6.04f;
    public static final float JAM_BEATS = 32f;                        // jam_loop is 8 bars
    public static final float RECORD_DEGREES_PER_SECOND = 200f;       // 33 1/3 rpm
    private static final double HEAR_RANGE = 128;
    private static final float TAPE_STOP_SECONDS = 0.35f;              // platter spin-down time constant

    private static final Map<Object, Playing> PLAYING = new HashMap<>();
    private static final Map<Object, Float> INTERRUPTED_AT_BEAT = new HashMap<>();
    private static final Map<BlockPos, Long> BOOTHS = new HashMap<>();      // playing booth blocks -> game time last seen
    private static ClientLevel lastLevel;
    private static long pausedSince = -1;

    private static final class Playing {
        final MusicSound sound;
        long startNanos;
        long stopNanos = -1;

        Playing(MusicSound sound) {
            this.sound = sound;
            this.startNanos = System.nanoTime();
        }

        float seconds() {
            long end = stopNanos >= 0 ? stopNanos : now();
            return ((end - startNanos) / 1e9f) % TRACK_SECONDS;
        }
    }

    private DiscoMusic() {
    }

    public static Object key(Entity entity) {
        return "joash:" + entity.getId();
    }

    public static Object key(BlockPos boothCenter) {
        return boothCenter.immutable();
    }

    // Seconds into the (looping) track for this source, or -1 when it isn't playing here.
    public static float trackSeconds(Object key) {
        Playing p = PLAYING.get(key);
        return p == null ? -1f : p.seconds();
    }

    // Beats since the track's first beat; falls back to game time when the source is silent.
    public static float beats(Object key, float ageInTicks) {
        float t = trackSeconds(key);
        if (t < 0) t = ageInTicks / 20f;
        return (t - FIRST_BEAT_SECONDS) * BPM / 60f;
    }

    // jam_loop time in milliseconds for a beat count (the clip is 32 beats long).
    public static long jamMillis(float beats) {
        float b = beats % JAM_BEATS;
        if (b < 0) b += JAM_BEATS;
        return (long) (b * 60000f / BPM);
    }

    // 0.4 during the intro, 1 once the kick drum is in.
    public static float energy(Object key) {
        float t = trackSeconds(key);
        return t >= 0 && t < KICK_ENTERS_SECONDS ? 0.4f : 1f;
    }

    // The beat at which this source was interrupted (for blending his jam pose into the upset clip), or NaN.
    public static float interruptedAtBeat(Object key) {
        Float b = INTERRUPTED_AT_BEAT.get(key);
        return b == null ? Float.NaN : b;
    }

    // Platter angle: spins at 33 1/3 while playing, spins down after a tape stop, still when silent.
    public static float platterDegrees(Object key) {
        Playing p = PLAYING.get(key);
        if (p == null) return 0f;
        float playSeconds = ((p.stopNanos >= 0 ? p.stopNanos : now()) - p.startNanos) / 1e9f;
        float angle = playSeconds * RECORD_DEGREES_PER_SECOND;
        if (p.stopNanos >= 0) {
            float since = (now() - p.stopNanos) / 1e9f;
            angle += RECORD_DEGREES_PER_SECOND * TAPE_STOP_SECONDS * (1f - (float) Math.exp(-since / TAPE_STOP_SECONDS));
        }
        return angle % 360f;
    }

    // Wall clock that stands still while the game (and so its sound) is paused.
    private static long now() {
        return pausedSince >= 0 ? pausedSince : System.nanoTime();
    }

    public static boolean isPlaying(Object key) {
        Playing p = PLAYING.get(key);
        return p != null && p.stopNanos < 0;
    }

    // Called every client tick by a booth block's ticker.
    public static void tickBooth(DjBoothBlockEntity booth) {
        if (booth.isPlaying() && booth.getLevel() != null) BOOTHS.put(booth.getBlockPos().immutable(), booth.getLevel().getGameTime());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            PLAYING.values().forEach(p -> p.sound.stopNow());
            PLAYING.clear();
            INTERRUPTED_AT_BEAT.clear();
            BOOTHS.clear();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.player == null) return;

        // the pause menu pauses the sound channels: keep the beat clocks in step by shifting them past the pause
        if (mc.isPaused()) {
            if (pausedSince < 0) pausedSince = System.nanoTime();
            return;
        }
        if (pausedSince >= 0) {
            long paused = System.nanoTime() - pausedSince;
            for (Playing p : PLAYING.values()) {
                p.startNanos += paused;
                if (p.stopNanos >= 0) p.stopNanos += paused;
            }
            pausedSince = -1;
        }

        // what should be playing right now
        Map<Object, Supplier<Vec3>> wanted = new HashMap<>();
        Set<Object> interrupted = new HashSet<>();
        for (JehoashEntity jehoash : mc.level.getEntitiesOfClass(JehoashEntity.class,
                new AABB(mc.player.blockPosition()).inflate(HEAR_RANGE), Entity::isAlive)) {
            Object key = key(jehoash);
            if (jehoash.isDormant() && jehoash.isBoothAttached()) {
                wanted.put(key, jehoash::boothSoundPosition);
            } else {
                interrupted.add(key);
            }
        }
        long now = mc.level.getGameTime();                        // stands still under /tick freeze, like the tickers
        BOOTHS.entrySet().removeIf(e -> now - e.getValue() > 5);
        for (BlockPos pos : BOOTHS.keySet()) {
            if (pos.distToCenterSqr(mc.player.position()) < HEAR_RANGE * HEAR_RANGE) {
                Vec3 at = Vec3.atBottomCenterOf(pos).add(0, 1.5, 0);
                wanted.put(key(pos), () -> at);
            }
        }

        // stop what shouldn't play: an interrupted Jehoash tape-stops, everything else just stops
        PLAYING.entrySet().removeIf(e -> {
            Playing p = e.getValue();
            if (p.sound.isStopped() && p.stopNanos < 0) {
                return true;
            }
            if (wanted.containsKey(e.getKey())) return false;
            if (interrupted.contains(e.getKey())) {
                if (p.stopNanos < 0) {
                    INTERRUPTED_AT_BEAT.put(e.getKey(), beats(e.getKey(), 0));
                    p.stopNanos = System.nanoTime();
                    p.sound.tapeStop();
                }
                return p.sound.isStopped() && System.nanoTime() - p.stopNanos > 3_000_000_000L;
            }
            p.sound.stopNow();
            return true;
        });

        // start what should
        for (Map.Entry<Object, Supplier<Vec3>> e : wanted.entrySet()) {
            if (!PLAYING.containsKey(e.getKey())) {
                MusicSound sound = new MusicSound(e.getValue());
                mc.getSoundManager().play(sound);
                PLAYING.put(e.getKey(), new Playing(sound));
                INTERRUPTED_AT_BEAT.remove(e.getKey());
            }
        }
    }

    private static final class MusicSound extends AbstractTickableSoundInstance {
        private final Supplier<Vec3> source;
        private boolean stopping;

        MusicSound(Supplier<Vec3> source) {
            super(ModSounds.DISCO_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.source = source;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0f;
            this.pitch = 1.0f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            Vec3 at = source.get();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        void tapeStop() {
            this.stopping = true;
        }

        void stopNow() {
            this.stop();
        }

        @Override
        public void tick() {
            if (this.stopping) {
                // the needle drags: pitch sinks (the engine floors it at 0.5) while the volume dies
                this.pitch = Math.max(0.5f, this.pitch - 0.04f);
                this.volume -= 0.075f;
                if (this.volume <= 0f) {
                    this.volume = 0f;
                    this.stop();
                }
                return;
            }
            Vec3 at = this.source.get();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }
    }
}
