package net.austin.cabinmod.entity.client.physics;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

// Runtime cloth for chains of rigid model parts (robe strips, capes, cables).
//
// The chain is a run of ModelParts, each a child of the one before, hanging down (+Y) from a parent part
// (e.g. the hips). Every frame, after the clips have posed the model, call apply(): the chain is simulated
// with Verlet integration in model space (pixels, Y down, root at y=24) and written back as xRot/zRot on
// each segment. The anchor follows the animated parent, so moves whip the cloth with real inertia; the
// entity's own motion blows it like wind; a ForceField adds anything else (beat-synced levitation, flares);
// `stiffness` pulls it back towards the animated pose so the Blender clip still shapes it. Per-entity state.
public final class ClothChain {
    // Extra acceleration (px/s^2, model space) on point `index` (1..n) at `pos`; add into `out`.
    @FunctionalInterface
    public interface ForceField {
        void accel(int index, Vector3f pos, Vector3f out);
    }

    public static final ForceField NONE = (i, p, out) -> {
    };
    private static final float FLOOR_Y = 24f;                 // feet level in model space

    private final ModelPart[] segments;
    private final float segmentLength;
    private float gravity = 160f;                              // px/s^2 (~9.8 m/s^2)
    private float damping = 0.92f;                             // velocity kept per 1/60 s
    private float stiffness = 0.08f;                           // pull towards the animated pose per 1/60 s
    private float drag = 1.2f;                                 // how strongly the entity's movement blows it
    private float bodyRadius = 0f;                             // keep points this far from the vertical axis

    private final Map<Entity, State> states = new WeakHashMap<>();

    private static final class State {
        final Vector3f[] pos, prev;
        float lastAge = Float.NaN;
        float lastYaw = Float.NaN;

        State(int n) {
            pos = new Vector3f[n + 1];
            prev = new Vector3f[n + 1];
            for (int i = 0; i <= n; i++) {
                pos[i] = new Vector3f();
                prev[i] = new Vector3f();
            }
        }
    }

    // Segment length comes from the rig (pivot-to-pivot of the 2nd segment), or `fallbackLength` for 1 segment.
    public ClothChain(float fallbackLength, ModelPart... segments) {
        this.segments = segments;
        this.segmentLength = segments.length > 1
                ? new Vector3f(segments[1].x, segments[1].y, segments[1].z).length()
                : fallbackLength;
    }

    public int size() {
        return segments.length;
    }

    public float segmentLength() {
        return segmentLength;
    }

    public ClothChain gravity(float g) { this.gravity = g; return this; }
    public ClothChain damping(float d) { this.damping = d; return this; }
    public ClothChain stiffness(float s) { this.stiffness = s; return this; }
    public ClothChain drag(float d) { this.drag = d; return this; }
    public ClothChain bodyRadius(float r) { this.bodyRadius = r; return this; }

    // parentInModel: the parent part's model-space transform after animation (ModelSpace.of(path...)).
    public void apply(Entity entity, float ageInTicks, ModelSpace.Transform parentInModel, ForceField field) {
        int n = segments.length;
        State st = states.computeIfAbsent(entity, e -> new State(n));

        // the animated pose of the chain: where the cloth "wants" to be
        Vector3f[] target = new Vector3f[n + 1];
        Matrix3f rot = new Matrix3f(parentInModel.rotation());
        target[0] = parentInModel.apply(new Vector3f(segments[0].x, segments[0].y, segments[0].z));
        for (int i = 0; i < n; i++) {
            rot.mul(ModelSpace.rotationOf(segments[i]));
            Vector3f step = i + 1 < n
                    ? new Vector3f(segments[i + 1].x, segments[i + 1].y, segments[i + 1].z)
                    : new Vector3f(0, segmentLength, 0);
            target[i + 1] = new Vector3f(target[i]).add(new Matrix3f(rot).transform(step));
        }

        boolean firstFrame = Float.isNaN(st.lastAge) || ageInTicks < st.lastAge;
        // Preserve world-space inertia when the body turns, without adding joints to the body rig.
        float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        if (!firstFrame && !Float.isNaN(st.lastYaw)) {
            float turn = Mth.wrapDegrees(yaw-st.lastYaw) * Mth.DEG_TO_RAD;
            if (Math.abs(turn) < 1.8f) {
                for (int i=1;i<=n;i++) { st.pos[i].rotateY(-turn); st.prev[i].rotateY(-turn); }
            } else firstFrame = true; // teleport/instant turn: reset instead of whipping through the torso
        }
        st.lastYaw = yaw;
        float dt = Float.isNaN(st.lastAge) ? 0f : Mth.clamp((ageInTicks - st.lastAge) / 20f, 0f, 0.1f);
        st.lastAge = ageInTicks;
        if (firstFrame) {                                     // initialize only; paused renders retain their cloth pose
            for (int i = 0; i <= n; i++) {
                st.pos[i].set(target[i]);
                st.prev[i].set(target[i]);
            }
        } else if (dt > 0f) {
            Vector3f wind = windInModel(entity).mul(-drag);
            int steps = Mth.clamp(Mth.ceil(dt / (1f / 120f)), 1, 8);
            float h = dt / steps;
            float keep = (float) Math.pow(damping, h * 60f);
            float pull = 1f - (float) Math.pow(1f - stiffness, h * 60f);
            Vector3f acc = new Vector3f();
            for (int s = 0; s < steps; s++) {
                st.pos[0].set(target[0]);
                st.prev[0].set(target[0]);
                for (int i = 1; i <= n; i++) {
                    Vector3f cur = st.pos[i];
                    Vector3f vel = new Vector3f(cur).sub(st.prev[i]).mul(keep);
                    st.prev[i].set(cur);
                    acc.set(wind).add(0, gravity, 0);
                    field.accel(i, cur, acc);
                    cur.add(vel).add(acc.mul(h * h));
                    cur.lerp(target[i], pull);
                }
                for (int iter = 0; iter < 3; iter++) {
                    for (int i = 1; i <= n; i++) {
                        Vector3f d = new Vector3f(st.pos[i]).sub(st.pos[i - 1]);
                        float len = d.length();
                        if (len > 1e-5f) st.pos[i].set(st.pos[i - 1]).add(d.mul(segmentLength / len));
                        if (bodyRadius > 0) pushOutOfBody(st.pos[i]);
                        if (st.pos[i].y > FLOOR_Y) st.pos[i].y = FLOOR_Y;
                    }
                }
            }
        }

        // write back: rotate each segment so it points at the next simulated point
        Matrix3f accRot = new Matrix3f(parentInModel.rotation());
        for (int i = 0; i < n; i++) {
            Vector3f world = new Vector3f(st.pos[i + 1]).sub(st.pos[i]);
            if (world.lengthSquared() < 1e-8f) continue;
            Vector3f local = new Matrix3f(accRot).transpose().transform(world).normalize();
            ModelPart seg = segments[i];
            seg.xRot = (float) Math.asin(Mth.clamp(local.z, -1f, 1f));
            seg.zRot = (float) Mth.atan2(-local.x, local.y);
            seg.yRot = 0f;
            accRot.mul(ModelSpace.rotationOf(seg));
        }
    }

    private void pushOutOfBody(Vector3f p) {
        float r = Mth.sqrt(p.x * p.x + p.z * p.z);
        if (r < bodyRadius) {
            if (r < 1e-4f) {
                p.z = bodyRadius;
            } else {
                p.x *= bodyRadius / r;
                p.z *= bodyRadius / r;
            }
        }
    }

    // Entity velocity in model space, px/s (model: +X = mob's left, +Y down, -Z = forward).
    private static Vector3f windInModel(Entity entity) {
        Vec3 v = entity.getDeltaMovement();
        if (entity instanceof LivingEntity living) {
            float yaw = living.yBodyRot * Mth.DEG_TO_RAD;
            double fwd = -Math.sin(yaw) * v.x + Math.cos(yaw) * v.z;
            double left = Math.cos(yaw) * v.x + Math.sin(yaw) * v.z;
            return new Vector3f((float) left, (float) -v.y, (float) -fwd).mul(16f * 20f);
        }
        return new Vector3f((float) v.x, (float) -v.y, (float) v.z).mul(16f * 20f);
    }
}
