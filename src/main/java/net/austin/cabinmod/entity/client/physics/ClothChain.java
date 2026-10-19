package net.austin.cabinmod.entity.client.physics;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

// Runtime cloth for chains of rigid model parts (robe strips, capes, sleeves, cables).
//
// The chain is a run of ModelParts, each a child of the one before, hanging down (+Y) from `parent`
// (e.g. the torso). Every frame, after the clip animation has posed the model, call apply(): the chain
// is simulated with Verlet integration in model space (pixels, Y down) and the result is written back
// as xRot/zRot on each segment. The anchor follows the animated parent, so bows and sways whip the
// cloth with real inertia; the entity's own motion blows it like wind; `stiffness` pulls it back
// towards the animated pose so the Blender animation still shapes it. State is per entity.
public final class ClothChain {
    private final ModelPart parent;
    private final ModelPart[] segments;
    private final float segmentLength;          // px
    private float gravity = 160f;               // px/s^2  (~9.8 m/s^2)
    private float damping = 0.92f;              // velocity kept per 1/60 s
    private float stiffness = 0.08f;            // pull back towards the animated pose per 1/60 s
    private float drag = 0.9f;                  // how strongly the entity's movement blows the cloth
    private float bodyRadius = 0f;              // keep points this far (px) from the model's vertical axis

    private final Map<Entity, State> states = new WeakHashMap<>();

    private static final class State {
        final Vector3f[] pos, prev;
        float lastAge = Float.NaN;

        State(int n) {
            pos = new Vector3f[n + 1];
            prev = new Vector3f[n + 1];
            for (int i = 0; i <= n; i++) {
                pos[i] = new Vector3f();
                prev[i] = new Vector3f();
            }
        }
    }

    public ClothChain(ModelPart parent, float segmentLength, ModelPart... segments) {
        this.parent = parent;
        this.segments = segments;
        this.segmentLength = segmentLength;
    }

    public ClothChain gravity(float g) { this.gravity = g; return this; }
    public ClothChain damping(float d) { this.damping = d; return this; }
    public ClothChain stiffness(float s) { this.stiffness = s; return this; }
    public ClothChain drag(float d) { this.drag = d; return this; }
    public ClothChain bodyRadius(float r) { this.bodyRadius = r; return this; }

    // parentInModel: the parent part's model-space transform after animation (see ModelSpace.of).
    public void apply(Entity entity, float ageInTicks, ModelSpace.Transform parentInModel) {
        int n = segments.length;
        State st = states.computeIfAbsent(entity, e -> new State(n));

        // animated pose of the chain, in model space: where the cloth "wants" to be
        Vector3f[] target = new Vector3f[n + 1];
        Matrix3f rot = new Matrix3f(parentInModel.rotation());
        Vector3f p = parentInModel.apply(new Vector3f(segments[0].x, segments[0].y, segments[0].z));
        target[0] = p;
        for (int i = 0; i < n; i++) {
            ModelPart s = segments[i];
            rot.mul(ModelSpace.rotationOf(s));
            Vector3f down = rot.transform(new Vector3f(0, segmentLength, 0));
            target[i + 1] = new Vector3f(target[i]).add(down);
            if (i + 1 < n) {
                ModelPart next = segments[i + 1];
                // children hang from (next.x, next.y, next.z) in this segment's frame; keep it simple: on the axis
                target[i + 1] = new Vector3f(target[i]).add(rot.transform(new Vector3f(next.x, next.y, next.z)));
            }
        }

        float dt = Float.isNaN(st.lastAge) ? 0f : Mth.clamp((ageInTicks - st.lastAge) / 20f, 0f, 0.1f);
        st.lastAge = ageInTicks;
        if (dt == 0f) {                                   // first frame (or paused): snap to the pose
            for (int i = 0; i <= n; i++) {
                st.pos[i].set(target[i]);
                st.prev[i].set(target[i]);
            }
        } else {
            Vector3f wind = windInModel(entity).mul(-drag);
            int steps = Mth.clamp(Mth.ceil(dt / (1f / 120f)), 1, 8);
            float h = dt / steps;
            float keep = (float) Math.pow(damping, h * 60f);
            float pull = 1f - (float) Math.pow(1f - stiffness, h * 60f);
            for (int s = 0; s < steps; s++) {
                st.pos[0].set(target[0]);
                st.prev[0].set(target[0]);
                for (int i = 1; i <= n; i++) {
                    Vector3f cur = st.pos[i];
                    Vector3f vel = new Vector3f(cur).sub(st.prev[i]).mul(keep);
                    st.prev[i].set(cur);
                    cur.add(vel).add(new Vector3f(wind).add(0, gravity, 0).mul(h * h));
                    cur.lerp(target[i], pull);
                }
                for (int iter = 0; iter < 3; iter++) {
                    for (int i = 1; i <= n; i++) {
                        Vector3f d = new Vector3f(st.pos[i]).sub(st.pos[i - 1]);
                        float len = d.length();
                        if (len > 1e-5f) st.pos[i].set(st.pos[i - 1]).add(d.mul(segmentLength / len));
                        if (bodyRadius > 0) pushOutOfBody(st.pos[i]);
                    }
                }
            }
        }

        // write back: rotate each segment so it points at the next simulated point
        Matrix3f acc = new Matrix3f(parentInModel.rotation());
        for (int i = 0; i < n; i++) {
            Vector3f world = new Vector3f(st.pos[i + 1]).sub(st.pos[i]);
            Vector3f local = new Matrix3f(acc).transpose().transform(world).normalize();
            ModelPart seg = segments[i];
            seg.xRot = (float) Math.asin(Mth.clamp(local.z, -1f, 1f));
            seg.zRot = (float) Mth.atan2(-local.x, local.y);
            seg.yRot = 0f;
            acc.mul(ModelSpace.rotationOf(seg));
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
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            float yaw = living.yBodyRot * Mth.DEG_TO_RAD;
            double fwd = -Math.sin(yaw) * v.x + Math.cos(yaw) * v.z;
            double left = Math.cos(yaw) * v.x + Math.sin(yaw) * v.z;
            return new Vector3f((float) left, (float) -v.y, (float) -fwd).mul(16f * 20f);
        }
        return new Vector3f((float) v.x, (float) -v.y, (float) v.z).mul(16f * 20f);
    }
}
