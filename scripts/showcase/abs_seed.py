#!/usr/bin/env python3
"""Sets up the showcase's Audiobookshelf (issue #741): its first user (admin/admin), a library of the made-up books and
one of the podcasts (books.py), scanned, and where the listener is in a book and an episode, so Home has something to
continue. Standard library only.

    python3 scripts/showcase/abs_seed.py http://127.0.0.1:13378
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:13378"
TOKEN = ""


def call(path, body=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method or ("POST" if data else "GET"))
    req.add_header("Content-Type", "application/json")
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    with urllib.request.urlopen(req, timeout=30) as r:
        text = r.read().decode()
    try:
        return json.loads(text)
    except ValueError:
        return text


def wait(what, check, seconds):
    end = time.time() + seconds
    while time.time() < end:
        try:
            got = check()
            if got:
                return got
        except (urllib.error.URLError, ConnectionError, OSError):
            pass
        time.sleep(2)
    raise SystemExit(f"Audiobookshelf: {what} didn't happen in {seconds} s")


def main():
    global TOKEN
    status = wait("starting", lambda: call("/status"), 120)
    if not status.get("isInit"):
        call("/init", {"newRoot": {"username": "admin", "password": "admin"}})
    user = call("/login", {"username": "admin", "password": "admin"})["user"]
    TOKEN = user.get("token") or user.get("accessToken")
    have = {lib["name"]: lib["id"] for lib in call("/api/libraries").get("libraries", [])}
    libraries = {}
    for name, folder, media, icon, provider in (("Audiobooks", "/audiobooks", "book", "audiobookshelf", "google"),
                                                ("Podcasts", "/podcasts", "podcast", "podcast", "itunes")):
        lib = have.get(name) or call("/api/libraries", {"name": name, "folders": [{"fullPath": folder}],
                                                         "mediaType": media, "icon": icon, "provider": provider})["id"]
        libraries[name] = lib
        try:
            call(f"/api/libraries/{lib}/scan", {})
        except urllib.error.HTTPError as e:
            print(f"{name}: scan asked, {e.code}")

    def items(lib, least):
        got = call(f"/api/libraries/{lib}/items?limit=50").get("results", [])
        return got if len(got) >= least else None

    books = wait("scanning the books", lambda: items(libraries["Audiobooks"], 4), 300)
    podcasts = wait("scanning the podcasts", lambda: items(libraries["Podcasts"], 3), 300)
    for b in books:
        m = b["media"]
        print(f"Book: {m['metadata'].get('title')} by {m['metadata'].get('authorName')}, "
              f"{len(m.get('chapters', []) or [])} chapters, {round(m.get('duration', 0) / 3600, 1)} h")
    for p in podcasts:
        print(f"Podcast: {p['media']['metadata'].get('title')}, {p['media'].get('numEpisodes', '?')} episodes")

    # Where the listener is: a third of the way into the lighthouse book, and halfway through an episode.
    lighthouse = next((b for b in books if "Lighthouse" in b["media"]["metadata"].get("title", "")), books[0])
    full = call(f"/api/items/{lighthouse['id']}?expanded=1")
    duration = full["media"].get("duration") or 21720
    call(f"/api/me/progress/{lighthouse['id']}",
         {"currentTime": duration * 0.37, "duration": duration, "progress": 0.37, "isFinished": False}, "PATCH")
    commute = next((p for p in podcasts if p["media"]["metadata"].get("title") == "The Long Commute"), podcasts[0])
    episodes = call(f"/api/items/{commute['id']}?expanded=1")["media"].get("episodes", [])
    if episodes:
        latest = max(episodes, key=lambda e: e.get("publishedAt") or 0)
        length = (latest.get("audioFile") or {}).get("duration") or 2000
        call(f"/api/me/progress/{commute['id']}/{latest['id']}",
             {"currentTime": length * 0.5, "duration": length, "progress": 0.5, "isFinished": False}, "PATCH")
        print(f"Episodes: {', '.join(e.get('title', '?') for e in episodes)}")
    print("Audiobookshelf ready: admin/admin")


if __name__ == "__main__":
    main()
