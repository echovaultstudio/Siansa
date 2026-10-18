"""Material-3-style tonal schemes from one seed colour (an approximation of HCT: tone = CIELAB L*,
hue and chroma from LCh), for mockups only. The app itself would use Google's material-color-utilities."""
import math, json

def srgb_to_lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

def lin_to_srgb(c):
    c = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
    return c * 255

def hex_to_rgb(h):
    h = h.lstrip('#')
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]

Xn, Yn, Zn = 0.95047, 1.0, 1.08883

def f(t):
    return t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116

def finv(t):
    return t ** 3 if t ** 3 > 216 / 24389 else (116 * t - 16) / (24389 / 27)

def rgb_to_lch(h):
    r, g, b = [srgb_to_lin(c) for c in hex_to_rgb(h)]
    X = 0.4124 * r + 0.3576 * g + 0.1805 * b
    Y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    Z = 0.0193 * r + 0.1192 * g + 0.9505 * b
    L = 116 * f(Y / Yn) - 16
    a = 500 * (f(X / Xn) - f(Y / Yn))
    bb = 200 * (f(Y / Yn) - f(Z / Zn))
    return L, math.hypot(a, bb), math.degrees(math.atan2(bb, a)) % 360

def lch_to_rgb(L, C, H):
    a = C * math.cos(math.radians(H)); b = C * math.sin(math.radians(H))
    fy = (L + 16) / 116; fx = fy + a / 500; fz = fy - b / 200
    X, Y, Z = Xn * finv(fx), Yn * finv(fy), Zn * finv(fz)
    r = 3.2406 * X - 1.5372 * Y - 0.4986 * Z
    g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z
    bl = 0.0557 * X - 0.2040 * Y + 1.0570 * Z
    return [lin_to_srgb(v) if v > 0 else -1 for v in (r, g, bl)]

def tone(H, C, T):
    """The colour of hue H at tone T, with as much of chroma C as fits in sRGB."""
    lo, hi = 0.0, C
    best = lch_to_rgb(T, 0, H)
    for _ in range(30):
        mid = (lo + hi) / 2
        rgb = lch_to_rgb(T, mid, H)
        if all(-0.5 <= v <= 255.5 for v in rgb):
            lo, best = mid, rgb
        else:
            hi = mid
    return '#%02X%02X%02X' % tuple(max(0, min(255, round(v))) for v in best)

def scheme(seed, dark, black=False, mono=False):
    _, c, h = rgb_to_lch(seed)
    pc = 0 if mono else max(c, 48)
    sc = 0 if mono else 16
    tc = 0 if mono else 28
    nc = 0 if mono else 5
    nvc = 0 if mono else 9
    P = lambda t: tone(h, pc, t)
    S = lambda t: tone(h, sc, t)
    T = lambda t: tone((h + 60) % 360, tc, t)
    N = lambda t: tone(h, nc, t)
    NV = lambda t: tone(h, nvc, t)
    if not dark:
        s = dict(primary=P(40), onPrimary=P(100), primaryContainer=P(90), onPrimaryContainer=P(10),
                 secondary=S(40), secondaryContainer=S(90), onSecondaryContainer=S(10), tertiary=T(40),
                 surface=N(98), onSurface=N(10), containerLow=N(96), container=N(94), containerHigh=N(92),
                 containerHighest=N(90), onSurfaceVariant=NV(30), outline=NV(50), outlineVariant=NV(80))
    else:
        s = dict(primary=P(80), onPrimary=P(20), primaryContainer=P(30), onPrimaryContainer=P(90),
                 secondary=S(80), secondaryContainer=S(30), onSecondaryContainer=S(90), tertiary=T(80),
                 surface=N(6), onSurface=N(90), containerLow=N(10), container=N(12), containerHigh=N(17),
                 containerHighest=N(22), onSurfaceVariant=NV(80), outline=NV(60), outlineVariant=NV(30))
        if black:
            s.update(surface='#000000', containerLow=N(4), container=N(8), containerHigh=N(12), containerHighest=N(17))
    return s

# Musix's own colours (ui/Theme.kt), light and dark, plus pure black.
MUSIX_DARK = dict(primary='#FFB547', onPrimary='#2A1700', primaryContainer='#3D2A55', onPrimaryContainer='#F1E8FF',
                  secondary='#B69CFF', secondaryContainer='#2E2450', onSecondaryContainer='#E6DDFF', tertiary='#FF8A7A',
                  surface='#110D1C', onSurface='#EDE7F6', containerLow='#161124', container='#1C1630',
                  containerHigh='#272040', containerHighest='#322A4D', onSurfaceVariant='#B9B0CC', outline='#6E6585',
                  outlineVariant='#3A3250')
MUSIX_LIGHT = dict(primary='#B86A00', onPrimary='#FFFFFF', primaryContainer='#EDE4FF', onPrimaryContainer='#22164A',
                   secondary='#5B3FD0', secondaryContainer='#E8E0FF', onSecondaryContainer='#22164A', tertiary='#9C4235',
                   surface='#FBF8FF', onSurface='#1C1B1F', containerLow='#F5F1FB', container='#F2EDF9',
                   containerHigh='#ECE6F5', containerHighest='#E6E0EE', onSurfaceVariant='#49454F', outline='#79747E',
                   outlineVariant='#CAC4D0')

SEEDS = {
    'Wallpaper': '#6F8F4E',   # an example wallpaper (a green landscape)
    'Ocean': '#1F6FD1',
    'Forest': '#2E7D4F',
    'Rose': '#C2416E',
    'Sunset': '#E8692A',
    'Mono': '#777777',
    'Your colour': '#0E9AA7',
}

def theme(name, dark, black=False):
    if name == 'Musix':
        s = dict(MUSIX_DARK if dark else MUSIX_LIGHT)
        if dark and black:
            s.update(surface='#000000', containerLow='#0B0814', container='#120E1E', containerHigh='#1C1630', containerHighest='#272040')
        return s
    return scheme(SEEDS[name], dark, black, mono=(name == 'Mono'))

if __name__ == '__main__':
    out = {}
    for n in ['Musix'] + list(SEEDS):
        out[n] = {'light': theme(n, False), 'dark': theme(n, True), 'black': theme(n, True, True)}
    print(json.dumps(out, indent=1))
