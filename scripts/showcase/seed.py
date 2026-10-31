#!/usr/bin/env python3
"""Gives the showcase library (issue #741) a listening past on Navidrome: plays over the last months, favourites,
and a few playlists, so Home, Rediscover and Liked songs have something to show.

    python3 scripts/showcase/seed.py http://127.0.0.1:4533
"""
import json
import random
import sys
import time
import urllib.parse
import urllib.request

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:4533"
AUTH = {"u": "admin", "p": "admin", "v": "1.16.1", "c": "showcase", "f": "json"}

# The artists played most, most first: they make "Top artists" and "On repeat".
FAVOURITES = ["Glass Harbour", "Neon Parish", "Velvet Coast", "Mara Lux", "Kel Arden", "Iris Okafor", "Sunborn"]
PLAYLISTS = {
    "Late Night Drive": ["Neon Parish", "Midnight Atlas", "Tove Marin", "Kel Arden"],
    "Sunday Kitchen": ["Velvet Coast", "Iris Okafor", "The Copper Lanes", "Hollow Pines"],
    "Deep Focus": ["Pale Signal", "Rosa Venn", "Northbound Hymns", "Kel Arden"],
    "Summer Windows Down": ["Arlo Grey", "Juno Fields", "Brass Meridian", "Loma Drift", "Mara Lux"],
}


def call(method, **params):
    query = urllib.parse.urlencode({**AUTH, **params}, doseq=True)
    with urllib.request.urlopen(f"{BASE}/rest/{method}?{query}", timeout=60) as r:
        body = json.load(r)["subsonic-response"]
    if body.get("status") != "ok":
        raise RuntimeError(f"{method}: {body.get('error')}")
    return body


def main():
    rnd = random.Random(741)
    albums = call("getAlbumList2", type="alphabeticalByName", size=500)["albumList2"]["album"]
    songs = []
    for a in albums:
        songs += call("getAlbum", id=a["id"])["album"]["song"]
    by_artist = {}
    for s in songs:
        by_artist.setdefault(s["artist"], []).append(s)

    # Plays: favourites far more often, the rest now and then, spread over four months and mostly in the evening.
    weights = [(s, 12 - 1.4 * FAVOURITES.index(s["artist"]) if s["artist"] in FAVOURITES else 1.0) for s in songs]
    population, w = zip(*weights)
    now = time.time()
    plays = 0
    for s in rnd.choices(population, weights=w, k=650):
        day = rnd.random() ** 1.6 * 120
        hour = rnd.choice([8, 9, 12, 13, 17, 18, 19, 20, 21, 21, 22, 22, 23])
        at = now - day * 86400
        at = at - (at % 86400) + hour * 3600 + rnd.randint(0, 3599)
        if at > now:
            at -= 86400
        call("scrobble", id=s["id"], time=int(at * 1000), submission="true")
        plays += 1

    # Favourites: a few dozen songs, some albums and artists.
    starred = rnd.sample(songs, 36)
    call("star", id=[s["id"] for s in starred])
    call("star", albumId=[a["id"] for a in albums if a["artist"] in FAVOURITES[:5]])
    call("star", artistId=list({s["artistId"] for s in songs if s["artist"] in FAVOURITES[:4]}))

    for name, artists in PLAYLISTS.items():
        pool = [s for a in artists for s in by_artist.get(a, [])]
        picked = rnd.sample(pool, min(len(pool), 16))
        call("createPlaylist", name=name, songId=[s["id"] for s in picked])

    print(f"Seeded {plays} plays, {len(starred)} favourite songs and {len(PLAYLISTS)} playlists for {len(songs)} songs")


if __name__ == "__main__":
    main()
