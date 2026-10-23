"""Builds the Ekkora logo board (issue #360) in the Musix palette: plum, violet and amber."""
import sys

AMBER, AMBER_HI, VIOLET, VIOLET_HI, PLUM = "#FFB547", "#FFE3B0", "#A891FF", "#E6DDFF", "#110D1C"

# Each glyph is drawn on a 108x108 canvas (Android's adaptive-icon grid, centre 54,54, safe zone r=33).
# {A} amber, {AH} light amber, {V} violet, {VH} light violet. The one-colour version swaps all for currentColor.
GLYPHS = {
    "s": (
        "Melody S", "One unbroken line: an S that is also a melody",
        "The S of Siansa drawn as a single flowing stroke, a melody line rising and falling. One gesture, instantly readable even tiny.",
        """
        <path d="M68 33 C64 28 58 26 52 26 C42 26 36 32 36 40 C36 50 46 52 54 54 C64 56 72 59 72 68 C72 77 64 82 55 82 C47 82 41 79 37 74" fill="none" stroke="{A}" stroke-width="8.5" stroke-linecap="round"/>
        """),
    "harmony": (
        "Harmony", "Two notes overlapping into one",
        "Two circles, two voices, overlapping where they meet in harmony. The overlap glows: amber and violet becoming one. Bold, calm and premium.",
        """
        <circle cx="45" cy="54" r="19" fill="{V}" opacity=".9"/>
        <circle cx="63" cy="54" r="19" fill="{A}" opacity=".9"/>
        <path d="M54 37.3 A19 19 0 0 1 54 70.7 A19 19 0 0 1 54 37.3 Z" fill="{AH}"/>
        """),
    "spiral": (
        "Spiral", "An Irish spiral that keeps coming back round",
        "A single spiral, as carved at Newgrange: Irish to the core, and a melody that keeps returning, which is rediscovery in one mark.",
        """
        <path d="M54 54 C54 50 58 49 60 52 C63 56 59 62 54 62 C47 62 44 55 46 49 C49 41 59 39 65 43 C73 48 74 59 69 66 C63 74 51 76 43 71 C34 65 32 52 36 43" fill="none" stroke="{A}" stroke-width="6.5" stroke-linecap="round"/>
        """),
}
# How each wordmark is set: font weight, letter spacing, case, and which letter gets the accent.
WORDMARKS = {
    "s": ("600", "-1.2px", "siansa", None),
    "harmony": ("300", "9px", "SIANSA", None),
    "spiral": ("500", "-0.5px", "Siansa", "dot"),
}

DEFS = f"""
<linearGradient id="trail" x1="0" y1="1" x2="1" y2="0">
  <stop offset="0" stop-color="{AMBER}" stop-opacity="0"/><stop offset="1" stop-color="{AMBER}" stop-opacity="1"/>
</linearGradient>
<radialGradient id="glow" cx=".5" cy=".5" r=".5">
  <stop offset="0" stop-color="#7C5CFF" stop-opacity=".45"/><stop offset=".6" stop-color="#7C5CFF" stop-opacity=".12"/><stop offset="1" stop-color="#7C5CFF" stop-opacity="0"/>
</radialGradient>
"""


def glyph(key: str, mono: bool = False) -> str:
    body = GLYPHS[key][3]
    if mono:
        for t in ("{AH}", "{A}", "{VH}", "{V}"):
            body = body.replace(t, "currentColor")
        body = body.replace("url(#trail)", "currentColor")
    else:
        body = body.replace("{AH}", AMBER_HI).replace("{A}", AMBER).replace("{VH}", VIOLET_HI).replace("{V}", VIOLET)
    return body


def icon(key: str, size: int, shape: str) -> str:
    """The launcher icon: plum background, soft violet glow, the glyph, in a circle or squircle mask."""
    radius = "50%" if shape == "circle" else f"{int(size * 0.27)}px"
    return (f'<div class="ic" style="width:{size}px;height:{size}px;border-radius:{radius}">'
            f'<svg viewBox="0 0 108 108" width="{size}" height="{size}"><rect width="108" height="108" fill="{PLUM}"/>'
            f'<circle cx="54" cy="54" r="40" fill="url(#glow)"/>{glyph(key)}</svg></div>')


def mono(key: str, size: int) -> str:
    """Android 13+ themed icon: one colour, tinted by the wallpaper."""
    return (f'<div class="ic" style="width:{size}px;height:{size}px;border-radius:50%;background:#2E2450;color:#D9CCFF">'
            f'<svg viewBox="0 0 108 108" width="{size}" height="{size}">{glyph(key, True)}</svg></div>')


def wordmark(key: str, size: int, colour: str, accent: str) -> str:
    weight, track, text, mark = WORDMARKS[key]
    style = f"font-family:'Inter Display';font-weight:{weight};letter-spacing:{track};font-size:{size}px;color:{colour};line-height:1"
    if mark == "first":
        text = f'<span style="color:{accent}">e</span>kkora'
    elif mark == "orbit":
        ring = (f'<svg viewBox="0 0 108 108" style="width:.62em;height:.62em;vertical-align:-.02em;margin:0 .02em">'
                f'<circle cx="54" cy="54" r="40" fill="none" stroke="{colour}" stroke-width="13"/>'
                f'<circle cx="82" cy="26" r="13" fill="{accent}"/></svg>')
        text = f"ekk{ring}ra"
    elif mark == "dot":
        text = f'{text}<span style="color:{accent}">.</span>'
    return f'<div style="{style}">{text}</div>'


def card(i: int, key: str) -> str:
    name, short, why, _ = GLYPHS[key]
    return f"""
<div class="card">
  <div class="hero">
    <span class="num">{chr(65 + i)}</span>
    {icon(key, 132, 'circle')}
    <div class="lock">{wordmark(key, 46, '#EDE7F6', AMBER)}<div class="tagline">{short}</div></div>
  </div>
  <div class="row">
    <div class="sizes">
      {icon(key, 60, 'squircle')}
      {icon(key, 44, 'circle')}
      {mono(key, 44)}
    </div>
    <div class="lightmark">{wordmark(key, 30, '#22164A', '#B86A00')}</div>
  </div>
  <div class="txt"><b>{name}.</b> {why}</div>
</div>"""


def splash(key: str, title: str) -> str:
    return f"""
<div class="phone">
  <div class="screen">
    <div class="glowbig"></div>
    <svg viewBox="0 0 108 108" width="150" height="150" style="position:relative">{glyph(key)}</svg>
    {wordmark(key, 34, '#EDE7F6', AMBER)}
    <div class="slogan">Your music, in harmony.</div>
  </div>
  <div class="cap">{title}</div>
</div>"""


ORDER = ["s", "harmony", "spiral"]

current = f"""<svg viewBox="0 0 108 108" width="64" height="64" style="border-radius:50%;background:{PLUM};display:block">
<circle cx="54" cy="52" r="34" fill="url(#glow)"/>
<rect x="31" y="56" width="6" height="14" rx="3" fill="#C9B9FF"/><rect x="41" y="44" width="6" height="26" rx="3" fill="#D3C6FF"/>
<rect x="51" y="34" width="6" height="36" rx="3" fill="{AMBER}"/><rect x="61" y="42" width="6" height="28" rx="3" fill="#D3C6FF"/>
<rect x="71" y="52" width="6" height="18" rx="3" fill="#C9B9FF"/></svg>"""

html = f"""<!doctype html><html><head><meta charset="utf-8"><style>
*{{box-sizing:border-box;margin:0;padding:0}}
body{{background:#0b0814;font-family:Inter,sans-serif}}
#shot{{width:1500px;padding:48px;background:linear-gradient(180deg,#141022,#0b0814)}}
header{{display:flex;align-items:center;gap:22px;margin-bottom:30px}}
header h1{{font-family:'Inter Display';font-weight:600;font-size:32px;letter-spacing:-.6px;color:#EDE7F6}}
header p{{font-size:15px;color:#B9B0CC;margin-top:6px;max-width:980px;line-height:1.45}}
.now{{display:flex;flex-direction:column;align-items:center;gap:6px;padding:12px 14px;border-radius:16px;background:#1C1630;border:1px solid #322A4D}}
.now span{{font-size:11px;color:#B9B0CC}}
.grid{{display:grid;grid-template-columns:repeat(3,1fr);gap:22px}}
.card{{background:#1C1630;border:1px solid #2a2342;border-radius:24px;overflow:hidden}}
.hero{{position:relative;display:flex;flex-direction:column;align-items:center;gap:18px;padding:34px 20px 26px;
  background:radial-gradient(90% 80% at 50% 0%,#2a2050 0%,#110D1C 70%)}}
.num{{position:absolute;top:14px;left:16px;font:600 12px Inter;color:#6E6585;letter-spacing:1px}}
.ic{{overflow:hidden;box-shadow:0 10px 28px rgba(0,0,0,.45),0 0 0 1px rgba(255,255,255,.04);flex:none;display:flex;align-items:center;justify-content:center}}
.ic svg{{display:block}}
.lock{{display:flex;flex-direction:column;align-items:center;gap:8px}}
.tagline{{font-size:12.5px;color:#B9B0CC}}
.row{{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:16px 18px;background:#FBF8FF}}
.sizes{{display:flex;align-items:center;gap:12px}}
.lightmark{{padding-right:4px}}
.txt{{padding:14px 18px 18px;font-size:13px;line-height:1.5;color:#B9B0CC}}
.txt b{{color:#EDE7F6;font-weight:600}}
h2{{font-family:'Inter Display';font-weight:600;font-size:22px;color:#EDE7F6;margin:40px 0 6px}}
.sub{{font-size:14px;color:#B9B0CC;margin-bottom:20px}}
.phones{{display:flex;gap:40px;align-items:flex-start}}
.phone{{display:flex;flex-direction:column;align-items:center;gap:12px}}
.screen{{position:relative;width:260px;height:530px;border-radius:38px;background:#110D1C;border:8px solid #241d38;
  display:flex;flex-direction:column;align-items:center;justify-content:center;gap:22px;overflow:hidden}}
.glowbig{{position:absolute;width:340px;height:340px;top:60px;border-radius:50%;
  background:radial-gradient(circle,rgba(124,92,255,.35),rgba(124,92,255,.08) 55%,transparent 70%)}}
.slogan{{position:absolute;bottom:46px;font-size:13px;color:#B9B0CC;letter-spacing:.3px}}
.cap{{font-size:13px;color:#B9B0CC}}
.notes{{flex:1;font-size:14px;color:#B9B0CC;line-height:1.6;padding-top:10px}}
.notes b{{color:#EDE7F6}}
.notes li{{margin:0 0 10px 18px}}
.sw{{display:inline-block;width:12px;height:12px;border-radius:3px;vertical-align:-1px;margin-right:4px}}
</style></head><body>
<svg width="0" height="0" style="position:absolute"><defs>{DEFS}</defs></svg>
<div id="shot">
<header>
  <div class="now">{current}<span>Musix today</span></div>
  <div><h1>Siansa · logo directions</h1>
  <p><b style=\'color:#EDE7F6\'>Siansa</b> (SHEE-an-sa), Irish for melody and harmony. Three simple, strong marks in the app's colours: deep plum, soft violet and the amber you press to play. Each one is shown as Android shows it: round and squircle masks, small, and the one-colour themed icon (third), plus the wordmark on light.</p></div>
</header>
<div class="grid">{''.join(card(i, k) for i, k in enumerate(ORDER))}</div>
<h2>Splash screen</h2>
<div class="sub">What opening the app could look like with each one.</div>
<div class="phones">
  {splash('s', 'A · Melody S')}
  {splash('harmony', 'B · Harmony')}
  {splash('spiral', 'C · Spiral')}
  <div class="notes">
    <ul>
      <li><b>A · Melody S</b>: the most powerful at small sizes. One stroke, readable as a notification icon.</li>
      <li><b>B · Harmony</b>: the most premium, and says what the word means.</li>
      <li><b>C · Spiral</b>: the most Irish, and the rediscovery idea in one mark.</li>
      <li>Same plum, violet and amber as the app today, so nothing else has to change.</li>
    </ul>
  </div></div>
</div>
</div></body></html>"""

open(sys.argv[1], "w").write(html)
