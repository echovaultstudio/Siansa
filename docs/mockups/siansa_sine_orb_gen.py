"""Siansa · Listening orb, recreated (issue #360): the AI orb made more musical, four ways.
Each icon is one SVG symbol on a 200x200 canvas, so it renders identically at every size."""
import colorsys
import math
import sys


def lerp_hex(stops, t):
    """Colour at t (0..1) along a list of hex stops."""
    t = max(0.0, min(1.0, t)) * (len(stops) - 1)
    i = min(int(t), len(stops) - 2)
    f = t - i
    a = [int(stops[i][k:k + 2], 16) for k in (1, 3, 5)]
    b = [int(stops[i + 1][k:k + 2], 16) for k in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * f):02x}" for x, y in zip(a, b))


SPECTRUM = ["#ff3da0", "#b44cff", "#6b3cff", "#16c6ff", "#2fe3c0", "#ffb347", "#ff3da0"]


def orb(cx, cy, r, uid):
    """The glassy AI orb: colour blobs swirling inside a sphere, with shading and a highlight."""
    s = r / 48
    def p(x, y):
        return cx + (x - 100) * s, cy + (y - 100) * s
    blobs = [((80, 82), 30, "#ffb347"), ((126, 124), 30, "#2fe3ff"), ((128, 76), 22, "#ff3da0"), ((74, 128), 20, "#7b3cff")]
    b = "".join(f'<circle cx="{p(*c)[0]:.1f}" cy="{p(*c)[1]:.1f}" r="{rr * s:.1f}" fill="{col}" filter="url(#blur12)" opacity=".95"/>'
                for c, rr, col in blobs)
    hx, hy = p(84, 74)
    return f"""
<circle cx="{cx}" cy="{cy + 6 * s:.1f}" r="{r}" fill="#5a2bd6" opacity=".35" filter="url(#blur12)"/>
<clipPath id="clip-{uid}"><circle cx="{cx}" cy="{cy}" r="{r}"/></clipPath>
<g clip-path="url(#clip-{uid})">
  <circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#orbBase)"/>{b}
  <circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#orbShade)"/>
</g>
<ellipse cx="{hx:.1f}" cy="{hy:.1f}" rx="{18 * s:.1f}" ry="{10 * s:.1f}" transform="rotate(-30 {hx:.1f} {hy:.1f})" fill="#fff" opacity=".72" filter="url(#blur3)"/>
<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="#fff" stroke-opacity=".4" stroke-width="1"/>"""


# ---------- 1 · Spectrum orb: a ring of equaliser bars around the orb ----------
def spectrum_bars(n=64, r0=58, mono=False):
    out = []
    for i in range(n):
        th = 2 * math.pi * i / n - math.pi / 2
        u = i / n
        # A smooth, music-like spectrum: two humps and some flutter, the same on both sides.
        m = abs(math.sin(math.pi * u))
        level = 0.35 + 0.65 * (0.55 * abs(math.sin(2 * math.pi * u)) + 0.3 * m + 0.15 * abs(math.sin(9 * math.pi * u)))
        length = 6 + 22 * level
        x0, y0 = 100 + r0 * math.cos(th), 100 + r0 * math.sin(th)
        x1, y1 = 100 + (r0 + length) * math.cos(th), 100 + (r0 + length) * math.sin(th)
        col = "currentColor" if mono else lerp_hex(SPECTRUM, u)
        w = 4.6 if mono else 3.2
        out.append(f'<line x1="{x0:.1f}" y1="{y0:.1f}" x2="{x1:.1f}" y2="{y1:.1f}" stroke="{col}" stroke-width="{w}" stroke-linecap="round"/>')
    return "".join(out)


SPECTRUM_ICON = f"""
<rect width="200" height="200" fill="url(#lightBg)"/>
<g filter="url(#glow4)" opacity=".55">{spectrum_bars()}</g>
{spectrum_bars()}
{orb(100, 100, 44, "spec")}
"""
SPECTRUM_MONO = f"""{spectrum_bars(40, 60, True)}<circle cx="100" cy="100" r="40" fill="currentColor"/>"""

# ---------- 2 · Orb note: the orb is the head of a quaver, with a sound-wave flag ----------
FLAG = "M121 44 C138 52 150 62 148 82 C146 96 138 104 130 110"
NOTE_ICON = f"""
<rect width="200" height="200" fill="url(#lightBg)"/>
<path d="M121 140 V44" stroke="#2b1f66" stroke-width="7" stroke-linecap="round"/>
<path d="{FLAG}" fill="none" stroke="url(#ribbon)" stroke-width="7" stroke-linecap="round"/>
<path d="M121 58 C134 64 142 72 141 86" fill="none" stroke="url(#ribbon)" stroke-width="4" stroke-linecap="round" opacity=".7"/>
<path d="M121 72 C129 76 134 82 134 90" fill="none" stroke="url(#ribbon)" stroke-width="3" stroke-linecap="round" opacity=".45"/>
{orb(92, 138, 34, "note")}
"""
NOTE_MONO = f"""<path d="M121 140 V44" stroke="currentColor" stroke-width="9" stroke-linecap="round"/>
<path d="{FLAG}" fill="none" stroke="currentColor" stroke-width="9" stroke-linecap="round"/>
<circle cx="92" cy="138" r="34" fill="currentColor"/>"""

# ---------- 3 · Vinyl orb: the orb is the glowing centre of a record, with a tonearm ----------
def groove(r, amp, k, ph):
    pts = []
    for i in range(241):
        t = 2 * math.pi * i / 240
        rr = r + amp * math.sin(k * t + ph)
        pts.append(f"{100 + rr * math.cos(t):.2f},{100 + rr * math.sin(t):.2f}")
    return "M" + " L".join(pts) + "Z"


grooves = "".join(f'<path d="{groove(r, 0.9 if i % 3 == 0 else 0.0, 24, i)}" fill="none" stroke="#2a2836" stroke-width=".8"/>'
                  for i, r in enumerate([40 + j * 2.1 for j in range(22)]))


def wedge(a0, a1, r=86):
    a0, a1 = math.radians(a0), math.radians(a1)
    return (f"M100 100 L{100 + r * math.cos(a0):.1f} {100 + r * math.sin(a0):.1f} "
            f"A{r} {r} 0 0 1 {100 + r * math.cos(a1):.1f} {100 + r * math.sin(a1):.1f}Z")


VINYL_ICON = f"""
<rect width="200" height="200" fill="url(#darkBg)"/>
<circle cx="100" cy="104" r="88" fill="#000" opacity=".6" filter="url(#soft)"/>
<circle cx="100" cy="100" r="86" fill="#0c0b12"/>
{grooves}
<path d="{wedge(295, 330)}" fill="url(#irisA)" opacity=".45"/>
<path d="{wedge(115, 150)}" fill="url(#irisB)" opacity=".38"/>
<circle cx="100" cy="100" r="86" fill="none" stroke="#2f2c3c" stroke-width="1.2"/>
{orb(100, 100, 32, "vinyl")}
<g>
  <circle cx="170" cy="30" r="9" fill="url(#metal)"/>
  <path d="M170 30 L160 104 L140 128" fill="none" stroke="url(#metal)" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round"/>
  <rect x="131" y="122" width="16" height="10" rx="2.5" transform="rotate(38 139 127)" fill="#d9d6e6"/>
</g>
"""
VINYL_MONO = f"""<circle cx="100" cy="100" r="76" fill="none" stroke="currentColor" stroke-width="7"/>
<circle cx="100" cy="100" r="56" fill="none" stroke="currentColor" stroke-width="3" opacity=".6"/>
<circle cx="100" cy="100" r="28" fill="currentColor"/>
<path d="M168 32 L158 104 L140 126" fill="none" stroke="currentColor" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>"""

# ---------- 4 · Sine orb: one clean sine wave running through the orb ----------
def sine(x0, x1, cy, amp, cycles, steps=200):
    pts = []
    for i in range(steps + 1):
        x = x0 + (x1 - x0) * i / steps
        y = cy - amp * math.sin(2 * math.pi * cycles * (x - x0) / (x1 - x0))
        pts.append(f"{x:.2f},{y:.2f}")
    return "M" + " L".join(pts)


SINE = sine(18, 182, 100, 22, 2)
SINE_ICON = f"""
<rect width="200" height="200" fill="url(#blackBg)"/>
<circle cx="100" cy="100" r="70" fill="#6b3cff" opacity=".22" filter="url(#blur12)"/>
<path d="{SINE}" fill="none" stroke="url(#sineGrad)" stroke-width="10" stroke-linecap="round" opacity=".6" filter="url(#glow4)"/>
{orb(100, 100, 46, "sine")}
<path d="{SINE}" fill="none" stroke="url(#sineGrad)" stroke-width="5" stroke-linecap="round"/>
<g clip-path="url(#clip-sine)"><path d="{SINE}" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".95"/></g>
"""
SINE_MONO = f"""<circle cx="100" cy="100" r="44" fill="none" stroke="currentColor" stroke-width="7"/>
<path d="{SINE}" fill="none" stroke="currentColor" stroke-width="8" stroke-linecap="round"/>"""

DEFS = """
<filter id="soft" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="5"/></filter>
<filter id="blur3" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3"/></filter>
<filter id="blur12" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="12"/></filter>
<filter id="glow4" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="4"/></filter>
<linearGradient id="lightBg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fdfbff"/><stop offset="1" stop-color="#e3e6fb"/></linearGradient>
<radialGradient id="blackBg" cx=".5" cy=".42" r=".75"><stop offset="0" stop-color="#16141f"/><stop offset="1" stop-color="#000000"/></radialGradient>
<radialGradient id="darkBg" cx=".5" cy=".35" r=".8"><stop offset="0" stop-color="#25233a"/><stop offset="1" stop-color="#07060c"/></radialGradient>
<radialGradient id="orbBase" cx=".5" cy=".5" r=".6"><stop offset="0" stop-color="#ff6fb5"/><stop offset="1" stop-color="#5a2bd6"/></radialGradient>
<radialGradient id="orbShade" cx=".38" cy=".32" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#1a0a4a" stop-opacity=".45"/></radialGradient>
<linearGradient id="ribbon" x1="120" y1="44" x2="150" y2="110" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#ff3da0"/><stop offset=".55" stop-color="#6b3cff"/><stop offset="1" stop-color="#16c6ff"/></linearGradient>
<linearGradient id="sineGrad" x1="18" y1="0" x2="182" y2="0" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#ff3da0"/><stop offset=".5" stop-color="#6b3cff"/><stop offset="1" stop-color="#16c6ff"/></linearGradient>
<linearGradient id="irisA" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7df9ff"/><stop offset=".5" stop-color="#ff7ae6"/><stop offset="1" stop-color="#7df9ff" stop-opacity="0"/></linearGradient>
<linearGradient id="irisB" x1="1" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#ffd36e"/><stop offset=".5" stop-color="#ff7ae6"/><stop offset="1" stop-color="#ffd36e" stop-opacity="0"/></linearGradient>
<linearGradient id="metal" x1="140" y1="20" x2="175" y2="130" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#ffffff"/><stop offset=".5" stop-color="#b9b5c9"/><stop offset="1" stop-color="#77738a"/></linearGradient>
"""

CONCEPTS = [
    ("spectrum", "Spectrum orb", SPECTRUM_ICON, SPECTRUM_MONO, ["AI", "Frequency", "Equaliser"],
     "The orb inside a ring of equaliser bars in every colour, like a live frequency visualiser: the AI listening while the music plays.", False),
    ("note", "Orb note", NOTE_ICON, NOTE_MONO, ["AI", "Music note"],
     "The orb becomes the head of a quaver, with a flag made of sound-wave ribbons. Unmistakably a music note, and unmistakably AI.", False),
    ("vinyl", "Vinyl orb", VINYL_ICON, VINYL_MONO, ["AI", "Record", "Tonearm"],
     "The orb glows at the centre of a spinning record, with fine grooves, an iridescent sheen and the tonearm resting on the groove. Your records, brought to life by AI.", True),
    ("sine", "Sine orb", SINE_ICON, SINE_MONO, ["AI", "Sine wave"],
     "One clean sine wave, the purest sound there is, running straight through the orb and lighting up inside it. Simple and calm.", False),
]

WORD_STYLE = ("font-family:Lora;font-style:italic;font-weight:600;letter-spacing:-.5px;"
              "background:linear-gradient(90deg,#ff3da0,#6b3cff 55%,#16c6ff);-webkit-background-clip:text;background-clip:text;color:transparent")


def icon(key, size, radius):
    return (f'<div class="ic" style="width:{size}px;height:{size}px;border-radius:{radius}">'
            f'<svg viewBox="0 0 200 200" width="{size}" height="{size}"><use href="#{key}"/></svg></div>')


def mono(key, size):
    return (f'<div class="ic mono" style="width:{size}px;height:{size}px;border-radius:50%">'
            f'<svg viewBox="0 0 200 200" width="{size}" height="{size}"><g transform="translate(22 22) scale(.78)">'
            f'<use href="#{key}-mono"/></g></svg></div>')


def card(c):
    key, title, _, _, tags, why, dark = c
    bg = "radial-gradient(120% 90% at 20% 10%,#2a2840,#0b0a12 70%)" if dark else "radial-gradient(120% 90% at 80% 10%,#ffffff,#e6e8f8 70%)"
    sub = "#A9A3BD" if dark else "#4b4670"
    chips = "".join(f'<span class="chip" style="border-color:{"#34304a" if dark else "#cfd2ee"};color:{sub}">{t}</span>' for t in tags)
    return f"""
<div class="card" style="background:{bg}">
  <div class="big">{icon(key, 300, '24%')}</div>
  <div class="ctitle" style="color:{sub}">{title}</div>
  <div class="word" style="{WORD_STYLE}">siansa</div>
  <div class="chips">{chips}</div>
  <p style="color:{sub}">{why}</p>
  <div class="sizes">{icon(key, 64, '24%')}{icon(key, 52, '50%')}{icon(key, 40, '50%')}{mono(key, 52)}</div>
</div>"""


def home():
    generic = ["#3b3f4a", "#2f4f6b", "#5a3b3b", "#3d5a40", "#4a3d63", "#5b4b2f", "#2f5a5a", "#4f4f4f"]
    labels = ["Phone", "Messages", "Camera", "Maps", "Mail", "Photos", "Clock", "Files"]
    order = [None, None, "sine", None, None, None, None, None, None, None, None, None]
    cells, gi = [], 0
    for k in order:
        if k:
            cells.append(f'<div class="cell">{icon(k, 58, "24%")}<span>Siansa</span></div>')
        else:
            cells.append(f'<div class="cell"><div class="ic" style="width:58px;height:58px;border-radius:24%;'
                         f'background:linear-gradient(160deg,{generic[gi % 8]},#1d1f26)"></div><span>{labels[gi % 8]}</span></div>')
            gi += 1
    return "".join(cells)


SINE_FREE = SINE_ICON.replace('<rect width="200" height="200" fill="url(#blackBg)"/>', '')
syms = f'<symbol id="sine-free" viewBox="0 0 200 200">{SINE_FREE}</symbol>' + "".join(f'<symbol id="{c[0]}" viewBox="0 0 200 200">{c[2]}</symbol><symbol id="{c[0]}-mono" viewBox="0 0 200 200">{c[3]}</symbol>'
               for c in CONCEPTS)

html = f"""<!doctype html><html><head><meta charset="utf-8"><style>
*{{box-sizing:border-box;margin:0;padding:0}}
body{{background:#000;font-family:Inter,sans-serif}}
#shot{{width:1500px;padding:60px 56px 52px;background:radial-gradient(70% 45% at 50% 0%,#1a1630,#000 70%)}}
.eyebrow{{font:600 12px Inter;letter-spacing:2.4px;color:#7d78a0;text-transform:uppercase}}
h1{{font:700 44px 'Inter Display';letter-spacing:-1.4px;color:#F4F1FF;margin:8px 0 10px}}
header p{{font-size:16px;line-height:1.5;color:#A9A3BD;max-width:1000px;margin-bottom:38px}}
.ic{{overflow:hidden;flex:none;box-shadow:0 18px 40px rgba(0,0,0,.6),0 0 0 1px rgba(255,255,255,.08) inset}}
.ic svg{{display:block}}
.ic.mono{{background:#2b2e45;color:#cfd8ff}}
.hero{{display:flex;gap:56px;align-items:center;border-radius:36px;padding:48px 56px;background:#000;
  border:1px solid rgba(255,255,255,.07);box-shadow:0 40px 80px rgba(0,0,0,.6)}}
.hero .word{{font-size:128px;line-height:1.05;padding:0 10px 10px}}
.tag{{font-size:17px;color:#A9A3BD;margin-top:10px;padding-left:8px}}
.row{{display:grid;grid-template-columns:1.05fr 1fr;gap:26px;margin-top:26px}}
.panel{{border-radius:30px;padding:30px;border:1px solid rgba(255,255,255,.07);background:#08070d}}
.panel h3{{font:600 12px Inter;letter-spacing:2px;text-transform:uppercase;color:#7d78a0;margin-bottom:20px}}
.sizes{{display:flex;align-items:flex-end;gap:22px;height:calc(100% - 32px);padding-bottom:8px}}
.sizes figure{{display:flex;flex-direction:column;align-items:center;gap:10px}}
.sizes figcaption{{font-size:12px;color:#A9A3BD}}
.locks{{display:flex;flex-direction:column;gap:16px}}
.lock{{border-radius:22px;padding:22px 26px;display:flex;align-items:center;gap:18px}}
.lock .word{{font-size:54px;line-height:1.1;padding-bottom:4px}}
.homes{{display:flex;gap:30px;align-items:flex-start;margin-top:26px}}
.phone{{width:330px;height:640px;border-radius:46px;border:10px solid #1c1c24;overflow:hidden;position:relative;flex:none}}
.dark{{background:radial-gradient(60% 40% at 30% 20%,#3b2a6b,transparent),radial-gradient(60% 50% at 80% 80%,#0d5a6b,transparent),#0b0b14}}
.light{{background:radial-gradient(60% 40% at 30% 20%,#ffd3e8,transparent),radial-gradient(60% 50% at 80% 80%,#cfe8ff,transparent),#f3f0fa}}
.splash{{background:#000;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:26px}}
.splash .word{{font-size:52px;line-height:1.1;padding-bottom:4px}}
.slogan{{position:absolute;bottom:44px;width:100%;text-align:center;font-size:13px;color:#8f89a8;letter-spacing:.3px}}
.clock{{text-align:center;margin-top:46px;font:300 64px 'Inter Display';color:#fff;letter-spacing:-2px}}
.light .clock{{color:#1b1640}}
.date{{text-align:center;font:500 14px Inter;color:rgba(255,255,255,.8);margin-bottom:34px}}
.light .date{{color:#4b4670}}
.apps{{display:grid;grid-template-columns:repeat(4,1fr);gap:22px 8px;padding:0 16px}}
.cell{{display:flex;flex-direction:column;align-items:center;gap:7px}}
.cell span{{font-size:11px;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,.5)}}
.light .cell span{{color:#1b1640;text-shadow:none}}
.cap{{text-align:center;font-size:13px;color:#A9A3BD;margin-top:12px}}
</style></head><body>
<svg width="0" height="0" style="position:absolute"><defs>{DEFS}</defs>{syms}</svg>
<div id="shot">
<header>
  <div class="eyebrow">Issue #360 · Siansa · chosen direction</div>
  <h1>Sine orb, on black</h1>
  <p>The glassy AI orb with one clean sine wave running through it, lighting up inside the orb, now on a pure black background. The wordmark is the italic serif from the Vinyl orb, in the orb's own gradient.</p>
</header>
<div class="hero">{icon('sine', 340, '24%')}
  <div><div class="word" style="{WORD_STYLE}">siansa</div><div class="tag">Your music, in harmony.</div></div>
</div>
<div class="row">
  <div class="panel"><h3>Every size</h3><div class="sizes">
    <figure>{icon('sine', 120, '24%')}<figcaption>App icon</figcaption></figure>
    <figure>{icon('sine', 84, '50%')}<figcaption>Round</figcaption></figure>
    <figure>{icon('sine', 56, '24%')}<figcaption>Small</figcaption></figure>
    <figure>{icon('sine', 36, '50%')}<figcaption>Tiny</figcaption></figure>
    <figure>{mono('sine', 84)}<figcaption>Themed</figcaption></figure>
  </div></div>
  <div class="panel"><h3>Logo</h3><div class="locks">
    <div class="lock" style="background:#000;border:1px solid rgba(255,255,255,.08)">{icon('sine', 84, '24%')}<div class="word" style="{WORD_STYLE}">siansa</div></div>
    <div class="lock" style="background:#fbfaff">{icon('sine', 84, '24%')}<div class="word" style="{WORD_STYLE}">siansa</div></div>
  </div></div>
</div>
<div class="homes">
  <div><div class="phone splash"><svg viewBox="0 0 200 200" width="200" height="200" style="overflow:visible"><use href="#sine-free"/></svg><div class="word" style="{WORD_STYLE}">siansa</div><div class="slogan">Your music, in harmony.</div></div><div class="cap">Splash screen</div></div>
  <div><div class="phone dark"><div class="clock">22:51</div><div class="date">Friday 23 October</div><div class="apps">{home()}</div></div><div class="cap">Dark wallpaper</div></div>
  <div><div class="phone light"><div class="clock">22:51</div><div class="date">Friday 23 October</div><div class="apps">{home()}</div></div><div class="cap">Light wallpaper</div></div>
</div>
</div></body></html>"""

open(sys.argv[1], "w").write(html)
