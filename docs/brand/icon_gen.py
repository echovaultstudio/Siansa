"""Echo bars launcher icon (from the Ekkora board, issue #360) as Android vector drawables, scaled into the 66dp safe circle."""
import sys
S = 0.74
f = lambda v: round(54 + (v - 54) * S, 2)
n = lambda v: round(v * S, 2)
BARS = [(36.25, 55, 12, "VH"), (43.75, 46, 21, "VH"), (51.25, 38, 29, "A"), (58.75, 46, 21, "VH"), (66.25, 55, 12, "VH")]
W = 5.5
# (x1,y1,r,sweep,x2,y2,stroke,alpha)
RINGS = [(33, 31, 33, 0, 33, 77, 4.5, .75), (75, 31, 33, 1, 75, 77, 4.5, .75),
         (25, 23, 44, 0, 25, 85, 4, .35), (83, 23, 44, 1, 83, 85, 4, .35)]
GRAD = {"VH": ("#EDE6FF", "#A891FF"), "A": ("#FFEBC2", "#FFB04A")}

def bar_path(x, y, h):
    w, r = n(W), n(W) / 2
    return f"M{f(x)},{round(f(y) + r, 2)}a{round(r,2)},{round(r,2)} 0 0,1 {w},0v{round(n(h) - 2 * r, 2)}a{round(r,2)},{round(r,2)} 0 0,1 -{w},0z"

def ring_path(x1, y1, r, sw, x2, y2):
    return f"M{f(x1)},{f(y1)}A{n(r)},{n(r)} 0 0,{sw} {f(x2)},{f(y2)}"

def rings(mono):
    out = []
    for x1, y1, r, sw, x2, y2, st, a in RINGS:
        col = "#FFFFFFFF" if mono else "#FFA891FF"
        out.append(f'    <path android:pathData="{ring_path(x1, y1, r, sw, x2, y2)}" android:strokeColor="{col}" '
                   f'android:strokeAlpha="{a}" android:strokeWidth="{n(st)}" android:strokeLineCap="round" />')
    return "\n".join(out)

HEAD = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">'''

fg = ['<?xml version="1.0" encoding="utf-8"?>',
      '<!-- Launcher icon (issue #407): the sound bars with echo rings around them, on the 108x108 canvas (centre 54,54).',
      '     Everything stays inside the 66dp circle every launcher shows. Bars are "Mx,ya r,r 0 0,1 w,0v h a r,r 0 0,1 -w,0z",',
      '     rings are "Mx,yA r,r 0 0,s x,y" arcs: LauncherIconTest reads them, keep the format. Made by docs/brand/icon_gen.py. -->',
      HEAD,
      '''    <path android:pathData="M54,54m-30,0a30,30 0 1,1 60,0a30,30 0 1,1 -60,0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="54" android:centerY="54" android:gradientRadius="30">
                <item android:offset="0" android:color="#737C5CFF" />
                <item android:offset="0.6" android:color="#1F7C5CFF" />
                <item android:offset="1" android:color="#007C5CFF" />
            </gradient>
        </aapt:attr>
    </path>''', rings(False)]
for x, y, h, c in BARS:
    a, b = GRAD[c]
    cx = round(f(x) + n(W) / 2, 2)
    fg.append(f'''    <path android:pathData="{bar_path(x, y, h)}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="{cx}" android:startY="{f(y)}" android:endX="{cx}" android:endY="{f(y + h)}"
                android:startColor="{a}" android:endColor="{b}" />
        </aapt:attr>
    </path>''')
fg.append("</vector>\n")
mono = ['<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Themed (monochrome) launcher icon: the same bars and rings as ic_launcher_foreground.xml in one colour,',
        '     which Android tints to match the wallpaper. -->',
        HEAD.replace('\n    xmlns:aapt="http://schemas.android.com/aapt"', ''), rings(True)]
for x, y, h, c in BARS:
    mono.append(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{bar_path(x, y, h)}" />')
mono.append("</vector>\n")
res = sys.argv[1]
open(res + "/ic_launcher_foreground.xml", "w").write("\n".join(fg))
open(res + "/ic_launcher_monochrome.xml", "w").write("\n".join(mono))
