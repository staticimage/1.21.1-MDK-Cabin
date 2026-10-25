package net.austin.cabinmod.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.austin.cabinmod.entity.RecordTracks;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/** Per-song beat timestamps and energy measured from the actual bundled audio. */
public final class RecordDance {
    public record Profile(float bpm, float seconds, float[] beats, float[] energy) {
        public float beat(float seconds) {
            float t = Math.max(0, seconds % this.seconds);
            int i = Arrays.binarySearch(beats, t);
            if (i >= 0) return i;
            i = -i - 1;
            if (i == 0) return (t - beats[0]) * bpm / 60;
            if (i == beats.length) return i - 1 + (t - beats[i-1]) * bpm / 60;
            return i - 1 + (t - beats[i-1]) / (beats[i] - beats[i-1]);
        }
        public float intensity(float seconds) {
            return energy[Math.min(energy.length - 1, Math.max(0, (int)((seconds % this.seconds) * 10)))];
        }
    }
    private static final Map<String, Profile> PROFILES = load();
    private static Map<String, Profile> load() {
        try (var stream = RecordDance.class.getResourceAsStream("/assets/cabinmod/animations/record_dances.json")) {
            if (stream == null) throw new IllegalStateException("Missing record dance profiles");
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Profile>>(){}.getType());
        } catch (Exception e) { throw new IllegalStateException("Cannot load record dance profiles", e); }
    }
    public static Profile of(int track) { return PROFILES.get(RecordTracks.name(track)); }
    private RecordDance() {}
}
