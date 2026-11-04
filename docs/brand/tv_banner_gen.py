"""The TV app's launcher banner (issue #860): the icon and the Siansa wordmark with an amber S, on black, 16:9.

python3 docs/brand/tv_banner_gen.py docs/brand/siansa-icon.svg tv/src/main/res/drawable-xhdpi/tv_banner.png

Android TV shows the banner at 320 × 180 dp; this draws it at 640 × 360 (xhdpi). The same look as banner_gen.py,
without the tagline, which is too small to read on a TV's home screen.
"""
import base64
import sys
from playwright.sync_api import sync_playwright

W, H = 1280, 720  # drawn twice as large, then halved
icon = base64.b64encode(open(sys.argv[1]).read().encode()).decode()
html = f'''<html><body style="margin:0;background:#000">
<div id="b" style="width:{W}px;height:{H}px;display:flex;align-items:center;justify-content:center;gap:64px;
  background:radial-gradient(ellipse 70% 90% at 30% 45%,#1B2430 0%,#0B0E12 55%,#000 100%);font-family:'Inter Display',sans-serif;">
  <img src="data:image/svg+xml;base64,{icon}" width="300" height="300"
    style="border-radius:66px;box-shadow:0 0 0 1px rgba(201,214,225,.10),0 34px 90px rgba(157,180,200,.22)">
  <div style="font-size:210px;font-weight:700;letter-spacing:-6px;line-height:1;color:#EEF2F5">
    <span style="background:linear-gradient(180deg,#FFE7C4,#E8A55A);-webkit-background-clip:text;color:transparent">S</span>iansa</div>
</div></body></html>'''
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": W, "height": H})
    pg.set_content(html)
    pg.wait_for_timeout(300)
    pg.locator("#b").screenshot(path=sys.argv[2])
    b.close()

from PIL import Image  # noqa: E402

Image.open(sys.argv[2]).resize((W // 2, H // 2), Image.LANCZOS).save(sys.argv[2], optimize=True)
