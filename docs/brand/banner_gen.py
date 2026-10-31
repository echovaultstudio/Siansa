"""The README banner (issues #360, #407, #726): the icon and the Siansa wordmark with an amber S, on black.

python3 docs/brand/banner_gen.py docs/brand/siansa-icon.svg docs/brand/siansa-banner.png
The icon comes from docs/brand/siansa-icon.svg (icon_gen.py), so the banner always shows the launcher icon.
"""
import base64
import sys
from playwright.sync_api import sync_playwright

icon = base64.b64encode(open(sys.argv[1]).read().encode()).decode()
html = f'''<html><body style="margin:0;background:#000">
<div id="b" style="width:1600px;height:560px;display:flex;align-items:center;justify-content:center;gap:80px;
  background:radial-gradient(ellipse 70% 90% at 28% 45%,#1B2430 0%,#0B0E12 55%,#000 100%);font-family:'Inter Display';">
  <img src="data:image/svg+xml;base64,{icon}" width="300" height="300"
    style="border-radius:66px;box-shadow:0 0 0 1px rgba(201,214,225,.10),0 34px 90px rgba(157,180,200,.22)">
  <div>
    <div style="font-size:190px;font-weight:700;letter-spacing:-6px;line-height:1;color:#EEF2F5">
      <span style="background:linear-gradient(180deg,#FFE7C4,#E8A55A);-webkit-background-clip:text;color:transparent">S</span>iansa</div>
    <div style="font-size:38px;font-weight:400;color:#9DB4C8;margin-top:22px;letter-spacing:.2px">Your own music server, mixed for the moment</div>
  </div>
</div></body></html>'''
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 1600, "height": 560})
    pg.set_content(html)
    pg.wait_for_timeout(300)
    pg.locator("#b").screenshot(path=sys.argv[2])
    b.close()
