"""The README banner (issues #360, #407, #726): the icon and the Siansa wordmark with an amber S, on black.

python3 docs/brand/banner_gen.py docs/brand/siansa-icon.svg docs/brand/siansa-banner.png
python3 docs/brand/banner_gen.py docs/brand/siansa-icon.svg docs/brand/siansa-feature.png 1024 500

The icon comes from docs/brand/siansa-icon.svg (icon_gen.py), so the banner always shows the launcher icon. With a
size, the same banner in that frame: 1024 × 500 is F-Droid's feature graphic, shown at the top of the app's page
(the F-Droid repo in the siansa-dev server kit uses it). The tagline is the website's headline.
"""
import base64
import sys
from playwright.sync_api import sync_playwright

# The banner is drawn 1600 wide; another size draws it in a frame of that shape, twice as large, then halves it.
out_w, out_h = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (1600, 560)
scale = 1 if (out_w, out_h) == (1600, 560) else 2
W, H = out_w * scale, out_h * scale
zoom = 1 if scale == 1 else 1.35  # the icon and wordmark fill the frame about as they do the banner (the frame
# is drawn W / zoom wide, so zoomed it is W)

icon = base64.b64encode(open(sys.argv[1]).read().encode()).decode()
html = f'''<html><body style="margin:0;background:#000">
<div id="b" style="width:{W / zoom:.2f}px;height:{H / zoom:.2f}px;display:flex;align-items:center;justify-content:center;gap:80px;zoom:{zoom};
  background:radial-gradient(ellipse 70% 90% at 28% 45%,#1B2430 0%,#0B0E12 55%,#000 100%);font-family:'Inter Display';">
  <img src="data:image/svg+xml;base64,{icon}" width="300" height="300"
    style="border-radius:66px;box-shadow:0 0 0 1px rgba(201,214,225,.10),0 34px 90px rgba(157,180,200,.22)">
  <div>
    <div style="font-size:190px;font-weight:700;letter-spacing:-6px;line-height:1;color:#EEF2F5">
      <span style="background:linear-gradient(180deg,#FFE7C4,#E8A55A);-webkit-background-clip:text;color:transparent">S</span>iansa</div>
    <div style="font-size:38px;font-weight:400;color:#9DB4C8;margin-top:22px;letter-spacing:.2px">Your music, mixed for the moment</div>
  </div>
</div></body></html>'''
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": W, "height": H})
    pg.set_content(html)
    pg.wait_for_timeout(300)
    pg.locator("#b").screenshot(path=sys.argv[2])
    b.close()
if scale != 1:
    from PIL import Image

    Image.open(sys.argv[2]).convert("RGB").resize((out_w, out_h), Image.LANCZOS).save(sys.argv[2], optimize=True)
