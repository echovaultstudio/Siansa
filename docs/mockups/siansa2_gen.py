"""Siansa logo concepts, round two (issue #360): rich, modern marks that fuse music, instruments, records,
frequency and AI. Each icon is one SVG symbol on a 200x200 canvas, so it renders identically at every size."""
import math
import sys


def wave_ring(r, amp, k, phase=0.0, cx=100, cy=100, steps=360):
    pts = []
    for i in range(steps + 1):
        t = 2 * math.pi * i / steps
        rr = r + amp * math.sin(k * t + phase)
        pts.append(f"{cx + rr * math.cos(t):.2f},{cy + rr * math.sin(t):.2f}")
    return "M" + " L".join(pts) + "Z"


def bez(p0, p1, p2, p3, t):
    u = 1 - t
    return (u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1])


# ---------- A · Groove: a vinyl record whose light glare is an S ----------
grooves = "".join(f'<circle cx="100" cy="100" r="{r:.1f}" fill="none" stroke="#23232e" stroke-width=".7"/>'
                  for r in [32 + i * 2.05 for i in range(24)])


def wedge(a0, a1, r=80):
    a0, a1 = math.radians(a0), math.radians(a1)
    return (f"M100 100 L{100 + r * math.cos(a0):.1f} {100 + r * math.sin(a0):.1f} "
            f"A{r} {r} 0 0 1 {100 + r * math.cos(a1):.1f} {100 + r * math.sin(a1):.1f}Z")


S_PATH = "M131 58 C116 42 76 44 74 70 C72 96 128 98 128 126 C128 154 86 162 68 144"
GROOVE = f"""
<rect width="200" height="200" fill="url(#gBg)"/>
<circle cx="100" cy="104" r="86" fill="#000" opacity=".55" filter="url(#soft)"/>
<circle cx="100" cy="100" r="84" fill="#0b0b10"/>
{grooves}
<path d="{wedge(292, 330)}" fill="url(#irisA)" opacity=".55"/>
<path d="{wedge(112, 150)}" fill="url(#irisB)" opacity=".45"/>
<circle cx="100" cy="100" r="84" fill="none" stroke="#2c2c38" stroke-width="1.2"/>
<circle cx="100" cy="100" r="25" fill="url(#gLabel)"/>
<circle cx="100" cy="100" r="25" fill="none" stroke="#fff" stroke-opacity=".18"/>
<circle cx="100" cy="100" r="3.2" fill="#07070b"/>
<path d="{S_PATH}" fill="none" stroke="url(#sGlow)" stroke-width="13" stroke-linecap="round" opacity=".55" filter="url(#glow8)"/>
<path d="{S_PATH}" fill="none" stroke="url(#sLine)" stroke-width="6" stroke-linecap="round"/>
<path d="{S_PATH}" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" opacity=".9"/>
"""
GROOVE_MONO = f"""<circle cx="100" cy="100" r="72" fill="none" stroke="currentColor" stroke-width="7"/>
<circle cx="100" cy="100" r="9" fill="currentColor"/>
<path d="{S_PATH}" fill="none" stroke="currentColor" stroke-width="11" stroke-linecap="round"/>"""

# ---------- B · Neural note: a quaver whose flag is a constellation of AI nodes ----------
nodes = [(98, 50), (128, 58), (146, 84), (126, 104), (150, 118)]
links = [(0, 1), (1, 2), (2, 3), (1, 3), (2, 4), (3, 4), (0, 3)]
NOTE = f"""
<rect width="200" height="200" fill="url(#nBg)"/>
<circle cx="56" cy="150" r="58" fill="#2f5bff" opacity=".55" filter="url(#blur24)"/>
<circle cx="150" cy="56" r="52" fill="#b44cff" opacity=".5" filter="url(#blur24)"/>
<circle cx="150" cy="150" r="30" fill="#18e0ff" opacity=".25" filter="url(#blur24)"/>
{''.join(f'<line x1="{nodes[a][0]}" y1="{nodes[a][1]}" x2="{nodes[b][0]}" y2="{nodes[b][1]}" stroke="#bfe6ff" stroke-width="2.2" stroke-opacity=".55"/>' for a, b in links)}
<path d="M98 133 V50" stroke="#e4f2ff" stroke-width="7" stroke-linecap="round"/><path d="M98 133 V50" stroke="#9fe1ff" stroke-width="12" stroke-linecap="round" opacity=".25" filter="url(#glow6)"/>
{''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#9fe1ff" filter="url(#glow6)" opacity=".9"/><circle cx="{x}" cy="{y}" r="{r * .55:.1f}" fill="#fff"/>' for (x, y), r in zip(nodes[1:], [7, 6.5, 6, 5.5]))}
<ellipse cx="78" cy="144" rx="24" ry="17" transform="rotate(-22 78 144)" fill="#000" opacity=".35" filter="url(#soft)"/>
<ellipse cx="77" cy="140" rx="24" ry="17" transform="rotate(-22 77 140)" fill="url(#head)"/>
<ellipse cx="69" cy="133" rx="9" ry="4.5" transform="rotate(-22 69 133)" fill="#fff" opacity=".75"/>
"""
NOTE_MONO = f"""{''.join(f'<line x1="{nodes[a][0]}" y1="{nodes[a][1]}" x2="{nodes[b][0]}" y2="{nodes[b][1]}" stroke="currentColor" stroke-width="4"/>' for a, b in links)}
<path d="M98 133 V50" stroke="currentColor" stroke-width="9" stroke-linecap="round"/>
{''.join(f'<circle cx="{x}" cy="{y}" r="9" fill="currentColor"/>' for x, y in nodes[1:])}
<ellipse cx="77" cy="140" rx="25" ry="18" transform="rotate(-22 77 140)" fill="currentColor"/>"""

# ---------- C · Spectrum harp: an Irish harp whose strings are a frequency spectrum ----------
P0, P1, P2, P3 = (52, 58), (84, 26), (114, 92), (154, 62)
neck = "M{} {} C{} {} {} {} {} {}".format(*P0, *P1, *P2, *P3)
strings = []
xs = [66, 79, 92, 105, 118, 131, 144]
hues = ["#5cffb0", "#45f0c8", "#3adcf0", "#3ab8ff", "#5a95ff", "#7a7cff", "#9a6bff"]
for x, hue in zip(xs, hues):
    t = min((s / 400 for s in range(401)), key=lambda t: abs(bez(P0, P1, P2, P3, t)[0] - x))
    top = bez(P0, P1, P2, P3, t)[1] + 5
    bottom = 166 - (x - 58) * (102 / 96) - 6
    strings.append((x, top, bottom, hue))
HARP = f"""
<rect width="200" height="200" fill="url(#hBg)"/>
<circle cx="104" cy="110" r="70" fill="#0fd39a" opacity=".18" filter="url(#blur24)"/>
{''.join(f'<line x1="{x}" y1="{t:.1f}" x2="{x}" y2="{b:.1f}" stroke="{h}" stroke-width="7" stroke-linecap="round" opacity=".55" filter="url(#glow6)"/>' for x, t, b, h in strings)}
{''.join(f'<line x1="{x}" y1="{t:.1f}" x2="{x}" y2="{b:.1f}" stroke="{h}" stroke-width="3.2" stroke-linecap="round"/>' for x, t, b, h in strings)}
<path d="M58 166 L154 62" stroke="url(#gold)" stroke-width="12" stroke-linecap="round"/>
<path d="{neck}" fill="none" stroke="url(#gold)" stroke-width="8" stroke-linecap="round"/>
<path d="M52 58 C46 96 48 132 58 166" fill="none" stroke="url(#gold)" stroke-width="9" stroke-linecap="round"/>
<path d="M58 166 L154 62" stroke="#fff6d8" stroke-width="2" stroke-linecap="round" opacity=".55"/>
"""
HARP_MONO = f"""{''.join(f'<line x1="{x}" y1="{t:.1f}" x2="{x}" y2="{b:.1f}" stroke="currentColor" stroke-width="4.5" stroke-linecap="round"/>' for x, t, b, h in strings)}
<path d="M58 166 L154 62" stroke="currentColor" stroke-width="12" stroke-linecap="round"/>
<path d="{neck}" fill="none" stroke="currentColor" stroke-width="9" stroke-linecap="round"/>
<path d="M52 58 C46 96 48 132 58 166" fill="none" stroke="currentColor" stroke-width="10" stroke-linecap="round"/>"""

# ---------- D · Listening orb: an AI orb inside a ring of sound ----------
ORB = f"""
<rect width="200" height="200" fill="url(#oBg)"/>
<path d="{wave_ring(80, 3.2, 22, 1.2)}" fill="none" stroke="#3b2a7a" stroke-width="1.6" opacity=".35"/>
<path d="{wave_ring(68, 5.5, 14)}" fill="none" stroke="url(#ringGrad)" stroke-width="3.2"/>
<circle cx="100" cy="106" r="46" fill="#5a2bd6" opacity=".35" filter="url(#blur12)"/>
<g clip-path="url(#orbClip)">
  <circle cx="100" cy="100" r="48" fill="url(#orbBase)"/>
  <circle cx="80" cy="82" r="30" fill="#ffb347" filter="url(#blur12)" opacity=".95"/>
  <circle cx="126" cy="124" r="30" fill="#2fe3ff" filter="url(#blur12)" opacity=".9"/>
  <circle cx="128" cy="76" r="22" fill="#ff3da0" filter="url(#blur12)" opacity=".9"/>
  <circle cx="74" cy="128" r="20" fill="#7b3cff" filter="url(#blur12)" opacity=".9"/>
  <circle cx="100" cy="100" r="48" fill="url(#orbShade)"/>
</g>
<ellipse cx="84" cy="74" rx="18" ry="10" transform="rotate(-30 84 74)" fill="#fff" opacity=".7" filter="url(#blur3)"/>
<circle cx="100" cy="100" r="48" fill="none" stroke="#fff" stroke-opacity=".35" stroke-width="1"/>
"""
ORB_MONO = f"""<path d="{wave_ring(70, 6, 14)}" fill="none" stroke="currentColor" stroke-width="7"/>
<circle cx="100" cy="100" r="40" fill="currentColor"/>"""

DEFS = """
<filter id="soft" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="5"/></filter>
<filter id="blur3" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3"/></filter>
<filter id="blur12" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="12"/></filter>
<filter id="blur24" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="24"/></filter>
<filter id="glow6" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="4"/></filter>
<filter id="glow8" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="6"/></filter>
<radialGradient id="gBg" cx=".5" cy=".35" r=".8"><stop offset="0" stop-color="#262634"/><stop offset="1" stop-color="#060609"/></radialGradient>
<linearGradient id="irisA" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7df9ff"/><stop offset=".5" stop-color="#ff7ae6"/><stop offset="1" stop-color="#7df9ff" stop-opacity="0"/></linearGradient>
<linearGradient id="irisB" x1="1" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#ffd36e"/><stop offset=".5" stop-color="#ff7ae6"/><stop offset="1" stop-color="#ffd36e" stop-opacity="0"/></linearGradient>
<radialGradient id="gLabel" cx=".35" cy=".3" r=".9"><stop offset="0" stop-color="#ff9a7a"/><stop offset=".55" stop-color="#ff4d6d"/><stop offset="1" stop-color="#c2185b"/></radialGradient>
<linearGradient id="sLine" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7df9ff"/><stop offset=".5" stop-color="#ffffff"/><stop offset="1" stop-color="#ff7ae6"/></linearGradient>
<linearGradient id="sGlow" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2ee6ff"/><stop offset="1" stop-color="#ff3dd2"/></linearGradient>
<linearGradient id="nBg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0a1033"/><stop offset="1" stop-color="#14062e"/></linearGradient>
<linearGradient id="stem" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#bfe6ff"/><stop offset="1" stop-color="#ffffff"/></linearGradient>
<radialGradient id="head" cx=".35" cy=".3" r=".9"><stop offset="0" stop-color="#ffffff"/><stop offset=".35" stop-color="#9cc8ff"/><stop offset=".8" stop-color="#3d55e8"/><stop offset="1" stop-color="#2a2a9a"/></radialGradient>
<radialGradient id="hBg" cx=".5" cy=".4" r=".85"><stop offset="0" stop-color="#0d3a2f"/><stop offset="1" stop-color="#020c09"/></radialGradient>
<linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff0b8"/><stop offset=".45" stop-color="#e8b45a"/><stop offset="1" stop-color="#a8681c"/></linearGradient>
<linearGradient id="oBg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fbf8ff"/><stop offset="1" stop-color="#e2e6fb"/></linearGradient>
<linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ff3da0"/><stop offset=".5" stop-color="#6b3cff"/><stop offset="1" stop-color="#16c6ff"/></linearGradient>
<radialGradient id="orbBase" cx=".5" cy=".5" r=".6"><stop offset="0" stop-color="#ff6fb5"/><stop offset="1" stop-color="#5a2bd6"/></radialGradient>
<radialGradient id="orbShade" cx=".38" cy=".32" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#1a0a4a" stop-opacity=".45"/></radialGradient>
<clipPath id="orbClip"><circle cx="100" cy="100" r="48"/></clipPath>
"""

CONCEPTS = [
    ("groove", "Groove", GROOVE, GROOVE_MONO, ["Record", "Frequency", "S"],
     "A vinyl record caught in the light: the glare across its grooves is a glowing S, shifting from cyan to magenta like the sheen on a real record.",
     "siansa", "font-family:'Inter Display';font-weight:800;letter-spacing:-2.5px",
     "linear-gradient(90deg,#7df9ff,#ffffff 45%,#ff7ae6)", "radial-gradient(120% 90% at 20% 10%,#2a2a3a,#08080c 70%)"),
    ("note", "Neural note", NOTE, NOTE_MONO, ["Music note", "AI"],
     "A quaver whose flag is a constellation of connected nodes, the shape of a neural network. A glossy note head, on a deep aurora. Music, thought through by AI.",
     "Siansa", "font-family:'Inter Display';font-weight:300;letter-spacing:1px",
     "linear-gradient(90deg,#bfe6ff,#9c8bff 60%,#e07bff)", "radial-gradient(120% 90% at 80% 10%,#2b1460,#070a1f 70%)"),
    ("harp", "Spectrum harp", HARP, HARP_MONO, ["Instrument", "Frequency", "Irish"],
     "The Irish harp, drawn in gold, whose strings are a frequency spectrum glowing from green to violet. The harp's shape is already a spectrum: short strings high, long strings low.",
     "SIANSA", "font-family:'GFS Baskerville';letter-spacing:12px",
     "linear-gradient(90deg,#fff0b8,#e8b45a 50%,#c8892e)", "radial-gradient(120% 90% at 20% 10%,#0f4a3a,#020c09 70%)"),
    ("orb", "Listening orb", ORB, ORB_MONO, ["AI", "Frequency"],
     "A glassy AI orb, swirling with colour, inside a ring of sound waves. It's listening to your music. Light and fresh, like a modern assistant.",
     "siansa", "font-family:Lora;font-style:italic;font-weight:600;letter-spacing:-.5px",
     "linear-gradient(90deg,#ff3da0,#6b3cff 55%,#16c6ff)", "radial-gradient(120% 90% at 80% 10%,#ffffff,#e4e7f8 70%)"),
]


def icon(key, size, radius):
    return (f'<div class="ic" style="width:{size}px;height:{size}px;border-radius:{radius}">'
            f'<svg viewBox="0 0 200 200" width="{size}" height="{size}"><use href="#{key}"/></svg></div>')


def mono(key, size):
    return (f'<div class="ic mono" style="width:{size}px;height:{size}px;border-radius:50%">'
            f'<svg viewBox="0 0 200 200" width="{size}" height="{size}"><g transform="translate(20 20) scale(.8)">'
            f'<use href="#{key}-mono"/></g></svg></div>')


def card(c):
    key, title, _, _, tags, why, word, font, grad, bg = c
    light = key == "orb"
    ink = "#1b1640" if light else "#EDEAF6"
    sub = "#4b4670" if light else "#A9A3BD"
    chips = "".join(f'<span class="chip" style="border-color:{"#cfd2ee" if light else "#2e2b3d"};color:{sub}">{t}</span>' for t in tags)
    return f"""
<div class="card" style="background:{bg}">
  <div class="left">{icon(key, 236, '24%')}</div>
  <div class="right">
    <div class="ctitle" style="color:{sub}">{title}</div>
    <div class="word" style="{font};background:{grad};-webkit-background-clip:text;background-clip:text;color:transparent">{word}</div>
    <div class="chips">{chips}</div>
    <p style="color:{sub}">{why}</p>
    <div class="sizes">{icon(key, 64, '24%')}{icon(key, 52, '50%')}{icon(key, 40, '50%')}{mono(key, 52)}
      <span class="hint" style="color:{sub}">small · round · tiny · themed</span></div>
  </div>
</div>"""


def home():
    generic = ["#3b3f4a", "#2f4f6b", "#5a3b3b", "#3d5a40", "#4a3d63", "#5b4b2f", "#2f5a5a", "#4f4f4f"]
    labels = ["Phone", "Messages", "Camera", "Maps", "Mail", "Photos", "Clock", "Files"]
    cells = []
    order = ["groove", None, "note", None, None, "harp", None, "orb", None, None, None, None]
    gi = 0
    for k in order:
        if k:
            cells.append(f'<div class="cell">{icon(k, 56, "24%")}<span>Siansa</span></div>')
        else:
            col, lab = generic[gi % 8], labels[gi % 8]
            gi += 1
            cells.append(f'<div class="cell"><div class="ic" style="width:56px;height:56px;border-radius:24%;'
                         f'background:linear-gradient(160deg,{col},#1d1f26)"></div><span>{lab}</span></div>')
    return "".join(cells)


syms = "".join(f'<symbol id="{c[0]}" viewBox="0 0 200 200">{c[2]}</symbol><symbol id="{c[0]}-mono" viewBox="0 0 200 200">{c[3]}</symbol>' for c in CONCEPTS)

html = f"""<!doctype html><html><head><meta charset="utf-8"><style>
*{{box-sizing:border-box;margin:0;padding:0}}
body{{background:#050507;font-family:Inter,sans-serif}}
#shot{{width:1500px;padding:56px 52px 48px;background:radial-gradient(80% 50% at 50% 0%,#17172a,#050507 70%)}}
header{{margin-bottom:34px}}
header .eyebrow{{font:600 12px Inter;letter-spacing:2.4px;color:#7d78a0;text-transform:uppercase}}
header h1{{font:700 44px 'Inter Display';letter-spacing:-1.4px;color:#F4F1FF;margin:8px 0 10px}}
header p{{font-size:16px;line-height:1.5;color:#A9A3BD;max-width:1050px}}
.grid{{display:grid;grid-template-columns:1fr 1fr;gap:26px}}
.card{{border-radius:30px;padding:34px;display:flex;gap:32px;align-items:center;border:1px solid rgba(255,255,255,.06);
  box-shadow:0 30px 60px rgba(0,0,0,.35)}}
.ic{{overflow:hidden;flex:none;box-shadow:0 18px 40px rgba(0,0,0,.45),0 0 0 1px rgba(255,255,255,.06) inset}}
.ic svg{{display:block}}
.ic.mono{{background:#2b2e45;color:#cfd8ff;box-shadow:0 8px 20px rgba(0,0,0,.35)}}
.left .ic{{box-shadow:0 30px 60px rgba(0,0,0,.55),0 0 0 1px rgba(255,255,255,.08) inset}}
.right{{flex:1;min-width:0}}
.ctitle{{font:600 12px Inter;letter-spacing:2px;text-transform:uppercase}}
.word{{font-size:62px;line-height:1.05;margin:6px 0 12px;padding-bottom:4px}}
.chips{{display:flex;gap:8px;margin-bottom:12px}}
.chip{{font-size:12px;padding:4px 10px;border-radius:99px;border:1px solid}}
.right p{{font-size:14px;line-height:1.55;margin-bottom:18px}}
.sizes{{display:flex;align-items:center;gap:14px}}
.hint{{font-size:11px;margin-left:4px}}
h2{{font:600 26px 'Inter Display';letter-spacing:-.5px;color:#F4F1FF;margin:46px 0 6px}}
.sub{{font-size:15px;color:#A9A3BD;margin-bottom:22px}}
.homes{{display:flex;gap:40px;align-items:flex-start}}
.phone{{width:340px;height:640px;border-radius:46px;border:10px solid #1c1c24;overflow:hidden;position:relative;
  background:radial-gradient(60% 40% at 30% 20%,#3b2a6b,transparent),radial-gradient(60% 50% at 80% 80%,#0d5a6b,transparent),#0b0b14}}
.phone.lightwp{{background:radial-gradient(60% 40% at 30% 20%,#ffd3e8,transparent),radial-gradient(60% 50% at 80% 80%,#cfe8ff,transparent),#f3f0fa}}
.clock{{text-align:center;margin-top:46px;font:300 64px 'Inter Display';color:#fff;letter-spacing:-2px}}
.lightwp .clock{{color:#1b1640}}
.date{{text-align:center;font:500 14px Inter;color:rgba(255,255,255,.8);margin-bottom:34px}}
.lightwp .date{{color:#4b4670}}
.apps{{display:grid;grid-template-columns:repeat(4,1fr);gap:22px 8px;padding:0 20px}}
.cell{{display:flex;flex-direction:column;align-items:center;gap:7px}}
.cell span{{font-size:11px;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,.5)}}
.lightwp .cell span{{color:#1b1640;text-shadow:none}}
.notes{{flex:1;font-size:15px;line-height:1.65;color:#A9A3BD}}
.notes b{{color:#F4F1FF}}
.notes li{{margin:0 0 12px 18px}}
</style></head><body>
<svg width="0" height="0" style="position:absolute"><defs>{DEFS}</defs>{syms}</svg>
<div id="shot">
<header>
  <div class="eyebrow">Issue #360 · Siansa · round two</div>
  <h1>Siansa: four marks with music built in</h1>
  <p>Each concept fuses at least two of: records, instruments, notes, frequency and AI. Each is drawn with real light and depth, in its own colours rather than the current theme. Every one is also shown small, round, tiny, and as the one-colour Android themed icon, so it holds up everywhere.</p>
</header>
<div class="grid">{''.join(card(c) for c in CONCEPTS)}</div>
<h2>On a home screen</h2>
<div class="sub">All four among ordinary apps, on a dark and a light wallpaper. A good icon should stand out without shouting.</div>
<div class="homes">
  <div class="phone"><div class="clock">22:41</div><div class="date">Friday 23 October</div><div class="apps">{home()}</div></div>
  <div class="phone lightwp"><div class="clock">22:41</div><div class="date">Friday 23 October</div><div class="apps">{home()}</div></div>
  <div class="notes"><ul>
    <li><b>Groove</b>: the most iconic and "music app" at a glance. A record anyone recognises, made ownable by the S of light.</li>
    <li><b>Neural note</b>: says <i>AI music</i> most directly. The constellation flag is distinctive and modern.</li>
    <li><b>Spectrum harp</b>: the most premium and the most Irish; it ties the name's meaning (harmony) to an instrument. Note: the Irish State harp is a protected emblem, so the final harp must stay clearly stylised, as here, not a copy of it.</li>
    <li><b>Listening orb</b>: the freshest and most "AI assistant", and the lightest on a home screen.</li>
  </ul></div>
</div>
</div></body></html>"""

open(sys.argv[1], "w").write(html)
