"""Build the Transient Diviner's space assets in Blender and export them for the game.

    "E:/Blender Foundation/Blender 5.1/blender.exe" --background --factory-startup \
        --python tools/blender/build_diviner_space.py -- <repo> [--skip-bake]

Creates design/blender/diviner_space.blend (collection MC_space) with:
    planet        UV sphere, radius 1. Material `planet_bake` is the authored look (continents, oceans,
                  deserts, ice, city lights) - tweak it in the .blend and re-run to re-bake.
    planet_clouds cloud shell, radius 1.008, material `clouds_bake`
    planet_atmo   atmosphere shell, radius 1.06 - mesh only; the glow is shaders/core/diviner_atmosphere.fsh
    comet         faceted crystal core + trailing tail fins, vertex colours (alpha fades down the tail)
    crater        molten-glass splash crown for the diviner top; shape keys crown / cool -> 3 mesh frames
    shard         small crystal for impact debris
Writes assets/cabinmod/meshes/*.cmesh and textures/diviner/planet_{albedo,data,clouds}.png:
    albedo RGB (sRGB); data R = ocean mask, G = terrain height, B = city lights; clouds R = density.
"""
import math
import os
import random
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
REPO = argv[0] if argv else os.getcwd()
SKIP_BAKE = "--skip-bake" in argv
sys.path.insert(0, os.path.join(REPO, "tools", "blender"))
from mesh_export import export, export_frames  # noqa: E402

ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "cabinmod")
MESHES = os.path.join(ASSETS, "meshes")
TEX = os.path.join(ASSETS, "textures", "diviner")
BLEND = os.path.join(REPO, "design", "blender", "diviner_space.blend")
TEX_W, TEX_H = 2048, 1024

for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
scene = bpy.context.scene
col = bpy.data.collections.get("MC_space") or bpy.data.collections.new("MC_space")
if col.name not in scene.collection.children:
    scene.collection.children.link(col)


def link(obj):
    col.objects.link(obj)
    return obj


def uv_sphere(name, radius, segments, rings):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bm.loops.layers.uv.new("UVMap")
    bmesh.ops.create_uvsphere(bm, u_segments=segments, v_segments=rings, radius=radius, calc_uvs=True)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    return link(bpy.data.objects.new(name, me))


# ------------------------------------------------------------------ node helpers
class G:
    """Tiny node-graph builder for one material."""

    def __init__(self, mat):
        mat.use_nodes = True
        self.nt = mat.node_tree
        self.nt.nodes.clear()
        self.x = 0

    def node(self, kind, **props):
        n = self.nt.nodes.new(kind)
        n.location = (self.x, 0)
        self.x += 180
        for k, v in props.items():
            setattr(n, k, v)
        return n

    def link(self, out, inp):
        self.nt.links.new(out, inp)

    @staticmethod
    def sock(sockets, ident):
        for s in sockets:
            if s.identifier == ident or s.name == ident:
                return s
        raise KeyError(ident)

    def val(self, x):
        if isinstance(x, (int, float)):
            n = self.node("ShaderNodeValue")
            n.outputs[0].default_value = x
            return n.outputs[0]
        return x

    def math(self, op, a, b=None, clamp=False):
        n = self.node("ShaderNodeMath", operation=op, use_clamp=clamp)
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                self.link(v, n.inputs[i])
        return n.outputs[0]

    def remap(self, v, a, b, lo=0.0, hi=1.0):
        n = self.node("ShaderNodeMapRange", clamp=True)
        self.link(v, n.inputs["Value"])
        n.inputs["From Min"].default_value = a
        n.inputs["From Max"].default_value = b
        n.inputs["To Min"].default_value = lo
        n.inputs["To Max"].default_value = hi
        return n.outputs["Result"]

    def noise(self, vec, scale, detail=8.0, rough=0.55, lac=2.0, distortion=0.0):
        n = self.node("ShaderNodeTexNoise", noise_dimensions="3D")
        self.link(vec, n.inputs["Vector"])
        n.inputs["Scale"].default_value = scale
        n.inputs["Detail"].default_value = detail
        n.inputs["Roughness"].default_value = rough
        n.inputs["Lacunarity"].default_value = lac
        n.inputs["Distortion"].default_value = distortion
        return n

    def vmath(self, op, a, b=None, scale=None):
        n = self.node("ShaderNodeVectorMath", operation=op)
        self.link(a, n.inputs[0])
        if b is not None:
            if isinstance(b, (tuple, list)):
                n.inputs[1].default_value = b
            else:
                self.link(b, n.inputs[1])
        if scale is not None:
            n.inputs[3].default_value = scale
        return n.outputs[0]

    def rgb(self, c):
        n = self.node("ShaderNodeRGB")
        n.outputs[0].default_value = (*c, 1.0)
        return n.outputs[0]

    def mix(self, fac, a, b):
        n = self.node("ShaderNodeMix", data_type="RGBA", clamp_factor=True)
        self.link(self.val(fac), self.sock(n.inputs, "Factor_Float"))
        self.link(a, self.sock(n.inputs, "A_Color"))
        self.link(b, self.sock(n.inputs, "B_Color"))
        return self.sock(n.outputs, "Result_Color")

    def ramp(self, v, stops):
        n = self.node("ShaderNodeValToRGB")
        self.link(v, n.inputs[0])
        els = n.color_ramp.elements
        while len(els) > 1:
            els.remove(els[-1])
        els[0].position, els[0].color = stops[0][0], (*stops[0][1], 1.0)
        for pos, c in stops[1:]:
            e = els.new(pos)
            e.color = (*c, 1.0)
        return n.outputs[0]

    def combine(self, r, g, b):
        n = self.node("ShaderNodeCombineColor")
        for i, v in enumerate((r, g, b)):
            self.link(self.val(v), n.inputs[i])
        return n.outputs[0]

    def emit_out(self, color):
        em = self.node("ShaderNodeEmission")
        self.link(color, em.inputs["Color"])
        out = self.node("ShaderNodeOutputMaterial")
        self.link(em.outputs[0], out.inputs["Surface"])
        return em


def planet_fields(g):
    """Shared terrain fields: height (0..1), land mask, ice mask, moisture. All in object space."""
    tc = g.node("ShaderNodeTexCoord")
    p = tc.outputs["Object"]
    warp = g.noise(p, 1.1, detail=3.0, rough=0.5)
    warped = g.vmath("ADD", p, g.vmath("SCALE", g.vmath("SUBTRACT", warp.outputs["Color"], (0.5, 0.5, 0.5)), scale=0.9))
    h = g.noise(warped, 1.35, detail=12.0, rough=0.6, lac=2.05).outputs["Fac"]
    ridge = g.noise(p, 4.0, detail=6.0, rough=0.5).outputs["Fac"]
    h = g.math("ADD", h, g.math("MULTIPLY", g.math("SUBTRACT", ridge, 0.5), 0.12))
    sep = g.node("ShaderNodeSeparateXYZ")
    g.link(p, sep.inputs[0])
    lat = g.math("ABSOLUTE", sep.outputs["Z"])
    land = g.remap(h, 0.515, 0.53)
    ice_n = g.noise(p, 5.0, detail=4.0).outputs["Fac"]
    ice = g.remap(g.math("ADD", lat, g.math("MULTIPLY", g.math("SUBTRACT", ice_n, 0.5), 0.12)), 0.915, 0.94)
    moist = g.noise(g.vmath("ADD", p, (3.1, -1.7, 0.4)), 2.2, detail=5.0).outputs["Fac"]
    return p, h, land, ice, moist, lat


def build_planet_material(kind):
    mat = bpy.data.materials.new("planet_bake_" + kind)
    g = G(mat)
    p, h, land, ice, moist, lat = planet_fields(g)
    if kind == "albedo":
        shallow = g.remap(h, 0.42, 0.52)
        ocean = g.mix(shallow, g.rgb((0.008, 0.03, 0.10)), g.rgb((0.02, 0.20, 0.32)))
        land_h = g.remap(h, 0.52, 0.78)
        ground = g.ramp(land_h, [(0.0, (0.52, 0.47, 0.31)), (0.04, (0.10, 0.25, 0.06)), (0.40, (0.17, 0.26, 0.08)),
                                 (0.65, (0.33, 0.29, 0.20)), (0.85, (0.42, 0.40, 0.38)), (0.96, (0.92, 0.93, 0.96))])
        dry = g.math("MULTIPLY", g.remap(moist, 0.52, 0.62), g.remap(lat, 0.50, 0.30))
        ground = g.mix(dry, ground, g.rgb((0.66, 0.50, 0.30)))
        surface = g.mix(land, ocean, ground)
        color = g.mix(ice, surface, g.rgb((0.90, 0.94, 0.99)))
    elif kind == "data":
        ocean = g.math("MULTIPLY", g.math("SUBTRACT", 1.0, land), g.math("SUBTRACT", 1.0, ice))
        fine = g.noise(p, 70.0, detail=2.0).outputs["Fac"]
        cluster = g.noise(g.vmath("ADD", p, (7.0, 2.0, -3.0)), 7.0, detail=3.0).outputs["Fac"]
        low = g.remap(h, 0.70, 0.56)
        lights = g.math("MULTIPLY", g.math("MULTIPLY", land, g.math("SUBTRACT", 1.0, ice)),
                        g.math("MULTIPLY", g.math("MULTIPLY", g.remap(fine, 0.50, 0.60), g.remap(cluster, 0.44, 0.56)), low))
        color = g.combine(ocean, h, lights)
    else:  # clouds
        tc = g.node("ShaderNodeTexCoord")
        q = g.vmath("MULTIPLY", tc.outputs["Object"], (1.0, 1.0, 2.0))
        swirl = g.noise(q, 1.6, detail=2.0)
        qq = g.vmath("ADD", q, g.vmath("SCALE", g.vmath("SUBTRACT", swirl.outputs["Color"], (0.5, 0.5, 0.5)), scale=0.9))
        c = g.noise(qq, 3.2, detail=10.0, rough=0.62, distortion=0.25).outputs["Fac"]
        wisp = g.noise(qq, 14.0, detail=6.0, rough=0.6).outputs["Fac"]
        d = g.remap(g.math("ADD", c, g.math("MULTIPLY", g.math("SUBTRACT", wisp, 0.5), 0.25)), 0.43, 0.68)
        color = g.combine(d, d, d)
    g.emit_out(color)
    img_node = g.node("ShaderNodeTexImage")
    return mat, img_node


def bake(obj, kind, filename, srgb):
    mat, img_node = build_planet_material(kind)
    img = bpy.data.images.new("bake_" + kind, TEX_W, TEX_H, alpha=False, float_buffer=False)
    img.colorspace_settings.name = "sRGB" if srgb else "Non-Color"
    img_node.image = img
    mat.node_tree.nodes.active = img_node
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.bake(type="EMIT", margin=4)
    path = os.path.join(TEX, filename)
    os.makedirs(TEX, exist_ok=True)
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    return img


def setup_cycles():
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 4
    scene.render.bake.margin = 4
    scene.view_settings.view_transform = "Standard"
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        for backend in ("OPTIX", "CUDA"):
            try:
                prefs.compute_device_type = backend
                prefs.get_devices()
                if any(d.type == backend for d in prefs.devices):
                    for d in prefs.devices:
                        d.use = True
                    scene.cycles.device = "GPU"
                    break
            except TypeError:
                continue
    except Exception as e:  # CPU fallback is fine, just slower
        print("cycles GPU setup failed:", e)


def preview_material(name, images):
    """A viewable material for the .blend: albedo + night lights, so the authored planet reads in the viewport."""
    mat = bpy.data.materials.new(name)
    g = G(mat)
    tex = g.node("ShaderNodeTexImage")
    tex.image = images["albedo"]
    bsdf = g.node("ShaderNodeBsdfPrincipled")
    g.link(tex.outputs[0], bsdf.inputs["Base Color"])
    out = g.node("ShaderNodeOutputMaterial")
    g.link(bsdf.outputs[0], out.inputs["Surface"])
    return mat


# ------------------------------------------------------------------ planet
planet = uv_sphere("planet", 1.0, 160, 80)
clouds = uv_sphere("planet_clouds", 1.008, 128, 64)
atmo = uv_sphere("planet_atmo", 1.06, 64, 32)
atmo["cmesh_smooth"] = True

images = {}
if not SKIP_BAKE:
    setup_cycles()
    images["albedo"] = bake(planet, "albedo", "planet_albedo.png", True)
    images["data"] = bake(planet, "data", "planet_data.png", False)
    images["clouds"] = bake(clouds, "clouds", "planet_clouds.png", False)
    planet.data.materials.clear()
    planet.data.materials.append(preview_material("planet_preview", images))

# ------------------------------------------------------------------ comet
rng = random.Random(7)


def set_colors(me, colors):
    attr = me.color_attributes.new("Col", "FLOAT_COLOR", "POINT")
    for i, c in enumerate(colors):
        attr.data[i].color = c
    me.color_attributes.active_color = attr


def comet_mesh():
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=0.5)
    for v in bm.verts:
        v.co *= 1.0 + rng.uniform(-0.12, 0.12)
        v.co.z *= 1.35
        v.co.z -= 0.1
    core_n = len(bm.verts)
    # tail fins trail upward (+Z = up in game): the comet falls head-first
    fins = 9
    for i in range(fins):
        a = i / fins * math.tau + rng.uniform(-0.2, 0.2)
        length = rng.uniform(2.4, 4.6) if i % 2 == 0 else rng.uniform(1.4, 2.6)
        spread = rng.uniform(0.18, 0.42)
        res = bmesh.ops.create_cone(bm, segments=4, radius1=rng.uniform(0.16, 0.3), radius2=0.0, depth=length,
                                    cap_ends=True)
        verts = res["verts"]
        tilt = Matrix.Rotation(spread, 4, Vector((math.cos(a + math.pi / 2), math.sin(a + math.pi / 2), 0)))
        spin = Matrix.Rotation(rng.uniform(0, math.tau), 4, "Z")
        off = Matrix.Translation(Vector((math.cos(a) * 0.22, math.sin(a) * 0.22, 0.25)))
        m = off @ tilt @ spin @ Matrix.Translation(Vector((0, 0, length / 2)))
        bmesh.ops.transform(bm, matrix=m, verts=verts)
    me = bpy.data.meshes.new("comet")
    bm.to_mesh(me)
    bm.free()
    colors = []
    for i, v in enumerate(me.vertices):
        if i < core_n:
            colors.append((1.0, 1.0, 1.0, 1.0))
        else:
            t = max(0.0, min(1.0, (v.co.z - 0.2) / 4.4))
            colors.append((0.55 + 0.1 * (1 - t), 0.95 - 0.65 * t, 1.0, 0.95 * (1 - t) ** 1.4))
    set_colors(me, colors)
    obj = link(bpy.data.objects.new("comet", me))
    obj["cmesh_smooth"] = False
    return obj


comet = comet_mesh()


def shard_mesh():
    bm = bmesh.new()
    pts = [Vector((0, 0, 1.6)), Vector((0, 0, -1.0))] + [Vector((math.cos(a), math.sin(a), 0)) for a in
                                                          (0, math.tau / 3, 2 * math.tau / 3)]
    vs = [bm.verts.new(p * 0.12 * (1 + rng.uniform(-0.15, 0.15))) for p in pts]
    for i in range(3):
        a, b = vs[2 + i], vs[2 + (i + 1) % 3]
        bm.faces.new((vs[0], a, b))
        bm.faces.new((vs[1], b, a))
    me = bpy.data.meshes.new("shard")
    bm.to_mesh(me)
    bm.free()
    set_colors(me, [(1, 1, 1, 1)] * len(me.vertices))
    obj = link(bpy.data.objects.new("shard", me))
    obj["cmesh_smooth"] = False
    return obj


shard = shard_mesh()

# ------------------------------------------------------------------ crater crown (diviner top = 1x1 block, origin at top centre)
SPIKES, SEG, SIDES = 18, 5, 3
spike_rand = [(rng.uniform(0.75, 1.3), rng.uniform(-0.15, 0.15), rng.uniform(0.8, 1.2)) for _ in range(SPIKES)]
FRAMES = {  # height, flare, droop, pool radius, pool height
    "rise": (0.22, 0.08, 0.0, 0.30, 0.02),
    "crown": (1.15, 0.55, 0.12, 0.56, 0.05),
    "cool": (0.55, 0.85, 0.55, 0.62, 0.03),
}


def crater_points(frame):
    height, flare, droop, pool_r, pool_h = FRAMES[frame]
    pts, cols = [], []
    for i, (hr, ar, wr) in enumerate(spike_rand):
        a = i / SPIKES * math.tau + ar
        radial = Vector((math.cos(a), math.sin(a), 0))
        side = Vector((-math.sin(a), math.cos(a), 0))
        for s in range(SEG + 1):
            t = s / SEG
            r = 0.36 + flare * t ** 1.5
            z = height * hr * t - droop * height * t * t
            w = (0.11 * (1 - t) + 0.008) * wr
            c = radial * r + Vector((0, 0, z))
            for k in range(SIDES):
                ang = k / SIDES * math.tau
                pts.append(c + side * math.cos(ang) * w + (radial * 0.6 + Vector((0, 0, 0.4))).normalized() * math.sin(ang) * w)
                cols.append((t, hr / 1.3, 0.0, 1.0))
    centre = len(pts)
    pts.append(Vector((0, 0, pool_h + 0.02)))
    cols.append((0.0, 1.0, 1.0, 1.0))
    ring = 24
    for k in range(ring):
        ang = k / ring * math.tau
        pts.append(Vector((math.cos(ang) * pool_r, math.sin(ang) * pool_r, pool_h)))
        cols.append((0.0, 0.5, 1.0, 1.0))
    return pts, cols, centre, ring


def crater_faces(centre, ring):
    faces = []
    for i in range(SPIKES):
        base = i * (SEG + 1) * SIDES
        for s in range(SEG):
            for k in range(SIDES):
                a = base + s * SIDES + k
                b = base + s * SIDES + (k + 1) % SIDES
                faces.append((a, b, b + SIDES, a + SIDES))
        tip = base + SEG * SIDES
        faces.append(tuple(tip + k for k in range(SIDES)))
    for k in range(ring):
        faces.append((centre, centre + 1 + k, centre + 1 + (k + 1) % ring))
    return faces


pts, cols, centre, ring = crater_points("rise")
me = bpy.data.meshes.new("crater")
me.from_pydata([tuple(p) for p in pts], [], crater_faces(centre, ring))
me.update()
for p in me.polygons:
    p.use_smooth = True
set_colors(me, cols)
crater = link(bpy.data.objects.new("crater", me))
crater.shape_key_add(name="Basis")
for frame in ("crown", "cool"):
    key = crater.shape_key_add(name=frame)
    for i, p in enumerate(crater_points(frame)[0]):
        key.data[i].co = p


def pose_crater(frame):
    def setter():
        for kb in crater.data.shape_keys.key_blocks[1:]:
            kb.value = 1.0 if kb.name == frame else 0.0
    return setter


# ------------------------------------------------------------------ export
results = [
    export(planet, os.path.join(MESHES, "planet.cmesh")),
    export(clouds, os.path.join(MESHES, "planet_clouds.cmesh")),
    export(atmo, os.path.join(MESHES, "planet_atmo.cmesh")),
    export(comet, os.path.join(MESHES, "comet.cmesh")),
    export(shard, os.path.join(MESHES, "shard.cmesh")),
    export_frames([pose_crater("rise"), pose_crater("crown"), pose_crater("cool")], crater,
                  os.path.join(MESHES, "crater.cmesh")),
]
pose_crater("crown")()
# lay the assets out for viewing in the saved file
comet.location = (3.5, 0, 0)
shard.location = (5, 0, 0)
crater.location = (6.5, 0, 0)
atmo.hide_set(True)
os.makedirs(os.path.dirname(BLEND), exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=BLEND, compress=True)
for r in results:
    print("CMESH", r)
