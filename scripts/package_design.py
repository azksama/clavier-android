"""Package the exported pen.dev screens without modifying their pixels."""
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
design = ROOT / 'design'
screens = json.loads((design / 'screens.json').read_text(encoding='utf-8'))
assert len(screens) == 30
assert len(PdfReader(design / 'Clavier-Android-Maquettes.pdf').pages) == 30
for screen in screens:
    with Image.open(design / screen['file']) as image:
        assert image.size == (824, 1784), screen['file']
output = ROOT / 'dist'
output.mkdir(exist_ok=True)
with ZipFile(output / 'Clavier-Android-Maquettes.zip', 'w', ZIP_DEFLATED) as archive:
    for screen in screens:
        archive.write(design / screen['file'], f"ecrans/{screen['number']:02d}-{screen['id']}.png")
    for name in ['screens.json', 'README.md', 'Clavier-Android.pen', 'Clavier-Android-Maquettes.pdf']:
        archive.write(design / name, name)
print('Verified: 30 PNG screens at 824x1784, 30 PDF pages, editable pen source.')
