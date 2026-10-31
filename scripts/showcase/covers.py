"""Album covers and artist pictures for the showcase library (issue #741), drawn from nothing but their names.

Each album names a style (waves, sunset, bauhaus…) and a palette; the same name always gives the same picture.
Needs Pillow and numpy, and the Inter font (fonts-inter) for the covers with lettering.
"""
import hashlib
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SIZE = 1000

PALETTES = {
    "harbour": ["#0B2233", "#123B57", "#2F6E8E", "#8CC5D6", "#F2E6C9"],
    "dusk": ["#140F2B", "#33204F", "#7A3B69", "#E58F65", "#FFD59E"],
    "neon": ["#0A0420", "#2A0B4F", "#FF2E88", "#FF9F45", "#2DE2E6"],
    "chrome": ["#0E0F12", "#3A3F47", "#9BA4B0", "#E6EBF0", "#FF4F79"],
    "forest": ["#0F1E17", "#1E3B2B", "#3C6B4A", "#9DBF8A", "#F0E2C0"],
    "peach": ["#3B1F2B", "#A2475E", "#F28A6B", "#FFC8A2", "#FFF1E0"],
    "tropic": ["#0B3B3B", "#0F7C7C", "#F2B33D", "#F25C3D", "#F7EBDD"],
    "magenta": ["#14061F", "#4B0F5C", "#C2185B", "#FF7AA2", "#FFE3EE"],
    "signal": ["#111111", "#E8432E", "#F4C430", "#F2EDE4", "#2B2B2B"],
    "paper": ["#F3E9D8", "#E6B566", "#D9663F", "#3B4A5A", "#9BB0A5"],
    "night": ["#05070F", "#121A33", "#25407A", "#F2A541", "#EDEDED"],
    "fog": ["#C9D3D9", "#A3B2BC", "#7C8E9B", "#56677A", "#EEF2F4"],
    "midnight": ["#070B19", "#14213D", "#2B3A67", "#E0C097", "#F5F1E8"],
    "golden": ["#2B1B12", "#7A3E1D", "#D9822B", "#F2C14E", "#FCE8B2"],
    "copper": ["#0D1B2A", "#1B4965", "#B87333", "#F2E8CF", "#62B6CB"],
    "honey": ["#2D1E2F", "#8E4162", "#E3A857", "#F6D38E", "#FFF4E0"],
    "atlas": ["#050505", "#111827", "#22D3EE", "#A3E635", "#E5E7EB"],
    "paperlight": ["#F7F3EC", "#E9E1D3", "#C9B79C", "#6D6875", "#2E2A33"],
    "seaglass": ["#BFE3E0", "#8ECDC8", "#F7C6C7", "#FBE7C6", "#6FA3A8"],
    "polaroid": ["#FFF6E9", "#FF6B6B", "#FFD93D", "#4D96FF", "#1B1B3A"],
    "sundial": ["#1A0F0A", "#C1440E", "#F4A259", "#5B8E7D", "#F4E285"],
    "ultraviolet": ["#0B0221", "#3A0CA3", "#7209B7", "#F72585", "#4CC9F0"],
    "kite": ["#E8F1F2", "#A7D7C5", "#F7B267", "#F25F5C", "#247BA0"],
    "glacier": ["#06141B", "#11212D", "#4A6274", "#9BA8AB", "#CCD0CF"],
}


def rgb(hex_colour):
    h = hex_colour.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest()[:12], 16))


def vertical(top, bottom, size=SIZE):
    t = np.linspace(0, 1, size)[:, None, None]
    a, b = np.array(rgb(top), float), np.array(rgb(bottom), float)
    img = a * (1 - t) + b * t
    return np.repeat(img, size, axis=1)


def radial(centre, inner, outer, radius, size=SIZE):
    y, x = np.mgrid[0:size, 0:size]
    d = np.sqrt((x - centre[0]) ** 2 + (y - centre[1]) ** 2) / radius
    t = np.clip(d, 0, 1)[..., None]
    a, b = np.array(rgb(inner), float), np.array(rgb(outer), float)
    return a * (1 - t) + b * t


def to_image(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def finish(img, rnd, grain=10, vignette=0.28):
    """Film grain and a soft vignette: printed, not computed."""
    arr = np.asarray(img).astype(float)
    noise = np.random.default_rng(rnd.randrange(1 << 30)).normal(0, grain, arr.shape[:2])[..., None]
    arr = arr + noise
    y, x = np.mgrid[0:arr.shape[0], 0:arr.shape[1]]
    d = np.sqrt((x - arr.shape[1] / 2) ** 2 + (y - arr.shape[0] / 2) ** 2) / (arr.shape[0] * 0.75)
    arr = arr * (1 - vignette * np.clip(d, 0, 1)[..., None] ** 2)
    return to_image(arr)


def font(size, weight="Bold"):
    for path in (f"/usr/share/fonts/opentype/inter/InterDisplay-{weight}.otf", f"/usr/share/fonts/opentype/inter/Inter-{weight}.otf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


# ---- Styles --------------------------------------------------------------------------------------------------------

def waves(p, rnd):
    img = to_image(vertical(p[0], p[1]))
    d = ImageDraw.Draw(img)
    layers = 6
    for i in range(layers):
        base = 380 + i * 105
        amp = 40 + rnd.random() * 50
        freq = 1.2 + rnd.random() * 1.6
        phase = rnd.random() * math.tau
        colour = p[min(len(p) - 1, 1 + i % (len(p) - 1))] if i < layers - 1 else p[-1]
        pts = [(x, base + amp * math.sin(phase + freq * x / SIZE * math.tau)) for x in range(0, SIZE + 10, 10)]
        d.polygon(pts + [(SIZE, SIZE), (0, SIZE)], fill=rgb(colour))
    return img


def lanterns(p, rnd):
    img = to_image(vertical(p[0], p[1]))
    glow = Image.new("RGB", img.size, (0, 0, 0))
    g = ImageDraw.Draw(glow)
    spots = [(rnd.randint(90, 910), rnd.randint(120, 820), rnd.randint(28, 70)) for _ in range(16)]
    for x, y, r in spots:
        g.ellipse([x - r * 3, y - r * 3, x + r * 3, y + r * 3], fill=rgb(p[3]))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    img = Image.blend(img, Image.fromarray(np.clip(np.asarray(img, float) + np.asarray(glow, float) * 0.55, 0, 255).astype(np.uint8)), 1)
    d = ImageDraw.Draw(img)
    for x, y, r in spots:
        d.line([x, 0, x, y - r], fill=(*rgb(p[4]),), width=2)
        d.ellipse([x - r, y - r, x + r, y + r * 1.25], fill=rgb(p[4] if r > 50 else p[3]))
    return img


def sunset(p, rnd):
    arr = vertical(p[0], p[1])
    img = to_image(arr)
    d = ImageDraw.Draw(img)
    cx, cy, r = 500, 520, 250
    sun = Image.new("RGB", img.size)
    sd = ImageDraw.Draw(sun)
    for i in range(r, 0, -2):
        t = i / r
        c = tuple(int(a * t + b * (1 - t)) for a, b in zip(rgb(p[2]), rgb(p[3])))
        sd.ellipse([cx - i, cy - r - (r - i) * 0, cx + i, cy - r + 2 * i], fill=c)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    for k in range(9):  # the classic slices through the lower half
        y = cy + 10 + k * k * 4 + k * 12
        ImageDraw.Draw(mask).rectangle([0, y, SIZE, y + 6 + k * 2], fill=0)
    img.paste(sun, (0, 0), mask)
    horizon = 640
    d.rectangle([0, horizon, SIZE, SIZE], fill=rgb(p[0]))
    for i in range(1, 14):  # the grid floor
        y = horizon + (i ** 1.8) * 3
        d.line([0, y, SIZE, y], fill=rgb(p[4]), width=2)
    for i in range(-12, 13):
        d.line([500 + i * 30, horizon, 500 + i * 160, SIZE], fill=rgb(p[4]), width=2)
    return img


def chrome(p, rnd):
    img = to_image(vertical(p[0], p[1]))
    d = ImageDraw.Draw(img)
    for i in range(18):
        y = 120 + i * 44
        t = (math.sin(i / 2.2) + 1) / 2
        c = tuple(int(a * t + b * (1 - t)) for a, b in zip(rgb(p[2]), rgb(p[3])))
        d.rounded_rectangle([140 + i * 6, y, 860 - i * 6, y + 26], radius=13, fill=c)
    d.ellipse([430, 430, 570, 570], fill=rgb(p[4]))
    return img


def hills(p, rnd):
    img = to_image(vertical(p[4], p[3]))
    d = ImageDraw.Draw(img)
    d.ellipse([620, 150, 790, 320], fill=rgb(p[4] if p[4] != p[3] else p[2]))
    for i in range(5):
        base = 430 + i * 120
        colour = p[min(3, 3 - i)] if i < 4 else p[0]
        amp = 90 - i * 8
        phase = rnd.random() * math.tau
        pts = [(x, base - amp * math.sin(phase + x / SIZE * math.pi * (1.1 + i * 0.25)) ** 2) for x in range(0, SIZE + 10, 10)]
        d.polygon(pts + [(SIZE, SIZE), (0, SIZE)], fill=rgb(colour))
    return img


def blobs(p, rnd):
    arr = np.zeros((SIZE, SIZE, 3)) + np.array(rgb(p[0]), float)
    y, x = np.mgrid[0:SIZE, 0:SIZE]
    for colour in p[1:]:
        cx, cy = rnd.randint(100, 900), rnd.randint(100, 900)
        r = rnd.randint(320, 560)
        w = np.exp(-(((x - cx) ** 2 + (y - cy) ** 2) / (2 * (r / 1.8) ** 2)))[..., None]
        arr = arr * (1 - w * 0.85) + np.array(rgb(colour), float) * w * 0.85
    return to_image(arr)


def bauhaus(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[0] if p[0] not in ("#FFF6E9",) else p[0]))
    d = ImageDraw.Draw(img)
    cells = [(0, 0), (500, 0), (0, 500), (500, 500)]
    rnd.shuffle(cells)
    shapes = ["half", "circle", "quarter", "square"]
    for (x, y), shape, colour in zip(cells, shapes, p[1:]):
        c = rgb(colour)
        if shape == "circle":
            d.ellipse([x + 40, y + 40, x + 460, y + 460], fill=c)
        elif shape == "half":
            d.pieslice([x, y, x + 500, y + 1000 if y == 0 else y + 500], 180, 360, fill=c) if y == 0 else d.pieslice([x, y - 250, x + 500, y + 250], 0, 180, fill=c)
        elif shape == "quarter":
            d.pieslice([x - 500, y - 500, x + 500, y + 500], 0, 90, fill=c)
        else:
            d.rectangle([x + 110, y + 110, x + 390, y + 390], fill=c)
    return img


def halftone(p, rnd):
    img = to_image(radial((rnd.randint(300, 700), rnd.randint(300, 700)), p[2], p[0], 700))
    d = ImageDraw.Draw(img)
    cx, cy = rnd.randint(250, 750), rnd.randint(250, 750)
    for gy in range(0, SIZE + 40, 34):
        for gx in range(0, SIZE + 40, 34):
            dist = math.hypot(gx - cx, gy - cy) / 700
            r = max(0.0, 15 * (1 - dist))
            if r > 0.8:
                d.ellipse([gx - r, gy - r, gx + r, gy + r], fill=rgb(p[3]))
    return img


def sun(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[0]))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 620, SIZE, SIZE], fill=rgb(p[4]))
    d.ellipse([280, 250, 720, 690], fill=rgb(p[1]))
    d.rectangle([0, 600, SIZE, SIZE], fill=rgb(p[4]))
    d.pieslice([140, 560, 860, 1280], 180, 360, fill=rgb(p[2]))
    d.rectangle([0, 900, SIZE, SIZE], fill=rgb(p[3]))
    return img


def fog(p, rnd):
    img = to_image(vertical(p[4], p[2]))
    layer = Image.new("RGB", img.size, rgb(p[0]))
    mask = Image.new("L", img.size, 0)
    md = ImageDraw.Draw(mask)
    for i in range(7):
        y = 250 + i * 110 + rnd.randint(-20, 20)
        md.rectangle([-50, y, SIZE + 50, y + 40 + i * 6], fill=60 + i * 22)
    mask = mask.filter(ImageFilter.GaussianBlur(28))
    img.paste(layer, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.ellipse([610, 160, 760, 310], fill=rgb(p[4]))
    return img


def moon(p, rnd):
    img = to_image(radial((500, 300), p[2], p[0], 900))
    d = ImageDraw.Draw(img)
    for _ in range(140):
        x, y = rnd.randint(0, SIZE), rnd.randint(0, 620)
        s = rnd.choice([1, 1, 1, 2, 2, 3])
        d.ellipse([x, y, x + s, y + s], fill=rgb(p[4]))
    d.ellipse([560, 170, 760, 370], fill=rgb(p[3]))
    d.ellipse([610, 150, 800, 340], fill=rgb(p[1]) if False else tuple(int(c * 0.9) for c in rgb(p[2])))
    d.rectangle([0, 700, SIZE, SIZE], fill=rgb(p[0]))
    # A window with a warm light: someone is still up.
    d.rectangle([180, 560, 420, 1000], fill=rgb(p[1]))
    d.rectangle([220, 610, 380, 760], fill=rgb(p[3]))
    d.line([300, 610, 300, 760], fill=rgb(p[1]), width=8)
    d.line([220, 685, 380, 685], fill=rgb(p[1]), width=8)
    return img


def retro(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[4]))
    d = ImageDraw.Draw(img)
    for i, colour in enumerate(p[:4][::-1]):
        off = i * 70
        d.rounded_rectangle([-200 + off, 520 + off, 1200, 600 + off], radius=40, fill=rgb(colour))
    d.ellipse([560, 150, 860, 450], fill=rgb(p[3]))
    return img


def rings(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[0]))
    d = ImageDraw.Draw(img)
    cx, cy = rnd.randint(380, 620), rnd.randint(380, 620)
    for i, r in enumerate(range(620, 40, -58)):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgb(p[1 + i % (len(p) - 1)]))
    return img


def type_rings(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[0]))
    d = ImageDraw.Draw(img)
    for i, r in enumerate(range(360, 60, -50)):
        d.ellipse([560 - r, 420 - r, 560 + r, 420 + r], outline=rgb(p[2] if i % 2 == 0 else p[4]), width=14)
    return img


def grid(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[0]))
    d = ImageDraw.Draw(img)
    for i in range(0, SIZE, 50):
        d.line([i, 0, i, SIZE], fill=rgb(p[1]), width=2)
        d.line([0, i, SIZE, i], fill=rgb(p[1]), width=2)
    pts = [(rnd.randint(2, 18) * 50, rnd.randint(2, 18) * 50) for _ in range(7)]
    d.line(pts, fill=rgb(p[2]), width=6, joint="curve")
    for x, y in pts:
        d.ellipse([x - 14, y - 14, x + 14, y + 14], fill=rgb(p[3]))
    return img


def line(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[0]))
    d = ImageDraw.Draw(img)
    pts = []
    for x in range(120, 890, 6):
        t = (x - 120) / 770
        pts.append((x, 520 - 120 * math.sin(t * math.pi * 2.4) * math.exp(-t * 1.2)))
    d.line(pts, fill=rgb(p[3]), width=5)
    d.ellipse([110, 505, 140, 535], fill=rgb(p[4]))
    return img


def stripes(p, rnd):
    img = Image.new("RGB", (SIZE * 2, SIZE * 2), rgb(p[0]))
    d = ImageDraw.Draw(img)
    for i, x in enumerate(range(-SIZE, SIZE * 3, 90)):
        d.rectangle([x, 0, x + 46, SIZE * 2], fill=rgb(p[1 + i % (len(p) - 1)]))
    img = img.rotate(32, resample=Image.BICUBIC).crop((SIZE // 2, SIZE // 2, SIZE // 2 + SIZE, SIZE // 2 + SIZE))
    return img


def type_stripes(p, rnd):
    img = Image.new("RGB", (SIZE, SIZE), rgb(p[3]))
    d = ImageDraw.Draw(img)
    for i in range(6):
        d.rectangle([0, 520 + i * 80, SIZE, 560 + i * 80], fill=rgb(p[1] if i % 2 == 0 else p[2]))
    return img


def mountains(p, rnd):
    img = to_image(vertical(p[0], p[2]))
    d = ImageDraw.Draw(img)
    # An aurora ribbon.
    glow = Image.new("RGB", img.size)
    gd = ImageDraw.Draw(glow)
    pts = [(x, 260 + 70 * math.sin(x / 160)) for x in range(-20, SIZE + 40, 20)]
    gd.line(pts, fill=(120, 230, 200), width=60)
    glow = glow.filter(ImageFilter.GaussianBlur(40))
    img = Image.fromarray(np.clip(np.asarray(img, float) + np.asarray(glow, float) * 0.7, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for i, colour in enumerate([p[3], p[2], p[1]]):
        base = 560 + i * 120
        peaks = [(0, base)]
        x = 0
        while x < SIZE:
            x += rnd.randint(90, 180)
            peaks.append((x, base - rnd.randint(60, 220)))
            x += rnd.randint(90, 180)
            peaks.append((x, base))
        d.polygon(peaks + [(SIZE, SIZE), (0, SIZE)], fill=rgb(colour))
    return img


STYLES = {
    "waves": waves, "lanterns": lanterns, "sunset": sunset, "chrome": chrome, "hills": hills, "blobs": blobs,
    "bauhaus": bauhaus, "halftone": halftone, "sun": sun, "fog": fog, "moon": moon, "retro": retro, "rings": rings,
    "type-rings": type_rings, "grid": grid, "line": line, "stripes": stripes, "type-stripes": type_stripes,
    "mountains": mountains,
}

# Lettering: (style) -> where the artist and title go. Most covers have none, as most records' don't.
LETTERED = {"type-rings", "type-stripes", "line", "retro", "grid", "chrome", "blobs"}


def letter(img, p, artist, album, style):
    d = ImageDraw.Draw(img)
    if style == "type-rings":
        d.text((80, 760), album.upper(), font=font(92, "Black") if os.path.exists("/usr/share/fonts/opentype/inter/InterDisplay-Black.otf") else font(92), fill=rgb(p[3]))
        d.text((84, 870), artist.upper(), font=font(40, "Medium"), fill=rgb(p[4]))
    elif style == "type-stripes":
        d.text((70, 90), album.upper(), font=font(130, "Black") if os.path.exists("/usr/share/fonts/opentype/inter/InterDisplay-Black.otf") else font(130), fill=rgb(p[0]))
        d.text((76, 240), artist, font=font(48, "Medium"), fill=rgb(p[0]))
    elif style == "line":
        d.text((120, 760), album, font=font(54, "Light"), fill=rgb(p[4]))
        d.text((120, 830), artist.upper(), font=font(30, "Medium"), fill=rgb(p[3]))
    elif style == "retro":
        d.text((80, 90), album, font=font(76, "Bold"), fill=rgb(p[0]))
        d.text((84, 185), artist, font=font(40, "Medium"), fill=rgb(p[1]))
    elif style == "grid":
        d.text((60, 870), album.upper(), font=font(64, "Bold"), fill=rgb(p[4]))
    elif style == "blobs":
        f = font(110, "Light")
        w = d.textlength(album.lower(), font=f)
        d.text(((SIZE - w) / 2, 430), album.lower(), font=f, fill=rgb(p[-1]))
    elif style == "chrome":
        w = d.textlength(album.upper(), font=font(70, "Bold"))
        d.text(((SIZE - w) / 2, 880), album.upper(), font=font(70, "Bold"), fill=rgb(p[3]))


def cover(artist, album, style, palette):
    rnd = rng_for(artist + "/" + album)
    p = PALETTES[palette]
    img = STYLES[style](p, rnd)
    if style in LETTERED:
        letter(img, p, artist, album, style)
    return finish(img, rnd, grain=9 if style not in ("line",) else 5)


def artist_picture(artist, palette, size=600):
    """
    The artist's picture: stage lights out of focus in the artist's colours, like a crop of a concert photo. Nobody
    real, and no face: the people here are made up.
    """
    rnd = rng_for("artist/" + artist)
    p = PALETTES[palette]
    arr = radial((rnd.randint(250, 750), rnd.randint(150, 450)), p[2], p[0], 1000)
    img = to_image(arr)
    lights = Image.new("RGB", img.size)
    d = ImageDraw.Draw(lights)
    for _ in range(26):
        x, y, r = rnd.randint(-50, 1050), rnd.randint(-50, 1050), rnd.randint(30, 150)
        d.ellipse([x - r, y - r, x + r, y + r], fill=rgb(rnd.choice(p[2:])))
    lights = lights.filter(ImageFilter.GaussianBlur(26))
    img = Image.fromarray(np.clip(np.asarray(img, float) * 0.75 + np.asarray(lights, float) * 0.6, 0, 255).astype(np.uint8))
    # A haze of smoke across the light.
    haze = Image.new("L", img.size, 0)
    hd = ImageDraw.Draw(haze)
    for _ in range(5):
        y = rnd.randint(200, 800)
        hd.ellipse([-200, y - 120, 1200, y + 120], fill=rnd.randint(30, 70))
    haze = haze.filter(ImageFilter.GaussianBlur(60))
    img.paste(Image.new("RGB", img.size, rgb(p[-1])), (0, 0), haze)
    img = finish(img, rnd, grain=11, vignette=0.4)
    return img.resize((size, size), Image.LANCZOS)
