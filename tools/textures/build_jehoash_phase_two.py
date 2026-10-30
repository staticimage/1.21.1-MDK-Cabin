"""Pack Jehoash's painted wing outfit into native cube UVs and an editable source.

Python 3 + Pillow. Geometry, atlas rectangles and Blender references share this manifest;
the generated Java helper is committed so ordinary Gradle builds need no art tools.
Open build/jehoash_phase_two.ora in GIMP and save design/textures/jehoash_phase_two.xcf.
"""
from pathlib import Path
from io import BytesIO
from collections import defaultdict
import json
import hashlib
import math
import xml.etree.ElementTree as ET
import zipfile
from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[2]
DESIGN = ROOT / "design/textures"
SCALE, UV_SIZE = 8, 128
SIZE = SCALE * UV_SIZE
ART = DESIGN / "jehoash_phase_two_wing_panels.png"
HEAD_ART = DESIGN / "jehoash_phase_two_head_panels.png"
REFERENCE = "design/references/cult-moodboard/Screenshot 2025-04-28 191821.png"
BONES = {
    "revealed_core": {"path": ["torso", "revealed_core"], "origin": [0, 0, 0]},
    "head": {"path": ["torso", "head"], "origin": [0, -18, 0]},
}
for side, sign in (("r", -1), ("l", 1)):
    BONES["arm_"+side] = {"path": ["torso", "arm_"+side], "origin": [sign*7, -16.5, 0]}
    BONES["fore_"+side] = {"path": ["torso", "arm_"+side, "forearm"], "origin": [sign*7, -3.5, 0]}
    BONES["hand_"+side] = {"path": ["torso", "arm_"+side, "forearm", "hand"], "origin": [sign*7, 9.5, 0]}
    BONES["leg_"+side] = {"path": ["leg_"+side], "origin": [sign*2.5, 0, 0]}
    BONES["shin_"+side] = {"path": ["leg_"+side, "shin"], "origin": [sign*2.5, 12, 0]}


def geometry():
    boxes = []

    def box(name, bone, pos, size, material):
        boxes.append(dict(name=name, bone=bone, pos=list(pos), size=list(size),
                          material=material))

    # Broad, uninterrupted Minecraft-style boxes. Detail comes from the painted
    # surfaces rather than layered chest plates, knee guards or tiny trim cubes.
    box("chest", "revealed_core", (-6, -18, -3.5), (12, 18, 7), "body")
    box("hips", "revealed_core", (-5.5, 0, -3.25), (11, 3, 6.5), "fabric")
    box("belt", "revealed_core", (-6.125, -1, -3.625), (12.25, 1.5, 7.25), "leather")
    for side in ("r", "l"):
        box("upper_arm_"+side, "arm_"+side, (-2.5, -.75, -2.5), (5, 14, 5), "fabric")
        box("forearm_"+side, "fore_"+side, (-2.375, -.5, -2.25), (4.75, 13, 4.5), "fabric")
        box("glove_"+side, "hand_"+side, (-2.375, -1.25, -2.25), (4.75, 3.25, 4.5), "leather")
        box("thigh_"+side, "leg_"+side, (-2.5, -.25, -2.5), (5, 12.75, 5), "fabric")
        box("shin_"+side, "shin_"+side, (-2.375, -.5, -2.375), (4.75, 10, 4.75), "fabric")
        box("boot_"+side, "shin_"+side, (-2.625, 8.25, -4.25), (5.25, 3, 6.75), "leather")
        box("boot_sole_"+side, "shin_"+side, (-2.625, 11.25, -4.5), (5.25, .75, 7), "leather")
    box("head", "head", (-4, -9, -4), (8, 9, 8), "portrait")
    return boxes


def faces(w, h, d, u, v):
    # Exact ModelPart.Cube net, including its flipped V on the lower face.
    return {
        "top": [u+d, v, u+d+w, v+d],
        "bottom": [u+d+w, v, u+d+2*w, v+d],
        "west": [u, v+d, u+d, v+d+h],
        "north": [u+d, v+d, u+d+w, v+d+h],
        "east": [u+d+w, v+d, u+2*d+w, v+d+h],
        "south": [u+2*d+w, v+d, u+2*d+2*w, v+d+h],
    }


def allocate(boxes):
    x, y, row_height = 1, 1, 0
    # The wide/high nets pack first; every box gets its own island and gutter.
    for box in sorted(boxes, key=lambda b: b["size"][1]+b["size"][2], reverse=True):
        w,h,d = box["size"]
        nw,nh = math.ceil(2*w+2*d), math.ceil(h+d)
        if x+nw+1>UV_SIZE:
            x,y,row_height = 1,y+row_height+1,0
        if y+nh+1>UV_SIZE:
            raise ValueError("Outfit cube nets do not fit the body atlas")
        box["uv"] = [x,y]
        box["net"] = [x,y,x+nw,y+nh]
        box["faces"] = faces(w,h,d,x,y)
        x += nw+1
        row_height = max(row_height,nh)


def painted_surfaces(box, panels, head_panels, portrait):
    """Sample finished artwork, preserving its aspect and continuous side seams.

    This is UV assembly only: no noise, stitching, borders or small stamped motifs
    are synthesized here. Both the materials and their detail are painted sources.
    """
    w,h,d = box["size"]
    side = panels["leather" if box["material"]=="leather" else "fabric"]
    perimeter = round((2*w+2*d)*SCALE)
    strip = ImageOps.fit(side,(perimeter,round(h*SCALE)),Image.Resampling.LANCZOS)
    offset = 0
    result = {}
    for face,span in (("west",d),("north",w),("east",d),("south",w)):
        end = offset+round(span*SCALE)
        result[face] = strip.crop((offset,0,end,strip.height))
        offset = end
    result["top"] = ImageOps.fit(side,(round(w*SCALE),round(d*SCALE)),Image.Resampling.LANCZOS)
    result["bottom"] = result["top"].copy()
    if box["material"]=="body":
        for face,panel in (("north","front"),("south","back")):
            result[face] = ImageOps.fit(panels[panel],(round(w*SCALE),round(h*SCALE)),Image.Resampling.LANCZOS)
    elif box["material"]=="portrait":
        # Keep the front photograph unchanged. Purpose-made matching profile
        # surfaces avoid stretching a second frontal face across the head sides.
        crops = {
            "north": (158,80,302,242), "bottom": (190,208,274,242),
        }
        for face,rect in crops.items():
            result[face] = portrait.crop(rect).resize(result[face].size,Image.Resampling.LANCZOS)
        for face,panel in head_panels.items():
            result[face] = panel.resize(result[face].size,Image.Resampling.LANCZOS)
    return result


def png_bytes(image):
    stream = BytesIO(); image.save(stream,format="PNG"); return stream.getvalue()


def build_atlas(boxes):
    source = Image.open(ART).convert("RGBA")
    assert source.width==source.height and source.width%2==0, "four equal square painted panels required"
    half = source.width//2
    panels = {name: source.crop(rect) for name,rect in {
        "front":(0,0,half,half), "back":(half,0,half*2,half),
        "fabric":(0,half,half,half*2), "leather":(half,half,half*2,half*2),
    }.items()}
    portrait = Image.open(ROOT/"src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_face.png").convert("RGBA")
    head_art = Image.open(HEAD_ART).convert("RGBA")
    assert head_art.width==head_art.height and head_art.width%2==0
    half_head = head_art.width//2
    head_panels = {face:head_art.crop(rect) for face,rect in {
        "west":(half_head,0,half_head*2,half_head), "east":(0,0,half_head,half_head),
        "south":(0,half_head,half_head,half_head*2), "top":(half_head,half_head,half_head*2,half_head*2),
    }.items()}
    background = Image.new("RGBA",(SIZE,SIZE),(31,27,33,255))
    outfit = Image.new("RGBA",(SIZE,SIZE))
    head = Image.new("RGBA",(SIZE,SIZE))
    for box in boxes:
        surfaces = painted_surfaces(box,panels,head_panels,portrait)
        layer = head if box["material"]=="portrait" else outfit
        for face,rect in box["faces"].items():
            x0,y0,x1,y1 = [round(v*SCALE) for v in rect]
            patch = surfaces[face]
            assert patch.size==(x1-x0,y1-y0), (box["name"],face,patch.size)
            # Native cube net stores the lower surface with V reversed.
            if face=="bottom": patch = ImageOps.flip(patch)
            layer.paste(patch,(x0,y0))
    merged = Image.alpha_composite(Image.alpha_composite(background,outfit),head)
    target = ROOT/"src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_phase_two.png"
    merged.save(target)
    layers = [("Original painted panels (unmodified, hidden)",source,False),
              ("Atlas background",background,True),
              ("Wing artwork fitted to body UVs",outfit,True),
              ("Supplied portrait and matching head surfaces",head,True)]
    doc = ET.Element("image",{"w":str(SIZE),"h":str(SIZE),"name":"Jehoash phase-two wing outfit"})
    stack = ET.SubElement(doc,"stack")
    ora = ROOT/"build/jehoash_phase_two.ora"; ora.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(ora,"w") as archive:
        archive.writestr("mimetype","image/openraster",compress_type=zipfile.ZIP_STORED)
        for index,(name,layer,visible) in reversed(list(enumerate(layers))):
            path = f"data/layer{index}.png"; archive.writestr(path,png_bytes(layer))
            ET.SubElement(stack,"layer",{"name":name,"src":path,"opacity":"1.0",
                                        "visibility":"visible" if visible else "hidden",
                                        "composite-op":"svg:src-over","x":"0","y":"0"})
        archive.writestr("stack.xml",ET.tostring(doc)); archive.writestr("mergedimage.png",png_bytes(merged))
    guide = merged.copy(); draw = ImageDraw.Draw(guide)
    for box in boxes:
        x0,y0,x1,y1 = [v*SCALE for v in box["net"]]
        draw.rectangle((x0,y0,x1,y1),outline=(255,210,70),width=1)
        draw.text((x0+2,y0+2),box["name"],fill=(255,235,180),stroke_width=1,stroke_fill=(0,0,0))
    guide.save(ROOT/"build/jehoash_phase_two_uv_guide.png")


def java_float(value):
    return f"{float(value):g}f"


def build_java(boxes):
    grouped = defaultdict(list)
    for box in boxes: grouped[box["bone"]].append(box)
    lines = ["// GENERATED by tools/textures/build_jehoash_phase_two.py; edit its geometry definition.",
             "package net.austin.cabinmod.entity.client.generated;", "",
             "import net.minecraft.client.model.geom.PartPose;",
             "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
             "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
             "public final class JehoashOutfitGeometry {", "    private JehoashOutfitGeometry() {}",
             "    public static void add(PartDefinition root) {"]
    for bone,items in grouped.items():
        parent = "root"+"".join(f'.getChild("{part}")' for part in BONES[bone]["path"])
        lines.append(f'        {parent}.addOrReplaceChild("outfit", CubeListBuilder.create()')
        for box in items:
            u,v = box["uv"]; dims = ",".join(java_float(x) for x in box["pos"]+box["size"])
            lines.append(f'                .texOffs({u},{v}).addBox({dims}) // {box["name"]}')
        lines.append("                , PartPose.ZERO);")
    lines += ["    }", "}", ""]
    target = ROOT/"src/main/java/net/austin/cabinmod/entity/client/generated/JehoashOutfitGeometry.java"
    target.write_text("\n".join(lines),encoding="utf-8",newline="\n")


def main():
    boxes = geometry(); allocate(boxes)
    build_atlas(boxes); build_java(boxes)
    java=ROOT/"src/main/java/net/austin/cabinmod/entity/client/generated/JehoashOutfitGeometry.java"
    (DESIGN/"jehoash_phase_two_model.json").write_text(json.dumps({
        "atlas_size":SIZE,"uv_size":UV_SIZE,"texture":"jehoash_phase_two.png","bones":BONES,"boxes":boxes,
        "art_reference":REFERENCE,"painted_source":ART.relative_to(ROOT).as_posix(),
        "head_source":HEAD_ART.relative_to(ROOT).as_posix(),
        "head_source_sha256":hashlib.sha256(HEAD_ART.read_bytes()).hexdigest(),
        "painted_source_sha256":hashlib.sha256(ART.read_bytes()).hexdigest(),
        "art_reference_sha256":hashlib.sha256((ROOT/REFERENCE).read_bytes()).hexdigest(),
        "generated_java_sha256":hashlib.sha256(java.read_text(encoding="utf-8").encode("utf-8")).hexdigest()
    },indent=2)+"\n")
    print("Built phase-two atlas, layered ORA, UV manifest and native Java geometry:",len(boxes),"cuboids")


if __name__=="__main__":
    main()
