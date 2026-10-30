package net.austin.cabinmod.client.mesh;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.austin.cabinmod.CabinMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * A triangle mesh exported from Blender by tools/blender/mesh_export.py (assets/cabinmod/meshes/*.cmesh).
 * Positions are in Minecraft axes (y up). UVs are flipped on load so v = 0 is the top of the texture.
 * Big static meshes (the planet) are uploaded once to a VertexBuffer; small ones are emitted into a
 * VertexConsumer each frame, which also lets multi-frame meshes (the crater) blend between frames.
 */
public final class CabinMesh {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, CabinMesh> CACHE = new HashMap<>();
    private static final Map<String, Boolean> MISSING = new HashMap<>();

    public final int vertexCount;
    public final int frameCount;
    private final float[][] positions;   // per frame: xyz * vertexCount
    private final float[][] normals;
    private final float[] uvs;
    private final byte[] colors;
    private final int[] indices;
    private VertexBuffer gpu;

    private CabinMesh(int vertexCount, int frameCount, float[][] positions, float[][] normals, float[] uvs, byte[] colors, int[] indices) {
        this.vertexCount = vertexCount;
        this.frameCount = frameCount;
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.colors = colors;
        this.indices = indices;
    }

    /** Loads (once) meshes/<name>.cmesh, or returns null if it is missing or broken. */
    public static CabinMesh get(String name) {
        CabinMesh mesh = CACHE.get(name);
        if (mesh != null || MISSING.containsKey(name)) return mesh;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "meshes/" + name + ".cmesh");
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(id)) {
            mesh = read(ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN));
            CACHE.put(name, mesh);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Can't load mesh {}", id, e);
            MISSING.put(name, true);
        }
        return mesh;
    }

    /** Drops every loaded mesh (resource reload). */
    public static void clearCache() {
        for (CabinMesh mesh : CACHE.values()) mesh.close();
        CACHE.clear();
        MISSING.clear();
    }

    private static CabinMesh read(ByteBuffer b) {
        if (b.get() != 'C' || b.get() != 'M' || b.get() != 'S' || b.get() != 'H') throw new IllegalStateException("not a cmesh");
        int version = b.getInt();
        if (version != 1) throw new IllegalStateException("cmesh version " + version);
        int vertices = b.getInt(), indexCount = b.getInt(), frames = b.getInt();
        float[][] pos = new float[frames][vertices * 3], nor = new float[frames][vertices * 3];
        for (int f = 0; f < frames; f++) {
            for (int v = 0; v < vertices; v++) {
                for (int k = 0; k < 3; k++) pos[f][v * 3 + k] = b.getFloat();
                for (int k = 0; k < 3; k++) nor[f][v * 3 + k] = b.getFloat();
            }
        }
        float[] uv = new float[vertices * 2];
        for (int v = 0; v < vertices; v++) {
            uv[v * 2] = b.getFloat();
            uv[v * 2 + 1] = 1f - b.getFloat();
        }
        byte[] rgba = new byte[vertices * 4];
        b.get(rgba);
        int[] idx = new int[indexCount];
        for (int i = 0; i < indexCount; i++) idx[i] = b.getInt();
        return new CabinMesh(vertices, frames, pos, nor, uv, rgba, idx);
    }

    private int color(int v, float r, float g, float b, float a) {
        int cr = Math.round((colors[v * 4] & 0xFF) * r), cg = Math.round((colors[v * 4 + 1] & 0xFF) * g);
        int cb = Math.round((colors[v * 4 + 2] & 0xFF) * b), ca = Math.round((colors[v * 4 + 3] & 0xFF) * a);
        return (Math.min(ca, 255) << 24) | (Math.min(cr, 255) << 16) | (Math.min(cg, 255) << 8) | Math.min(cb, 255);
    }

    /** Draws frame 0 from a VertexBuffer (built on first use) with the given shader. */
    public void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (gpu == null) {
            ByteBufferBuilder bytes = new ByteBufferBuilder(indices.length * DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL.getVertexSize());
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            for (int v : indices) {
                builder.addVertex(positions[0][v * 3], positions[0][v * 3 + 1], positions[0][v * 3 + 2])
                        .setUv(uvs[v * 2], uvs[v * 2 + 1])
                        .setColor(color(v, 1, 1, 1, 1))
                        .setNormal(normals[0][v * 3], normals[0][v * 3 + 1], normals[0][v * 3 + 2]);
            }
            try (MeshData data = builder.buildOrThrow()) {
                gpu = new VertexBuffer(VertexBuffer.Usage.STATIC);
                gpu.bind();
                gpu.upload(data);
                VertexBuffer.unbind();
            }
            bytes.close();
        }
        gpu.bind();
        gpu.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    /**
     * Emits the mesh into `out` (format POSITION_TEX_COLOR_NORMAL). `frame` blends between stored frames
     * (0..frameCount-1). `pose` transforms positions; the normal matrix is taken from its upper 3x3.
     */
    public void emit(VertexConsumer out, Matrix4f pose, float frame, float r, float g, float b, float a) {
        int f0 = Math.max(0, Math.min(frameCount - 1, (int) Math.floor(frame)));
        int f1 = Math.min(frameCount - 1, f0 + 1);
        float t = Math.max(0f, Math.min(1f, frame - f0));
        float[] p0 = positions[f0], p1 = positions[f1], n0 = normals[f0], n1 = normals[f1];
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (int v : indices) {
            int i = v * 3;
            p.set(p0[i] + (p1[i] - p0[i]) * t, p0[i + 1] + (p1[i + 1] - p0[i + 1]) * t, p0[i + 2] + (p1[i + 2] - p0[i + 2]) * t);
            n.set(n0[i] + (n1[i] - n0[i]) * t, n0[i + 1] + (n1[i + 1] - n0[i + 1]) * t, n0[i + 2] + (n1[i + 2] - n0[i + 2]) * t);
            pose.transformPosition(p);
            pose.transformDirection(n);
            if (n.lengthSquared() > 1e-8f) n.normalize();
            out.addVertex(p.x, p.y, p.z).setUv(uvs[v * 2], uvs[v * 2 + 1]).setColor(color(v, r, g, b, a)).setNormal(n.x, n.y, n.z);
        }
    }

    /** Unit direction (object space) of the vertex whose UV is nearest to (u, v); v measured from the top. */
    public Vector3f directionAtUv(float u, float v) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            float du = uvs[i * 2] - u, dv = uvs[i * 2 + 1] - v;
            float d = du * du + dv * dv;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return new Vector3f(positions[0][best * 3], positions[0][best * 3 + 1], positions[0][best * 3 + 2]).normalize();
    }

    private void close() {
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
    }
}
