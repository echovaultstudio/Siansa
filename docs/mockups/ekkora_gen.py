"""Builds the Ekkora logo board (issue #360) in the Musix palette: plum, violet and amber."""
import sys

AMBER, AMBER_HI, VIOLET, VIOLET_HI, PLUM = "#FFB547", "#FFE3B0", "#A891FF", "#E6DDFF", "#110D1C"

# Each glyph is drawn on a 108x108 canvas (Android's adaptive-icon grid, centre 54,54, safe zone r=33).
# {A} amber, {AH} light amber, {V} violet, {VH} light violet. The one-colour version swaps all for currentColor.
GLYPHS = {
    "bars": (
        "Echo bars", "Your sound bars, with the echo around them",
        "The current icon's sunrise bars, kept, with echo rings on both sides. Familiar to everyone who uses Musix now.",
        """
        <path d="M33 31 A33 33 0 0 0 33 77" fill="none" stroke="{V}" stroke-width="4.5" stroke-linecap="round" opacity=".75"/>
        <path d="M75 31 A33 33 0 0 1 75 77" fill="none" stroke="{V}" stroke-width="4.5" stroke-linecap="round" opacity=".75"/>
        <path d="M25 23 A44 44 0 0 0 25 85" fill="none" stroke="{V}" stroke-width="4" stroke-linecap="round" opacity=".35"/>
        <path d="M83 23 A44 44 0 0 1 83 85" fill="none" stroke="{V}" stroke-width="4" stroke-linecap="round" opacity=".35"/>
        <rect x="36.25" y="55" width="5.5" height="12" rx="2.75" fill="{VH}"/>
        <rect x="43.75" y="46" width="5.5" height="21" rx="2.75" fill="{VH}"/>
        <rect x="51.25" y="38" width="5.5" height="29" rx="2.75" fill="{A}"/>
        <rect x="58.75" y="46" width="5.5" height="21" rx="2.75" fill="{VH}"/>
        <rect x="66.25" y="55" width="5.5" height="12" rx="2.75" fill="{VH}"/>
        """),
    "play": (
        "Play, echoed", "A play button and its echoes",
        "The amber play button you press everywhere in the app, followed by two fading echoes. Says 'play' and 'again' at once.",
        """
        <path d="M58 38 L80 54 L58 70" fill="none" stroke="{V}" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round" opacity=".35"/>
        <path d="M48 38 L70 54 L48 70" fill="none" stroke="{V}" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round" opacity=".7"/>
        <path d="M31 39.5 Q31 35.5 34.6 37.6 L57.4 51.4 Q60.6 53.4 57.4 55.6 L34.6 70.4 Q31 72.5 31 68.5 Z" fill="{A}"/>
        """),
    "orbit": (
        "Orbit", "A record with a light coming back round",
        "A record ring with an amber light travelling round it and a fading trail: your music, coming back. Calm and premium.",
        """
        <circle cx="54" cy="54" r="24" fill="none" stroke="{V}" stroke-width="5" opacity=".35"/>
        <path d="M30 54 A24 24 0 0 1 71 37" fill="none" stroke="url(#trail)" stroke-width="5" stroke-linecap="round"/>
        <circle cx="71" cy="37" r="6.5" fill="{A}"/>
        <circle cx="54" cy="54" r="5" fill="{VH}"/>
        """),
    "e": (
        "Echo e", "A lowercase e with echo rings",
        "The first letter of the name as a clean monogram, with two echo rings behind it. Works well as a tiny badge.",
        """
        <path d="M70 32 A31 31 0 0 1 70 76" fill="none" stroke="{V}" stroke-width="4" stroke-linecap="round" opacity=".35"/>
        <path d="M66 38.5 A22.5 22.5 0 0 1 66 69.5" fill="none" stroke="{V}" stroke-width="4" stroke-linecap="round" opacity=".7"/>
        <path d="M37 55 H63 A13 13 0 1 0 59.2 64.2" fill="none" stroke="{A}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" transform="translate(-4 -1)"/>
        """),
    "ripple": (
        "Ripple", "One dot sending out rings",
        "An amber dot (the play accent) sending out three rings of sound, fading as they go. The simplest of the six.",
        """
        <circle cx="42" cy="54" r="8" fill="{A}"/>
        <path d="M53.6 40.2 A18 18 0 0 1 53.6 67.8" fill="none" stroke="{VH}" stroke-width="5" stroke-linecap="round"/>
        <path d="M60 32.6 A28 28 0 0 1 60 75.4" fill="none" stroke="{V}" stroke-width="5" stroke-linecap="round" opacity=".7"/>
        <path d="M66.4 24.9 A38 38 0 0 1 66.4 83.1" fill="none" stroke="{V}" stroke-width="5" stroke-linecap="round" opacity=".35"/>
        """),
    "wave": (
        "Echo wave", "A sound wave and its echoes",
        "One amber wave with two violet echoes trailing below it: flow and echo, like crossfades and mixes.",
        """
        <path d="M24 62 C34 62 36 44 46 44 S58 70 68 70 S80 52 84 52" fill="none" stroke="{V}" stroke-width="4.5" stroke-linecap="round" opacity=".3" transform="translate(0 8)"/>
        <path d="M24 62 C34 62 36 44 46 44 S58 70 68 70 S80 52 84 52" fill="none" stroke="{V}" stroke-width="4.5" stroke-linecap="round" opacity=".6" transform="translate(0 2)"/>
        <path d="M24 62 C34 62 36 44 46 44 S58 70 68 70 S80 52 84 52" fill="none" stroke="{A}" stroke-width="6" stroke-linecap="round" transform="translate(0 -6)"/>
        """),
}

# How each wordmark is set: font weight, letter spacing, case, and which letter gets the accent.
WORDMARKS = {
    "bars": ("600", "-1.5px", "ekkora", None),
    "play": ("700", "5px", "EKKORA", None),
    "orbit": ("500", "-1px", "ekk&#x25CB;ra", "orbit"),
    "e": ("600", "-1.5px", "ekkora", "first"),
    "ripple": ("300", "6px", "ekkora", None),
    "wave": ("600", "-1px", "ekkora", "dot"),
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
        text = f'ekkora<span style="color:{accent}">.</span>'
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
    <div class="slogan">Your music, again.</div>
  </div>
  <div class="cap">{title}</div>
</div>"""


ORDER = ["bars", "play", "orbit", "e", "ripple", "wave"]

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
  <div><h1>Ekkora · logo directions</h1>
  <p>Built on the app as it is now: deep plum backgrounds, soft violet and the warm amber you press to play. Each one is shown as Android shows it: round and squircle masks, small, and the one-colour themed icon (third), plus the wordmark on light.</p></div>
</header>
<div class="grid">{''.join(card(i, k) for i, k in enumerate(ORDER))}</div>
<h2>Splash screen</h2>
<div class="sub">What opening the app could look like, with the two strongest directions.</div>
<div class="phones">
  {splash('play', 'B · Play, echoed')}
  {splash('orbit', 'C · Orbit')}
  <div class="notes">
    <ul>
      <li><b>Colours stay the same.</b> <span class="sw" style="background:#110D1C;border:1px solid #444"></span>Plum #110D1C, <span class="sw" style="background:#A891FF"></span>violet #A891FF and <span class="sw" style="background:#FFB547"></span>amber #FFB547, so screens, buttons and themes carry over unchanged.</li>
      <li><b>Amber means "play"</b>, as it does across the app today, which is why the play button (B) and the travelling light (C) are amber and everything else is violet.</li>
      <li><b>Type is Inter Display</b>, a clean modern sans with the same calm feel as the app's text.</li>
      <li><b>A (Echo bars)</b> keeps the current icon's sunrise bars, the smallest change for people who know Musix.</li>
      <li><b>B and C</b> are the boldest new marks: simple enough for a tiny notification icon and a big splash alike.</li>
      <li>All six fit Android's adaptive-icon safe zone, so no launcher will crop them.</li>
    </ul>
  </div>
</div>
</div></body></html>"""

open(sys.argv[1], "w").write(html)
