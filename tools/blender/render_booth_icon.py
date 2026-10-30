"""Render textured Minecraft boxes headless: the DJ booth item icon (and texture previews of any mob).

Run with a model JSON containing the active booth atlas and UV face rectangles:
    "E:/Blender Foundation/blender/blender.exe" --background --factory-startup \
        --python tools/blender/render_booth_icon.py -- --model <model.json> --out <png> [--size 512] [--view icon]

model.json: atlas, texture, emissive, boxes [{name, lo, hi, rects {face: [x, y, w, h]}}] - rest-pose boxes in
Blender coordinates (+X = mob's left, -Y = front, Z up, 1 unit = 1 px) and Minecraft's exact box-UV face rects
(atlas px). Each face shows texture x Minecraft's flat face shade (top 1.0, front/back 0.8, sides 0.6, bottom 0.5)
+ the emissive texture, alpha cut out at 0.1 - close to how the game draws it. Cycles, emission only, no lights.
Views: icon (orthographic 3/4 from the front, above), front34, back34, front, back, side.
--light scales the lit base (1 = daylight, 0.12 = night: mostly the glow is left).
"""
import json
import math
import os
import sys

import bpy
from mathutils import Vector

SHADE = {"top": 1.0, "bottom": 0.5, "front": 0.8, "back": 0.8, "left": 0.6, "right": 0.6}
NORMAL = {"front": (0, -1, 0), "back": (0, 1, 0), "left": (1, 0, 0), "right": (-1, 0, 0),
          "top": (0, 0, 1), "bottom": (0, 0, -1)}
VIEWS = {   # azimuth (deg, 0 = in front, + = towards the mob's left), elevation (deg), orthographic
    "icon": (35.0, 30.0, True), "front34": (-30.0, 10.0, True), "back34": (150.0, 14.0, True),
    "front": (0.0, 0.0, True), "back": (180.0, 0.0, True), "side": (90.0, 0.0, True),
}


def parse_args():
    import argparse
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--size", type=int, default=512)
    ap.add_argument("--view", default="icon", choices=sorted(VIEWS))
    ap.add_argument("--samples", type=int, default=32)
    ap.add_argument("--light", type=float, default=1.0, help="world light on the base texture (0.12 = night)")
    return ap.parse_args(argv)


def corner(face, lo, hi, s, t):
    """Texture (s left->right, t top->bottom) -> point; same layout as ModelPart.Cube / paint_placeholders.face_xyz."""
    X0, Y0, Z0 = lo
    X1, Y1, Z1 = hi
    if face == "front":
        return (X0 + s * (X1 - X0), Y0, Z1 - t * (Z1 - Z0))
    if face == "back":
        return (X1 - s * (X1 - X0), Y1, Z1 - t * (Z1 - Z0))
    if face == "right":
        return (X0, Y1 - s * (Y1 - Y0), Z1 - t * (Z1 - Z0))
    if face == "left":
        return (X1, Y0 + s * (Y1 - Y0), Z1 - t * (Z1 - Z0))
    if face == "top":
        return (X0 + s * (X1 - X0), Y1 - t * (Y1 - Y0), Z1)
    return (X0 + s * (X1 - X0), Y1 - t * (Y1 - Y0), Z0)


def build_mesh(model, light=1.0):
    A = float(model["atlas"])
    verts, faces, uvs, shades = [], [], [], []
    for box in model["boxes"]:
        lo, hi = box["lo"], box["hi"]
        for face, (x0, y0, w, h) in box["rects"].items():
            if w <= 0 or h <= 0:
                continue
            st = [(0, 0), (1, 0), (1, 1), (0, 1)]
            pts = [Vector(corner(face, lo, hi, s, t)) for s, t in st]
            n = (pts[1] - pts[0]).cross(pts[3] - pts[0])
            order = (0, 1, 2, 3) if n.dot(Vector(NORMAL[face])) > 0 else (0, 3, 2, 1)
            i0 = len(verts)
            for k in order:
                verts.append(tuple(pts[k]))
                s, t = st[k]
                uvs.append(((x0 + s * w) / A, 1.0 - (y0 + t * h) / A))
            faces.append((i0, i0 + 1, i0 + 2, i0 + 3))
            shades.append(SHADE[face] * light)
    me = bpy.data.meshes.new("mc_" + model["mob"])
    me.from_pydata(verts, [], faces)
    me.update()
    uv = me.uv_layers.new(name="UVMap")
    for loop in me.loops:
        uv.data[loop.index].uv = uvs[loop.vertex_index]
    attr = me.attributes.new("shade", "FLOAT", "FACE")
    attr.data.foreach_set("value", shades)
    ob = bpy.data.objects.new("mc_" + model["mob"], me)
    bpy.context.scene.collection.objects.link(ob)
    ob.data.materials.append(make_material(model))
    return ob, verts


def sock(node, ident, out=False):
    return next(s for s in (node.outputs if out else node.inputs) if s.identifier == ident)


def make_material(model):
    mat = bpy.data.materials.new("mc")
    mat.use_nodes = True
    nt = mat.node_tree
    nodes, links = nt.nodes, nt.links
    nodes.clear()
    uvn = nodes.new("ShaderNodeUVMap")
    uvn.uv_map = "UVMap"
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(model["texture"])
    tex.interpolation = "Closest"
    emi = nodes.new("ShaderNodeTexImage")
    emi.image = bpy.data.images.load(model["emissive"])
    emi.interpolation = "Closest"
    links.new(uvn.outputs["UV"], tex.inputs["Vector"])
    links.new(uvn.outputs["UV"], emi.inputs["Vector"])
    shade = nodes.new("ShaderNodeAttribute")
    shade.attribute_type = "GEOMETRY"
    shade.attribute_name = "shade"
    mul = nodes.new("ShaderNodeMix")
    mul.data_type, mul.blend_type = "RGBA", "MULTIPLY"
    sock(mul, "Factor_Float").default_value = 1.0
    links.new(tex.outputs["Color"], sock(mul, "A_Color"))
    links.new(shade.outputs["Fac"], sock(mul, "B_Color"))
    add = nodes.new("ShaderNodeMix")
    add.data_type, add.blend_type = "RGBA", "ADD"
    sock(add, "Factor_Float").default_value = 1.0
    links.new(sock(mul, "Result_Color", True), sock(add, "A_Color"))
    links.new(emi.outputs["Color"], sock(add, "B_Color"))
    em = nodes.new("ShaderNodeEmission")
    em.inputs["Strength"].default_value = 1.0
    links.new(sock(add, "Result_Color", True), em.inputs["Color"])
    cut = nodes.new("ShaderNodeMath")
    cut.operation = "GREATER_THAN"
    cut.inputs[1].default_value = 0.1
    links.new(tex.outputs["Alpha"], cut.inputs[0])
    tr = nodes.new("ShaderNodeBsdfTransparent")
    mix = nodes.new("ShaderNodeMixShader")
    links.new(cut.outputs["Value"], mix.inputs["Fac"])
    links.new(tr.outputs["BSDF"], mix.inputs[1])
    links.new(em.outputs["Emission"], mix.inputs[2])
    out = nodes.new("ShaderNodeOutputMaterial")
    links.new(mix.outputs["Shader"], out.inputs["Surface"])
    return mat


def frame_camera(verts, view):
    az, el, ortho = VIEWS[view]
    az, el = math.radians(az), math.radians(el)
    d = Vector((math.sin(az) * math.cos(el), -math.cos(az) * math.cos(el), math.sin(el)))   # towards the camera
    lo = Vector((min(v[i] for v in verts) for i in range(3)))
    hi = Vector((max(v[i] for v in verts) for i in range(3)))
    centre = (lo + hi) / 2
    radius = (hi - lo).length
    cam_d = bpy.data.cameras.new("cam")
    cam = bpy.data.objects.new("cam", cam_d)
    bpy.context.scene.collection.objects.link(cam)
    cam.location = centre + d * radius * 3
    cam.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    bpy.context.view_layer.update()
    rot = cam.matrix_world.to_3x3()
    right, up = rot.col[0], rot.col[1]
    xs = [Vector(v).dot(right) for v in verts]
    ys = [Vector(v).dot(up) for v in verts]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    cam.location += right * (cx - centre.dot(right)) + up * (cy - centre.dot(up))
    cam_d.type = "ORTHO" if ortho else "PERSP"
    cam_d.ortho_scale = max(max(xs) - min(xs), max(ys) - min(ys)) * 1.06
    cam_d.clip_start, cam_d.clip_end = 0.1, radius * 10
    bpy.context.scene.camera = cam


def main():
    a = parse_args()
    model = json.load(open(a.model, encoding="utf-8"))
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    _, verts = build_mesh(model, a.light)
    frame_camera(verts, a.view)
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = a.samples
    scene.cycles.use_denoising = False
    scene.cycles.max_bounces = 0
    scene.cycles.transparent_max_bounces = 16
    scene.render.film_transparent = True
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.render.resolution_x = scene.render.resolution_y = a.size
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "8"
    scene.render.filepath = os.path.abspath(a.out)
    world = bpy.data.worlds.new("w")
    world.color = (0, 0, 0)
    scene.world = world
    bpy.ops.render.render(write_still=True)
    print("RENDERED", a.out, flush=True)


main()
