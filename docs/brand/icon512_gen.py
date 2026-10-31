"""The Play Store icon (512 x 512, issue #726) from docs/brand/siansa-icon.svg, made by icon_gen.py.

python3 docs/brand/icon512_gen.py docs/brand/siansa-icon.svg docs/brand/siansa-icon-512.png
Google Play rounds the corners itself, so the picture is a full black square.
"""
import sys
from playwright.sync_api import sync_playwright

svg = open(sys.argv[1]).read()
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 512, "height": 512})
    pg.set_content(f'<body style="margin:0;background:#000">{svg}</body>')
    pg.locator("svg").screenshot(path=sys.argv[2])
    b.close()
