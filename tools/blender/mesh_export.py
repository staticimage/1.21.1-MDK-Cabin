"""Export Blender meshes as Cabin Mod triangle meshes (.cmesh), read in game by client/mesh/CabinMesh.java.

mc_export.py only knows Minecraft cubes; this is the path for real geometry (the diviner's planet,
atmosphere shell, comet, impact crater). 1 Blender unit = 1 block (or 1 planet radius for the planet).
Blender (x right, y back, z up) -> Minecraft world space (x, y up, z): (x, z, -y).

Format, little-endian:
    b"CMSH", int32 version (1), int32 vertexCount, int32 indexCount, int32 frameCount
    frameCount x (float32[3] position, float32[3] normal) per vertex
    float32[2] uv per vertex, uint8[4] rgba per vertex
    int32 indices (triangles)
Frames share topology: the crater's rise -> crown -> cool shapes are stored as frames and blended in game.

Use from another script: `from mesh_export import export_frames` (tools/blender on sys.path), or through
tools/blender/bl.py with --set obj=<name> --set out=<path>.
"""
import os
import struct

import bpy
from mathutils import Vector


def to_mc(v):
    return (v.x, v.z, -v.y)


def _evaluated_mesh(obj):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = obj.evaluated_get(dg)
    me = ev.to_mesh()
    me.calc_loop_triangles()
    return ev, me


def _corners(obj):
    """Unique (vertex, uv, colour, normal) corners of the evaluated, triangulated mesh, in loop order."""
    ev, me = _evaluated_mesh(obj)
    uv_layer = me.uv_layers.active.data if me.uv_layers.active else None
    col = me.color_attributes.active_color if me.color_attributes else None
    smooth = obj.get("cmesh_smooth", True)
    corners, tris = [], []
    for tri in me.loop_triangles:
        idx = []
        for k, li in enumerate(tri.loops):
            vi = me.loops[li].vertex_index
            uv = tuple(uv_layer[li].uv) if uv_layer else (0.0, 0.0)
            if col is None:
                rgba = (1.0, 1.0, 1.0, 1.0)
            elif col.domain == "CORNER":
                rgba = tuple(col.data[li].color)
            else:
                rgba = tuple(col.data[vi].color)
            n = tuple(me.loops[li].normal) if smooth else tuple(tri.normal)
            idx.append(len(corners))
            corners.append((vi, uv, rgba, n, li))
        tris.append(idx)
    ev.to_mesh_clear()
    return corners, tris


def _frame(obj, corners):
    ev, me = _evaluated_mesh(obj)
    mw = obj.matrix_world if obj.get("cmesh_world", False) else None
    smooth = obj.get("cmesh_smooth", True)
    pos, nor = [], []
    tri_normals = {}
    if not smooth:
        for tri in me.loop_triangles:
            for li in tri.loops:
                tri_normals[li] = tri.normal.copy()
    for vi, _, _, _, li in corners:
        p = me.vertices[vi].co.copy()
        n = me.loops[li].normal.copy() if smooth else tri_normals[li]
        if mw is not None:
            p = mw @ p
            n = (mw.to_3x3() @ n).normalized()
        pos.append(to_mc(p))
        nor.append(to_mc(n))
    ev.to_mesh_clear()
    return pos, nor


def _dedupe(corners, tris):
    key_to_new, verts, remap = {}, [], []
    for vi, uv, rgba, n, li in corners:
        key = (vi, round(uv[0], 6), round(uv[1], 6), tuple(round(c, 4) for c in rgba), tuple(round(c, 4) for c in n))
        if key not in key_to_new:
            key_to_new[key] = len(verts)
            verts.append(len(remap))
        remap.append(key_to_new[key])
    indices = [remap[i] for t in tris for i in t]
    return verts, indices


def export_frames(frame_setters, obj, path):
    """frame_setters: list of callables that pose `obj` for each frame (or [None] for a static mesh)."""
    if frame_setters and frame_setters[0]:
        frame_setters[0]()
    bpy.context.view_layer.update()
    corners, tris = _corners(obj)
    keep, indices = _dedupe(corners, tris)
    frames = []
    for setter in frame_setters:
        if setter:
            setter()
        bpy.context.view_layer.update()
        pos, nor = _frame(obj, corners)
        frames.append(([pos[i] for i in keep], [nor[i] for i in keep]))
    uvs = [corners[i][1] for i in keep]
    cols = [corners[i][2] for i in keep]

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"CMSH")
        f.write(struct.pack("<4i", 1, len(keep), len(indices), len(frames)))
        for pos, nor in frames:
            for p, n in zip(pos, nor):
                f.write(struct.pack("<6f", *p, *n))
        for (u, v) in uvs:
            f.write(struct.pack("<2f", u, v))
        for c in cols:
            f.write(bytes(max(0, min(255, int(round(x * 255)))) for x in c))
        f.write(struct.pack("<%di" % len(indices), *indices))
    return {"path": path, "vertices": len(keep), "triangles": len(indices) // 3, "frames": len(frames)}


def export(obj, path):
    return export_frames([None], obj, path)


if __name__ == "__main__" or "ARGS" in globals():
    ARGS = globals().get("ARGS", {})
    if "obj" in ARGS:
        result = export(bpy.data.objects[ARGS["obj"]], ARGS["out"])
