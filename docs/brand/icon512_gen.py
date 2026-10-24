import re, sys
from playwright.sync_api import sync_playwright
xml = open(sys.argv[1]).read()
parts = []
for i, m in enumerate(re.finditer(r'<path android:pathData="([^"]+)"(.*?)(/>|</path>)', xml, re.S)):
    d, rest = m.group(1), m.group(2)
    if 'strokeColor' in rest:
        a = re.search(r'strokeAlpha="([^"]+)"', rest).group(1); w = re.search(r'strokeWidth="([^"]+)"', rest).group(1)
        parts.append(f'<path d="{d}" fill="none" stroke="#A891FF" stroke-opacity="{a}" stroke-width="{w}" stroke-linecap="round"/>')
    elif 'radial' in rest:
        parts.append('<circle cx="54" cy="54" r="30" fill="url(#g)"/>')
    else:
        c = re.search(r'startColor="([^"]+)"', rest).group(1); e = re.search(r'endColor="([^"]+)"', rest).group(1)
        parts.append(f'<linearGradient id="l{i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{c}"/><stop offset="1" stop-color="{e}"/></linearGradient><path d="{d}" fill="url(#l{i})"/>')
svg = (f'<svg width="512" height="512" viewBox="18 18 72 72"><defs><radialGradient id="g"><stop offset="0" stop-color="#7C5CFF" stop-opacity=".45"/>'
       f'<stop offset="1" stop-color="#7C5CFF" stop-opacity="0"/></radialGradient></defs><rect x="0" y="0" width="108" height="108" fill="#110D1C"/>{"".join(parts)}</svg>')
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width": 512, "height": 512}); pg.set_content(f'<body style="margin:0">{svg}</body>')
    pg.locator("svg").screenshot(path=sys.argv[2]); b.close()
