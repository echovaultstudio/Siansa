#!/usr/bin/env python3
"""Siansa Beats for the showcase (the README's and website's screenshots): the real service, from a checkout of
echovaultstudio/siansa-beats, reading the showcase's Navidrome, with a stand-in for the beat model (PyTorch and the
model files would take the run's time and disk). The stand-in gives every song a steady grid, four beats a bar, at
one of a few close tempos, so DJ mixes can be started and blended. Loudness, fades and keys are measured for real.
Genres and "what songs sound like" aren't in it: the app takes those from the songs' tags and the AudioMuse-AI stand-in.

    python3 beats_service.py <siansa-beats checkout>/beats <port> <Navidrome address> <data folder>

Then it switches every library on and waits until all songs are analysed.
"""
import json
import os
import sys
import threading
import time
import urllib.request

TEMPOS = [120.0, 122.0, 124.0, 126.0]


class Tracker:
    loaded, load_seconds, device, device_name, device_note = True, 0.0, "cpu", "a stand-in", None

    def __call__(self, samples):
        seconds = len(samples) / 22_050
        period = 60.0 / TEMPOS[len(samples) % len(TEMPOS)]
        beats = [round(i * period, 3) for i in range(int((seconds - 0.5) / period))]
        return beats, beats[::4]


def call(base, path, body=None):
    request = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"}, method="GET" if body is None else "POST")
    with urllib.request.urlopen(request, timeout=30) as answer:
        return json.loads(answer.read() or b"null")


def main(code, port, navidrome, data):
    sys.path.insert(0, code)
    from beats import log
    from beats.server import App, Config, make_server

    os.makedirs(data, exist_ok=True)
    log.setup("info")
    app = App(Config({"NAVIDROME_URL": navidrome, "NAVIDROME_USER": os.environ.get("ND_USER", "admin"), "NAVIDROME_PASSWORD": os.environ.get("ND_PASSWORD", "admin"),
                      "DATA_DIR": data, "PORT": str(port)}), tracker=Tracker())
    app.worker.start()
    threading.Thread(target=lambda: make_server(app, host="0.0.0.0", port=port).serve_forever(), daemon=True).start()

    base = f"http://127.0.0.1:{port}"
    for _ in range(60):
        try:
            call(base, "/api/health")
            break
        except OSError:
            time.sleep(0.5)
    libraries = call(base, "/api/libraries")
    found = libraries.get("libraries", libraries) if isinstance(libraries, dict) else libraries
    ids = [str(l["id"]) for l in found]
    chosen = call(base, "/api/libraries", {"ids": ids})
    print("Libraries:", ids, chosen, flush=True)
    wanted = int(chosen.get("songs") or chosen.get("queued") or 0)
    end = time.time() + 900
    while time.time() < end:
        caps = call(base, "/api/v1/capabilities")["features"]
        grids, loud = caps["beat_grids"]["songs"], caps["loudness"]["songs"]
        print(f"Siansa Beats: {grids} grids, {loud} loudness", flush=True)
        # Every song with its grid and loudness (a song or two the stand-in can't read doesn't hold it up).
        if wanted and grids >= wanted - 2 and loud >= wanted - 2:
            break
        time.sleep(5)
    print("Siansa Beats ready", flush=True)
    while True:
        time.sleep(3600)


if __name__ == "__main__":
    main(sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4])
