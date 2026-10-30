"""Render the inventory icon from the exported native player garment and existing BRG atlas.

Run the robe visual checks first, then Blender --background --factory-startup --python this_file.
No new painted texture or separate hand-maintained garment mesh is used.
"""
from pathlib import Path
import json
import bpy
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[2]
model=json.loads((ROOT/'build/robe-review/geometry/classic-stand.json').read_text())
for obj in list(bpy.data.objects):
    bpy.data.objects.remove(obj,do_unlink=True)
points=[(v[0],v[2],24-v[1]) for v in model['vertices']]
mesh=bpy.data.meshes.new('Native player robe')
mesh.from_pydata(points,[],model['quads'])
mesh.update()
uv=mesh.uv_layers.new(name='Runtime BRG UV')
for polygon in mesh.polygons:
    for loop in polygon.loop_indices:
        vertex=mesh.loops[loop].vertex_index
        uv.data[loop].uv=(model['vertices'][vertex][3],1-model['vertices'][vertex][4])
    polygon.use_smooth=True
obj=bpy.data.objects.new('Jehoash robe',mesh)
bpy.context.scene.collection.objects.link(obj)
mat=bpy.data.materials.new('Existing BRG cloak artwork')
mat.use_nodes=True
tex=mat.node_tree.nodes.new('ShaderNodeTexImage')
tex.image=bpy.data.images.load(str(ROOT/'src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_brg_cloak.png'))
tex.image.pack()
bsdf=mat.node_tree.nodes.get('Principled BSDF')
mat.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
bsdf.inputs['Roughness'].default_value=.95
mesh.materials.append(mat)
scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.samples=64
scene.render.resolution_x=128
scene.render.resolution_y=128
scene.render.resolution_percentage=100
scene.render.film_transparent=True
scene.render.image_settings.file_format='PNG'
scene.render.image_settings.color_mode='RGBA'
scene.world.color=(.6,.6,.6)
scene.view_settings.view_transform='Standard'
for name,location,power,size in [('key',(-25,-50,60),30000,45),('fill',(35,35,45),20000,45)]:
    data=bpy.data.lights.new(name,'AREA');data.energy=power;data.size=size
    light=bpy.data.objects.new(name,data);scene.collection.objects.link(light)
    light.location=location
    light.rotation_euler=(Vector((0,0,16))-light.location).to_track_quat('-Z','Y').to_euler()
data=bpy.data.cameras.new('Item icon');data.type='ORTHO';data.ortho_scale=39
camera=bpy.data.objects.new('Item icon',data);scene.collection.objects.link(camera)
camera.location=(25,-70,37)
camera.rotation_euler=(Vector((0,0,16))-camera.location).to_track_quat('-Z','Y').to_euler()
scene.camera=camera
scene.render.filepath=str(ROOT/'src/main/resources/assets/cabinmod/textures/item/jehoash_robe.png')
bpy.ops.render.render(write_still=True)
