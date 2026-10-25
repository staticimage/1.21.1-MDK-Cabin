package net.austin.cabinmod.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.block.entity.DjBoothBlockEntity;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.entity.RecordTracks;
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
import java.util.Map;
import java.util.function.Supplier;

// Selected CabinMod records play continuously from the attached boss or occupied booth.
// The local audio clock drives both the DJ and decks; pause shifts that clock with the sound engine.
// Per-song beat/energy maps come from tools/audio/analyze_records.py.
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
/*
 * AUDIO / SYNC MAP
 * Playback is client-local. RecordTracks chooses one of the six existing mod records;
 * RecordDance supplies measured beat timestamps and energy sampled from those files.
 * Jehoash's DJ choreography follows this playback clock, including game pauses.
 * The rave screen intentionally does NOT read this class: it shows original stills.
 * Late listeners start their own local song; this is not cross-client sample seeking.
 * The server still owns the selected record index and the single booth reward.
 * AUDIO HANDOFF: the boss UUID is the key for BOTH the attached model and its saved
 * booth block. First hit, phase changes, teleport and victory never replace the
 * Playing/MusicSound objects or their start time. Only their spatial source changes.
 * Locked means the record cannot be taken; it does not mute the song. Looping keeps
 * long fights supplied with music. Removing the record stops its sound next tick.
 */
public final class DiscoMusic {
    public static final float BPM = 114.0f; // authored tempo of the booth control clip, not the playing record
    public static final float JAM_BEATS = 32f;                        // jam_loop is 8 bars
    public static final float RECORD_DEGREES_PER_SECOND = 200f;       // 33 1/3 rpm
    private static final double HEAR_RANGE = 128;

    private static final Map<Object, Playing> PLAYING = new HashMap<>();
    private static final Map<Object, Float> INTERRUPTED_AT_BEAT = new HashMap<>();
    private static final Map<BlockPos, Long> BOOTHS = new HashMap<>();      // playing booth blocks -> game time last seen
    private static ClientLevel lastLevel;
    private static long pausedSince = -1;

    private static final class Playing {
        final MusicSound sound;
        final int track;
        long startNanos;

        Playing(MusicSound sound, int track) {
            this.track = track;
            this.sound = sound;
            this.startNanos = System.nanoTime();
        }

        float seconds() {
            return ((now() - startNanos) / 1e9f) % RecordDance.of(track).seconds();
        }
    }

    private DiscoMusic() {
    }

    public static Object key(Entity entity) {
        return entity.getUUID();
    }

    public static Object key(BlockPos boothCenter) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(boothCenter) instanceof DjBoothBlockEntity booth && booth.bossId() != null)
            return booth.bossId();
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
        Playing p = PLAYING.get(key);
        return RecordDance.of(p == null ? 0 : p.track).beat(t);
    }

    // jam_loop time in milliseconds for a beat count (the clip is 32 beats long).
    public static long jamMillis(float beats) {
        float b = beats % JAM_BEATS;
        if (b < 0) b += JAM_BEATS;
        return (long) (b * 60000f / BPM);
    }

    // Measured energy envelope of the selected song.
    public static float energy(Object key) {
        float t = trackSeconds(key);
        Playing p = PLAYING.get(key);
        return t < 0 ? 0.5f : RecordDance.of(p == null ? 0 : p.track).intensity(t);
    }

    // The beat at which this source was interrupted (for blending his jam pose into the upset clip), or NaN.
    public static float interruptedAtBeat(Object key) {
        Float b = INTERRUPTED_AT_BEAT.get(key);
        return b == null ? Float.NaN : b;
    }

    // Platter angle stays continuous across activation and booth separation.
    public static float platterDegrees(Object key) {
        Playing p = PLAYING.get(key);
        if (p == null) return 0f;
        float playSeconds = (now() - p.startNanos) / 1e9f;
        float angle = playSeconds * RECORD_DEGREES_PER_SECOND;
        return angle % 360f;
    }

    // Wall clock that stands still while the game (and so its sound) is paused.
    private static long now() {
        return pausedSince >= 0 ? pausedSince : System.nanoTime();
    }

    public static boolean isPlaying(Object key) {
        Playing p = PLAYING.get(key);
        return p != null && !p.sound.isStopped();
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
            pausedSince = -1;
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
            }
            pausedSince = -1;
        }

        // what should be playing right now
        Map<Object, Supplier<Vec3>> wanted = new HashMap<>();
        Map<Object, Integer> tracks = new HashMap<>();
        for (JehoashEntity jehoash : mc.level.getEntitiesOfClass(JehoashEntity.class,
                new AABB(mc.player.blockPosition()).inflate(HEAR_RANGE), Entity::isAlive)) {
            Object key = key(jehoash);
            if (jehoash.isBoothAttached()) {
                // Capture the fixed stage position, not a supplier following his later teleport.
                Vec3 at = jehoash.boothSoundPosition();
                wanted.put(key, () -> at);
                tracks.put(key, jehoash.recordTrack());
            } else if (PLAYING.containsKey(key)) {
                // Bridge entity/block packet ordering without stopping/restarting the stream.
                // The booth entry below takes over the position when its update arrives.
                wanted.put(key, PLAYING.get(key).sound.source);
                tracks.put(key, jehoash.recordTrack());
            }
            if (!jehoash.isDormant()) INTERRUPTED_AT_BEAT.computeIfAbsent(key, k -> beats(k, 0));
        }
        long now = mc.level.getGameTime();                        // stands still under /tick freeze, like the tickers
        BOOTHS.entrySet().removeIf(e -> now - e.getValue() > 5);
        for (BlockPos pos : BOOTHS.keySet()) {
            if (pos.distToCenterSqr(mc.player.position()) < HEAR_RANGE * HEAR_RANGE) {
                Vec3 at = Vec3.atBottomCenterOf(pos).add(0, 1.5, 0);
                if (mc.level.getBlockEntity(pos) instanceof DjBoothBlockEntity booth && booth.isPlaying()) {
                    wanted.put(key(pos), () -> at);
                    tracks.put(key(pos), booth.recordTrack());
                }
            }
        }

        // Combat transitions deliberately do not stop, pitch-shift or restart the record.
        PLAYING.entrySet().removeIf(e -> {
            Playing p = e.getValue();
            if (p.sound.isStopped()) {
                return true;
            }
            if (wanted.containsKey(e.getKey()) && p.track == tracks.getOrDefault(e.getKey(), -1)) {
                p.sound.source = wanted.get(e.getKey());
                return false;
            }
            p.sound.stopNow();
            return true;
        });

        // start what should
        for (Map.Entry<Object, Supplier<Vec3>> e : wanted.entrySet()) {
            if (!PLAYING.containsKey(e.getKey())) {
                int track = tracks.getOrDefault(e.getKey(), 0);
                MusicSound sound = new MusicSound(e.getValue(), track);
                mc.getSoundManager().play(sound);
                PLAYING.put(e.getKey(), new Playing(sound, track));
                INTERRUPTED_AT_BEAT.remove(e.getKey());
            }
        }
    }

    private static final class MusicSound extends AbstractTickableSoundInstance {
        private Supplier<Vec3> source;

        MusicSound(Supplier<Vec3> source, int track) {
            super(RecordTracks.sound(track), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.source = source;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0f;
            this.pitch = 1.0f;
            // A room soundtrack must remain audible at the far wall. Apply our own
            // falloff outside 32 blocks instead of the records' short default radius.
            this.attenuation = SoundInstance.Attenuation.NONE;
            Vec3 at = source.get();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        void stopNow() {
            this.stop();
        }

        @Override
        public void tick() {
            Vec3 at = this.source.get();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            var listener = Minecraft.getInstance().player;
            if (listener != null) this.volume = (float)Math.clamp((HEAR_RANGE - listener.position().distanceTo(at)) / (HEAR_RANGE - 32), 0, 1);
        }
    }
}
