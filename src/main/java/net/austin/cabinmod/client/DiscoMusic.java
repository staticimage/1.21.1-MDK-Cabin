package net.austin.cabinmod.client;

import net.austin.cabinmod.CabinMod;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.austin.cabinmod.sounds.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;

// The Disco's rave track, looped from each Jehoash, plus the beat clock his jam animation locks to.
// Track facts come from tools/audio/build_music.py (design/disco_music.json).
@EventBusSubscriber(modid = CabinMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class DiscoMusic {
    public static final float BPM = 114.0f;
    public static final float FIRST_BEAT_SECONDS = 0.0058f;
    public static final float TRACK_SECONDS = 147.41f;
    public static final float KICK_ENTERS_SECONDS = 6.04f;
    private static final double HEAR_RANGE = 64;

    private static final Map<Integer, Playing> PLAYING = new HashMap<>();

    private record Playing(MusicSound sound, long startNanos) {
    }

    private DiscoMusic() {
    }

    // Seconds into the (looping) track for this Jehoash, or -1 if his music isn't playing here.
    public static float trackSeconds(Entity entity) {
        Playing p = PLAYING.get(entity.getId());
        if (p == null) return -1f;
        return ((System.nanoTime() - p.startNanos()) / 1e9f) % TRACK_SECONDS;
    }

    // Beats since the first beat of the track (fractional); falls back to game time when silent.
    public static float beats(Entity entity, float ageInTicks) {
        float t = trackSeconds(entity);
        if (t < 0) t = ageInTicks / 20f;
        return (t - FIRST_BEAT_SECONDS) * BPM / 60f;
    }

    // 0 during the intro, 1 once the kick drum is in.
    public static float energy(Entity entity) {
        float t = trackSeconds(entity);
        return t >= 0 && t < KICK_ENTERS_SECONDS ? 0.4f : 1f;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PLAYING.clear();
            return;
        }
        PLAYING.entrySet().removeIf(e -> e.getValue().sound().isStopped());
        for (JehoashEntity jehoash : mc.level.getEntitiesOfClass(JehoashEntity.class,
                new AABB(mc.player.blockPosition()).inflate(HEAR_RANGE), Entity::isAlive)) {
            if (!PLAYING.containsKey(jehoash.getId())) {
                MusicSound sound = new MusicSound(jehoash);
                mc.getSoundManager().play(sound);
                PLAYING.put(jehoash.getId(), new Playing(sound, System.nanoTime()));
            }
        }
    }

    private static final class MusicSound extends AbstractTickableSoundInstance {
        private final JehoashEntity source;

        MusicSound(JehoashEntity source) {
            super(ModSounds.DISCO_MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.source = source;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = source.getX();
            this.y = source.getY() + 1.5;
            this.z = source.getZ();
        }

        @Override
        public void tick() {
            if (this.source.isRemoved() || !this.source.isAlive()) {
                this.stop();
                return;
            }
            this.x = this.source.getX();
            this.y = this.source.getY() + 1.5;
            this.z = this.source.getZ();
        }
    }
}
