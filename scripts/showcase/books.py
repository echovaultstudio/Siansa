#!/usr/bin/env python3
"""Made-up audiobooks and podcasts for the showcase's Audiobookshelf (issue #741): a folder per book (Author/Title)
with an .m4b that has chapters, a cover, the narrator and a description, and a folder per podcast with a cover and its
episodes. Nobody here is real.

    python3 scripts/showcase/books.py BOOKS_FOLDER PODCASTS_FOLDER

The audio is silence, which gives each book and episode its length and takes almost no room. Needs ffmpeg and Pillow.
"""
import datetime
import json
import os
import subprocess
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from covers import PALETTES, cover, font, rgb  # noqa: E402

# (author, title, narrator, year, genre, publisher, palette, style, description, [(chapter, minutes)])
BOOKS = [
    ("Elena Marsh", "The Lighthouse at Merrow Point", "Daniel Hale", 2023, "Mystery", "Harbour Lane Audio", "harbour",
     "waves",
     "When the keeper of a lighthouse on a far island goes missing, his granddaughter crosses on the last ferry of "
     "the season. In his logbook she finds ships that never sailed, and a light that was seen on nights the lamp was "
     "out.",
     [("The Last Ferry", 38), ("The Keeper's Logbook", 44), ("Fog Signal", 41), ("Charts and Tides", 52),
      ("The Lamp Room", 47), ("Nobody Sails in October", 49), ("What the Sea Kept", 55), ("Merrow Point", 36)]),
    ("Tomás Reyes", "Small Hours in Lisbon", "Inês Carvalho", 2024, "Travel", "Northbound Press", "golden", "sun",
     "A year of walking Lisbon between midnight and dawn: the bakery that opens at four, the last tram up the hill, "
     "and the people who keep a city running while it sleeps.",
     [("Before the Trams", 31), ("Four O'Clock Bread", 35), ("The River at Night", 40), ("Night Shift", 33),
      ("Rooftops", 29), ("The First Tram", 37)]),
    ("Hana Okoye", "Twelve Quiet Rooms", "Hana Okoye", 2022, "Essays", "Fieldnote Books", "forest", "hills",
     "Twelve short essays on finding stillness in loud places, from hospital corridors at night to the back row of a "
     "concert hall, read by the author.",
     [("The Waiting Room", 22), ("A Library in August", 26), ("The Back Row", 24), ("Night Corridor", 28),
      ("The Empty Pool", 21), ("A Station Before Dawn", 25)]),
    ("Petra Lind", "Salt Roads North", "Owen Price", 2021, "Adventure", "Northbound Press", "glacier", "mountains",
     "Two sisters follow an old salt road from the sea to the mountains: a thousand kilometres on foot, one summer, "
     "and the things they never said.",
     [("The Coast", 46), ("Marsh Country", 51), ("The Long Valley", 48), ("High Pastures", 53), ("The Pass", 44),
      ("Salt", 39)]),
]

# (title, host, palette, style, description, [(episode, date, minutes, about)])
PODCASTS = [
    ("The Long Commute", "Sam Arden", "night", "sunset",
     "Stories for the drive home: one person, one journey, every Tuesday.",
     [("The Night Bus Driver", "2026-10-27", 34, "Twenty years on the N7, and what you see from the front seat at 3 am."),
      ("Ferry Crossings", "2026-10-20", 29, "An islander who has crossed the same strait twice a day since 1998."),
      ("The Ring Road", "2026-10-13", 41, "Two hours a day in traffic, and the audiobooks that got her through."),
      ("Last Train to the Coast", "2026-10-06", 37, "The guard on the last train of the night, and who rides it."),
      ("Cycling in the Rain", "2026-09-29", 26, "Twelve kilometres each way, all year, and why he'd never stop."),
      ("Walking Home", "2026-09-22", 31, "A nurse's walk home after a night shift, as the city wakes up.")]),
    ("Night Shift Radio", "Mara Ellis", "midnight", "moon",
     "Music and talk for people who are awake while everyone else is asleep.",
     [("Bakers, Bus Drivers and Us", "2026-10-30", 52, "Calls from people starting work at four in the morning."),
      ("The Quietest Hour", "2026-10-23", 47, "Why 4 am feels the way it does, with a sleep researcher."),
      ("Radio for Insomniacs", "2026-10-16", 55, "Your songs for the hours you can't sleep."),
      ("Lighthouse Keepers", "2026-10-09", 44, "The last people who kept the lights on, all night, by hand.")]),
    ("Field Recordings", "Jonah Pike", "fog", "fog",
     "Ten minutes of somewhere else: harbours, markets, forests and stations, recorded where they are.",
     [("A Harbour at Dawn", "2026-10-28", 12, "Gulls, ropes and an engine starting, recorded on the quay."),
      ("Night Market", "2026-10-14", 11, "Sizzling pans and voices, in a market that opens at dusk."),
      ("Pine Forest After Rain", "2026-09-30", 14, "Drops from the branches, and a woodpecker far off."),
      ("The Station Before Dawn", "2026-09-16", 10, "Announcements, footsteps and the first train in.")]),
]


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def lettered(art, title, byline, palette):
    """A cover: the picture with the title and the byline on a band of the palette's darkest colour."""
    colours = sorted((rgb(c) for c in PALETTES[palette]), key=lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2])
    dark, light = colours[0], colours[-1]
    # A light palette's darkest colour is still light: the band goes nearly black, so the words stay clear.
    if sum(dark) > 240:
        dark = tuple(int(c * 0.25) for c in dark)
    img = art.convert("RGB")
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle([0, 640, 1000, 1000], fill=dark + (235,))
    size = 92 if len(title) < 22 else 74
    words, lines, line = title.split(), [], ""
    for w in words:
        test = (line + " " + w).strip()
        if d.textlength(test, font=font(size)) > 860 and line:
            lines.append(line)
            line = w
        else:
            line = test
    lines.append(line)
    y = 690
    for text in lines[:2]:
        d.text((70, y), text, font=font(size), fill=light)
        y += size + 14
    d.text((72, y + 18), byline.upper(), font=font(36, "Medium"), fill=light + (190,))
    return img


def book(folder, author, title, narrator, year, genre, publisher, palette, style, about, chapters):
    path = os.path.join(folder, author, title)
    os.makedirs(path, exist_ok=True)
    lettered(cover(author, title, style, palette), title, author, palette).save(os.path.join(path, "cover.jpg"), quality=90)
    marks, start = [], 0
    for name, minutes in chapters:
        end = start + minutes * 60
        marks.append((name, start, end))
        start = end
    meta = os.path.join(path, "chapters.txt")
    with open(meta, "w") as f:
        f.write(";FFMETADATA1\n")
        for key, value in (("title", title), ("album", title), ("artist", author), ("album_artist", author),
                           ("composer", narrator), ("genre", genre), ("date", str(year)), ("comment", about)):
            f.write(f"{key}={value}\n")
        for name, a, b in marks:
            f.write(f"\n[CHAPTER]\nTIMEBASE=1/1000\nSTART={a * 1000}\nEND={b * 1000}\ntitle={name}\n")
    out = os.path.join(path, f"{title}.m4b")
    if not os.path.exists(out):
        run("ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-t", str(start), "-i", "anullsrc=r=16000:cl=mono",
            "-i", meta, "-map", "0:a", "-map_metadata", "1", "-map_chapters", "1", "-c:a", "aac", "-b:a", "12k",
            "-f", "mp4", out)
    os.remove(meta)
    open(os.path.join(path, "desc.txt"), "w").write(about + "\n")
    open(os.path.join(path, "reader.txt"), "w").write(narrator + "\n")
    json.dump({
        "tags": [], "title": title, "subtitle": None, "authors": [author], "narrators": [narrator], "series": [],
        "genres": [genre], "publishedYear": str(year), "publishedDate": None, "publisher": publisher,
        "description": about, "isbn": None, "asin": None, "language": "English", "explicit": False, "abridged": False,
        "chapters": [{"id": i, "start": a, "end": b, "title": name} for i, (name, a, b) in enumerate(marks)],
    }, open(os.path.join(path, "metadata.json"), "w"), indent=2)
    return start


def podcast(folder, title, host, palette, style, about, episodes):
    path = os.path.join(folder, title)
    os.makedirs(path, exist_ok=True)
    lettered(cover(host, title, style, palette), title, host, palette).save(os.path.join(path, "cover.jpg"), quality=90)
    json.dump({
        "tags": [], "title": title, "author": host, "description": about, "releaseDate": None,
        "genres": ["Society & Culture"], "feedUrl": None, "imageUrl": None, "itunesPageUrl": None, "itunesId": None,
        "itunesArtistId": None, "explicit": False, "language": "English", "type": "episodic",
    }, open(os.path.join(path, "metadata.json"), "w"), indent=2)
    for n, (name, date, minutes, text) in enumerate(reversed(episodes), 1):
        out = os.path.join(path, f"{n:02d} - {name}.mp3")
        if not os.path.exists(out):
            run("ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-t", str(minutes * 60), "-i",
                "anullsrc=r=8000:cl=mono", "-c:a", "libmp3lame", "-b:a", "8k",
                "-metadata", f"title={name}", "-metadata", f"album={title}", "-metadata", f"artist={host}",
                "-metadata", f"date={date}", "-metadata", f"comment={text}", "-metadata", f"description={text}",
                "-metadata", f"track={n}", "-metadata", "genre=Podcast", out)
        # The episode's date as the file's too, for a server that goes by the file.
        y, m, d = (int(x) for x in date.split("-"))
        stamp = datetime.datetime(y, m, d, 6, 0, tzinfo=datetime.timezone.utc).timestamp()
        os.utime(out, (stamp, stamp))


def main(books, podcasts):
    for b in BOOKS:
        seconds = book(books, *b)
        print(f"{b[1]}: {len(b[-1])} chapters, {seconds // 3600} h {seconds % 3600 // 60} min")
    for p in PODCASTS:
        podcast(podcasts, *p)
        print(f"{p[0]}: {len(p[-1])} episodes")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: books.py BOOKS_FOLDER PODCASTS_FOLDER")
    main(sys.argv[1], sys.argv[2])
