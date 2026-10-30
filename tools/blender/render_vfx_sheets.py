"""Render the Transient Diviner's VFX flipbooks in Blender (Eevee) and pack them into atlases.

    "E:/Blender Foundation/Blender 5.1/blender.exe" --background --factory-startup \
        --python tools/blender/render_vfx_sheets.py -- <repo> [effect ...]

Each effect is a small animated scene of emission shaders seen top-down by an orthographic camera,
rendered with a transparent film and packed row-major (frame 0 top-left) into
assets/cabinmod/textures/vfx/diviner/<name>.png. design/blender/diviner_vfx.blend keeps the last scene.
Used in game by client/diviner/DivinerFx.java (additive: rgb * alpha).
"""
import math
import os
import random
import sys
import tempfile

import bmesh
import bpy
import numpy as np
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
REPO = argv[0] if argv else os.getcwd()
ONLY = set(argv[1:])
OUT = os.path.join(REPO, "src", "main", "resources", "assets", "cabinmod", "textures", "vfx", "diviner")
BLEND = os.path.join(REPO, "design", "blender", "diviner_vfx.blend")
CELL = 128
TMP = tempfile.mkdtemp(prefix="cabin_vfx_")
scene = bpy.context.scene
rng = random.Random(11)


def reset():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for m in list(bpy.data.materials):
        bpy.data.materials.remove(m)
    for me in list(bpy.data.meshes):
        bpy.data.meshes.remove(me)
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = scene.render.resolution_y = CELL
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    try:
        scene.eevee.taa_render_samples = 16
    except AttributeError:
        pass
    world = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs["Color"].default_value = (0, 0, 0, 1)
        bg.inputs["Strength"].default_value = 0
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = 2.2
    cam = bpy.data.objects.new("cam", cam_data)
    scene.collection.objects.link(cam)
    cam.location = (0, 0, 10)
    scene.camera = cam


def emissive_material(name, color_fn=None):
    """Emission mixed with transparency. Animate the custom 'alpha', 'strength', 'hue' through the returned sockets."""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    try:
        mat.surface_render_method = "BLENDED"
    except AttributeError:
        mat.blend_method = "BLEND"
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    mix = nt.nodes.new("ShaderNodeMixShader")
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    em = nt.nodes.new("ShaderNodeEmission")
    nt.links.new(tr.outputs[0], mix.inputs[1])
    nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    if color_fn:
        color_fn(nt, em, mix)
    return mat, em, mix


def key(sock, value, frame):
    sock.default_value = value
    sock.keyframe_insert("default_value", frame=frame)


def obj_from_bm(name, bm, mat):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    o = bpy.data.objects.new(name, me)
    scene.collection.objects.link(o)
    return o


def ease_out(t):
    return 1 - (1 - t) ** 3


def ramp_color(c0, c1, t):
    return tuple(a + (b - a) * t for a, b in zip(c0, c1)) + (1.0,)


# ------------------------------------------------------------------ effects
def starburst(n):
    mat, em, mix = emissive_material("starburst")
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=4, radius=0.25)
    core = obj_from_bm("core", bm, mat)
    tex = bpy.data.textures.new("burst_noise", "CLOUDS")
    tex.noise_scale = 0.35
    disp = core.modifiers.new("disp", "DISPLACE")
    disp.texture = tex
    rays_mat, rem, rmix = emissive_material("rays")
    rays = []
    for i in range(14):
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, segments=4, radius1=0.035, radius2=0.0, depth=1.0, cap_ends=True)
        bmesh.ops.translate(bm, vec=(0, 0, 0.5), verts=bm.verts)
        r = obj_from_bm("ray%d" % i, bm, rays_mat)
        a = i / 14 * math.tau + rng.uniform(-0.15, 0.15)
        r.rotation_euler = (math.pi / 2, 0, a - math.pi / 2)
        r["len"] = rng.uniform(0.55, 1.0)
        rays.append(r)
    for f in range(n):
        t = f / (n - 1)
        e = ease_out(min(1, t * 1.6))
        core.scale = Vector((1, 1, 1)) * (0.25 + 2.6 * e)
        core.keyframe_insert("scale", frame=f)
        disp.strength = 0.05 + 0.5 * t
        disp.keyframe_insert("strength", frame=f)
        key(em.inputs["Color"], ramp_color((1, 1, 1), (0.35, 0.55, 1.0), min(1, t * 1.4)), f)
        key(em.inputs["Strength"], 1.0, f)
        key(mix.inputs[0], max(0.0, 1 - t) ** 1.6, f)
        for r in rays:
            s = ease_out(min(1, t * 2.2)) * r["len"]
            r.scale = (1, 1, max(0.001, s))
            r.keyframe_insert("scale", frame=f)
        key(rem.inputs["Color"], ramp_color((1, 0.98, 0.9), (0.55, 0.8, 1.0), t), f)
        key(rmix.inputs[0], max(0.0, 1 - t * 1.5) ** 1.2, f)


def shockwave_ring(n):
    mat, em, mix = emissive_material("ring")
    bm = bmesh.new()
    me_tmp = bpy.data.meshes.new("tmp")
    bpy.ops.mesh.primitive_torus_add(major_radius=1.0, minor_radius=0.08, major_segments=96, minor_segments=12)
    ring = bpy.context.active_object
    ring.data.materials.append(mat)
    bm.free()
    bpy.data.meshes.remove(me_tmp)
    for f in range(n):
        t = f / (n - 1)
        e = ease_out(t)
        ring.scale = (0.05 + 0.95 * e, 0.05 + 0.95 * e, max(0.05, 1 - t))
        ring.keyframe_insert("scale", frame=f)
        key(em.inputs["Color"], ramp_color((1, 1, 1), (0.25, 0.85, 1.0), min(1, t * 2)), f)
        key(mix.inputs[0], (1 - t) ** 1.3, f)


def star_shard(n):
    mat, em, mix = emissive_material("shard")
    nt = mat.node_tree
    lw = nt.nodes.new("ShaderNodeLayerWeight")
    lw.inputs["Blend"].default_value = 0.35
    cr = nt.nodes.new("ShaderNodeValToRGB")
    cr.color_ramp.elements[0].color = (0.75, 0.95, 1.0, 1)
    cr.color_ramp.elements[1].color = (0.45, 0.2, 1.0, 1)
    nt.links.new(lw.outputs["Facing"], cr.inputs[0])
    nt.links.new(cr.outputs[0], em.inputs["Color"])
    mix.inputs[0].default_value = 1.0
    bm = bmesh.new()
    pts = [Vector((0, 0, 0.9)), Vector((0, 0, -0.6))] + [Vector((math.cos(a) * 0.35, math.sin(a) * 0.35, 0))
                                                         for a in (0, math.tau / 3, 2 * math.tau / 3)]
    vs = [bm.verts.new(p) for p in pts]
    for i in range(3):
        a, b = vs[2 + i], vs[2 + (i + 1) % 3]
        bm.faces.new((vs[0], a, b))
        bm.faces.new((vs[1], b, a))
    o = obj_from_bm("shard", bm, mat)
    for f in range(n):
        t = f / n
        o.rotation_euler = (0.6 + t * math.tau, 0.3, t * math.tau * 0.5)
        o.keyframe_insert("rotation_euler", frame=f)


def nebula_wisp(n):
    mat, em, mix = emissive_material("wisp")
    nt = mat.node_tree
    tc = nt.nodes.new("ShaderNodeTexCoord")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.noise_dimensions = "4D"
    noise.inputs["Scale"].default_value = 2.2
    noise.inputs["Detail"].default_value = 6
    noise.inputs["Distortion"].default_value = 0.6
    nt.links.new(tc.outputs["Object"], noise.inputs["Vector"])
    grad = nt.nodes.new("ShaderNodeTexGradient")
    grad.gradient_type = "SPHERICAL"
    nt.links.new(tc.outputs["Object"], grad.inputs["Vector"])
    shape = nt.nodes.new("ShaderNodeMath")
    shape.operation = "MULTIPLY"
    nt.links.new(noise.outputs["Fac"], shape.inputs[0])
    nt.links.new(grad.outputs["Fac"], shape.inputs[1])
    sharpen = nt.nodes.new("ShaderNodeMapRange")
    nt.links.new(shape.outputs[0], sharpen.inputs["Value"])
    sharpen.inputs["From Min"].default_value = 0.12
    sharpen.inputs["From Max"].default_value = 0.55
    life = nt.nodes.new("ShaderNodeMath")
    life.operation = "MULTIPLY"
    nt.links.new(sharpen.outputs["Result"], life.inputs[0])
    nt.links.new(life.outputs[0], mix.inputs[0])
    cr = nt.nodes.new("ShaderNodeValToRGB")
    els = cr.color_ramp.elements
    els[0].color = (0.35, 0.1, 0.7, 1)
    els[1].color = (0.4, 0.95, 1.0, 1)
    e = els.new(0.55)
    e.color = (0.95, 0.35, 0.85, 1)
    nt.links.new(noise.outputs["Fac"], cr.inputs[0])
    nt.links.new(cr.outputs[0], em.inputs["Color"])
    bpy.ops.mesh.primitive_plane_add(size=2.0)
    plane = bpy.context.active_object
    plane.data.materials.append(mat)
    for f in range(n):
        t = f / (n - 1)
        key(noise.inputs["W"], t * 1.5, f)
        key(life.inputs[1], math.sin(math.pi * t) ** 0.8, f)
        plane.scale = Vector((1, 1, 1)) * (0.7 + 0.3 * t)
        plane.keyframe_insert("scale", frame=f)


def spark(n):
    mat, em, mix = emissive_material("spark")
    nt = mat.node_tree
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Object"], sep.inputs[0])

    def m(op, a, b=None):
        node = nt.nodes.new("ShaderNodeMath")
        node.operation = op
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                node.inputs[i].default_value = v
            else:
                nt.links.new(v, node.inputs[i])
        return node.outputs[0]

    ax, ay = m("ABSOLUTE", sep.outputs["X"]), m("ABSOLUTE", sep.outputs["Y"])
    r = m("SQRT", m("ADD", m("MULTIPLY", ax, ax), m("MULTIPLY", ay, ay)))
    core = m("POWER", m("MAXIMUM", m("SUBTRACT", 1.0, m("MULTIPLY", r, 3.0)), 0.0), 2.0)
    cross = m("MULTIPLY", m("MAXIMUM", m("SUBTRACT", 1.0, m("MULTIPLY", m("MULTIPLY", ax, ay), 60.0)), 0.0),
              m("MAXIMUM", m("SUBTRACT", 1.0, r), 0.0))
    total = m("MINIMUM", m("ADD", core, m("POWER", cross, 3.0)), 1.0)
    life = m("MULTIPLY", total, 1.0)
    nt.links.new(life, mix.inputs[0])
    em.inputs["Color"].default_value = (0.85, 0.95, 1.0, 1)
    bpy.ops.mesh.primitive_plane_add(size=2.0)
    plane = bpy.context.active_object
    plane.data.materials.append(mat)
    life_node = life.node
    for f in range(n):
        t = f / n
        plane.rotation_euler = (0, 0, t * math.pi / 2)
        plane.keyframe_insert("rotation_euler", frame=f)
        key(life_node.inputs[1], 0.55 + 0.45 * math.cos(t * math.tau) ** 2, f)


EFFECTS = {  # name: (builder, grid)
    "starburst_8x8": (starburst, 8),
    "shockwave_ring_8x8": (shockwave_ring, 8),
    "star_shard_4x4": (star_shard, 4),
    "nebula_wisp_8x8": (nebula_wisp, 8),
    "spark_4x4": (spark, 4),
}


def render_sheet(name, builder, grid):
    reset()
    n = grid * grid
    builder(n)
    scene.frame_start, scene.frame_end = 0, n - 1
    atlas = np.zeros((grid * CELL, grid * CELL, 4), dtype=np.float32)
    for f in range(n):
        scene.frame_set(f)
        path = os.path.join(TMP, "%s_%03d.png" % (name, f))
        scene.render.filepath = path
        bpy.ops.render.render(write_still=True)
        img = bpy.data.images.load(path)
        px = np.empty(CELL * CELL * 4, dtype=np.float32)
        img.pixels.foreach_get(px)
        bpy.data.images.remove(img)
        px = px.reshape(CELL, CELL, 4)[::-1]            # Blender rows are bottom-up
        row, colm = divmod(f, grid)
        atlas[row * CELL:(row + 1) * CELL, colm * CELL:(colm + 1) * CELL] = px
    out = bpy.data.images.new(name, grid * CELL, grid * CELL, alpha=True)
    out.pixels.foreach_set(atlas[::-1].ravel())
    os.makedirs(OUT, exist_ok=True)
    out.filepath_raw = os.path.join(OUT, name + ".png")
    out.file_format = "PNG"
    out.save()
    print("VFX", name, n, "frames, alpha max", float(atlas[..., 3].max()))


for name, (builder, grid) in EFFECTS.items():
    if ONLY and name not in ONLY:
        continue
    render_sheet(name, builder, grid)
os.makedirs(os.path.dirname(BLEND), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=BLEND, compress=True)
