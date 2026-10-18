import json, sys
sys.path.insert(0, '/tmp/claude-0/themes')
from palette import theme, SEEDS

ICON = {
 'back': '<path d="M15 5l-7 7 7 7"/>',
 'user': '<circle cx="12" cy="8" r="4"/><path d="M4 20c1.5-4 4.5-6 8-6s6.5 2 8 6"/>',
 'link': '<path d="M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1"/>',
 'sync': '<path d="M20 11a8 8 0 00-14-5l-2 2"/><path d="M4 4v4h4"/><path d="M4 13a8 8 0 0014 5l2-2"/><path d="M20 20v-4h-4"/>',
 'play': '<path d="M8 5v14l11-7z" fill="currentColor" stroke="none"/>',
 'palette': '<path d="M12 3a9 9 0 100 18c1.2 0 1.5-.8 1.5-1.5 0-1.2-1-1.3-1-2.3 0-.8.7-1.2 1.5-1.2H16a5 5 0 005-5c0-4.4-4-8-9-8z"/><circle cx="7.5" cy="11" r="1.2" fill="currentColor"/><circle cx="10" cy="7" r="1.2" fill="currentColor"/><circle cx="14.5" cy="7" r="1.2" fill="currentColor"/>',
 'scrobble': '<path d="M4 18l5-6 4 3 7-9"/>',
 'storage': '<rect x="4" y="5" width="16" height="6" rx="2"/><rect x="4" y="13" width="16" height="6" rx="2"/>',
 'backup': '<path d="M7 18a5 5 0 01-.5-10A6 6 0 0118 9a4 4 0 01-1 9z"/><path d="M12 15V10m-2.5 2.5L12 10l2.5 2.5"/>',
 'chev': '<path d="M9 6l6 6-6 6"/>',
 'home': '<path d="M4 11l8-7 8 7v9h-5v-6H9v6H4z" fill="currentColor" stroke="none"/>',
 'search': '<circle cx="11" cy="11" r="6"/><path d="M20 20l-4.5-4.5"/>',
 'lib': '<rect x="5" y="4" width="14" height="16" rx="2"/><path d="M9 8h6"/>',
 'gear': '<circle cx="12" cy="12" r="3"/><path d="M12 3v3m0 12v3M3 12h3m12 0h3M5.6 5.6l2.1 2.1m8.6 8.6l2.1 2.1M5.6 18.4l2.1-2.1m8.6-8.6l2.1-2.1"/>',
 'filter': '<path d="M5 7h14M8 12h8M11 17h2"/>',
 'heart': '<path d="M12 20s-7-4.4-7-10a4 4 0 017-2.6A4 4 0 0119 10c0 5.6-7 10-7 10z" fill="currentColor" stroke="none"/>',
 'pause': '<path d="M8 5h3v14H8zM13 5h3v14h-3z" fill="currentColor" stroke="none"/>',
 'check': '<path d="M5 12l5 5 9-10"/>',
 'plus': '<path d="M12 5v14M5 12h14"/>',
 'wall': '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M4 16l5-5 4 4 3-3 4 4"/>',
 'eq': '<path d="M6 4v16M12 4v16M18 4v16"/><circle cx="6" cy="14" r="2" fill="currentColor"/><circle cx="12" cy="8" r="2" fill="currentColor"/><circle cx="18" cy="16" r="2" fill="currentColor"/>',
 'shuffle': '<path d="M4 7h3l10 10h3M4 17h3l3-3m4-4l3-3h3"/><path d="M18 5l2 2-2 2M18 15l2 2-2 2"/>',
 'prev': '<path d="M6 5v14M19 5l-10 7 10 7z" fill="currentColor"/>',
 'next': '<path d="M18 5v14M5 5l10 7-10 7z" fill="currentColor"/>',
 'repeat': '<path d="M5 10V8a2 2 0 012-2h11l-2-2m2 2l-2 2M19 14v2a2 2 0 01-2 2H6l2 2m-2-2l2-2"/>',
}
def ic(name, size=22, color='currentColor', sw=2):
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round">{ICON[name]}</svg>'

def vars_(s):
    return ';'.join(f'--{k}:{v}' for k, v in s.items())

CSS = '''
*{box-sizing:border-box;margin:0;padding:0}
body{background:#3a3a40;font-family:Inter,Roboto,"Segoe UI",sans-serif;padding:36px;display:flex;flex-wrap:wrap;gap:36px;align-items:flex-start}
.cap{width:100%;color:#eee;font-size:20px;font-weight:700;margin-bottom:-18px}
.wrap{display:flex;flex-direction:column;gap:10px;align-items:center}
.label{color:#ddd;font-size:14px;font-weight:600}
.phone{width:360px;height:760px;position:relative;overflow:hidden;background:var(--surface);color:var(--onSurface);border-radius:22px;box-shadow:0 10px 30px rgba(0,0,0,.4)}
.small{width:270px;height:560px;border-radius:18px}
.status{height:28px;display:flex;justify-content:space-between;align-items:center;padding:0 18px;font-size:12px;font-weight:600}
.bar{display:flex;align-items:center;gap:12px;padding:10px 12px 12px}
.bar h1{font-size:22px;font-weight:600}
.row{display:flex;align-items:center;gap:16px;padding:12px 20px}
.row .ico{color:var(--onSurfaceVariant);display:flex}
.row .t{flex:1;min-width:0}
.row b{display:block;font-size:16px;font-weight:500}
.row span{display:block;font-size:13px;color:var(--onSurfaceVariant);margin-top:2px}
.hl{background:var(--secondaryContainer);border-radius:18px;margin:2px 10px;padding:12px 10px}
.hl .ico{color:var(--onSecondaryContainer)}
.badge{font-size:11px;font-weight:700;background:var(--primary);color:var(--onPrimary);border-radius:10px;padding:2px 8px}
.sec{font-size:14px;font-weight:600;color:var(--primary);padding:18px 20px 8px}
.seg{display:flex;margin:0 20px;border:1px solid var(--outline);border-radius:22px;overflow:hidden}
.seg div{flex:1;text-align:center;padding:10px 0;font-size:14px;font-weight:500;border-left:1px solid var(--outline);display:flex;gap:6px;justify-content:center;align-items:center}
.seg div:first-child{border-left:none}
.seg .on{background:var(--secondaryContainer);color:var(--onSecondaryContainer)}
.switch{width:52px;height:32px;border-radius:16px;border:2px solid var(--outline);background:var(--containerHighest);position:relative;flex:none}
.switch::after{content:"";position:absolute;left:6px;top:6px;width:16px;height:16px;border-radius:50%;background:var(--outline)}
.switch.on{background:var(--primary);border-color:var(--primary)}
.switch.on::after{left:24px;top:2px;width:24px;height:24px;background:var(--onPrimary)}
.dis{opacity:.45}
.sw{display:grid;grid-template-columns:repeat(4,1fr);gap:14px 6px;padding:4px 16px}
.sw .s{display:flex;flex-direction:column;align-items:center;gap:6px;font-size:11.5px;color:var(--onSurfaceVariant);text-align:center;line-height:1.15}
.dot{width:58px;height:58px;border-radius:50%;overflow:hidden;position:relative;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;outline-offset:3px}
.dot.sel{outline:3px solid var(--primary)}
.dot .a{grid-column:1/3}
.dot .ck{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#fff}
.dot.plus{display:flex;align-items:center;justify-content:center;border:2px dashed var(--outline);color:var(--onSurfaceVariant)}
.note{font-size:12.5px;color:var(--onSurfaceVariant);padding:10px 20px;line-height:1.4}
/* preview / home */
.prev{margin:8px 16px 0;border-radius:20px;background:var(--containerLow);border:1px solid var(--outlineVariant);padding:14px;overflow:hidden}
.hello{font-size:22px;font-weight:700;display:flex;justify-content:space-between;align-items:center}
.chips{display:flex;gap:8px;margin-top:10px}
.chip{font-size:12px;font-weight:600;background:var(--secondaryContainer);color:var(--onSecondaryContainer);padding:6px 11px;border-radius:14px}
.tiles{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:12px}
.tile{display:flex;align-items:center;gap:8px;background:var(--containerHigh);border-radius:8px;overflow:hidden;font-size:12.5px;font-weight:600;height:44px}
.tile i{width:44px;height:44px;display:flex;align-items:center;justify-content:center;flex:none}
.mfy{display:flex;gap:10px;margin-top:14px}
.mfy div{flex:1;height:96px;border-radius:12px;padding:10px;font-weight:700;font-size:15px;line-height:1.1}
.mini{display:flex;align-items:center;gap:10px;background:var(--containerHighest);border-radius:12px;padding:8px;margin-top:14px}
.mini .art{width:38px;height:38px;border-radius:6px;background:linear-gradient(135deg,#6b4fa8,#2b6aa8)}
.mini .t{flex:1;font-size:12.5px;font-weight:600}
.mini .t span{display:block;font-weight:400;color:var(--onSurfaceVariant);font-size:11.5px}
.pb{width:38px;height:38px;border-radius:50%;background:var(--primary);color:var(--onPrimary);display:flex;align-items:center;justify-content:center}
.nav{position:absolute;left:0;right:0;bottom:0;height:70px;background:var(--container);display:flex;justify-content:space-around;align-items:center;font-size:11px;color:var(--onSurfaceVariant)}
.nav div{display:flex;flex-direction:column;align-items:center;gap:3px}
.nav .on span{background:var(--secondaryContainer);color:var(--onSecondaryContainer);border-radius:14px;padding:3px 16px;display:flex}
.home{padding:6px 16px}
/* sheet */
.scrim{position:absolute;inset:0;background:rgba(0,0,0,.42)}
.sheet{position:absolute;left:0;right:0;bottom:0;background:var(--containerLow);border-radius:28px 28px 0 0;padding:10px 20px 22px}
.handle{width:36px;height:4px;border-radius:2px;background:var(--onSurfaceVariant);opacity:.5;margin:4px auto 14px}
.sheet h2{font-size:20px;font-weight:600}
.sheet p{font-size:13px;color:var(--onSurfaceVariant);margin-top:4px}
.hue{height:30px;border-radius:15px;margin-top:18px;position:relative;background:linear-gradient(90deg,#e53935,#fb8c00,#fdd835,#43a047,#00acc1,#1e88e5,#5e35b1,#d81b60,#e53935)}
.hue i{position:absolute;top:-4px;width:38px;height:38px;border-radius:50%;border:4px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.35)}
.picks{display:flex;justify-content:space-between;margin-top:16px}
.picks div{width:34px;height:34px;border-radius:50%}
.picks .sel{outline:3px solid var(--onSurface);outline-offset:2px}
.hex{display:flex;align-items:center;gap:10px;margin-top:16px;border:1px solid var(--outline);border-radius:8px;padding:10px 12px;font-size:14px}
.hex i{width:20px;height:20px;border-radius:4px}
.pair{display:flex;gap:10px;margin-top:16px}
.pair>div{flex:1;min-width:0;border-radius:14px;padding:10px;border:1px solid var(--outlineVariant)}
.pair .mini{gap:8px;padding:6px}
.pair .mini .art{width:30px;height:30px}
.pair .pb{width:30px;height:30px}
.btns{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}
.btn{padding:10px 20px;border-radius:20px;font-size:14px;font-weight:600}
.btn.f{background:var(--primary);color:var(--onPrimary)}
.btn.t{color:var(--primary)}
/* now playing */
.np{background:linear-gradient(180deg,#6d4c9e 0%,#3b2d63 45%,#151022 100%);color:#fff}
.np .cover{margin:46px 28px 0;height:300px;border-radius:10px;background:linear-gradient(135deg,#8a63d2,#2e5fa8 60%,#1b2a52);box-shadow:0 16px 40px rgba(0,0,0,.45)}
.np .tt{margin:34px 28px 0;font-size:22px;font-weight:700}
.np .ar{margin:4px 28px 0;font-size:15px;color:rgba(255,255,255,.65)}
.np .seek{margin:22px 28px 0;height:4px;border-radius:2px;background:rgba(255,255,255,.25)}
.np .seek div{width:38%;height:4px;border-radius:2px;background:#fff}
.np .ctl{display:flex;justify-content:space-between;align-items:center;margin:22px 24px 0}
.np .big{width:66px;height:66px;border-radius:50%;background:var(--npAccent);color:var(--npOn);display:flex;align-items:center;justify-content:center}
'''

def page(body, width=None):
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body{f" style=width:{width}px" if width else ""}>{body}</body></html>'

def status():
    return '<div class="status"><span>9:41</span><span>▾ ▴ ▮</span></div>'

def swatch(name, dark, selected=False):
    if name == 'Your colour+':
        return f'<div class="s"><div class="dot plus">{ic("plus",26)}</div>Your colour</div>'
    s = theme(name, dark)
    sub = ''
    if name == 'Wallpaper':
        sub = '<br><span style="font-size:10px;opacity:.8">Android 12+</span>'
    ck = f'<div class="ck">{ic("check",26,"#fff",3)}</div>' if selected else ''
    return (f'<div class="s"><div class="dot{" sel" if selected else ""}"><div class="a" style="background:{s["primary"]}"></div>'
            f'<div style="background:{s["secondary"]}"></div><div style="background:{s["tertiary"]}"></div>{ck}</div>{name}{sub}</div>')

def preview(s_name, dark, compact=False):
    s = theme(s_name, dark)
    return f'''<div class="prev">
  <div class="hello">Good evening <span style="color:var(--primary);display:flex">{ic("filter",20)}</span></div>
  <div class="chips"><div class="chip">Only downloaded</div><div class="chip">2 users</div></div>
  <div class="tiles"><div class="tile"><i style="background:linear-gradient(135deg,var(--primary),var(--tertiary));color:var(--onPrimary)">{ic("heart",18)}</i>Liked songs</div>
  <div class="tile"><i style="background:var(--tertiary)"></i>Daily mix</div></div>
  <div class="mini"><div class="art"></div><div class="t">Midnight City<span>M83</span></div><div class="pb">{ic("pause",18)}</div></div>
</div>'''

def themes_page(name, dark, black=False, mode='Auto'):
    s = theme(name, dark, black)
    modes = ''.join(f'<div class="{"on" if m == mode else ""}">{ic("check",16) if m == mode else ""}{m}</div>' for m in ['Auto', 'Light', 'Dark'])
    names = ['Musix', 'Wallpaper', 'Ocean', 'Forest', 'Rose', 'Sunset', 'Mono', 'Your colour+']
    sw = ''.join(swatch(n, dark, n == name) for n in names)
    return f'''<div class="phone" style="{vars_(s)}">{status()}
<div class="bar">{ic("back",24)}<h1>Themes</h1></div>
{preview(name, dark)}
<div class="sec">Light or dark</div>
<div class="seg">{modes}</div>
<div class="row{"" if dark else " dis"}" style="padding-top:14px"><div class="t"><b>Pure black</b><span>Black background when dark. Saves battery on OLED screens.</span></div><div class="switch{" on" if black else ""}"></div></div>
<div class="sec" style="padding-top:6px">Colours</div>
<div class="sw">{sw}</div>
<div class="note">Album, artist and now playing pages keep their colours from the cover.</div>
</div>'''

def settings_hub(name, dark):
    s = theme(name, dark)
    rows = [('user', 'Users', '2 users on this phone · switch with one tap', False),
            ('link', 'Connections', 'Navidrome · AudioMuse', False),
            ('sync', 'Library sync', 'Last synced 5 minutes ago', False),
            ('eq', 'Playback', 'ReplayGain track · Wi-Fi original', False),
            ('palette', 'Themes', 'Musix colours · follows the phone (auto)', True),
            ('scrobble', 'Scrobbling', 'Navidrome · Last.fm', False),
            ('storage', 'Storage', 'Songs up to 2 GB · images 500 MB', False),
            ('backup', 'Backup', 'Save or restore your settings, vibes and history', False)]
    body = ''.join(
        f'<div class="row{" hl" if h else ""}"><div class="ico">{ic(i)}</div><div class="t"><b>{t}{" <span class=badge style=display:inline;margin-left:6px>New</span>" if h else ""}</b><span>{sub}</span></div></div>'
        for i, t, sub, h in rows)
    return f'''<div class="phone" style="{vars_(s)}">{status()}<div class="bar"><h1 style="font-size:28px;padding:6px 8px">Settings</h1></div>{body}
<div class="nav"><div>{ic("home")}Home</div><div>{ic("search")}Search</div><div>{ic("lib")}Your Library</div><div class="on"><span>{ic("gear")}</span>Settings</div></div></div>'''

def your_colour(dark):
    base = theme('Your colour', dark)
    picks = ['#E53935', '#F4511E', '#F6BF26', '#33B679', '#0E9AA7', '#3F51B5', '#8E24AA', '#D81B60']
    pk = ''.join(f'<div style="background:{c}" class="{"sel" if c == "#0E9AA7" else ""}"></div>' for c in picks)
    light, darkS = theme('Your colour', False), theme('Your colour', True)
    def mini(s, label):
        return f'''<div style="{vars_(s)};background:var(--surface);color:var(--onSurface)"><div style="font-size:11px;font-weight:600;color:var(--onSurfaceVariant)">{label}</div>
<div class="mini" style="margin-top:6px"><div class="art"></div><div class="t">Midnight City</div><div class="pb">{ic("play",14)}</div></div>
<div class="chips" style="margin-top:8px"><div class="chip">Chill</div><div class="chip">Focus</div></div></div>'''
    return f'''<div class="phone" style="{vars_(base)}">{status()}
<div class="bar">{ic("back",24)}<h1>Themes</h1></div>
{preview('Your colour', dark)}
<div class="scrim"></div>
<div class="sheet"><div class="handle"></div><h2>Your colour</h2><p>Pick one colour. Musix makes the rest of the theme from it, in light and dark.</p>
<div class="hue"><i style="left:52%;background:#0E9AA7"></i></div>
<div class="picks">{pk}</div>
<div class="hex"><i style="background:#0E9AA7"></i>#0E9AA7</div>
<div class="pair">{mini(light, "Light")}{mini(darkS, "Dark")}</div>
<div class="btns"><div class="btn t">Cancel</div><div class="btn f">Use this colour</div></div>
</div></div>'''

def home(name, dark, black=False, cls='phone small'):
    s = theme(name, dark, black)
    return f'''<div class="{cls}" style="{vars_(s)}">{status()}<div class="home">
<div class="hello">Good evening <span style="color:var(--primary);display:flex">{ic("filter",20)}</span></div>
<div class="tiles"><div class="tile"><i style="background:linear-gradient(135deg,var(--primary),var(--tertiary));color:var(--onPrimary)">{ic("heart",18)}</i>Liked songs</div>
<div class="tile"><i style="background:var(--secondary)"></i>Daily mix</div></div>
<div style="font-size:16px;font-weight:700;margin-top:16px">Made for you</div>
<div class="mfy"><div style="background:var(--primaryContainer);color:var(--onPrimaryContainer)">Your sound</div><div style="background:var(--secondaryContainer);color:var(--onSecondaryContainer)">On repeat</div></div>
<div class="mini"><div class="art"></div><div class="t">Midnight City<span>M83</span></div><div class="pb">{ic("pause",18)}</div></div>
</div><div class="nav" style="height:58px"><div class="on"><span>{ic("home",20)}</span>Home</div><div>{ic("search",20)}Search</div><div>{ic("lib",20)}Library</div><div>{ic("gear",20)}Settings</div></div></div>'''

def now_playing(name, dark):
    s = theme(name, dark)
    acc = theme(name, True)['primary']
    on = theme(name, True)['onPrimary']
    return f'''<div class="phone small np" style="{vars_(s)};--npAccent:{acc};--npOn:{on};height:560px">{status()}
<div class="cover" style="height:200px;margin:30px 22px 0"></div><div class="tt" style="margin:24px 22px 0">Midnight City</div><div class="ar" style="margin:4px 22px 0">M83</div>
<div class="seek" style="margin:18px 22px 0"><div></div></div>
<div class="ctl" style="margin:18px 18px 0">{ic("shuffle",22,"#fff")}{ic("prev",24,"#fff")}<div class="big">{ic("play",30)}</div>{ic("next",24,"#fff")}{ic("repeat",22,"#fff")}</div></div>'''

pages = {}
pages['1-where-and-themes-page'] = page(
    '<div class="cap">Settings → Themes: where it lives, and the page in light (Auto) and dark with Pure black</div>' +
    f'<div class="wrap">{settings_hub("Musix", False)}<div class="label">Settings: new "Themes" row</div></div>' +
    f'<div class="wrap">{themes_page("Musix", False, mode="Auto")}<div class="label">Themes, light (phone is light)</div></div>' +
    f'<div class="wrap">{themes_page("Ocean", True, black=True, mode="Dark")}<div class="label">Themes, Ocean picked, Dark + Pure black</div></div>', 1260)
pages['2-your-colour'] = page(
    '<div class="cap">"Your colour": one seed colour, Musix builds the light and dark theme from it</div>' +
    f'<div class="wrap">{your_colour(False)}<div class="label">Picker, light</div></div>' +
    f'<div class="wrap">{your_colour(True)}<div class="label">Picker, dark</div></div>', 860)
row = [('Musix', True, False, 'Musix · dark'), ('Musix', False, False, 'Musix · light'), ('Wallpaper', False, False, 'Wallpaper (example) · light'),
       ('Ocean', True, False, 'Ocean · dark'), ('Forest', False, False, 'Forest · light'), ('Rose', True, False, 'Rose · dark'),
       ('Sunset', True, True, 'Sunset · Pure black'), ('Mono', False, False, 'Mono · light')]
pages['3-presets-on-home'] = page(
    '<div class="cap">Presets on Home, in light and dark</div>' +
    ''.join(f'<div class="wrap">{home(n, d, b)}<div class="label">{l}</div></div>' for n, d, b, l in row), 1260)
pages['4-cover-pages-stay'] = page(
    '<div class="cap">Now playing, album and artist pages keep their cover colours; only buttons follow the theme</div>' +
    ''.join(f'<div class="wrap">{now_playing(n, d)}<div class="label">{n}</div></div>' for n, d in [('Musix', True), ('Ocean', False), ('Forest', True), ('Rose', False)]), 1260)

for k, v in pages.items():
    open(f'/tmp/claude-0/themes/{k}.html', 'w').write(v)
print('ok')
