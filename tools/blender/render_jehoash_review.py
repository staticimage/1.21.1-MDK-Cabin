"""Render the shared runtime reference meshes without saving preview scene changes.

Blender --background design/blender/joash_v2.blend --python this_file
"""
from pathlib import Path
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/brg-review"
OUT.mkdir(parents=True, exist_ok=True)
refs = bpy.data.collections["MC_joash_runtime_reference"]
for obj in bpy.data.objects:
    obj.hide_render = True
for obj in refs.objects:
    obj.hide_render = False
scene = bpy.context.scene
scene.render.engine = "CYCLES"
scene.cycles.samples = 24
scene.render.resolution_x = 600
scene.render.resolution_y = 800
scene.render.resolution_percentage = 100
scene.world.color = (.6, .6, .6)
scene.view_settings.view_transform = "Standard"
for name, loc, power, size in [("key", (0, -70, 95), 70000, 75), ("fill", (40, 65, 65), 60000, 65)]:
    data = bpy.data.lights.new(name, "AREA")
    data.energy, data.shape, data.size = power, "DISK", size
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    obj.location = loc
    obj.rotation_euler = (Vector((0, 0, 25)) - obj.location).to_track_quat("-Z", "Y").to_euler()
data = bpy.data.cameras.new("Jehoash review")
data.type, data.ortho_scale = "ORTHO", 62
camera = bpy.data.objects.new("Jehoash review", data)
scene.collection.objects.link(camera)
scene.camera = camera
cloth_names = {"ref_robe", "ref_cape", "ref_mantle", "ref_hood_shell", "ref_hood_veil", "ref_sleeve_right", "ref_sleeve_left"}
for name, loc, cloth in [
    ("cloak-front", (0, -140, 27), True), ("cloak-back", (0, 140, 27), True),
    ("body-front", (0, -140, 27), False), ("body-back", (0, 140, 27), False),
    ("body-side", (140, 0, 27), False), ("body-three-quarter", (85, -140, 37), False),
]:
    for obj in refs.objects:
        obj.hide_render = obj.name in cloth_names and not cloth
    camera.location = loc
    camera.rotation_euler = (Vector((0, 0, 27)) - camera.location).to_track_quat("-Z", "Y").to_euler()
    scene.render.filepath = str(OUT / (name + ".png"))
    bpy.ops.render.render(write_still=True)
