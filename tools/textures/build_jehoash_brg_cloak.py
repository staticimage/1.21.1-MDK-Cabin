"""Assemble the BRG garment atlas; cover prints use the unchanged supplied artwork.

Run from the repository root with Python 3/Pillow. The build/ ORA contains separately editable
fabric and cover-print layers and can be opened in GIMP and saved as the matching XCF.
Pixel rectangles here match ClothSheet's UV regions and the Blender inspection mesh.
"""
from pathlib import Path
from io import BytesIO
import json
import xml.etree.ElementTree as ET
import zipfile
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
DESIGN = ROOT / "design/textures"
SIZE = 2048
REGIONS = {
    "robe": {"rect": [0, 0, 1536, 1536], "mirror_u": True, "model_size": [40, 38]},
    "cape": {"rect": [1536, 0, 2048, 1024], "mirror_u": True, "model_size": [20, 39]},
    "mantle": {"rect": [1536, 1024, 2048, 1280], "mirror_u": False, "model_size": [40, 10]},
    "hood": {"rect": [1536, 1280, 2048, 1792], "mirror_u": True, "model_size": [36, 12]},
    "veil": {"rect": [1536, 1792, 2048, 2048], "mirror_u": False, "model_size": [10, 10]},
    "sleeve_r": {"rect": [0, 1536, 768, 2048], "mirror_u": False, "model_size": [25, 25]},
    "sleeve_l": {"rect": [768, 1536, 1536, 2048], "mirror_u": False, "model_size": [25, 25]},
}
PRINTS = {"Front BRG cover": [492, 461, 1044, 983], "Back BRG cover": [1577, 287, 2007, 728]}
SOURCE = "design/references/cult-moodboard/Screenshot 2025-04-24 200027.png"


def png_bytes(image):
    stream = BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def main():
    ornament = Image.open(DESIGN / "jehoash_brg_botanicals.png").convert("RGBA")
    cover = Image.open(ROOT / SOURCE).convert("RGBA")
    fabric = Image.new("RGBA", (SIZE, SIZE))
    for index, (name, region) in enumerate(REGIONS.items()):
        x0, y0, x1, y1 = region["rect"]
        mw, mh = region["model_size"]
        # Sample at consistent physical density before fitting each authored UV island.
        crop_w, crop_h = min(ornament.width, round(mw * 25)), min(ornament.height, round(mh * 25))
        ox = (index * 137) % max(1, ornament.width - crop_w + 1)
        oy = (index * 181) % max(1, ornament.height - crop_h + 1)
        patch = ornament.crop((ox, oy, ox + crop_w, oy + crop_h))
        patch = patch.resize((x1 - x0, y1 - y0), Image.Resampling.LANCZOS)
        # The cylindrical pieces meet at their back seam. Mirrored edge samples
        # keep the print continuous there; the large back cape covers the join.
        if name in ("robe", "hood"):
            half = patch.crop((0, 0, patch.width // 2, patch.height))
            patch.paste(ImageOps.mirror(half), (patch.width // 2, 0))
        fabric.paste(patch, (x0, y0))
    layers = [("Botanical fabric / garment UV islands", fabric)]
    for name, (x0, y0, x1, y1) in PRINTS.items():
        layer = Image.new("RGBA", (SIZE, SIZE))
        layer.paste(cover.resize((x1-x0, y1-y0), Image.Resampling.LANCZOS), (x0, y0))
        layers.append((name + " / supplied original", layer))
    merged = Image.new("RGBA", (SIZE, SIZE))
    for _, layer in layers:
        merged.alpha_composite(layer)
    target = ROOT / "src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_brg_cloak.png"
    merged.save(target)
    image_xml = ET.Element("image", {"w": str(SIZE), "h": str(SIZE), "name": "Jehoash BRG cloak"})
    stack = ET.SubElement(image_xml, "stack")
    ora=ROOT / "build/jehoash_brg_cloak.ora"
    ora.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(ora, "w") as archive:
        archive.writestr("mimetype", "image/openraster", compress_type=zipfile.ZIP_STORED)
        for index, (name, layer) in reversed(list(enumerate(layers))):
            source = f"data/layer{index}.png"
            archive.writestr(source, png_bytes(layer))
            ET.SubElement(stack, "layer", {"name": name, "src": source, "opacity": "1.0",
                                          "visibility": "visible", "composite-op": "svg:src-over", "x": "0", "y": "0"})
        archive.writestr("stack.xml", ET.tostring(image_xml))
        archive.writestr("mergedimage.png", png_bytes(merged))
    (DESIGN / "jehoash_brg_cloak_uv.json").write_text(json.dumps({
        "atlas_size": SIZE, "source_cover": SOURCE, "regions": REGIONS, "prints": PRINTS,
        "orientation": "Prints face outward; mirrored robe/cape U compensates for mesh winding."
    }, indent=2) + "\n")
    print("Built", target.relative_to(ROOT), "and layered ORA source")


if __name__ == "__main__":
    main()
