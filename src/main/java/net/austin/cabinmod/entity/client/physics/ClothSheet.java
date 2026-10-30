package net.austin.cabinmod.entity.client.physics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.austin.cabinmod.entity.StageCollapse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reusable, entity-local XPBD fabric solver. A garment is one set of connected surfaces with
 * bone pins, stretch/shear/bend constraints, capsule and block collision, and self collision.
 * Positions are Minecraft model pixels (+Y down, feet at Y=24), independent of render FPS.
 */
public final class ClothSheet {
    public enum Bone { TORSO, HEAD, ARM_R, ARM_L, FORE_R, FORE_L }
    public enum Piece { ROBE, CAPE, MANTLE, HOOD, VEIL, SLEEVE_R, SLEEVE_L }

    public record Pose(ModelSpace.Transform torso, ModelSpace.Transform head,
                       ModelSpace.Transform armR, ModelSpace.Transform armL,
                       ModelSpace.Transform foreR, ModelSpace.Transform foreL) {
        ModelSpace.Transform bone(Bone bone) {
            return switch (bone) {
                case TORSO -> torso; case HEAD -> head;
                case ARM_R -> armR; case ARM_L -> armL;
                case FORE_R -> foreR; case FORE_L -> foreL;
            };
        }
    }

    private record Pin(Bone bone, Vector3f offset) {}
    private record Vertex(Vector3f rest, float u, float v, int piece, int row, int column,
                          int columns, Pin pin, boolean hood, Piece part) {}
    private record Quad(int a, int b, int c, int d) {}
    private record Link(int a, int b, float length, float compliance) {}
    private record Capsule(Vector3f a, Vector3f b, float radius) {}

    // Pixel rectangles match design/textures/jehoash_brg_cloak_uv.json. Only UVs
    // change here; garment vertices, pins, links and simulation tuning stay intact.
    private record UvRegion(float u0, float v0, float u1, float v1, boolean mirror) {
        float u(float across) { return Mth.lerp(mirror ? 1-across : across,u0,u1)/2048f; }
        float v(float down) { return Mth.lerp(down,v0,v1)/2048f; }
    }
    private static final UvRegion ROBE_UV=new UvRegion(0,0,1536,1536,true);
    private static final UvRegion CAPE_UV=new UvRegion(1536,0,2048,1024,true);
    private static final UvRegion MANTLE_UV=new UvRegion(1536,1024,2048,1280,false);
    private static final UvRegion HOOD_UV=new UvRegion(1536,1280,2048,1792,true);
    private static final UvRegion VEIL_UV=new UvRegion(1536,1792,2048,2048,false);
    private static final UvRegion SLEEVE_R_UV=new UvRegion(0,1536,768,2048,false);
    private static final UvRegion SLEEVE_L_UV=new UvRegion(768,1536,1536,2048,false);

    private static final float STEP = 1f / 120f;
    private static final float GRAVITY = 175f;
    private static final float CONTACT = .75f;
    private static final float STRUCTURAL = .00001f;
    private static final float SHEAR = .00003f;
    private static final float BEND = .00025f;
    private static final int ITERATIONS = 4;

    private final Vertex[] vertices;
    private final Quad[] quads;
    private final Link[] links;
    private final Boolean slimPlayer; // null preserves the original boss pattern and simulation.
    private final Map<Entity, State> states = new WeakHashMap<>();
    private static long sampleNanoseconds, sampleCount;

    public static String drainPerformanceSample() {
        double milliseconds = sampleCount == 0 ? 0 : sampleNanoseconds / (double) sampleCount / 1_000_000d;
        String result = String.format(java.util.Locale.ROOT, "%.3f ms/update across %d samples", milliseconds, sampleCount);
        sampleNanoseconds = sampleCount = 0;
        return result;
    }

    private static final class State {
        final Vector3f[] position, previous, beforeStep, renderPosition, pinMotion;
        final float[] lambda;
        final Vector3f lastEntityPosition = new Vector3f();
        float lastAge = Float.NaN;
        float lastYaw;
        boolean detached, expired;
        float lastStep = STEP, remainder;
        float detachedAge = Float.NaN;
        PlayerFrame playerFrame;
        ResourceKey<Level> playerDimension;

        State(int vertexCount, int linkCount) {
            position = new Vector3f[vertexCount];
            previous = new Vector3f[vertexCount];
            beforeStep = new Vector3f[vertexCount];
            renderPosition = new Vector3f[vertexCount];
            pinMotion = new Vector3f[vertexCount];
            lambda = new float[linkCount];
            for (int i = 0; i < vertexCount; i++) {
                position[i] = new Vector3f();
                previous[i] = new Vector3f();
                beforeStep[i] = new Vector3f();
                renderPosition[i] = new Vector3f();
                pinMotion[i] = new Vector3f();
            }
        }
    }

    public ClothSheet(boolean detailed) {
        slimPlayer = null;
        Pattern pattern = new Pattern();
        buildRobe(pattern, detailed ? 24 : 12, detailed ? 17 : 9);
        buildCape(pattern, detailed ? 19 : 11, detailed ? 18 : 10);
        buildMantle(pattern, detailed ? 21 : 11, detailed ? 7 : 5);
        int shellStart = pattern.vertices.size();
        buildHood(pattern, detailed ? 20 : 12, detailed ? 12 : 7);
        int shellEnd = pattern.vertices.size();
        buildVeil(pattern, detailed ? 13 : 9, detailed ? 11 : 7, shellStart, shellEnd);
        buildSleeve(pattern, true, detailed ? 12 : 8, detailed ? 11 : 7);
        buildSleeve(pattern, false, detailed ? 12 : 8, detailed ? 11 : 7);
        vertices = pattern.vertices.toArray(Vertex[]::new);
        quads = pattern.quads.toArray(Quad[]::new);
        links = pattern.links.toArray(Link[]::new);
    }

    /** Vanilla player proportions, with an actual open hood rather than a hidden veil. */
    public static ClothSheet player(boolean slim) { return new ClothSheet(slim, null); }

    /** Separate view-model simulation; it cannot advance the world's full garment twice. */
    public static ClothSheet playerSleeve(boolean slim, boolean right) {
        return new ClothSheet(slim, right ? Piece.SLEEVE_R : Piece.SLEEVE_L);
    }

    private ClothSheet(boolean slim, Piece sleeve) {
        slimPlayer = slim;
        Pattern p = sleeve == null ? new Pattern(Piece.ROBE, Piece.CAPE, Piece.MANTLE,
                Piece.HOOD, Piece.SLEEVE_R, Piece.SLEEVE_L) : new Pattern(sleeve);
        if (sleeve == null) {
            buildPlayerBody(p);
            buildPlayerCape(p);
            buildPlayerMantle(p);
            buildPlayerHood(p);
        }
        if (sleeve == null || sleeve == Piece.SLEEVE_R) buildPlayerSleeve(p, slim, true);
        if (sleeve == null || sleeve == Piece.SLEEVE_L) buildPlayerSleeve(p, slim, false);
        vertices=p.vertices.toArray(Vertex[]::new);
        quads=p.quads.toArray(Quad[]::new);
        links=p.links.toArray(Link[]::new);
    }

    public void forget(Entity entity) { states.remove(entity); }
    public void clear() { states.clear(); }

    public int vertexCount() { return vertices.length; }
    public int quadCount() { return quads.length; }

    private static final class Pattern {
        final List<Vertex> vertices = new ArrayList<>();
        final List<Quad> quads = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        int piece;
        final Piece[] parts;

        Pattern(Piece... parts) { this.parts=parts.length==0 ? Piece.values() : parts; }

        @FunctionalInterface interface Shape { Vector3f at(int column, int row, float across, float down); }
        @FunctionalInterface interface Pins { Pin at(int column, int row, Vector3f rest); }

        void grid(int columns, int rows, boolean hoodPiece, Shape shape, Pins pins,
                  UvRegion uv) {
            int start = vertices.size();
            int thisPiece = piece++;
            for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
                float across = x / (float)(columns - 1), down = y / (float)(rows - 1);
                Vector3f rest = shape.at(x, y, across, down);
                int id = vertices.size();
                vertices.add(new Vertex(rest, uv.u(across), uv.v(down),
                        thisPiece, y, x, columns, pins.at(x, y, rest), hoodPiece, parts[thisPiece]));
            }
            for (int y = 0; y < rows - 1; y++) for (int x = 0; x < columns - 1; x++) {
                int a = start + y * columns + x, b = a + 1, d = a + columns, c = d + 1;
                quads.add(new Quad(a, d, c, b));
            }
            for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
                int a = start + y * columns + x;
                if (x + 1 < columns) link(a, a + 1, STRUCTURAL);
                if (y + 1 < rows) link(a, a + columns, STRUCTURAL);
                if (x + 1 < columns && y + 1 < rows) {
                    link(a, a + columns + 1, SHEAR);
                    link(a + 1, a + columns, SHEAR);
                }
                if (x + 2 < columns) link(a, a + 2, BEND);
                if (y + 2 < rows) link(a, a + columns * 2, BEND);
            }
        }

        void link(int a, int b, float compliance) {
            links.add(new Link(a, b, vertices.get(a).rest.distance(vertices.get(b).rest), compliance));
        }
    }

    private static Pin torsoPin(Vector3f rest) {
        return new Pin(Bone.TORSO, new Vector3f(rest)); // torso's neutral origin is (0,0,0)
    }

    // Rounded rectangular cross sections clear Minecraft's cuboid corners without
    // using the boss's oversized circular torso/arm colliders on a smaller player.
    private static Vector3f roundedSection(float angle, float x, float z, float corner) {
        float s=Mth.sin(angle), c=Mth.cos(angle), edge=Math.max(Math.abs(s),Math.abs(c));
        return new Vector3f((x-corner)*s/edge+corner*s,0,(z-corner)*c/edge+corner*c);
    }

    private static void buildPlayerBody(Pattern p) {
        int start=p.vertices.size(), cols=25, rows=13;
        p.grid(cols,rows,false,(x,y,u,v) -> {
            float belowWaist=Math.max(0,(v-.5f)*2);
            float flare=1+.32f*belowWaist*belowWaist;
            return roundedSection(u*Mth.TWO_PI,4.85f*flare,2.85f*flare,.55f)
                    .add(0,.1f+22.1f*v+.35f*v*v*Mth.sin(u*Mth.TWO_PI*5),0);
        // A fitted bodice stays on the torso when raised arms sweep across it.
        // Fabric below the waist remains free to drape over the animated legs.
        },(x,y,rest) -> y<=4 ? torsoPin(rest) : null,ROBE_UV);
        closeSeam(p,start,cols,rows);
    }

    private static void buildPlayerCape(Pattern p) {
        p.grid(13,14,false,(x,y,u,v) -> new Vector3f((2*u-1)*(5.4f+1.5f*v),
                .15f+21.9f*v+.45f*v*v*Mth.sin(x*2.17f),3.35f+2.2f*v*v),
                (x,y,rest) -> y==0 ? torsoPin(rest) : null,CAPE_UV);
    }

    private static void buildPlayerMantle(Pattern p) {
        p.grid(17,5,false,(x,y,u,v) -> roundedSection((u-.5f)*4.9f,5.1f+3.2f*v,3.05f+.6f*v,.6f)
                .add(0,-.35f+4.1f*v,0), (x,y,rest) -> y==0 ? torsoPin(rest) : null,MANTLE_UV);
    }

    private static void buildPlayerHood(Pattern p) {
        // The front sector is absent all the way down to the collar; skin faces
        // stay visible even when looking up, down or sideways.
        p.grid(17,9,true,(x,y,u,v) -> {
            float crown=Mth.clamp(v/.125f,0,1);
            float radius=Mth.lerp(crown,.45f,5.55f);
            return roundedSection(Mth.lerp(u,-2.42f,2.42f),radius,radius,Math.min(.6f,radius*.5f))
                    .add(0,v<=.125f?Mth.lerp(crown,-9.7f,-9.25f):-9.25f+(v-.125f)/.875f*9.6f,0);
        },(x,y,rest) -> y<2 ? new Pin(Bone.HEAD,new Vector3f(rest)) : null,HOOD_UV);
    }

    private static void buildPlayerSleeve(Pattern p, boolean slim, boolean right) {
        float sign=right?-1:1, shoulderY=slim?2.5f:2f;
        float centerX=sign*(slim?5.5f:6f), top=shoulderY-2;
        Bone arm=right?Bone.ARM_R:Bone.ARM_L, fore=right?Bone.FORE_R:Bone.FORE_L;
        int start=p.vertices.size(), cols=13, rows=9;
        p.grid(cols,rows,false,(x,y,u,v) -> roundedSection(u*Mth.TWO_PI,
                (slim?2.1f:2.6f)+.12f*v,2.6f+.12f*v,.45f).add(centerX,top+9*v,0),
                (x,y,rest) -> {
                    if(y==0) return new Pin(arm,new Vector3f(rest).sub(sign*5,shoulderY,0));
                    if(y==rows/2 || y==rows-1)
                        return new Pin(fore,new Vector3f(rest).sub(sign*5,shoulderY+4,0));
                    return null;
                },right?SLEEVE_R_UV:SLEEVE_L_UV);
        closeSeam(p,start,cols,rows);
        // Close the shoulder over the arm cube's top face. An open tube exposes
        // the player's shirt when crouching or raising the arm under the mantle.
        Vector3f center=new Vector3f(centerX,top,0);
        UvRegion uv=right?SLEEVE_R_UV:SLEEVE_L_UV;
        int cap=p.vertices.size();
        p.vertices.add(new Vertex(center,uv.u(.5f),uv.v(0),p.piece-1,0,cols/2,cols,
                new Pin(arm,new Vector3f(center).sub(sign*5,shoulderY,0)),false,
                right?Piece.SLEEVE_R:Piece.SLEEVE_L));
        for(int x=0;x<cols-1;x++) p.quads.add(new Quad(cap,start+x+1,start+x,start+x));
    }

    private static void buildRobe(Pattern p, int circumference, int rows) {
        int columns = circumference + 1; // duplicate the UV seam, constrained to its twin
        int start = p.vertices.size();
        p.grid(columns, rows, false, (x,y,u,v) -> {
            float angle = u * Mth.TWO_PI;
            float flare = 1f + .34f * v * v;
            float hem = v * v * (2f * Mth.sin(angle * 5f) + 1.1f * Mth.sin(angle * 11f));
            return new Vector3f(Mth.sin(angle) * 6.5f * flare,
                    -16.5f + v * 38f + hem, Mth.cos(angle) * 5.1f * flare);
        }, (x,y,rest) -> y == 0 ? torsoPin(rest) : null,
                ROBE_UV);
        closeSeam(p, start, columns, rows);
    }

    private static void buildCape(Pattern p, int columns, int rows) {
        p.grid(columns, rows, false, (x,y,u,v) -> {
            float across = 2f * u - 1f;
            float hem = v * v * (2.5f * Mth.sin(x * 2.17f) + 1.3f * Mth.sin(x * 4.3f));
            return new Vector3f(across * (7.8f + 4.4f * v), -17f + 39f * v + hem,
                    5.4f + 5.3f * v * v - 1.7f * across * across);
        }, (x,y,rest) -> y == 0 ? torsoPin(rest) : null,
                CAPE_UV);
    }

    private static void buildMantle(Pattern p, int columns, int rows) {
        p.grid(columns, rows, false, (x,y,u,v) -> {
            float angle = (u - .5f) * 4.6f;
            float radius = 7.2f + 2.8f * v;
            return new Vector3f(Mth.sin(angle) * radius, -18f + 10f * v
                    + .75f * Mth.sin(x * 3f) * v * v, Mth.cos(angle) * (5.7f + 2.2f * v));
        }, (x,y,rest) -> y == 0 ? torsoPin(rest) : null,
                MANTLE_UV);
    }

    private static void buildHood(Pattern p, int circumference, int rows) {
        int columns = circumference + 1;
        int start = p.vertices.size();
        p.grid(columns, rows, true, (x,y,u,v) -> {
            float angle = u * Mth.TWO_PI;
            float radius = v < .12f ? Mth.lerp(v / .12f, .6f, 5.5f) : 5.5f + .4f * v;
            float fringe = v * v * (.45f * Mth.sin(angle * 7f) + .25f * Mth.sin(angle * 13f));
            return new Vector3f(Mth.sin(angle) * radius, -30f + 12f * v + fringe,
                    Mth.cos(angle) * (radius + (Mth.cos(angle) < 0 ? .4f * v : 0)));
        }, (x,y,rest) -> y < 2 ? new Pin(Bone.HEAD, new Vector3f(rest).add(0,18f,0)) : null,
                HOOD_UV);
        closeSeam(p, start, columns, rows);
    }

    private static void buildVeil(Pattern p, int columns, int rows, int shellStart, int shellEnd) {
        int start = p.vertices.size();
        p.grid(columns, rows, true, (x,y,u,v) -> {
            float across = 2f*u-1f;
            return new Vector3f(across*5f, -27.4f + 9.4f*v
                    + .3f*v*v*Mth.sin(x*2.2f), -6.45f - .65f*v + .7f*across*across);
        }, (x,y,rest) -> y==0 || x==0 || x==columns-1
                ? new Pin(Bone.HEAD, new Vector3f(rest).add(0,18f,0)) : null,
                VEIL_UV);
        // Sew the veil to the front rim so the detached hood remains one cloth assembly.
        for (int y=0;y<rows;y++) for (int x=0;x<columns;x++) {
            if (x!=0 && x!=columns-1 && y!=0) continue;
            int id=start+y*columns+x, nearest=-1;
            float best=Float.POSITIVE_INFINITY;
            for (int other=shellStart;other<shellEnd;other++) {
                float distance=p.vertices.get(id).rest.distanceSquared(p.vertices.get(other).rest);
                if (distance<best) { best=distance;nearest=other; }
            }
            if (nearest>=0) p.link(id,nearest,STRUCTURAL);
        }
    }

    private static void buildSleeve(Pattern p, boolean right, int circumference, int rows) {
        int columns = circumference + 1;
        int start = p.vertices.size();
        float side = right ? -1f : 1f;
        Bone arm = right ? Bone.ARM_R : Bone.ARM_L;
        Bone fore = right ? Bone.FORE_R : Bone.FORE_L;
        int elbow = rows / 2;
        p.grid(columns, rows, false, (x,y,u,v) -> {
            float angle = u * Mth.TWO_PI;
            float radius = 2.7f + 2.3f * v;
            float cuff = v * v * .8f * Mth.sin(angle * 5f);
            return new Vector3f(side * 6.5f + Mth.cos(angle) * radius,
                    -17f + 25f * v + cuff, Mth.sin(angle) * radius);
        }, (x,y,rest) -> {
            if (y == 0) return new Pin(arm, new Vector3f(rest).sub(side * 6.5f, -17f, 0));
            if (y == elbow) return new Pin(fore, new Vector3f(rest).sub(side * 6.5f, -4f, 0));
            return null;
        }, right ? SLEEVE_R_UV : SLEEVE_L_UV);
        closeSeam(p, start, columns, rows);
    }

    private static void closeSeam(Pattern p, int start, int columns, int rows) {
        for (int y = 0; y < rows; y++)
            p.link(start + y * columns, start + y * columns + columns - 1, STRUCTURAL);
    }

    public void update(Entity entity, float age, Pose pose, float wind, boolean detachAll) {
        long sampleStart = Boolean.getBoolean("cabinmod.visualSmoke") ? System.nanoTime() : 0;
        try {
        State state = states.computeIfAbsent(entity, ignored -> new State(vertices.length, links.length));
        if (Float.isNaN(state.lastAge) && detachAll) {
            // A client that first sees an already-unrobed boss must not replay the throw.
            expire(state, age);
            return;
        }
        if (state.expired || state.detached && age - state.detachedAge >= StageCollapse.CLOTH_LIFETIME) {
            state.lastAge = age; // keep the renderer's expiry clock moving too
            return;
        }
        float yaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        Vector3f entityPosition = new Vector3f((float)entity.getX(), (float)entity.getY(), (float)entity.getZ());
        if (state.detached) {
            // Match the renderer's interpolated owner transform, so discarded cloth
            // stays in world space when phase-two locomotion starts.
            float partial = Mth.clamp(age-entity.tickCount,0,1);
            if (entity instanceof LivingEntity living) yaw=Mth.rotLerp(partial,living.yBodyRotO,living.yBodyRot);
            entityPosition.set((float)Mth.lerp(partial,entity.xo,entity.getX()),
                    (float)Mth.lerp(partial,entity.yo,entity.getY()),(float)Mth.lerp(partial,entity.zo,entity.getZ()));
        }
        boolean reset = Float.isNaN(state.lastAge) || age < state.lastAge
                || entityPosition.distanceSquared(state.lastEntityPosition) > 64f
                || !state.detached && Math.abs(Mth.wrapDegrees(yaw - state.lastYaw)) > 120f;
        float elapsed = reset ? 0 : (age - state.lastAge) / 20f;
        if (elapsed > .25f) reset = true;
        if (reset && (state.detached || detachAll)) {
            // A stale/teleported simulation cannot be rebuilt on the exposed body.
            expire(state,age);
            return;
        }
        Vector3f[] targets = new Vector3f[vertices.length];
        Vector3f[] hoodShape = new Vector3f[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            Pin pin = vertices[i].pin;
            if (pin != null) targets[i] = pose.bone(pin.bone).apply(new Vector3f(pin.offset));
            if (vertices[i].hood) hoodShape[i] = pose.head.apply(new Vector3f(vertices[i].rest).add(0,18f,0));
            if (pin != null && !state.detached && elapsed > 0 && !reset)
                state.pinMotion[i].set(targets[i]).sub(state.position[i]).mul(STEP/elapsed);
        }
        if (reset) {
            for (int i = 0; i < vertices.length; i++) {
                state.position[i].set(targets[i] != null ? targets[i] : vertices[i].rest);
                state.previous[i].set(state.position[i]);
            }
            state.lastStep=STEP;
            for (Vector3f motion : state.pinMotion) motion.zero();
        } else if (elapsed > 0) {
            // Keep cloth in approximately the same world place as its animated owner moves/turns.
            float turn = -Mth.wrapDegrees(yaw - state.lastYaw) * Mth.DEG_TO_RAD;
            float c = Mth.cos(yaw * Mth.DEG_TO_RAD), s = Mth.sin(yaw * Mth.DEG_TO_RAD);
            float dx = (entityPosition.x - state.lastEntityPosition.x) * 16f;
            float dz = (entityPosition.z - state.lastEntityPosition.z) * 16f;
            Vector3f shift = new Vector3f(-(c * dx + s * dz),
                    (entityPosition.y - state.lastEntityPosition.y) * 16f,
                    -(s * dx - c * dz));
            for (int i = 0; i < vertices.length; i++) {
                if (targets[i] != null && !state.detached) continue;
                state.position[i].rotateY(turn).add(shift);
                state.previous[i].rotateY(turn).add(shift);
                if (state.detached) state.beforeStep[i].rotateY(turn).add(shift);
            }
        }
        state.lastAge = age;
        state.lastYaw = yaw;
        state.lastEntityPosition.set(entityPosition);
        if (detachAll && Float.isNaN(state.detachedAge)) {
            state.detached = true;
            state.detachedAge = age;
            for (int i = 0; i < vertices.length; i++) {
                Vertex v = vertices[i];
                Vector3f p = state.position[i];
                // An upward, radial fling releases every seam and cuff together. The hood
                // gets extra lift so the supplied face is clear by the on-foot entrance.
                float radial = v.hood ? 1.7f : .9f;
                float vx = Math.signum(p.x + (v.piece % 2 == 0 ? .5f : -.5f)) * radial;
                float vz = (v.hood ? 1.8f : .45f) + .45f * Math.signum(p.z);
                float vy = v.hood ? -2.5f : -1.5f;
                Vector3f motion = v.pin != null ? state.pinMotion[i]
                        : new Vector3f(p).sub(state.previous[i]).mul(STEP/state.lastStep);
                state.previous[i].set(p).sub(motion).sub(vx, vy, vz);
                state.beforeStep[i].set(p);
            }
            state.remainder=0;
            // The preceding interval was still attached. Start the flight here;
            // never integrate a whole render frame's worth of throw on release.
            elapsed=0;
        }
        if (reset || elapsed <= 0) return;
        int steps;
        float h;
        if (state.detached) {
            state.remainder+=elapsed;
            steps=Mth.floor((state.remainder+1e-6f)/STEP);
            state.remainder=Math.max(0,state.remainder-steps*STEP);
            h=STEP;
        } else {
            steps=Mth.clamp(Mth.ceil(elapsed/STEP),1,8);
            h=elapsed/steps;
        }
        if (steps==0) return;
        state.lastStep=h;
        List<Capsule> capsules = colliders(pose);
        List<AABB> blocks = nearbyBlocks(entity);
        float c = cFor(yaw), s = sFor(yaw);
        for (int step = 0; step < steps; step++) {
            if (state.detached)
                for (int i=0;i<vertices.length;i++) state.beforeStep[i].set(state.position[i]);
            float drag = (float)Math.pow(.935f, h * 60f);
            for (int i = 0; i < vertices.length; i++) {
                if (pinned(vertices[i], state)) {
                    state.position[i].set(targets[i]);
                    state.previous[i].set(targets[i]);
                    continue;
                }
                Vector3f p = state.position[i], old = state.previous[i];
                float vx = (p.x - old.x) * drag;
                float vy = (p.y - old.y) * drag;
                float vz = (p.z - old.z) * drag;
                old.set(p);
                float exposed = Math.min(1f, Math.max(0f, (p.y + 18f) / 40f));
                p.add(vx + wind * exposed * h * h,
                        vy + GRAVITY * h * h,
                        vz + (vertices[i].hood ? 12f : 24f) * h * h);
            }
            Arrays.fill(state.lambda, 0f);
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                for (int i = 0; i < links.length; i++) solveLink(state, links[i], i, h);
                for (int i = 0; i < vertices.length; i++) {
                    if (pinned(vertices[i], state)) continue;
                    collideCapsules(state.position[i], capsules);
                    if (state.position[i].y > 23.75f) state.position[i].y = 23.75f;
                    if (vertices[i].hood && !state.detached)
                        retainHood(state.position[i], hoodShape[i], vertices[i].rest.z < 0 ? .65f : 1.2f);
                }
            }
            for (int i = 0; i < vertices.length; i++)
                if (!pinned(vertices[i], state)) collideBlocks(state.position[i], entity, blocks, c, s);
            selfCollide(state);
        }
        } finally {
            if (sampleStart != 0) {
                sampleNanoseconds += System.nanoTime() - sampleStart;
                sampleCount++;
            }
        }
    }

    /** Double-precision world origin with a small, camera-independent model matrix.
     * Keeping translation separate avoids cloth jitter far from the world origin. */
    public static final class PlayerFrame {
        public final Vec3 origin;
        private final Matrix4f forward, inverse;

        public PlayerFrame(Vec3 origin, Matrix4f pixelsToEntity) {
            this.origin=origin;
            forward=new Matrix4f(pixelsToEntity);
            inverse=new Matrix4f(forward).invert();
        }

        private void rebase(Vector3f point, PlayerFrame previous) {
            previous.forward.transformPosition(point);
            point.add((float)(previous.origin.x-origin.x),(float)(previous.origin.y-origin.y),
                    (float)(previous.origin.z-origin.z));
            inverse.transformPosition(point);
        }
    }

    /** An oriented box in the actual animated ModelPart's local coordinates. */
    public static final class BodyBox {
        private final ModelSpace.Transform transform;
        private final Matrix3f inverse;
        private final Vector3f min, max;

        public BodyBox(ModelSpace.Transform transform, Vector3f min, Vector3f max) {
            this.transform=transform;
            inverse=new Matrix3f(transform.rotation()).invert();
            this.min=new Vector3f(min).sub(.4f,.4f,.4f);
            this.max=new Vector3f(max).add(.4f,.4f,.4f);
        }

        private void project(Vector3f point, Vector3f scratch) {
            inverse.transform(scratch.set(point).sub(transform.origin()));
            float x=scratch.x,y=scratch.y,z=scratch.z;
            if(x<=min.x || x>=max.x || y<=min.y || y>=max.y || z<=min.z || z>=max.z) return;
            float nearest=x-min.x;
            int face=0;
            if(max.x-x<nearest) { nearest=max.x-x;face=1; }
            if(y-min.y<nearest) { nearest=y-min.y;face=2; }
            if(max.y-y<nearest) { nearest=max.y-y;face=3; }
            if(z-min.z<nearest) { nearest=z-min.z;face=4; }
            if(max.z-z<nearest) face=5;
            switch(face) {
                case 0 -> scratch.x=min.x; case 1 -> scratch.x=max.x;
                case 2 -> scratch.y=min.y; case 3 -> scratch.y=max.y;
                case 4 -> scratch.z=min.z; case 5 -> scratch.z=max.z;
            }
            transform.rotation().transform(scratch).add(transform.origin());
            point.set(scratch);
        }
    }

    public record LegPose(ModelSpace.Transform transform, Vector3f bindOrigin) {
        Vector3f apply(Vector3f rest) { return transform.apply(new Vector3f(rest).sub(bindOrigin)); }
    }

    public record PlayerPose(Pose upper, List<BodyBox> boxes, PlayerFrame frame, boolean worldCollision,
                             LegPose legR, LegPose legL) {}

    private Vector3f posedRest(Vertex vertex, PlayerPose pose) {
        Bone bone=switch(vertex.part) {
            case HOOD -> Bone.HEAD;
            case SLEEVE_R -> Bone.ARM_R;
            case SLEEVE_L -> Bone.ARM_L;
            default -> Bone.TORSO;
        };
        Vector3f local=new Vector3f(vertex.rest);
        if(bone==Bone.ARM_R || bone==Bone.ARM_L)
            local.sub(bone==Bone.ARM_R?-5:5,slimPlayer?2.5f:2,0);
        Vector3f result=pose.upper.bone(bone).apply(local);
        if(vertex.part==Piece.ROBE && vertex.row>4) {
            float down=Mth.clamp((vertex.row-4)/4f,0,1);
            float left=Mth.clamp(.5f+vertex.rest.x/9.7f,0,1);
            Vector3f legs=pose.legR.apply(vertex.rest).lerp(pose.legL.apply(vertex.rest),left);
            // A loose skirt follows the hips and progressively the legs, with
            // room for physical folds. This prevents a ring of free vertices
            // slipping through a knee and settling behind both legs.
            result.lerp(legs,down*down*(3-2*down));
        }
        return result;
    }

    /** Player garments stay equipped; they never use the boss's release/expiry clock. */
    public void updatePlayer(Entity entity, float age, PlayerPose pose, boolean simulate) {
        if(slimPlayer==null) throw new IllegalStateException("Use the boss update for a boss garment");
        State state=states.computeIfAbsent(entity,ignored -> new State(vertices.length,links.length));
        float elapsed=(age-state.lastAge)/20f;
        boolean reset=!simulate || state.playerFrame==null || !Float.isFinite(elapsed)
                || elapsed<0 || elapsed>.25f || !entity.level().dimension().equals(state.playerDimension)
                || state.playerFrame.origin.distanceToSqr(pose.frame.origin)>64;
        Vector3f[] targets=new Vector3f[vertices.length];
        Vector3f[] shapeTargets=new Vector3f[vertices.length];
        for(int i=0;i<vertices.length;i++) {
            Pin pin=vertices[i].pin;
            if(pin!=null) targets[i]=pose.upper.bone(pin.bone).apply(new Vector3f(pin.offset));
            else if(vertices[i].hood || vertices[i].part==Piece.ROBE) shapeTargets[i]=posedRest(vertices[i],pose);
        }
        Vector3f scratch=new Vector3f();
        if(reset) {
            for(int i=0;i<vertices.length;i++) {
                state.position[i].set(targets[i]!=null?targets[i]:posedRest(vertices[i],pose));
                if(targets[i]==null) for(BodyBox box:pose.boxes) box.project(state.position[i],scratch);
                state.previous[i].set(state.position[i]);
                state.beforeStep[i].set(state.position[i]);
                state.renderPosition[i].set(state.position[i]);
            }
            state.remainder=0;
        } else {
            for(int i=0;i<vertices.length;i++) {
                pose.frame.rebase(state.position[i],state.playerFrame);
                pose.frame.rebase(state.previous[i],state.playerFrame);
                pose.frame.rebase(state.beforeStep[i],state.playerFrame);
            }
            state.remainder+=elapsed;
            int steps=Mth.floor((state.remainder+1e-6f)/STEP);
            state.remainder=Math.max(0,state.remainder-steps*STEP);
            for(int i=0;i<vertices.length;i++) if(targets[i]!=null) state.pinMotion[i].set(state.position[i]);
            List<AABB> blocks=pose.worldCollision?playerBlocks(entity):List.of();
            Vector3f acceleration=pose.frame.inverse.transformDirection(new Vector3f(
                    Mth.sin(age*.09f)*.25f,-GRAVITY/16f,.12f)).mul(STEP*STEP);
            float drag=(float)Math.pow(.935f,STEP*60);
            for(int step=0;step<steps;step++) {
                for(int i=0;i<vertices.length;i++) {
                    state.beforeStep[i].set(state.position[i]);
                    if(targets[i]!=null) {
                        state.position[i].set(state.pinMotion[i]).lerp(targets[i],(step+1f)/steps);
                        state.previous[i].set(state.position[i]);
                    } else {
                        Vector3f point=state.position[i],old=state.previous[i];
                        float vx=(point.x-old.x)*drag,vy=(point.y-old.y)*drag,vz=(point.z-old.z)*drag;
                        old.set(point);
                        point.add(vx+acceleration.x,vy+acceleration.y,vz+acceleration.z);
                    }
                }
                Arrays.fill(state.lambda,0);
                for(int iteration=0;iteration<ITERATIONS;iteration++) {
                    for(int i=0;i<links.length;i++) solveLink(state,links[i],i,STEP);
                    for(int i=0;i<vertices.length;i++) if(targets[i]==null) {
                        if(shapeTargets[i]!=null) {
                            float freedom=vertices[i].hood?.35f:.5f+2*(vertices[i].row-4)/8f;
                            retainHood(state.position[i],shapeTargets[i],freedom);
                        }
                        for(BodyBox box:pose.boxes) box.project(state.position[i],scratch);
                    }
                }
                selfCollide(state);
                for(int i=0;i<vertices.length;i++) if(targets[i]==null) {
                    // A projection out of one overlapping limb can enter the
                    // torso again. Finish against the union, after self contact.
                    for(int pass=0;pass<3;pass++)
                        for(BodyBox box:pose.boxes) box.project(state.position[i],scratch);
                    projectPlayerBlocks(state.position[i],pose.frame,blocks,scratch);
                }
            }
            float fraction=Mth.clamp(state.remainder/STEP,0,1);
            for(int i=0;i<vertices.length;i++) {
                if(!state.position[i].isFinite() || state.position[i].lengthSquared()>65536) {
                    states.remove(entity);
                    updatePlayer(entity,age,pose,false);
                    return;
                }
                state.renderPosition[i].set(targets[i]!=null?targets[i]:state.beforeStep[i]);
                if(targets[i]==null) state.renderPosition[i].lerp(state.position[i],fraction);
            }
        }
        state.lastAge=age;
        state.playerFrame=pose.frame;
        state.playerDimension=entity.level().dimension();
    }

    private static List<AABB> playerBlocks(Entity entity) {
        List<AABB> result=new ArrayList<>();
        if(entity.getVehicle()!=null) result.add(entity.getVehicle().getBoundingBox());
        AABB area=entity.getBoundingBox().inflate(1.15);
        for(BlockPos at:BlockPos.betweenClosed(BlockPos.containing(area.minX,area.minY,area.minZ),
                BlockPos.containing(area.maxX,area.maxY,area.maxZ))) {
            if(!entity.level().hasChunkAt(at)) continue;
            var shape=entity.level().getBlockState(at).getCollisionShape(entity.level(),at);
            for(AABB box:shape.toAabbs()) result.add(box.move(at));
        }
        return result;
    }

    private static void projectPlayerBlocks(Vector3f point, PlayerFrame frame, List<AABB> blocks, Vector3f scratch) {
        if(blocks.isEmpty()) return;
        frame.forward.transformPosition(scratch.set(point));
        double x=frame.origin.x+scratch.x,y=frame.origin.y+scratch.y,z=frame.origin.z+scratch.z;
        for(AABB box:blocks) {
            double x0=box.minX-.018,x1=box.maxX+.018,y0=box.minY-.018,y1=box.maxY+.018,z0=box.minZ-.018,z1=box.maxZ+.018;
            if(x<=x0 || x>=x1 || y<=y0 || y>=y1 || z<=z0 || z>=z1) continue;
            double distance=x-x0;
            int face=0;
            if(x1-x<distance) { distance=x1-x;face=1; }
            if(y-y0<distance) { distance=y-y0;face=2; }
            if(y1-y<distance) { distance=y1-y;face=3; }
            if(z-z0<distance) { distance=z-z0;face=4; }
            if(z1-z<distance) face=5;
            switch(face) {
                case 0 -> x=x0; case 1 -> x=x1; case 2 -> y=y0;
                case 3 -> y=y1; case 4 -> z=z0; case 5 -> z=z1;
            }
        }
        frame.inverse.transformPosition(scratch.set((float)(x-frame.origin.x),
                (float)(y-frame.origin.y),(float)(z-frame.origin.z)));
        point.set(scratch);
    }

    private static void expire(State state, float age) {
        state.detached=true;
        state.expired=true;
        state.lastAge=age;
    }

    private static float cFor(float yaw) { return Mth.cos(yaw * Mth.DEG_TO_RAD); }
    private static float sFor(float yaw) { return Mth.sin(yaw * Mth.DEG_TO_RAD); }
    private static boolean pinned(Vertex vertex, State state) {
        return vertex.pin != null && !state.detached;
    }

    private static void retainHood(Vector3f position, Vector3f target, float freedom) {
        float dx=position.x-target.x,dy=position.y-target.y,dz=position.z-target.z;
        float distance=Mth.sqrt(dx*dx+dy*dy+dz*dz);
        if (distance>freedom) position.set(target).add(dx*freedom/distance,
                dy*freedom/distance,dz*freedom/distance);
    }

    private void solveLink(State state, Link link, int index, float h) {
        Vector3f a = state.position[link.a], b = state.position[link.b];
        float dx = b.x-a.x, dy = b.y-a.y, dz = b.z-a.z;
        float length = Mth.sqrt(dx*dx + dy*dy + dz*dz);
        if (length < 1e-6f) return;
        float wa = pinned(vertices[link.a], state) ? 0f : 1f;
        float wb = pinned(vertices[link.b], state) ? 0f : 1f;
        if (wa + wb == 0) return;
        float alpha = link.compliance / (h*h);
        float delta = (length - link.length + alpha * state.lambda[index]) / (wa + wb + alpha);
        state.lambda[index] -= delta;
        float factor = delta / length;
        a.add(dx * factor * wa, dy * factor * wa, dz * factor * wa);
        b.sub(dx * factor * wb, dy * factor * wb, dz * factor * wb);
    }

    private static List<Capsule> colliders(Pose pose) {
        List<Capsule> result = new ArrayList<>(7);
        result.add(new Capsule(pose.torso.apply(new Vector3f(0,-16,0)),
                pose.torso.apply(new Vector3f(0,19,0)), 5.6f));
        result.add(new Capsule(pose.head.apply(new Vector3f(0,-7,0)),
                pose.head.apply(new Vector3f(0,-2,0)), 5.1f));
        for (ModelSpace.Transform arm : List.of(pose.armR, pose.armL))
            result.add(new Capsule(arm.apply(new Vector3f()), arm.apply(new Vector3f(0,13,0)), 2.5f));
        for (ModelSpace.Transform fore : List.of(pose.foreR, pose.foreL))
            result.add(new Capsule(fore.apply(new Vector3f()), fore.apply(new Vector3f(0,12,0)), 2.5f));
        return result;
    }

    private static void collideCapsules(Vector3f p, List<Capsule> capsules) {
        for (Capsule capsule : capsules) {
            float ax = capsule.a.x, ay = capsule.a.y, az = capsule.a.z;
            float vx = capsule.b.x-ax, vy = capsule.b.y-ay, vz = capsule.b.z-az;
            float denominator = vx*vx + vy*vy + vz*vz;
            float t = denominator < 1e-5f ? 0f : Mth.clamp(((p.x-ax)*vx+(p.y-ay)*vy+(p.z-az)*vz)/denominator,0f,1f);
            float dx = p.x-ax-vx*t, dy = p.y-ay-vy*t, dz = p.z-az-vz*t;
            float len2 = dx*dx + dy*dy + dz*dz;
            float radius = capsule.radius + CONTACT;
            if (len2 >= radius*radius) continue;
            float len = Mth.sqrt(len2);
            if (len < 1e-4f) { p.z += radius; continue; }
            float push = radius/len - 1f;
            p.add(dx*push, dy*push, dz*push);
        }
    }

    private static List<AABB> nearbyBlocks(Entity entity) {
        List<AABB> result = new ArrayList<>();
        AABB area = entity.getBoundingBox().inflate(1.2f);
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(area.minX,area.minY,area.minZ),
                BlockPos.containing(area.maxX,area.maxY,area.maxZ))) {
            var shape = entity.level().getBlockState(block).getCollisionShape(entity.level(), block);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                AABB moved=box.move(block);
                if (moved.maxY > entity.getY()+.12) result.add(moved);
            }
        }
        return result;
    }

    private static void collideBlocks(Vector3f p, Entity entity, List<AABB> blocks, float c, float s) {
        double wx = entity.getX() + (c*p.x+s*p.z)/16f;
        double wy = entity.getY() + (24f-p.y)/16f;
        double wz = entity.getZ() + (s*p.x-c*p.z)/16f;
        for (AABB box : blocks) {
            double x0 = box.minX-.035, x1 = box.maxX+.035;
            double y0 = box.minY-.035, y1 = box.maxY+.035;
            double z0 = box.minZ-.035, z1 = box.maxZ+.035;
            if (wx<=x0 || wx>=x1 || wy<=y0 || wy>=y1 || wz<=z0 || wz>=z1) continue;
            double left=wx-x0, right=x1-wx, bottom=wy-y0, top=y1-wy, front=wz-z0, back=z1-wz;
            double nearest = Math.min(Math.min(Math.min(left,right),Math.min(bottom,top)),Math.min(front,back));
            if (nearest==left) wx=(float)x0;
            else if (nearest==right) wx=(float)x1;
            else if (nearest==bottom) wy=(float)y0;
            else if (nearest==top) wy=(float)y1;
            else if (nearest==front) wz=(float)z0;
            else wz=(float)z1;
        }
        float dx=(float)(wx-entity.getX())*16f, dz=(float)(wz-entity.getZ())*16f;
        p.set(c*dx+s*dz, 24f-(float)(wy-entity.getY())*16f, s*dx-c*dz);
    }

    private void selfCollide(State state) {
        final float cell = slimPlayer==null?1.4f:1f, radius = slimPlayer==null?.85f:.45f;
        Map<Long,Integer> heads = new HashMap<>(vertices.length*2);
        int[] next = new int[vertices.length];
        for (int i=0;i<vertices.length;i++) {
            Vector3f p=state.position[i];
            int x=Mth.floor(p.x/cell), y=Mth.floor(p.y/cell), z=Mth.floor(p.z/cell);
            long key=cellKey(x,y,z);
            next[i]=heads.getOrDefault(key,-1);
            heads.put(key,i);
        }
        for (int i=0;i<vertices.length;i++) {
            if (pinned(vertices[i],state)) continue;
            Vector3f a=state.position[i];
            int x=Mth.floor(a.x/cell), y=Mth.floor(a.y/cell), z=Mth.floor(a.z/cell);
            for (int oz=-1;oz<=1;oz++) for(int oy=-1;oy<=1;oy++) for(int ox=-1;ox<=1;ox++) {
                int j=heads.getOrDefault(cellKey(x+ox,y+oy,z+oz),-1);
                while(j>=0) {
                    if(j>i && !neighbors(vertices[i],vertices[j])) {
                        Vector3f b=state.position[j];
                        float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
                        float distance2=dx*dx+dy*dy+dz*dz;
                        if(distance2>1e-6f && distance2<radius*radius) {
                            float distance=Mth.sqrt(distance2);
                            float wb=pinned(vertices[j],state)?0f:1f;
                            float push=(radius-distance)/distance/(1f+wb);
                            a.sub(dx*push,dy*push,dz*push);
                            if(wb>0) b.add(dx*push,dy*push,dz*push);
                        }
                    }
                    j=next[j];
                }
            }
        }
    }

    private static boolean neighbors(Vertex a, Vertex b) {
        if (a.piece!=b.piece) return false;
        int dc=Math.abs(a.column-b.column);
        dc=Math.min(dc,a.columns-1-dc); // the first and last columns are the same UV seam
        return Math.abs(a.row-b.row)<=2 && dc<=2;
    }

    private static long cellKey(int x,int y,int z) {
        return ((long)(x & 0x1fffff)<<42) | ((long)(y & 0x1fffff)<<21) | (z & 0x1fffffL);
    }

    public void render(Entity entity, PoseStack pose, MultiBufferSource buffers,
                       ResourceLocation texture, int light, int overlay) {
        State state=states.get(entity);
        if(state==null || state.expired) return;
        float alpha=1f;
        Vector3f[] points=slimPlayer==null?state.position:state.renderPosition;
        if (state.detached) {
            float fade=Mth.clamp((state.lastAge-state.detachedAge
                    -(StageCollapse.CLOTH_LIFETIME-StageCollapse.CLOTH_FADE_TICKS))/StageCollapse.CLOTH_FADE_TICKS,0,1);
            alpha=1-fade*fade*(3-2*fade);
            if (alpha<=0) return;
            float fraction=Mth.clamp(state.remainder/STEP,0,1);
            for (int i=0;i<vertices.length;i++)
                state.renderPosition[i].set(state.beforeStep[i]).lerp(state.position[i],fraction);
            points=state.renderPosition;
        }
        int color=((int)(alpha*255)<<24)|0xFFFFFF;
        VertexConsumer out=buffers.getBuffer(alpha<1 ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutoutNoCull(texture));
        pose.pushPose();
        pose.scale(1f/16f,1f/16f,1f/16f);
        for(Quad quad:quads) {
            Vector3f a=points[quad.a], b=points[quad.b], c=points[quad.c], d=points[quad.d];
            Vector3f normal=new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a));
            if(normal.lengthSquared()<1e-8f) continue;
            normal.normalize();
            vertex(out,pose.last(),a,vertices[quad.a],normal,light,overlay,color);
            vertex(out,pose.last(),b,vertices[quad.b],normal,light,overlay,color);
            vertex(out,pose.last(),c,vertices[quad.c],normal,light,overlay,color);
            vertex(out,pose.last(),d,vertices[quad.d],normal,light,overlay,color);
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f p, Vertex v,
                               Vector3f normal, int light, int overlay, int color) {
        out.addVertex(pose,p.x,p.y,p.z).setColor(color).setUv(v.u,v.v)
                .setOverlay(overlay).setLight(light).setNormal(pose,normal.x,normal.y,normal.z);
    }
}
