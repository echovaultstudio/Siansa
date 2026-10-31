#!/usr/bin/env python3
"""Stand-ins for AudioMuse-AI and an AI service, for the showcase screenshots only (issue #741).

They answer the calls Siansa makes with songs of the showcase library on Navidrome, picked by genre for the words
asked: enough for Home's mixes, Flow, radios and song paths to fill up on the emulator. Nothing here is AudioMuse's
analysis or a real AI: the screenshots show the app, not what these say.

    python3 scripts/showcase/fake_services.py 8000 http://127.0.0.1:4533
"""
import hashlib
import http.server
import json
import random
import re
import sys
import threading
import urllib.parse
import urllib.request

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
NAVIDROME = (sys.argv[2] if len(sys.argv) > 2 else "http://127.0.0.1:4533").rstrip("/")
AUTH = "u=admin&p=admin&v=1.16.1&c=showcase&f=json"

# How each genre sounds, roughly: energy (0..1), tempo, and its moods.
GENRES = {
    "Dream Pop": (0.35, 96, {"relaxed": 0.7, "happy": 0.4, "sad": 0.35}),
    "Synthwave": (0.7, 112, {"danceable": 0.6, "happy": 0.4, "aggressive": 0.2}),
    "Indie Folk": (0.3, 92, {"relaxed": 0.65, "happy": 0.45, "sad": 0.3}),
    "Soul": (0.5, 94, {"happy": 0.55, "relaxed": 0.6, "danceable": 0.4}),
    "House": (0.85, 124, {"danceable": 0.9, "happy": 0.6}),
    "Indie Rock": (0.75, 128, {"happy": 0.5, "aggressive": 0.45}),
    "Lo-Fi": (0.25, 82, {"relaxed": 0.85, "sad": 0.3}),
    "Ambient": (0.1, 70, {"relaxed": 0.95, "sad": 0.35}),
    "Singer-Songwriter": (0.3, 88, {"relaxed": 0.6, "sad": 0.5}),
    "Downtempo": (0.4, 98, {"relaxed": 0.75, "happy": 0.4}),
    "Jazz": (0.4, 104, {"relaxed": 0.7, "happy": 0.45}),
    "Neo-Soul": (0.5, 90, {"relaxed": 0.6, "happy": 0.5, "danceable": 0.45}),
    "Techno": (0.9, 130, {"danceable": 0.85, "aggressive": 0.5}),
    "Modern Classical": (0.15, 72, {"relaxed": 0.85, "sad": 0.45}),
    "Chillwave": (0.4, 100, {"relaxed": 0.75, "happy": 0.5}),
    "Indie Pop": (0.7, 118, {"happy": 0.85, "danceable": 0.55}),
    "Afrobeat": (0.8, 116, {"happy": 0.8, "danceable": 0.85}),
    "Electropop": (0.8, 122, {"happy": 0.7, "danceable": 0.8}),
    "Post-Rock": (0.45, 96, {"relaxed": 0.5, "sad": 0.5, "aggressive": 0.25}),
}

# Words in a search, and the genres they bring up.
WORDS = {
    ("calm", "quiet", "soft", "rain", "rainy", "piano", "ambient", "focus", "study", "sleep", "atmospheric", "slow",
     "gentle", "mist", "fog"): ["Ambient", "Modern Classical", "Lo-Fi", "Dream Pop", "Post-Rock"],
    ("energy", "energetic", "dance", "workout", "party", "upbeat", "driving", "fast", "run", "club"):
        ["House", "Electropop", "Afrobeat", "Techno", "Indie Pop", "Synthwave"],
    ("night", "late", "drive", "neon", "city", "midnight", "tunnel", "highway", "headlights"):
        ["Synthwave", "Techno", "Lo-Fi", "Electropop"],
    ("morning", "coffee", "sunday", "warm", "bright", "kitchen", "soulful", "smooth"):
        ["Soul", "Neo-Soul", "Jazz", "Indie Folk", "Indie Pop", "Downtempo"],
    ("acoustic", "folk", "guitar", "campfire", "forest"): ["Indie Folk", "Singer-Songwriter"],
    ("jazz", "saxophone", "trumpet", "brass", "swing"): ["Jazz", "Afrobeat"],
    ("summer", "sun", "sunshine", "beach", "happy", "fun"): ["Indie Pop", "Afrobeat", "Chillwave", "Electropop"],
    ("sad", "melancholy", "lonely", "heartbreak"): ["Singer-Songwriter", "Dream Pop", "Post-Rock", "Modern Classical"],
    ("romance", "romantic", "love", "evening", "relaxed"): ["Soul", "Neo-Soul", "Dream Pop", "Downtempo"],
}

MOODS = {
    "happy": ["pop", "indie", "sunny"], "relaxed": ["ambient", "piano", "lo-fi"], "sad": ["acoustic", "slow", "strings"],
    "danceable": ["house", "electronic", "groove"], "aggressive": ["rock", "techno", "drums"],
}

TITLES = {
    "Morning": [("Copper Kettle Morning", "Warm, bright songs for the first coffee, building gently as the day starts."),
                ("Sunlight on the Tram Line", "Soft acoustic and light electronic songs with the window open.")],
    "Daytime": [("Bright Room, Steady Hum", "Focused and bright songs with a steady pulse to keep you going."),
                ("Afternoon Through the Blinds", "Melodic, upbeat songs for the middle of the day.")],
    "Evening": [("Harbour Lights at Dusk", "Warm, soulful songs as the day winds down and the lights come on."),
                ("Velvet Hour After Nine", "Smooth and laid back, for slow evenings with good company.")],
    "Night": [("Last Train, Window Seat", "Calm and atmospheric songs for the ride home."),
              ("Neon After Rain", "Soft synths and slow beats for the quiet hours.")],
}

_songs = []
_lock = threading.Lock()


def library():
    """The library's songs, read from Navidrome the first time they're needed."""
    with _lock:
        if not _songs:
            def get(method, extra=""):
                with urllib.request.urlopen(f"{NAVIDROME}/rest/{method}?{AUTH}{extra}", timeout=60) as r:
                    return json.load(r)["subsonic-response"]
            for album in get("getAlbumList2", "&type=alphabeticalByName&size=500")["albumList2"]["album"]:
                _songs.extend(get("getAlbum", "&id=" + album["id"])["album"]["song"])
        return _songs


def stable(*parts):
    return int(hashlib.sha256("/".join(map(str, parts)).encode()).hexdigest()[:12], 16)


def genres_for(text):
    words = set(re.findall(r"[a-z]+", text.lower()))
    found = []
    for keys, genres in WORDS.items():
        if words & set(keys):
            found += [g for g in genres if g not in found]
    return found


def ranked(text, n, seed=""):
    """Songs for [text]: the genres its words bring up first, then the rest, shuffled the same way each time."""
    wanted = next((v["genres"] for v in VIBES.values() if v["query"] == text), None) or genres_for(text)
    songs = library()
    def score(s):
        g = s.get("genre", "")
        return (wanted.index(g) if g in wanted else len(wanted) + 1, stable(seed or text, s["id"]) % 1000)
    return sorted(songs, key=score)[:n]


def row(s, i):
    return {"item_id": s["id"], "title": s["title"], "author": s["artist"], "album": s.get("album", ""),
            "distance": round(0.08 + i * 0.012, 3)}


def similar(song_id, n):
    songs = library()
    first = next((s for s in songs if s["id"] == song_id), None)
    if not first:
        return ranked("", n, song_id)
    near = sorted(songs, key=lambda s: (s["id"] == song_id, s.get("genre") != first.get("genre"),
                                         s["artist"] == first["artist"], stable(song_id, s["id"]) % 1000))
    return near[:n]


def features(s):
    energy, tempo, moods = GENRES.get(s.get("genre", ""), (0.5, 100, {"relaxed": 0.5}))
    jitter = (stable("f", s["id"]) % 100) / 1000
    return {
        "id": s["id"], "artist": s["artist"], "tempo": tempo + (stable("t", s["id"]) % 9) - 4,
        "key": "CDEFGAB"[stable("k", s["id"]) % 7], "scale": "major" if stable("s", s["id"]) % 3 else "minor",
        "energy": round(energy + jitter, 3),
        "other_features": ",".join(f"{m}:{round(v, 2)}" for m, v in moods.items()),
        "mood_vector": f"{s.get('genre', 'pop').lower()}:0.62,electronic:0.21",
    }


# What a real AI makes of the description ShowcaseTest types in Flow: a name, a sentence and sound concepts to steer
# by. Anything else gets its own words back as the name.
VIBES = {
    "rainy jazz piano": {
        "title": "Wet Streets, Warm Keys",
        "description": "Soft jazz piano and brushed drums for a rainy evening in.",
        "query": "soft jazz piano, brushed drums, slow and warm, rain on the window",
        "steer": [{"term": "piano", "direction": "more"}, {"term": "jazz", "direction": "more"}],
        # What the sound search finds for that query, best first.
        "genres": ["Jazz", "Modern Classical", "Neo-Soul", "Lo-Fi", "Soul", "Ambient"],
    },
}


def ai_answer(messages):
    """A mix plan in the shape Siansa asks for: {title, query, description, steer}."""
    user = next((m.get("content", "") for m in reversed(messages) if m.get("role") == "user"), "")
    wish = re.search(r'describes their mood or wish: "([^"]+)"', user)
    if wish:
        words = wish.group(1).strip()
        if words.lower() in VIBES:
            return {k: v for k, v in VIBES[words.lower()].items() if k != "genres"}
        title = words[:1].upper() + words[1:]
        return {"title": title[:40], "query": words, "description": f"Songs that sound like {words.lower()}.", "steer": []}
    slot = re.search(r"Time slot: (\w+)", user)
    name = slot.group(1) if slot else "Daytime"
    options = TITLES.get(name, TITLES["Daytime"])
    rejected = re.findall(r'Your title "([^"]+)" was rejected', user)
    previous = re.search(r"Previous mix titles: (.*)", user)
    used = set(rejected) | set((previous.group(1).split("; ") if previous else []))
    title, description = next(((t, d) for t, d in options if t not in used), options[-1])
    query = {"Morning": "warm bright acoustic morning", "Daytime": "upbeat steady bright", "Evening": "warm soulful smooth evening",
             "Night": "calm atmospheric night drive"}.get(name, "warm bright")
    return {"title": title, "query": query, "description": description, "steer": []}


class Handler(http.server.BaseHTTPRequestHandler):
    def send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw or b"{}")
        except ValueError:
            return {}

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        q = {k: v[0] for k, v in urllib.parse.parse_qs(url.query).items()}
        path = url.path.rstrip("/")
        n = int(q.get("n", 25))
        if path == "/api/similar_tracks":
            if "mood" in q:
                songs = ranked(" ".join(MOODS.get(q["mood"], [])) + " " + q["mood"], n, q["mood"] + q.get("centroid_index", ""))
            else:
                songs = similar(q.get("item_id", ""), n)
            return self.send(200, [row(s, i) for i, s in enumerate(songs)])
        if path == "/api/mood_centroids":
            return self.send(200, {m: [{"index": 0, "top_tags": tags}] for m, tags in MOODS.items()})
        if path == "/api/find_path":
            start, end = q.get("start_song_id"), q.get("end_song_id")
            size = int(q.get("max_steps", q.get("path_length", 12)))
            middle = [s for s in similar(start or "", 40) if s["id"] not in (start, end)][: max(0, size - 2)]
            ends = {s["id"]: s for s in library()}
            path_songs = ([ends[start]] if start in ends else []) + middle + ([ends[end]] if end in ends else [])
            return self.send(200, {"path": [row(s, i) for i, s in enumerate(path_songs)]})
        if path == "/api/clap/concepts":
            cats = [("genre", "Genre", sorted({g.lower() for g in GENRES})), ("mood", "Mood", list(MOODS)),
                    ("instrument", "Instrument", ["piano", "guitar", "synth", "strings", "brass", "drums"])]
            return self.send(200, {"available": True, "max_terms": 10, "categories": [
                {"category": c, "label": label, "terms": [{"term": t, "grounding": 0.8} for t in terms]} for c, label, terms in cats]})
        if path == "/api/sync":
            ids = set(q.get("ids", "").split(","))
            return self.send(200, {"tracks": [features(s) for s in library() if s["id"] in ids]})
        if path == "/api/health":
            return self.send(200, {"status": "ok"})
        return self.send(404, {"error": "not here"})

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path.rstrip("/")
        body = self.body()
        if path == "/api/clap/search":
            songs = ranked(body.get("query", ""), int(body.get("limit", 50)))
            hits = [{**row(s, i), "similarity": round(0.92 - i * 0.008, 3)} for i, s in enumerate(songs)]
            return self.send(200, {"results": hits, "steering_warnings": []})
        if path == "/api/alchemy":
            ids = [i["id"] for i in body.get("items", []) if i.get("op") == "ADD"]
            songs = similar(ids[0], int(body.get("n", 40))) if ids else ranked("", 40)
            return self.send(200, {"results": [row(s, i) for i, s in enumerate(songs)]})
        if path == "/api/sonic_fingerprint/generate":
            return self.send(200, [row(s, i) for i, s in enumerate(ranked("warm soulful night drive", int(body.get("n", 50)), "you"))])
        if path.endswith("/chat/completions"):
            answer = ai_answer(body.get("messages", []))
            return self.send(200, {"id": "showcase", "object": "chat.completion", "model": body.get("model", "showcase"),
                                   "choices": [{"index": 0, "finish_reason": "stop",
                                                "message": {"role": "assistant", "content": json.dumps(answer)}}]})
        return self.send(404, {"error": "not here"})

    def log_message(self, fmt, *args):
        sys.stdout.write("%s %s\n" % (self.command, self.path))
        sys.stdout.flush()


if __name__ == "__main__":
    print(f"Showcase services on :{PORT}, songs from {NAVIDROME}", flush=True)
    http.server.ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
