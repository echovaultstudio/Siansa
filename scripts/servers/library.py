#!/usr/bin/env python3
"""A small music library tagged like real ones, for testing Siansa on Navidrome, Emby and Jellyfin (issue #903).

    python3 scripts/servers/library.py <folder>

Writes the songs (made with ffmpeg: tones, nobody's music) and library.json, which says what each server should find:
the tests in app/src/test/java/app/siansa/servers/ read it. Four albums in four formats: FLAC with a cover and synced
lyrics, MP3 on two discs, an AAC compilation by several artists, and an Opus single.
"""
import json
import os
import subprocess
import sys

SONGS = [
    # folder, file, format, title, artist, album artist, album, year, genre, disc, track, seconds, lyrics
    ("Aurora Test/First Light", "01 Morning Tone.flac", "flac", "Morning Tone", "Aurora Test", "Aurora Test", "First Light", 2021, "Electronic", 1, 1, 30, False),
    ("Aurora Test/First Light", "02 Second Glow.flac", "flac", "Second Glow", "Aurora Test", "Aurora Test", "First Light", 2021, "Electronic", 1, 2, 31, True),
    ("Aurora Test/First Light", "03 Late Bloom.flac", "flac", "Late Bloom", "Aurora Test", "Aurora Test", "First Light", 2021, "Electronic", 1, 3, 32, False),
    ("Bram Example/Two Discs", "1-01 Upstairs.mp3", "mp3", "Upstairs", "Bram Example", "Bram Example", "Two Discs", 2019, "Rock", 1, 1, 33, False),
    ("Bram Example/Two Discs", "1-02 Landing.mp3", "mp3", "Landing", "Bram Example", "Bram Example", "Two Discs", 2019, "Rock", 1, 2, 34, False),
    ("Bram Example/Two Discs", "2-01 Downstairs.mp3", "mp3", "Downstairs", "Bram Example", "Bram Example", "Two Discs", 2019, "Rock", 2, 1, 35, False),
    ("Various Artists/Mixed Bag", "01 Shared Room.m4a", "m4a", "Shared Room", "Aurora Test", "Various Artists", "Mixed Bag", 2023, "Pop", 1, 1, 30, False),
    ("Various Artists/Mixed Bag", "02 Open Door.m4a", "m4a", "Open Door", "Cleo Sample", "Various Artists", "Mixed Bag", 2023, "Pop", 1, 2, 31, False),
    ("Cleo Sample/Loose Ends", "01 Loose Ends.opus", "opus", "Loose Ends", "Cleo Sample", "Cleo Sample", "Loose Ends", 2024, "Jazz", 1, 1, 32, False),
]

CODEC = {"flac": ["-c:a", "flac"], "mp3": ["-c:a", "libmp3lame", "-b:a", "128k", "-id3v2_version", "3"],
         "m4a": ["-c:a", "aac", "-b:a", "128k"], "opus": ["-c:a", "libopus", "-b:a", "96k"]}


def run(args):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", *args], check=True)


def main(root):
    out = []
    for n, (folder, name, fmt, title, artist, album_artist, album, year, genre, disc, track, secs, lyrics) in enumerate(SONGS):
        os.makedirs(os.path.join(root, folder), exist_ok=True)
        path = os.path.join(folder, name)
        tags = {"title": title, "artist": artist, "album_artist": album_artist, "album": album, "date": str(year),
                "genre": genre, "disc": str(disc), "track": str(track)}
        if album_artist == "Various Artists":
            tags["compilation"] = "1"
        meta = [x for k, v in tags.items() for x in ("-metadata", f"{k}={v}")]
        run(["-f", "lavfi", "-i", f"sine=frequency={220 + 40 * n}:duration={secs}:sample_rate=44100", "-ac", "2",
             *CODEC[fmt], *meta, os.path.join(root, path)])
        if lyrics:
            base = os.path.splitext(os.path.join(root, path))[0]
            with open(base + ".lrc", "w") as f:
                f.write("[00:01.00]The first line\n[00:10.00]The second line\n[00:20.00]The last line\n")
        out.append({"path": path, "format": fmt, "title": title, "artist": artist, "albumArtist": album_artist, "album": album,
                    "year": year, "genre": genre, "disc": disc, "track": track, "seconds": secs, "lyrics": lyrics,
                    "bytes": os.path.getsize(os.path.join(root, path))})
    # A cover for the album with one: a still picture, as cover.jpg (every server reads it).
    run(["-f", "lavfi", "-i", "testsrc=size=600x600", "-frames:v", "1", os.path.join(root, "Aurora Test/First Light/cover.jpg")])
    with open(os.path.join(root, "library.json"), "w") as f:
        json.dump({"songs": out}, f, indent=1)
    print(f"{len(out)} songs in {root}")


if __name__ == "__main__":
    main(sys.argv[1])
