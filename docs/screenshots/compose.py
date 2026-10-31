#!/usr/bin/env python3
"""The README's pictures from the showcase screenshots (issue #741): each screen in a phone, and the three-phone
picture at the top.

    python3 docs/screenshots/compose.py SHOTS_FOLDER docs/screenshots

SHOTS_FOLDER holds the full-size screenshots the showcase workflow takes (the showcase-shots branch). Needs Pillow.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

# The README's grid, in order: (screenshot, file name).
SINGLES = [
    ("home", "home"),
    ("now-playing", "now-playing"),
    ("search", "search"),
    ("flow", "flow"),
    ("listening", "listening"),
    ("listening-clock", "listening-clock"),
    ("album", "album"),
    ("lyrics", "lyrics"),
    ("library-grid", "library"),
    ("style", "style"),
]
HERO = ["listening", "home", "now-playing"]  # left, middle (in front), right


def rounded_mask(size, radius):
    big = Image.new("L", (size[0] * 4, size[1] * 4), 0)
    ImageDraw.Draw(big).rounded_rectangle([0, 0, big.width - 1, big.height - 1], radius=radius * 4, fill=255)
    return big.resize(size, Image.LANCZOS)


def phone(shot, width):
    """[shot] in a slim black phone with rounded corners and a hairline edge, [width] px wide, on transparent."""
    bezel = round(width * 0.028)
    screen_w = width - 2 * bezel
    screen_h = round(shot.height * screen_w / shot.width)
    height = screen_h + 2 * bezel
    radius = round(width * 0.11)
    body = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    edge = Image.new("RGBA", (width, height), (52, 58, 66, 255))
    body.paste(edge, (0, 0), rounded_mask((width, height), radius))
    inner = Image.new("RGBA", (width - 2, height - 2), (8, 9, 11, 255))
    body.paste(inner, (1, 1), rounded_mask(inner.size, radius - 1))
    screen = shot.convert("RGB").resize((screen_w, screen_h), Image.LANCZOS)
    body.paste(screen, (bezel, bezel), rounded_mask((screen_w, screen_h), radius - bezel))
    return body


def shadowed(img, blur, offset, alpha=150):
    pad = blur * 3
    canvas = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", img.size, (0, 0, 0, alpha))
    canvas.paste(shadow, (pad, pad + offset), img.split()[3])
    canvas = canvas.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(img, (pad, pad))
    return canvas, pad


def hero(shots, out):
    w, h = 2400, 1240
    bg = Image.new("RGB", (w, h))
    px = bg.load()
    # Dark slate, lighter behind the middle phone, with a breath of amber low down: the brand's colours.
    for y in range(h):
        for x in range(0, w, 1):
            dx, dy = (x - w / 2) / (w * 0.55), (y - h * 0.42) / (h * 0.9)
            d = min(1.0, (dx * dx + dy * dy) ** 0.5)
            amber = max(0.0, 1 - (((x - w / 2) / (w * 0.35)) ** 2 + ((y - h * 1.05) / (h * 0.45)) ** 2) ** 0.5) * 0.35
            base = (27 * (1 - d) + 2 * d, 36 * (1 - d) + 3 * d, 48 * (1 - d) + 5 * d)
            px[x, y] = tuple(int(c + (a - c) * amber) for c, a in zip(base, (232, 165, 90)))
    canvas = bg.convert("RGBA")
    sides = [phone(shots[HERO[0]], 560), phone(shots[HERO[2]], 560)]
    middle = phone(shots[HERO[1]], 640)
    for img, x, y in ((sides[0], 300, 170), (sides[1], 2400 - 300 - 560, 170)):
        s, pad = shadowed(img, 28, 20)
        canvas.alpha_composite(s, (x - pad, y - pad))
    s, pad = shadowed(middle, 36, 26, 190)
    canvas.alpha_composite(s, ((w - middle.width) // 2 - pad, 90 - pad))
    # Rounded corners, so it sits well on GitHub's light and dark pages.
    mask = rounded_mask((w, h), 48)
    final = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    final.paste(canvas, (0, 0), mask)
    final.save(os.path.join(out, "hero.png"), optimize=True)


def main(src, out):
    os.makedirs(out, exist_ok=True)
    shots = {os.path.splitext(f)[0]: Image.open(os.path.join(src, f)) for f in os.listdir(src) if f.endswith((".jpg", ".png"))}
    missing = [n for n, _ in SINGLES if n not in shots] + [n for n in HERO if n not in shots]
    if missing:
        print("Missing screenshots:", ", ".join(sorted(set(missing))))
    for name, file in SINGLES:
        if name in shots:
            phone(shots[name], 640).save(os.path.join(out, f"{file}.png"), optimize=True)
    if all(n in shots for n in HERO):
        hero(shots, out)
    print("Written to", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
