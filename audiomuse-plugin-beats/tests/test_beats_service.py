import json
import unittest

from flask import Flask

import support
from plugin import api
from siansa_beats import beats_service, store, web

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
        out = beats_service.merge(self.siansa(), CONTROL_GRID)
        self.assertEqual((out["ref_lufs"], out["start_ramp"], out["end_ramp"], out["duration_ms"]), (-9.2, "AAA=", "BBB=", 210_000))
        self.assertEqual(out["intro_bpm"], 140.0)
        self.assertEqual(out["outro_phrase_ms"], 196_400.0)
        self.assertNotIn("beats_until_ms", out)  # Siansa Beats: it beats to the end; the plugin's 150 s is gone
        self.assertNotIn("beats_until_checked", out)
        self.assertEqual(out["beat_grid"], "siansa_beats")
        self.assertNotIn("track_id", out)

    def test_without_a_grid_the_plugins_stays_or_goes(self):
        kept = beats_service.merge(self.siansa(), None, "plugin")
        self.assertEqual((kept["intro_bpm"], kept["beat_grid"]), (187.1, "plugin"))
        none = beats_service.merge(self.siansa(), None, "none")
        self.assertFalse([k for k in none if beats_service.is_beat_field(k)])
        self.assertEqual(none["ref_lufs"], -9.2)
        self.assertNotIn("beat_grid", none)

    def test_a_song_only_siansa_beats_has(self):
        out = beats_service.merge(None, CONTROL_GRID)
        self.assertIsNone(out["ref_lufs"])
        self.assertEqual(out["intro_bpm"], 140.0)
        self.assertIsNone(beats_service.merge(None, None))


class BatchTest(unittest.TestCase):
    def test_requests_stay_short_enough(self):
        ids = [f"{n:022d}" for n in range(1200)]
        chunks = beats_service.batches(ids)
        self.assertEqual(sum(len(c) for c in chunks), 1200)
        for c in chunks:
            self.assertLessEqual(len(beats_service.GRIDS_PATH + "?ids=" + ",".join(c)), beats_service.URL_BUDGET)


class CacheTest(unittest.TestCase):
    def setUp(self):
        self.now = 1000.0
        self.cache = beats_service.GridCache(clock=lambda: self.now)
        self.asked = []
        self.down = False

    def fetch(self, path):
        self.asked.append(path)
        if self.down:
            raise beats_service.BeatsError("Siansa Beats answered HTTP 502")
        ids = path.split("ids=")[1].split(",")
        return {"grids": [dict(CONTROL_GRID, track_id=i) for i in ids if i == "c"]}

    def test_kept_and_asked_again_later(self):
        self.assertEqual(set(self.cache.grids(["c", "x"], self.fetch)), {"c"})
        self.cache.grids(["c", "x"], self.fetch)
        self.assertEqual(len(self.asked), 1)
        self.now += beats_service.MISSING_SECONDS + 1
        self.cache.grids(["c", "x"], self.fetch)
        self.assertEqual(self.asked[-1], "/api/grids?ids=x")  # the missing one again, the found one kept
        self.now += beats_service.FOUND_SECONDS
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
    """The profiles route: this plugin's loudness and ramps with Siansa Beats' grids and AudioMuse-AI's keys."""

    def setUp(self):
        app = Flask(__name__)
        app.secret_key = "test"
        app.register_blueprint(web.bp, url_prefix="/plugins/siansa_beats")
        self.client = app.test_client()
        api.settings.clear()
        api.settings["beats_url"] = "http://beats:8090"
        beats_service.CACHE.clear()
        self.sent, self.down, self.wanted = [], False, []
        own = {r[0]: {"ref_lufs": r[1], "start_ramp": r[2], "end_ramp": r[3], "duration_ms": r[4], **json.loads(r[5])}
               for r in (CONTROL, OTHER)}
        self.patches = [
            (beats_service, "_beats_get", self.fake_beats), (web, "_canonical", lambda ids: {i: {"c": "c-canon", "o": "o-canon"}.get(i, i) for i in ids}),
            (store, "profiles_for", lambda db, ids: {i: own[i] for i in ids if i in own}),
            (beats_service, "song_keys", lambda db, ids: {"c-canon": {"key": "A", "scale": "minor"}}),
            (store, "mark_wanted", lambda db, ids: self.wanted.append(set(ids))),
            (web, "get_db", lambda: None),
        ]
        self.saved = [(m, n, getattr(m, n)) for m, n, _ in self.patches]
        for m, n, v in self.patches:
            setattr(m, n, v)

    def tearDown(self):
        for m, n, v in self.saved:
            setattr(m, n, v)

    def fake_beats(self, path, body=None):
        if self.down:
            raise beats_service.BeatsError("Nothing answers at beats:8090: is Siansa Beats running?")
        if body is not None:
            self.sent.append((path, body))
            return {"id": 7}
        if path == "/api/health":
            return {"status": "ok", "version": "0.13.0"}
        if path == "/api/genres":
            return {"styles": {"available": True}, "genres": [
                {"label": "Electronic---Trance", "genre": "Electronic", "style": "Trance", "songs": 40, "name": "Trance"},
                {"label": "Hip Hop---Electro", "genre": "Hip Hop", "style": "Electro", "songs": 3, "name": "Hip Hop / Electro"}]}
        if path.startswith("/api/genre/about?"):
            return {"name": "Dubstep", "about": {"summary": "Dubstep is a genre of electronic dance music.", "source": "Wikipedia",
                                                  "url": "https://en.wikipedia.org/wiki/Dubstep", "title": "Dubstep"}}
        if path.startswith("/api/genre?"):
            self.asked_genre = path
            return {"ids": ["c", "o"], "styles": ["Electronic---Trance"], "genres": [], "why": None}
        ids = path.split("ids=")[1].split(",")
        return {"grids": [dict(CONTROL_GRID, intro_lock_ms=18.0, mix_track=False, drops_ms=[60_000.0]) for i in ids if i == "c"],
                "missing": [i for i in ids if i != "c"]}

    def test_profiles_carry_its_own_loudness_siansa_beats_grids_and_the_key(self):
        body = self.client.get("/plugins/siansa_beats/api/profiles?ids=c,o,new").get_json()
        by_id = {p["track_id"]: p for p in body["profiles"]}
        control = by_id["c"]
        self.assertEqual((control["ref_lufs"], control["intro_bpm"], control["beat_grid"]), (-9.2, 140.0, "siansa_beats"))
        self.assertEqual((control["intro_lock_ms"], control["drops_ms"], control["mix_track"]), (18.0, [60_000.0], False))
        self.assertEqual((control["key"], control["scale"]), ("A", "minor"))
        self.assertEqual((by_id["o"]["intro_bpm"], by_id["o"]["beat_grid"]), (128.0, "plugin"))  # not in Siansa Beats yet
        self.assertEqual(body["pending"], ["new"])
        self.assertIn("new", self.wanted[0])  # measured first

    def test_siansa_beats_down_keeps_its_own_readings(self):
        self.down = True
        body = self.client.get("/plugins/siansa_beats/api/profiles?ids=c").get_json()
        self.assertEqual(body["profiles"][0]["intro_bpm"], 187.1)

    def test_bad_blends_go_on_to_siansa_beats(self):
        mark = {"from_id": "c", "to_id": "o", "technique": "bass swap", "details": {"bars": 16}, "extra": "dropped"}
        answer = self.client.post("/plugins/siansa_beats/api/bad-blends", json=mark)
        self.assertEqual((answer.status_code, answer.get_json()), (200, {"id": 7}))
        self.assertEqual(self.sent, [("/api/bad-blends", {"from_id": "c", "to_id": "o", "technique": "bass swap", "details": {"bars": 16}})])
        self.assertEqual(self.client.post("/plugins/siansa_beats/api/bad-blends", json={"from_id": "c"}).status_code, 400)
        self.down = True
        self.assertEqual(self.client.post("/plugins/siansa_beats/api/bad-blends", json=mark).status_code, 502)

    def test_the_genre_list_comes_from_siansa_beats_vote(self):
        answer = self.client.get("/plugins/siansa_beats/api/genres?limit=1").get_json()
        self.assertEqual(answer, {"genres": [{"name": "Trance", "style": "Trance", "genre": "Electronic", "songs": 40}],
                                  "available": True, "source": "siansa_beats"})
        self.assertEqual(len(self.client.get("/plugins/siansa_beats/api/genres").get_json()["genres"]), 2)
        self.down = True
        self.assertEqual(self.client.get("/plugins/siansa_beats/api/genres").status_code, 502)

    def test_genre_radios_come_from_siansa_beats_vote(self):
        answer = self.client.get("/plugins/siansa_beats/api/genre?name=Electronic+%2F+Trance&limit=50").get_json()
        self.assertEqual(answer, {"name": "Electronic / Trance", "ids": ["c", "o"], "styles": ["Electronic---Trance"],
                                  "genres": [], "why": None, "source": "siansa_beats"})
        self.assertEqual(self.asked_genre, "/api/genre?name=Electronic+%2F+Trance&limit=50")
        self.assertEqual(self.client.get("/plugins/siansa_beats/api/genre").status_code, 400)
        self.down = True  # the app then plays by its tags
        self.assertEqual(self.client.get("/plugins/siansa_beats/api/genre?name=Trance").status_code, 502)

    def test_a_genre_s_description_comes_from_siansa_beats(self):
        answer = self.client.get("/plugins/siansa_beats/api/genre/about?name=Dubstep").get_json()
        self.assertEqual(answer["about"]["source"], "Wikipedia")
        self.assertIn("electronic dance music", answer["about"]["summary"])
        self.assertEqual(self.client.get("/plugins/siansa_beats/api/genre/about").status_code, 400)
        self.down = True  # the page then shows without it
        self.assertEqual(self.client.get("/plugins/siansa_beats/api/genre/about?name=Dubstep").status_code, 502)

    def test_the_service_part_of_the_page(self):
        page = web.render_service({"reachable": True, "version": "0.13.0"}, "http://beats:8090", True, "plugin")
        self.assertIn("Siansa Beats 0.13.0 answers", page)
        self.assertIn("kept: leave empty to keep it", page)
        self.assertNotIn("beats_password\" value=\"secret", page)
        self.assertIn("address isn't set", web.render_service(None, "", False, "none"))


if __name__ == "__main__":
    unittest.main()
