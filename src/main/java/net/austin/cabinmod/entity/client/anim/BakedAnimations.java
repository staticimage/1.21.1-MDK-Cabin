package net.austin.cabinmod.entity.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.austin.cabinmod.entity.anim.BakedClips;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Turns baked Blender clips into vanilla AnimationDefinitions. Keys are dense LINEAR samples,
// so the in-game curve matches Blender (custom easing, IK, simulation) exactly.
public final class BakedAnimations {
    private static final Map<String, AnimationDefinition> CACHE = new ConcurrentHashMap<>();

    private BakedAnimations() {
    }

    public static AnimationDefinition get(String mob, String clip) {
        return CACHE.computeIfAbsent(mob + "/" + clip, k -> build(BakedClips.get(mob, clip)));
    }

    // Empty until the clip has been exported from Blender.
    public static Optional<AnimationDefinition> find(String mob, String clip) {
        return BakedClips.find(mob, clip).map(c -> CACHE.computeIfAbsent(mob + "/" + clip, k -> build(c)));
    }

    private static AnimationDefinition build(BakedClips.Clip clip) {
        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(Math.max(clip.length(), 0.05f));
        if (clip.looping()) {
            builder.looping();
        }
        for (Map.Entry<String, JsonElement> bone : clip.bones().entrySet()) {
            JsonObject channels = bone.getValue().getAsJsonObject();
            if (channels.has("rot")) {
                builder.addAnimation(bone.getKey(), channel(AnimationChannel.Targets.ROTATION, channels.getAsJsonArray("rot"), 'r'));
            }
            if (channels.has("pos")) {
                builder.addAnimation(bone.getKey(), channel(AnimationChannel.Targets.POSITION, channels.getAsJsonArray("pos"), 'p'));
            }
            if (channels.has("scale")) {
                builder.addAnimation(bone.getKey(), channel(AnimationChannel.Targets.SCALE, channels.getAsJsonArray("scale"), 's'));
            }
        }
        return builder.build();
    }

    private static AnimationChannel channel(AnimationChannel.Target target, JsonArray rows, char kind) {
        Keyframe[] keys = new Keyframe[rows.size()];
        for (int i = 0; i < keys.length; i++) {
            JsonArray r = rows.get(i).getAsJsonArray();
            float x = r.get(1).getAsFloat(), y = r.get(2).getAsFloat(), z = r.get(3).getAsFloat();
            Vector3f v = switch (kind) {
                case 'r' -> KeyframeAnimations.degreeVec(x, y, z);
                case 'p' -> KeyframeAnimations.posVec(x, y, z);
                default -> KeyframeAnimations.scaleVec(x, y, z);
            };
            keys[i] = new Keyframe(r.get(0).getAsFloat(), v, AnimationChannel.Interpolations.LINEAR);
        }
        return new AnimationChannel(target, keys);
    }
}
