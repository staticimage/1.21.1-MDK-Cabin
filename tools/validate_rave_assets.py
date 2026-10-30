"""Validate the encounter's cross-file asset contracts (standard library plus Pillow)."""
import json
import math
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/cabinmod'
profiles = json.loads((assets / 'animations/record_dances.json').read_text())
assert set(profiles) == {name + '_disc' for name in ('mo2', 'brg', 'hyg', 'bla', 'dna', 'sos')}
for name, p in profiles.items():
    assert 60 < p['bpm'] < 200 and p['seconds'] > 0
    assert len(p['beats']) > 100 and all(a < b for a, b in zip(p['beats'], p['beats'][1:]))
    assert 0 <= p['beats'][0] < p['beats'][-1] <= p['seconds']
    assert all(math.isfinite(x) and 0 <= x <= 1 for x in p['energy'])
    assert abs(len(p['energy']) / 10 - p['seconds']) < .11
    assert (assets / f'sounds/{name}.ogg').is_file()
    assert (assets / f'textures/item/{name}.png').is_file()

texture = assets / 'textures/block/living_flesh.png'
meta = json.loads(Path(str(texture) + '.mcmeta').read_text())['animation']
im = Image.open(texture)
assert im.size == (64, 64 * 16) and meta['interpolate'] and meta['frames'] == list(range(16))
assert len({im.crop((0, i * 64, 64, (i + 1) * 64)).tobytes() for i in range(16)}) == 16
for block in ('rave_wall', 'rave_floor'):
    variants = json.loads((assets / f'blockstates/{block}.json').read_text())['variants']
    assert variants['corrupted=true']['model'] == 'cabinmod:block/living_flesh'

loot = json.loads((root / 'src/generated/resources/data/cabinmod/loot_table/entities/joash.json').read_text())
items = [entry['name'] for pool in loot['pools'] for entry in pool['entries']]
assert items == ['cabinmod:wardens_spare_key','cabinmod:jehoash_robe'], items
assert loot['pools'][0]['rolls']==1 and not loot['pools'][0].get('conditions')
assert loot['pools'][1]['rolls']==1 and loot['pools'][1]['bonus_rolls']==0
assert loot['pools'][1]['conditions']==[
    {'condition':'minecraft:killed_by_player'}, {'condition':'minecraft:random_chance','chance':.005}
], 'robe gets one fixed player-kill roll, with no Looting modifier'
assert not json.loads((root/'src/generated/resources/data/cabinmod/loot_table/entities/joash_illusion.json').read_text()).get('pools')
robe_icon = Image.open(assets / 'textures/item/jehoash_robe.png')
assert robe_icon.size == (128,128) and robe_icon.mode == 'RGBA'
assert robe_icon.getchannel('A').getextrema() == (0,255), 'robe icon needs visible cloth and a transparent background'
assert json.loads((root/'src/generated/resources/assets/cabinmod/models/item/jehoash_robe.json').read_text()) == {
    'parent':'minecraft:item/generated','textures':{'layer0':'cabinmod:item/jehoash_robe'}
}
prop = json.loads((assets / 'models/block/burning_portapotty.json').read_text())
assert prop['textures']['fire'] == 'minecraft:block/fire_0'
assert prop['render_type'] == 'minecraft:cutout'
assert all('uv' in f for element in prop['elements'] for f in element['faces'].values())
for name in ('entity/joash/joash_direct.png', 'entity/dj_booth/dj_booth_direct.png', 'entity/rave/supplied_wings.png', 'entity/rave/supplied_angel.png'):
    art = Image.open(assets / 'textures' / name)
    assert art.width >= 128 and art.height >= 128
    art.verify()
for name in ('joash_direct', 'jehoash_face', 'jehoash_brg_cloak', 'jehoash_phase_two', 'dj_booth_direct', 'dj_booth_item'):
    source = root / 'design/textures' / f'{name}.xcf'
    assert source.is_file() and source.open('rb').read(8) == b'gimp xcf', f'missing layered source: {name}'
assert Image.open(assets / 'textures/entity/dj_booth/dj_booth_direct.png').size == (1024, 1024)
assert Image.open(assets / 'textures/item/dj_booth.png').size == (128, 128)
cloak = Image.open(assets / 'textures/entity/joash/jehoash_brg_cloak.png').convert('RGBA')
uv = json.loads((root / 'design/textures/jehoash_brg_cloak_uv.json').read_text())
assert cloak.size == (uv['atlas_size'], uv['atlas_size']) == (2048, 2048)
assert set(uv['regions']) == {'robe','cape','mantle','hood','veil','sleeve_r','sleeve_l'}
assert cloak.getchannel('A').getextrema() == (255,255), 'solid cloth must not expose the face through alpha holes'
original_cover = Image.open(root / uv['source_cover']).convert('RGBA')
for rect in uv['prints'].values():
    x0,y0,x1,y1 = rect
    expected = original_cover.resize((x1-x0,y1-y0),Image.Resampling.LANCZOS)
    assert cloak.crop(rect).tobytes() == expected.tobytes(), 'cover print must preserve the supplied artwork'
rectangles = [region['rect'] for region in uv['regions'].values()]
for i,(x0,y0,x1,y1) in enumerate(rectangles):
    assert 0 <= x0 < x1 <= 2048 and 0 <= y0 < y1 <= 2048
    for a,b,c,d in rectangles[i+1:]:
        assert x1<=a or c<=x0 or y1<=b or d<=y0, 'garment UV islands overlap'

face = Image.open(assets / 'textures/entity/joash/jehoash_face.png')
assert face.size == (366, 378), 'JehoashFaceLayer crop UVs must match the supplied photo dimensions'
face.verify()
outfit = Image.open(assets/'textures/entity/joash/jehoash_phase_two.png').convert('RGBA')
model = json.loads((root/'design/textures/jehoash_phase_two_model.json').read_text())
assert outfit.size == (model['atlas_size'],model['atlas_size']) == (1024,1024)
assert outfit.getchannel('A').getextrema() == (255,255)
required = {'chest','hips','belt','head'} | {
    part+'_'+side for part in ('upper_arm','forearm','glove','thigh','shin','boot','boot_sole') for side in ('r','l')
}
assert {box['name'] for box in model['boxes']} == required, 'missing articulated blocky body components'
assert model['art_reference'] == 'design/references/cult-moodboard/Screenshot 2025-04-28 191821.png'
nets = []
for box in model['boxes']:
    assert box['bone'] in model['bones']
    x0,y0,x1,y1 = box['net']
    assert 0 <= x0 < x1 <= 128 and 0 <= y0 < y1 <= 128
    for a,b,c,d in nets:
        assert x1<=a or c<=x0 or y1<=b or d<=y0, 'body cube UV islands overlap'
    nets.append(box['net'])
    assert set(box['faces']) == {'top','bottom','west','east','north','south'}
    for a,b,c,d in box['faces'].values():
        assert x0 <= a < c <= x1 and y0 <= b < d <= y1
head = next(box for box in model['boxes'] if box['name']=='head')
assert head['pos']==[-4,-9,-4] and head['size']==[8,9,8], 'head must align with the supplied face layer'
feet = [box for box in model['boxes'] if box['name'].startswith('boot_sole')]
assert len(feet)==2 and all(model['bones'][box['bone']]['origin'][1]+box['pos'][1]+box['size'][1]==24 for box in feet)

import hashlib
assert hashlib.sha256((root/model['painted_source']).read_bytes()).hexdigest()==model['painted_source_sha256']
assert hashlib.sha256((root/model['head_source']).read_bytes()).hexdigest()==model['head_source_sha256']
assert hashlib.sha256((root/model['art_reference']).read_bytes()).hexdigest()==model['art_reference_sha256']
java=root/'src/main/java/net/austin/cabinmod/entity/client/generated/JehoashOutfitGeometry.java'
assert hashlib.sha256(java.read_text(encoding='utf-8').encode('utf-8')).hexdigest()==model['generated_java_sha256'], 'regenerate body geometry and atlas together'
for original, copied in [('Screenshot 2025-04-28 191821.png', 'supplied_wings.png'), ('Screenshot 2025-04-28 180651.png', 'supplied_angel.png')]:
    assert hashlib.sha256((root/'design/references/cult-moodboard'/original).read_bytes()).digest() == hashlib.sha256((assets/'textures/entity/rave'/copied).read_bytes()).digest()
truss = json.loads((assets / 'blockstates/stage_truss.json').read_text())['variants']
assert set(truss) == {'axis=x', 'axis=y', 'axis=z'}
print('PASS: six record beat maps, audio/textures, 16 unique flesh frames, wall/floor bindings, key + rare robe loot, bowl fire UVs, fitted outfit UVs/geometry, cult art and truss axes')
