package net.austin.cabinmod.entity.client;

import net.austin.cabinmod.client.DiscoMusic;
import net.austin.cabinmod.entity.client.anim.BakedAnimations;
import net.austin.cabinmod.entity.client.generated.JehoashModelBase;
import net.austin.cabinmod.entity.client.physics.ClothChain;
import net.austin.cabinmod.entity.client.physics.ModelSpace;
import net.austin.cabinmod.entity.custom.ClipMob;
import net.austin.cabinmod.entity.custom.JehoashEntity;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Jehoash v2 (design/JEHOASH_V2.md). Rig and clips come from Blender (JehoashModelBase + animations/joash.json);
// this class layers them and runs the cloth:
//  - dormant: jam_loop driven by the music's beat clock;
//  - interrupted: crossfades from the frozen jam pose into the upset clip;
//  - fight: idle/walk base + action clips + hurt overlay + head look; death clip when dying;
//  - every phys_* chain (cape panels, robe strands) is simulated by a ClothChain, with an eldritch force field:
//    floating and pulsing to the beat while he DJs, dropping when the needle scratches, flaring on the roar.
public class JehoashModel<T extends ClipMob> extends JehoashModelBase<T> {
    private static final String MOB = JehoashEntity.MOB;
    private static final float G = 160f;                       // px/s^2, ClothChain gravity

    private final Vector3f cache = new Vector3f();
    private final List<Chain> chains = new ArrayList<>();
    private final ModelPart lookHead, lookNeck;

    private record Chain(String name, ClothChain cloth, ModelPart[] parentPath, boolean cape, float phase) {
    }

    // How the cloth behaves right now.
    private record ClothMode(float lift, float capeLift, float radial, float swirl, float back, float wave, float time) {
    }

    public JehoashModel(ModelPart root) {
        super(root);
        Map<String, ModelPart> parts = parts();
        this.lookHead = parts.get("head");
        this.lookNeck = parts.get("neck");

        // phys_<chain>_<n> -> chains, segments in order
        Map<String, TreeMap<Integer, ModelPart>> groups = new TreeMap<>();
        for (Map.Entry<String, ModelPart> e : parts.entrySet()) {
            String name = e.getKey();
            if (!name.startsWith("phys_")) continue;
            int cut = name.lastIndexOf('_');
            try {
                int index = Integer.parseInt(name.substring(cut + 1));
                groups.computeIfAbsent(name.substring(5, cut), k -> new TreeMap<>()).put(index, e.getValue());
            } catch (NumberFormatException ignored) {
            }
        }
        int i = 0;
        for (Map.Entry<String, TreeMap<Integer, ModelPart>> g : groups.entrySet()) {
            ModelPart[] segments = g.getValue().values().toArray(new ModelPart[0]);
            String first = "phys_" + g.getKey() + "_" + g.getValue().firstKey();
            boolean cape = g.getKey().startsWith("cape");
            ClothChain cloth = new ClothChain(5f, segments)
                    .bodyRadius(cape ? 4.2f : 5.4f)
                    .stiffness(cape ? 0.05f : 0.06f)
                    .drag(cape ? 1.5f : 1.1f)
                    .damping(0.93f);
            this.chains.add(new Chain(g.getKey(), cloth, pathTo(PARENT.get(first)), cape, (i++ * 0.61803f % 1f) * Mth.TWO_PI));
        }
    }

    // Parts from the top of the rig down to `name` (for forward kinematics).
    private ModelPart[] pathTo(String name) {
        Deque<ModelPart> path = new ArrayDeque<>();
        for (String n = name; n != null; n = PARENT.get(n)) {
            ModelPart p = parts().get(n);
            if (p == null) break;
            path.addFirst(p);
        }
        return path.toArray(new ModelPart[0]);
    }

    private void play(String clip, long millis, float weight) {
        if (weight <= 0.001f) return;
        BakedAnimations.find(MOB, clip).ifPresent(def -> KeyframeAnimations.animate(this, def, millis, weight, this.cache));
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        JehoashEntity jehoash = entity instanceof JehoashEntity j ? j : null;
        ClothMode mode;

        if (jehoash != null && jehoash.isDormant()) {
            Object key = DiscoMusic.key(jehoash);
            float beats = DiscoMusic.beats(key, ageInTicks);
            play("jam_loop", DiscoMusic.jamMillis(beats), 1f);
            mode = djMode(beats, DiscoMusic.energy(key));
        } else if (entity.isDeadOrDying()) {
            entity.deathState.updateTime(ageInTicks, 1f);
            long ms = entity.deathState.getAccumulatedTime();
            play("death", ms, 1f);
            mode = dyingMode(ms / 1000f);
        } else {
            String action = entity.currentAction();
            long actionMs = 0;
            if (!action.isEmpty()) {
                entity.actionState.updateTime(ageInTicks, 1f);
                actionMs = entity.actionState.getAccumulatedTime();
            }
            boolean interrupted = action.equals("interrupted");

            float base = action.isEmpty() ? 1f : interrupted ? 0f : 0.3f;
            float walk = Math.min(limbSwingAmount * 2.5f, 1f);
            if (base > 0f) {
                entity.idleState.updateTime(ageInTicks, 1f);
                play("idle_loop", entity.idleState.getAccumulatedTime(), base * (1f - walk));
                play("walk_loop", (long) (limbSwing * 100f), base * walk);
            }
            if (interrupted && jehoash != null) {
                // the jam pose he was frozen in melts into the upset clip over 0.25 s
                float frozen = DiscoMusic.interruptedAtBeat(DiscoMusic.key(jehoash));
                float blend = Float.isNaN(frozen) ? 1f : Mth.clamp(actionMs / 250f, 0f, 1f);
                if (blend < 1f) play("jam_loop", DiscoMusic.jamMillis(frozen), 1f - blend);
                play(action, actionMs, blend);
            } else if (!action.isEmpty()) {
                play(action, actionMs, 1f);
            }
            if (action.isEmpty() && entity.hurtTime > 0) {
                entity.hurtState.updateTime(ageInTicks, 1f);
                play("hurt", entity.hurtState.getAccumulatedTime(), 1f);
            }
            if (action.isEmpty() && this.lookHead != null) {
                this.lookHead.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.6f;
                this.lookHead.xRot += headPitch * Mth.DEG_TO_RAD * 0.6f;
                if (this.lookNeck != null) this.lookNeck.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.4f;
            }
            mode = interrupted ? interruptedMode(actionMs / 1000f, ageInTicks) : fightMode(ageInTicks);
        }

        for (Chain chain : this.chains) {
            chain.cloth().apply(entity, ageInTicks, ModelSpace.of(chain.parentPath()), field(chain, mode));
        }
    }

    // DJing: strands float as if underwater and pulse outward on every kick; the cape billows behind him.
    private static ClothMode djMode(float beats, float energy) {
        float phase = beats - Mth.floor(beats);
        float kick = (float) Math.exp(-phase * 6f) * energy;
        return new ClothMode(G * (0.82f + 0.3f * kick), G * (0.5f + 0.2f * kick), 40f + 170f * kick,
                35f * Mth.sin(beats * Mth.HALF_PI), 50f + 90f * kick, 30f, beats * 60f / DiscoMusic.BPM);
    }

    // Needle scratch: everything drops; tense hover; the roar (2.9 s) blasts the strands outward.
    private static ClothMode interruptedMode(float t, float ageInTicks) {
        if (t < 0.35f) return new ClothMode(-G * 0.25f, -G * 0.2f, 0f, 0f, 0f, 0f, t);
        if (t < 2.9f) return new ClothMode(G * 0.3f, G * 0.2f, 20f, 0f, 20f, 15f, t);
        if (t < 4.0f) {
            float flare = (float) Math.exp(-(t - 2.9f) * 2.5f);
            return new ClothMode(G * (0.6f + 0.8f * flare), G * (0.5f + 0.7f * flare), 40f + 520f * flare,
                    60f * flare, 60f + 260f * flare, 40f, t);
        }
        return fightMode(ageInTicks);
    }

    // Fighting: a restless eldritch float.
    private static ClothMode fightMode(float ageInTicks) {
        return new ClothMode(G * 0.55f, G * 0.4f, 30f, 20f * Mth.sin(ageInTicks * 0.05f), 40f, 40f, ageInTicks / 20f);
    }

    // Dying: the float drains out of the cloth.
    private static ClothMode dyingMode(float t) {
        float k = Math.max(0f, 1f - t / 2.5f);
        return new ClothMode(G * 0.5f * k, G * 0.4f * k, 20f * k, 0f, 30f * k, 20f * k, t);
    }

    private ClothChain.ForceField field(Chain chain, ClothMode m) {
        int n = chain.cloth().size();
        float phase = chain.phase();
        return (index, pos, out) -> {
            float tip = 0.6f + 0.4f * index / n;                       // tips float more than roots
            float wave = m.wave() * Mth.sin(m.time() * 3f + phase + index * 0.7f);
            if (chain.cape()) {
                out.add(wave * 0.5f, -m.capeLift() * tip, m.back() * tip);          // model +Z is behind him
                return;
            }
            out.add(0, -m.lift() * tip, 0);                            // model -Y is up
            float r = Mth.sqrt(pos.x * pos.x + pos.z * pos.z);
            if (r > 1e-3f) {
                float rx = pos.x / r, rz = pos.z / r;
                float push = m.radial() * tip;
                float spin = m.swirl() + wave;
                out.add(rx * push - rz * spin, 0, rz * push + rx * spin);
            }
        };
    }
}
