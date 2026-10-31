#!/usr/bin/env python3
"""Writes the showcase library (issue #741) into a music folder for Navidrome: made-up artists and albums
(catalog.py), their covers and pictures (covers.py), songs with real tags, and synced lyrics for a few.

    python3 scripts/showcase/library.py MUSIC_FOLDER

The songs are quiet chords, a few lengths of them, each tagged as its own song: 320 kbit/s at 44.1 kHz, as a library's
songs would be (now playing shows it under About this song). Needs ffmpeg, Pillow and numpy.
"""
import hashlib
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalog import ARTISTS, LYRICS  # noqa: E402
from covers import artist_picture, cover  # noqa: E402

LENGTHS = [167, 192, 218, 245, 281, 323]  # seconds: 2:47 … 5:23


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def base_songs(folder):
    """One quiet chord per length, encoded once; every song is a copy of one with its own tags."""
    paths = {}
    for i, length in enumerate(LENGTHS):
        path = os.path.join(folder, f"base-{length}.mp3")
        if not os.path.exists(path):
            root = [110.0, 123.47, 130.81, 146.83, 164.81, 98.0][i]
            chord = "+".join(f"0.18*sin(2*PI*{root * k}*t)" for k in (1, 1.25, 1.5, 2))
            run("ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
                f"aevalsrc='({chord})*(0.6+0.4*sin(2*PI*0.25*t))':s=44100:d={length}",
                "-af", f"afade=t=in:d=3,afade=t=out:st={length - 4}:d=4", "-ac", "2", "-b:a", "320k", path)
        paths[length] = path
    return paths


def length_of(artist, title):
    return LENGTHS[int(hashlib.sha256(f"{artist}/{title}".encode()).hexdigest(), 16) % len(LENGTHS)]


def lrc(lines):
    return "\n".join(f"[{s // 60:02d}:{s % 60:02d}.00]{text}" for s, text in lines) + "\n"


def main(music):
    os.makedirs(music, exist_ok=True)
    bases = base_songs(os.path.join(music, ".."))
    count = 0
    for artist, genre, label, albums in ARTISTS:
        artist_dir = os.path.join(music, artist)
        os.makedirs(artist_dir, exist_ok=True)
        artist_picture(artist, albums[0][3]).save(os.path.join(artist_dir, "artist.jpg"), quality=88)
        for album, year, style, palette, songs in albums:
            album_dir = os.path.join(artist_dir, album)
            os.makedirs(album_dir, exist_ok=True)
            cover(artist, album, style, palette).save(os.path.join(album_dir, "cover.jpg"), quality=90)
            for n, title in enumerate(songs, 1):
                name = f"{n:02d} - {title.replace('/', '-')}.mp3"
                out = os.path.join(album_dir, name)
                run("ffmpeg", "-loglevel", "error", "-y", "-i", bases[length_of(artist, title)], "-c", "copy",
                    "-map_metadata", "-1", "-id3v2_version", "3",
                    "-metadata", f"title={title}", "-metadata", f"artist={artist}", "-metadata", f"album_artist={artist}",
                    "-metadata", f"album={album}", "-metadata", f"track={n}/{len(songs)}", "-metadata", "disc=1/1",
                    "-metadata", f"date={year}", "-metadata", f"genre={genre}", "-metadata", f"publisher={label}", out)
                if (artist, title) in LYRICS:
                    with open(out[:-4] + ".lrc", "w") as f:
                        f.write(lrc(LYRICS[(artist, title)]))
                count += 1
    print(f"Showcase library: {len(ARTISTS)} artists, {sum(len(a[3]) for a in ARTISTS)} albums, {count} songs in {music}")


if __name__ == "__main__":
    main(sys.argv[1])
