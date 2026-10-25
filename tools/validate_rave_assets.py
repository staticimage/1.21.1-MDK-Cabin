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
assert items == ['cabinmod:wardens_spare_key'], items
prop = json.loads((assets / 'models/block/burning_portapotty.json').read_text())
assert prop['textures']['fire'] == 'minecraft:block/fire_0'
assert prop['render_type'] == 'minecraft:cutout'
assert all('uv' in f for element in prop['elements'] for f in element['faces'].values())
for name in ('entity/joash/joash_direct.png', 'entity/dj_booth/dj_booth_direct.png', 'entity/rave/supplied_wings.png', 'entity/rave/supplied_angel.png'):
    art = Image.open(assets / 'textures' / name)
    assert art.width >= 128 and art.height >= 128
    art.verify()
import hashlib
for original, copied in [('Screenshot 2025-04-28 191821.png', 'supplied_wings.png'), ('Screenshot 2025-04-28 180651.png', 'supplied_angel.png')]:
    assert hashlib.sha256((root/'design/references/cult-moodboard'/original).read_bytes()).digest() == hashlib.sha256((assets/'textures/entity/rave'/copied).read_bytes()).digest()
truss = json.loads((assets / 'blockstates/stage_truss.json').read_text())['variants']
assert set(truss) == {'axis=x', 'axis=y', 'axis=z'}
print('PASS: six record beat maps, audio/textures, 16 unique flesh frames, wall/floor bindings, key-only loot, bowl fire UVs, cult art and truss axes')
