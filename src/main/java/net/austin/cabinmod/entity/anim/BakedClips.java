package net.austin.cabinmod.entity.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.austin.cabinmod.CabinMod;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Clips baked by tools/blender/mc_export.py (assets/cabinmod/animations/<mob>.json).
// Common side: lengths and marker events, so the server can time hits/lasers to the animation.
public final class BakedClips {
    public record Event(float time, String type, String arg) {
        public int tick() {
            return Math.round(time * 20f);
        }
    }

    public record Clip(String name, float length, boolean looping, List<Event> events, JsonObject bones) {
        public int lengthTicks() {
            return Math.round(length * 20f);
        }

        // Events whose tick falls in (fromTick, toTick], for polling once per server tick.
        public List<Event> eventsBetween(int fromTick, int toTick) {
            List<Event> out = new ArrayList<>();
            for (Event e : events) {
                if (e.tick() > fromTick && e.tick() <= toTick) out.add(e);
            }
            return out;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Map<String, Clip>> CACHE = new ConcurrentHashMap<>();

    private BakedClips() {
    }

    public static Clip get(String mob, String clip) {
        Clip c = all(mob).get(clip);
        if (c == null) {
            throw new IllegalArgumentException("No baked clip '" + clip + "' for " + mob);
        }
        return c;
    }

    // Empty until the clip has been exported from Blender; callers fall back to stand-in timings.
    public static Optional<Clip> find(String mob, String clip) {
        return Optional.ofNullable(all(mob).get(clip));
    }

    public static Map<String, Clip> all(String mob) {
        return CACHE.computeIfAbsent(mob, BakedClips::load);
    }

    private static Map<String, Clip> load(String mob) {
        String path = "/assets/" + CabinMod.MOD_ID + "/animations/" + mob + ".json";
        try (InputStream in = BakedClips.class.getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.warn("No baked animations at {} yet - using stand-in timings until it is exported from Blender", path);
                return Map.of();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Clip> clips = new ConcurrentHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("clips").entrySet()) {
                JsonObject c = entry.getValue().getAsJsonObject();
                List<Event> events = new ArrayList<>();
                for (JsonElement e : c.getAsJsonArray("events")) {
                    JsonObject o = e.getAsJsonObject();
                    events.add(new Event(o.get("t").getAsFloat(), o.get("type").getAsString(), o.get("arg").getAsString()));
                }
                clips.put(entry.getKey(), new Clip(entry.getKey(), c.get("length").getAsFloat(),
                        c.get("loop").getAsBoolean(), List.copyOf(events), c.getAsJsonObject("bones")));
            }
            return clips;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load baked animations for " + mob, e);
        }
    }
}
