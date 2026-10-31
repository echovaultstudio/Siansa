"""Siansa's icon (issue #726): sound bars with their reflection, between Siansa's double half circles, on black.

One drawing, written out three ways so they can't drift apart:
- app/src/main/res/drawable/ic_launcher_foreground.xml and ic_launcher_monochrome.xml (the launcher and themed icons),
- docs/brand/siansa-icon.svg (black square, for the Play Store icon and the banner) and docs/brand/siansa-mark.svg
  (no background, for the website).

Run from the repository: python3 docs/brand/icon_gen.py app/src/main/res/drawable
Then docs/brand/icon512_gen.py and docs/brand/banner_gen.py make the Play Store icon and the README banner from the SVG.

Everything sits on the 108x108 adaptive-icon canvas (centre 54,54) inside the 66dp circle every launcher shows.
Bars are "Mx,ya r,r 0 0,1 w,0v h a r,r 0 0,1 -w,0z", reflections "Mx,yv-h a r,r 0 0,1 w,0v h a r,r 0 0,1 -w,0z" and the
half circles "Mx,yA r,r 0 0,s x,y" arcs: LauncherIconTest reads them, keep the formats.
"""
import os
import sys

C = 54.0

# The bars: left edge and height, all standing on one line; the middle one is amber.
W = 4.0
BOTTOM = 61.5
BARS = [(40, 9), (46, 16), (52, 24), (58, 16), (64, 9)]
MID = 52
FROST = ("#FFFFFF", "#9DB4C8")
AMBER = ("#FFE7C4", "#E8A55A")

# The reflection: just under the bars, mirrored, fading out over FADE as it gets further from them; softened a little
# where SVG can (Android's vector drawables can't blur).
GAP = 1.0
FADE = 14.0
SURFACE = BOTTOM + GAP
# How strong it is (alpha) and its colour, at the top, a third of the way down, and where it's gone.
REFLECT = {
    "frost": [(0.0, "#9DB4C8", 0.46), (0.35, "#B5C7D6", 0.207), (1.0, "#FFFFFF", 0.0)],
    "amber": [(0.0, "#E8A55A", 0.46), (0.35, "#F0BC80", 0.207), (1.0, "#FFE7C4", 0.0)],
}
SOFT = 0.32

# The double half circles: the same places as before (they fit the launcher circle), quieter so the bars lead.
# (radius, left x, top y, bottom y, width, opacity)
ARC_COLOUR = "#AFC2D3"
ARCS = [(24.42, 38.46, 36.98, 71.02, 2.6, 0.55), (32.56, 32.54, 31.06, 76.94, 2.2, 0.24)]
# How much of that opacity is left at their tips: they fade towards both ends.
ARC_ENDS = 0.55


def r2(v):
    return f"{round(v, 2):g}"


def bar_path(x, top, h):
    r = W / 2
    return f"M{r2(x)},{r2(top + r)}a{r2(r)},{r2(r)} 0 0,1 {r2(W)},0v{r2(h - W)}a{r2(r)},{r2(r)} 0 0,1 -{r2(W)},0z"


def reflection_path(x, h):
    # Drawn from its far end up, so it never reads as a bar.
    r = W / 2
    length = min(h, FADE)
    bottom = SURFACE + length
    return f"M{r2(x)},{r2(bottom - r)}v-{r2(length - W)}a{r2(r)},{r2(r)} 0 0,1 {r2(W)},0v{r2(length - W)}a{r2(r)},{r2(r)} 0 0,1 -{r2(W)},0z"


def arc_paths():
    for r, xl, top, bottom, width, alpha in ARCS:
        xr = 2 * C - xl
        yield f"M{r2(xl)},{r2(top)}A{r2(r)},{r2(r)} 0 0,0 {r2(xl)},{r2(bottom)}", top, bottom, width, alpha
        yield f"M{r2(xr)},{r2(top)}A{r2(r)},{r2(r)} 0 0,1 {r2(xr)},{r2(bottom)}", top, bottom, width, alpha


def argb(colour, alpha):
    return f"#{round(alpha * 255):02X}{colour[1:].upper()}"


# ---- Android vector drawables ----

HEAD = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">'''


def gradient(start_y, end_y, x, items, indent="            "):
    stops = "\n".join(f'{indent}    <item android:offset="{o:g}" android:color="{c}" />' for o, c in items)
    return (f'{indent}<gradient android:type="linear" android:startX="{r2(x)}" android:startY="{r2(start_y)}" '
            f'android:endX="{r2(x)}" android:endY="{r2(end_y)}">\n{stops}\n{indent}</gradient>')


def vector(mono):
    out = []
    for d, top, bottom, width, alpha in arc_paths():
        colour = "#FFFFFF" if mono else ARC_COLOUR
        items = [(0, argb(colour, alpha * ARC_ENDS)), (0.5, argb(colour, alpha)), (1, argb(colour, alpha * ARC_ENDS))]
        out.append(f'''    <path android:pathData="{d}" android:strokeWidth="{width:g}" android:strokeLineCap="round">
        <aapt:attr name="android:strokeColor">
{gradient(top, bottom, C, items)}
        </aapt:attr>
    </path>''')
    for x, h in BARS:
        kind = "amber" if x == MID else "frost"
        items = [(o, argb("#FFFFFF" if mono else c, a)) for o, c, a in REFLECT[kind]]
        out.append(f'''    <path android:pathData="{reflection_path(x, h)}">
        <aapt:attr name="android:fillColor">
{gradient(SURFACE, SURFACE + FADE, x + W / 2, items)}
        </aapt:attr>
    </path>''')
    for x, h in BARS:
        if mono:
            out.append(f'    <path android:pathData="{bar_path(x, BOTTOM - h, h)}" android:fillColor="#FFFFFFFF" />')
            continue
        top_c, bottom_c = AMBER if x == MID else FROST
        items = [(0, argb(top_c, 1)), (1, argb(bottom_c, 1))]
        out.append(f'''    <path android:pathData="{bar_path(x, BOTTOM - h, h)}">
        <aapt:attr name="android:fillColor">
{gradient(BOTTOM - h, BOTTOM, x + W / 2, items)}
        </aapt:attr>
    </path>''')
    note = ("Themed (monochrome) launcher icon: the same shapes as ic_launcher_foreground.xml in one colour, which Android\n"
            "     tints to match the wallpaper." if mono else
            "Launcher icon (issue #726): sound bars with their reflection between Siansa's double half circles. Made by\n"
            "     docs/brand/icon_gen.py; LauncherIconTest reads the paths, keep their formats.")
    return "\n".join(['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {note} -->", HEAD, *out, "</vector>", ""])


# ---- SVG ----

def svg(background, view):
    defs, body = [], []
    for i, (d, top, bottom, width, alpha) in enumerate(arc_paths()):
        defs.append(f'<linearGradient id="arc{i}" x1="0" y1="{r2(top)}" x2="0" y2="{r2(bottom)}" gradientUnits="userSpaceOnUse">'
                    f'<stop offset="0" stop-color="{ARC_COLOUR}" stop-opacity="{alpha * ARC_ENDS:.3f}"/>'
                    f'<stop offset="0.5" stop-color="{ARC_COLOUR}" stop-opacity="{alpha:.3f}"/>'
                    f'<stop offset="1" stop-color="{ARC_COLOUR}" stop-opacity="{alpha * ARC_ENDS:.3f}"/></linearGradient>')
        body.append(f'<path d="{d}" fill="none" stroke="url(#arc{i})" stroke-width="{width:g}" stroke-linecap="round"/>')
    for kind, stops in REFLECT.items():
        s = "".join(f'<stop offset="{o:g}" stop-color="{c}" stop-opacity="{a:g}"/>' for o, c, a in stops)
        defs.append(f'<linearGradient id="reflect-{kind}" x1="0" y1="{r2(SURFACE)}" x2="0" y2="{r2(SURFACE + FADE)}" gradientUnits="userSpaceOnUse">{s}</linearGradient>')
    for name, (top_c, bottom_c) in (("frost", FROST), ("amber", AMBER)):
        defs.append(f'<linearGradient id="bar-{name}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{top_c}"/><stop offset="1" stop-color="{bottom_c}"/></linearGradient>')
    defs.append(f'<filter id="soft" x="-50%" y="-20%" width="200%" height="140%"><feGaussianBlur stdDeviation="{SOFT}"/></filter>')
    reflections = "".join(f'<path d="{reflection_path(x, h)}" fill="url(#reflect-{"amber" if x == MID else "frost"})"/>' for x, h in BARS)
    body.append(f'<g filter="url(#soft)">{reflections}</g>')
    body += [f'<path d="{bar_path(x, BOTTOM - h, h)}" fill="url(#bar-{"amber" if x == MID else "frost"})"/>' for x, h in BARS]
    back = f'<rect x="0" y="0" width="108" height="108" fill="{background}"/>' if background else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" width="512" height="512">'
            f'<defs>{"".join(defs)}</defs>{back}{"".join(body)}</svg>\n')


if __name__ == "__main__":
    res = sys.argv[1]
    brand = os.path.dirname(os.path.abspath(__file__))
    open(os.path.join(res, "ic_launcher_foreground.xml"), "w").write(vector(mono=False))
    open(os.path.join(res, "ic_launcher_monochrome.xml"), "w").write(vector(mono=True))
    # The part launchers show (the middle 72 of 108), on black: the Play Store icon and the banner start from this.
    open(os.path.join(brand, "siansa-icon.svg"), "w").write(svg("#000000", "18 18 72 72"))
    # The same without a background, cropped closer, for the website on its own dark colours.
    open(os.path.join(brand, "siansa-mark.svg"), "w").write(svg(None, "24 24 60 60"))
