"""Add non-exported rest-shape references for Jehoash's runtime meshes to joash_v2.blend.

Run with Blender --background design/blender/joash_v2.blend --python this_file.
The ref_* objects are inspection/editing guides; ClothSheet and the phase-two model/UV manifest are
the runtime sources of truth and the existing rig/export collections are untouched.
"""
from pathlib import Path
import math
import json
import bpy

ROOT = Path(__file__).resolve().parents[2]
NAME = "MC_joash_runtime_reference"
old = bpy.data.collections.get(NAME)
if old:
    for obj in list(old.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    bpy.data.collections.remove(old)
collection = bpy.data.collections.new(NAME)
bpy.context.scene.collection.children.link(collection)


def material(name, path):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = nodes.get("Principled BSDF")
    tex = nodes.get("Runtime texture") or nodes.new("ShaderNodeTexImage")
    tex.name = "Runtime texture"
    tex.image = bpy.data.images.load(str(path), check_existing=True)
    tex.image.reload()
    tex.image.pack()
    mat.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = .9
    return mat


cloth = material("Jehoash BRG cloak (runtime)", ROOT / "src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_brg_cloak.png")
body = material("Jehoash painted wing outfit (runtime)", ROOT / "src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_phase_two.png")
UV = json.loads((ROOT / "design/textures/jehoash_brg_cloak_uv.json").read_text())["regions"]


def convert(p):
    # Minecraft model pixels (+Y down, feet at 24) to Blender (-Y forward, +Z up).
    return p[0], p[2], 24 - p[1]


def grid(name, cols, rows, shape, mat):
    points = [convert(shape(x/(cols-1), y/(rows-1), x, y))
              for y in range(rows) for x in range(cols)]
    faces = [(y*cols+x, (y+1)*cols+x, (y+1)*cols+x+1, y*cols+x+1)
             for y in range(rows-1) for x in range(cols-1)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(points, [], faces)
    mesh.update()
    uv = mesh.uv_layers.new(name="Runtime UV")
    for poly in mesh.polygons:
        for loop in poly.loop_indices:
            vertex = mesh.loops[loop].vertex_index
            x, y = vertex % cols, vertex // cols
            region=UV[{"hood_shell":"hood","hood_veil":"veil","sleeve_right":"sleeve_r","sleeve_left":"sleeve_l"}.get(name,name)]
            x0,y0,x1,y1=region["rect"]
            across=x/(cols-1)
            if region["mirror_u"]: across=1-across
            uv.data[loop].uv = ((x0+(x1-x0)*across)/2048, 1-(y0+(y1-y0)*y/(rows-1))/2048)
        poly.use_smooth = True
    obj = bpy.data.objects.new("ref_" + name, mesh)
    collection.objects.link(obj)
    mesh.materials.append(mat)
    return obj


tau = math.tau
grid("robe", 25, 17, lambda u,v,x,y: (
    math.sin(u*tau)*6.5*(1+.34*v*v),
    -16.5+38*v+v*v*(2*math.sin(u*tau*5)+1.1*math.sin(u*tau*11)),
    math.cos(u*tau)*5.1*(1+.34*v*v)), cloth)
grid("cape", 19, 18, lambda u,v,x,y: (
    (2*u-1)*(7.8+4.4*v),-17+39*v+v*v*(2.5*math.sin(x*2.17)+1.3*math.sin(x*4.3)),
    5.4+5.3*v*v-1.7*(2*u-1)**2), cloth)
grid("mantle", 21, 7, lambda u,v,x,y: (
    math.sin((u-.5)*4.6)*(7.2+2.8*v),-18+10*v+.75*math.sin(x*3)*v*v,
    math.cos((u-.5)*4.6)*(5.7+2.2*v)), cloth)
grid("hood_shell", 21, 12, lambda u,v,x,y: (
    math.sin(u*tau)*(.6+(5.5-.6)*min(v/.12,1) if v<.12 else 5.5+.4*v),
    -30+12*v+v*v*(.45*math.sin(u*tau*7)+.25*math.sin(u*tau*13)),
    math.cos(u*tau)*(.6+(5.5-.6)*min(v/.12,1) if v<.12 else 5.5+.4*v)), cloth)
grid("hood_veil", 13, 11, lambda u,v,x,y: (
    (2*u-1)*5,-27.4+9.4*v+.3*v*v*math.sin(x*2.2),
    -6.45-.65*v+.7*(2*u-1)**2), cloth)
for side, label in ((-1,"right"),(1,"left")):
    grid("sleeve_"+label,13,11,lambda u,v,x,y,side=side: (
        side*6.5+math.cos(u*tau)*(2.7+2.3*v),
        -17+25*v+v*v*.8*math.sin(u*tau*5),
        math.sin(u*tau)*(2.7+2.3*v)),cloth)


# Use the exact cube nets emitted to Java; no second hand-maintained body layout.
BODY=json.loads((ROOT/"design/textures/jehoash_phase_two_model.json").read_text())
FACE_VERTICES={"top":(5,4,0,1),"bottom":(2,3,7,6),"west":(0,4,7,3),
               "north":(1,0,3,2),"east":(5,1,2,6),"south":(4,5,6,7)}
for box in BODY["boxes"]:
    ox,oy,oz=BODY["bones"][box["bone"]]["origin"]
    x,y,z=box["pos"]; x+=ox; y+=oy; z+=oz
    w,h,d=box["size"]
    points=[convert((x+xx*w,y+yy*h,z+zz*d)) for xx,yy,zz in
            [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]]
    mesh=bpy.data.meshes.new("body_"+box["name"])
    mesh.from_pydata(points,[],list(FACE_VERTICES.values())); mesh.update()
    uv=mesh.uv_layers.new(name="Native cube UV")
    for poly,face in zip(mesh.polygons,FACE_VERTICES):
        u0,v0,u1,v1=box["faces"][face]
        if face=="bottom": v0,v1=v1,v0
        for loop,(u,v) in zip(poly.loop_indices,[(u1,v0),(u0,v0),(u0,v1),(u1,v1)]):
            uv.data[loop].uv=(u/BODY["uv_size"],1-v/BODY["uv_size"])
        poly.use_smooth=False
    obj=bpy.data.objects.new("ref_body_"+box["name"],mesh)
    obj["runtime_bone"]=box["bone"]
    collection.objects.link(obj);mesh.materials.append(body)

# The supplied face is a separate layer in game and in this inspection reference.
face=material("Jehoash supplied face (runtime)",ROOT/"src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_face.png")
mesh=bpy.data.meshes.new("head_face")
mesh.from_pydata([convert(p) for p in [(-4,-27,-4.02),(-4,-18,-4.02),(4,-18,-4.02),(4,-27,-4.02)]],[],[(0,1,2,3)])
mesh.update();uv=mesh.uv_layers.new(name="Supplied face crop")
for loop,(u,v) in zip(mesh.polygons[0].loop_indices,[(158/366,80/378),(158/366,242/378),(302/366,242/378),(302/366,80/378)]):
    uv.data[loop].uv=(u,1-v)
obj=bpy.data.objects.new("ref_head_face",mesh);collection.objects.link(obj);mesh.materials.append(face)

bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
print("Synced Jehoash runtime reference meshes:", len(collection.objects))
