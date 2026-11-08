import json
import unittest

from flask import Flask

import support
from plugin import api
from siansa_beats import web

# The Siansa plugin's row for Control: its loudness, ramps, and its wrong reading of the start (187.1).
CONTROL = ("c-canon", -9.2, "AAA=", "BBB=", 210_000, json.dumps({
    "intro_bpm": 187.1, "intro_beat_ms": 120.0, "intro_rhythm": 0.9, "intro_steady": 0.8,
    "outro_bpm": 140.1, "outro_beat_ms": 200_000.0, "outro_rhythm": 0.9, "outro_steady": 0.8,
    "beats_until_ms": 150_000, "beats_until_checked": True}))
OTHER = ("o-canon", -11.0, "CCC=", "DDD=", 180_000, json.dumps({"intro_bpm": 128.0, "intro_beat_ms": 50.0}))

# Siansa Beats' grid of Control, as GET /api/grids answers.
CONTROL_GRID = {"track_id": "c", "beats_until_ms": None,
                "intro_bpm": 140.0, "intro_beat_ms": 18.0, "intro_bar_ms": 18.0, "intro_phrase_ms": 18.0, "intro_phrase_bars": 8,
                "intro_rhythm": 0.97, "intro_steady": 0.98,
                "outro_bpm": 140.0, "outro_beat_ms": 205_000.0, "outro_bar_ms": 203_300.0, "outro_phrase_ms": 196_400.0,
                "outro_phrase_bars": 8, "outro_rhythm": 0.98, "outro_steady": 0.98}


class MergeTest(unittest.TestCase):
    def siansa(self, row=CONTROL):
        return {"ref_lufs": row[1], "start_ramp": row[2], "end_ramp": row[3], "duration_ms": row[4], **json.loads(row[5])}

    def test_siansa_beats_grid_replaces_the_plugins_and_the_loudness_stays(self):
        out = web.merge(self.siansa(), CONTROL_GRID)
        self.assertEqual((out["ref_lufs"], out["start_ramp"], out["end_ramp"], out["duration_ms"]), (-9.2, "AAA=", "BBB=", 210_000))
        self.assertEqual(out["intro_bpm"], 140.0)
        self.assertEqual(out["outro_phrase_ms"], 196_400.0)
        self.assertNotIn("beats_until_ms", out)  # Siansa Beats: it beats to the end; the plugin's 150 s is gone
        self.assertNotIn("beats_until_checked", out)
        self.assertEqual(out["beat_grid"], "siansa_beats")
        self.assertNotIn("track_id", out)

    def test_without_a_grid_the_plugins_stays_or_goes(self):
        kept = web.merge(self.siansa(), None, "plugin")
        self.assertEqual((kept["intro_bpm"], kept["beat_grid"]), (187.1, "siansa_analysis"))
        none = web.merge(self.siansa(), None, "none")
        self.assertFalse([k for k in none if web.is_beat_field(k)])
        self.assertEqual(none["ref_lufs"], -9.2)
        self.assertNotIn("beat_grid", none)

    def test_a_song_only_siansa_beats_has(self):
        out = web.merge(None, CONTROL_GRID)
        self.assertIsNone(out["ref_lufs"])
        self.assertEqual(out["intro_bpm"], 140.0)
        self.assertIsNone(web.merge(None, None))


class BatchTest(unittest.TestCase):
    def test_requests_stay_short_enough(self):
        ids = [f"{n:022d}" for n in range(1200)]
        chunks = web.batches(ids)
        self.assertEqual(sum(len(c) for c in chunks), 1200)
        for c in chunks:
            self.assertLessEqual(len(web.GRIDS_PATH + "?ids=" + ",".join(c)), web.URL_BUDGET)


class CacheTest(unittest.TestCase):
    def setUp(self):
        self.now = 1000.0
        self.cache = web.GridCache(clock=lambda: self.now)
        self.asked = []
        self.down = False

    def fetch(self, path):
        self.asked.append(path)
        if self.down:
            raise web.BeatsError("Siansa Beats answered HTTP 502")
        ids = path.split("ids=")[1].split(",")
        return {"grids": [dict(CONTROL_GRID, track_id=i) for i in ids if i == "c"]}

    def test_kept_and_asked_again_later(self):
        self.assertEqual(set(self.cache.grids(["c", "x"], self.fetch)), {"c"})
        self.cache.grids(["c", "x"], self.fetch)
        self.assertEqual(len(self.asked), 1)
        self.now += web.MISSING_SECONDS + 1
        self.cache.grids(["c", "x"], self.fetch)
        self.assertEqual(self.asked[-1], "/api/grids?ids=x")  # the missing one again, the found one kept
        self.now += web.FOUND_SECONDS
        self.cache.grids(["c"], self.fetch)
        self.assertEqual(self.asked[-1], "/api/grids?ids=c")

    def test_down_backs_off_and_gives_nothing(self):
        self.down = True
        self.assertEqual(self.cache.grids(["c"], self.fetch), {})
        self.assertIsNotNone(self.cache.problem)
        self.cache.grids(["c"], self.fetch)
        self.assertEqual(len(self.asked), 1)
        self.down = False
        self.now += 61
        self.assertEqual(set(self.cache.grids(["c"], self.fetch)), {"c"})
        self.assertIsNone(self.cache.problem)


class ApiTest(unittest.TestCase):
    def setUp(self):
        app = Flask(__name__)
        app.secret_key = "test"
        app.register_blueprint(web.bp, url_prefix="/plugins/siansa_beats")
        self.client = app.test_client()
        api.settings.clear()
        api.settings["beats_url"] = "http://beats:8090"
        api.db = support.FakeDb([CONTROL, OTHER])
        web.CACHE.clear()
        self.beats_down = False
        self.original = (web._beats_get, web._canonical)
        web._beats_get = self.fake_beats
        web._canonical = lambda ids: {i: {"c": "c-canon", "o": "o-canon"}.get(i, i) for i in ids}

    def tearDown(self):
        web._beats_get, web._canonical = self.original

    def fake_beats(self, path):
        if self.beats_down:
            raise web.BeatsError("Nothing answers at beats:8090: is Siansa Beats running?")
        if path == "/api/health":
            return {"status": "ok", "version": "0.8.1"}
        ids = path.split("ids=")[1].split(",")
        return {"grids": [CONTROL_GRID for i in ids if i == "c"], "missing": [i for i in ids if i != "c"]}

    def test_profiles_carry_the_plugins_loudness_and_siansa_beats_grids(self):
        answer = self.client.get("/plugins/siansa_beats/api/profiles?ids=c,o,nowhere").get_json()
        self.assertEqual(answer["schema_version"], 1)
        by_id = {p["track_id"]: p for p in answer["profiles"]}
        self.assertEqual(by_id["c"]["intro_bpm"], 140.0)
        self.assertEqual(by_id["c"]["ref_lufs"], -9.2)
        self.assertEqual(by_id["o"]["intro_bpm"], 128.0)  # the plugin's, Siansa Beats hasn't done it
        self.assertEqual(answer["pending"], ["nowhere"])
        self.assertTrue(all(sql.lstrip().upper().startswith("SELECT") for sql in api.db.seen))

    def test_siansa_beats_down_keeps_the_plugins(self):
        self.beats_down = True
        answer = self.client.get("/plugins/siansa_beats/api/profiles?ids=c").get_json()
        self.assertEqual(answer["profiles"][0]["intro_bpm"], 187.1)

    def test_without_the_siansa_plugin_only_siansa_beats(self):
        api.db = support.FakeDb(installed=False)
        answer = self.client.get("/plugins/siansa_beats/api/profiles?ids=c,o").get_json()
        self.assertEqual([p["track_id"] for p in answer["profiles"]], ["c"])
        self.assertIsNone(answer["profiles"][0]["ref_lufs"])

    def test_too_many_ids(self):
        response = self.client.get("/plugins/siansa_beats/api/profiles?ids=" + ",".join(str(n) for n in range(501)))
        self.assertEqual(response.status_code, 400)

    def test_health_says_what_answers(self):
        answer = self.client.get("/plugins/siansa_beats/api/health").get_json()
        self.assertEqual((answer["plugin"], answer["status"], answer["beats"]["version"], answer["siansa_plugin"]),
                         ("siansa_beats", "ok", "0.8.1", True))
        self.beats_down = True
        answer = self.client.get("/plugins/siansa_beats/api/health").get_json()
        self.assertFalse(answer["beats"]["reachable"])
        self.assertIn("is Siansa Beats running", answer["beats"]["problem"])

    def test_the_page_saves_and_never_shows_the_password(self):
        response = self.client.post("/plugins/siansa_beats/settings", data={
            "beats_url": "http://192.168.1.106:8090/", "beats_password": "sesame", "missing": "none"})
        self.assertEqual(response.status_code, 302)
        self.assertEqual((api.settings["beats_url"], api.settings["beats_password"], api.settings["missing"]),
                         ("http://192.168.1.106:8090", "sesame", "none"))
        page = self.client.get("/plugins/siansa_beats/settings?saved=1").get_data(as_text=True)
        self.assertNotIn("sesame", page)
        self.assertIn("kept: leave empty to keep it", page)
        self.assertIn("Siansa Beats 0.8.1 answers", page)
        # Saving again without a password keeps it; the box removes it.
        self.client.post("/plugins/siansa_beats/settings", data={"beats_url": "http://b:8090", "missing": "plugin"})
        self.assertEqual(api.settings["beats_password"], "sesame")
        self.client.post("/plugins/siansa_beats/settings", data={"beats_url": "http://b:8090", "forget_password": "1"})
        self.assertEqual(api.settings["beats_password"], "")


class VersionTest(unittest.TestCase):
    def test_the_code_says_the_newest_version(self):
        with open(support.os.path.join(support.HERE, "..", "plugin.json")) as f:
            plugin = json.load(f)
        self.assertEqual(plugin["versions"][0]["version"], web.VERSION)
        self.assertEqual(plugin["id"], web.PLUGIN_ID)


if __name__ == "__main__":
    unittest.main()
