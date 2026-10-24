"""The README banner: the echo-bars icon and the Siansa wordmark with an amber S (issues #360, #407)."""
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
icon = (f'<svg width="300" height="300" viewBox="18 18 72 72"><defs><radialGradient id="g"><stop offset="0" stop-color="#7C5CFF" stop-opacity=".45"/>'
        f'<stop offset="1" stop-color="#7C5CFF" stop-opacity="0"/></radialGradient><clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath></defs>'
        f'<g clip-path="url(#c)"><rect width="108" height="108" fill="#110D1C"/>{"".join(parts)}</g></svg>')
html = f'''<html><body style="margin:0">
<div id="b" style="width:1600px;height:560px;display:flex;align-items:center;justify-content:center;gap:72px;
  background:radial-gradient(ellipse at 30% 40%,#2B2055 0%,#15102A 55%,#0B0814 100%);font-family:'Inter Display';">
  <div style="border-radius:50%;box-shadow:0 30px 80px rgba(124,92,255,.35)">{icon}</div>
  <div>
    <div style="font-size:190px;font-weight:700;letter-spacing:-6px;line-height:1;color:#EDE7F6"><span style="color:#FFB547">S</span>iansa</div>
    <div style="font-size:38px;font-weight:400;color:#B9AEDB;margin-top:22px;letter-spacing:.2px">Your own music server, mixed for the moment</div>
  </div>
</div></body></html>'''
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width": 1600, "height": 560}); pg.set_content(html)
    pg.wait_for_timeout(300); pg.locator("#b").screenshot(path=sys.argv[2]); b.close()
