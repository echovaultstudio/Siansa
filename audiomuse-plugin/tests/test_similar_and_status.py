import unittest

import numpy as np

import support  # noqa: F401  (import path and the plugin.api stand-in)
from siansa_analysis import generations, similarity, status, web
from siansa_analysis.generations import Inputs


def vec(*values):
    return np.asarray(values, dtype=np.float32)


class SimilarityTest(unittest.TestCase):
    def setUp(self):
        # Three "sounds": x, y and z. Two artists sound like x, one like y, one like z.
        self.tracks = [
            ("a1", "Night", "Ann", "Ann", vec(1, 0.1, 0)),
            ("a2", "Night", "Ann", "Ann", vec(1, 0, 0.1)),
            ("a3", "Night (Deluxe Edition)", "Ann", "Ann", vec(1, 0.05, 0.05)),
            ("a4", "Day", "Ann", "Ann", vec(0.9, 0.2, 0)),
            ("a5", "Dusk", "Ann", "Ann", vec(0.95, 0.1, 0)),
            ("a6", "Dawn", "Ann", "Ann", vec(0.97, 0.1, 0)),
            ("b1", "Coast", "Bea", "Bea", vec(0.95, 0.15, 0)),
            ("c1", "Engine", "Cal", "Cal", vec(0, 1, 0)),
            ("d1", "Snow", "Dee", "Dee", vec(0, 0, 1)),
            ("x1", "Mix", "Various", "Bea", vec(0.9, 0.1, 0)),
            ("n1", "No vector", "Eve", "Eve", None),
        ]
        self.albums, self.artists = similarity.group_tracks(self.tracks)

    def test_groups(self):
        self.assertEqual(len(self.albums["ann\x1fnight"].vectors), 2)
        self.assertNotIn("eve", self.artists)  # no analysis, no entry
        self.assertEqual(len(self.artists["bea"].vectors), 2)  # the compilation song counts for Bea

    def test_editions_and_per_artist_cap(self):
        lists = similarity.rank_albums(self.albums)
        night = lists["ann\x1fnight"]
        names = [(c["artist"], c["album"]) for c in night]
        self.assertNotIn(("Ann", "Night (Deluxe Edition)"), names)
        self.assertLessEqual(sum(1 for artist, _ in names if artist == "Ann"), similarity.PER_ARTIST)
        self.assertIn(names[-1], [("Dee", "Snow"), ("Cal", "Engine")])  # the least alike come last
        scores = [c["score"] for c in night]
        self.assertEqual(scores, sorted(scores, reverse=True))
        self.assertTrue(all(c["track_id"] for c in night))

    def test_artists(self):
        lists = similarity.rank_artists(self.artists)
        self.assertEqual(lists["ann"][0]["artist"], "Bea")
        self.assertNotIn("Ann", [c["artist"] for c in lists["ann"]])

    def test_blocks_give_the_same_answer(self):
        rng = np.random.default_rng(7)
        tracks = [(f"t{i}", f"Album {i}", f"Artist {i % 40}", f"Artist {i % 40}", rng.normal(size=16).astype(np.float32))
                  for i in range(300)]
        albums, _ = similarity.group_tracks(tracks)
        whole = similarity.rank_albums(albums)
        old = similarity.BLOCK
        similarity.BLOCK = 37
        try:
            self.assertEqual(similarity.rank_albums(albums), whole)
        finally:
            similarity.BLOCK = old

    def test_edition_titles(self):
        self.assertEqual(similarity.base_title("Night (Deluxe Edition) [2011 Remaster]"), "night")
        self.assertEqual(similarity.base_title("Live at Leeds"), "live at leeds")


class GenerationTest(unittest.TestCase):
    def test_a_generation_moves_only_when_its_digest_changes(self):
        first = generations.advance(Inputs(), "L1", 10, "A1", 8)
        self.assertEqual((first.library_generation, first.analysis_generation), (1, 1))
        same = generations.advance(first, "L1", 10, "A1", 8)
        self.assertEqual(same, first)
        library = generations.advance(first, "L2", 11, "A1", 8)
        self.assertEqual((library.library_generation, library.analysis_generation), (2, 1))
        analysis = generations.advance(library, "L2", 11, "A2", 9)
        self.assertEqual((analysis.library_generation, analysis.analysis_generation), (2, 2))

    def test_rebuild_reasons(self):
        inputs = Inputs("L", 3, 10, "A", 5, 8)
        built = {"library_generation": 3, "analysis_generation": 5, "algorithm_version": 1}
        self.assertIsNone(generations.rebuild_reason(inputs, built, 1))
        self.assertEqual(generations.rebuild_reason(inputs, {}, 1), "first build")
        self.assertEqual(generations.rebuild_reason(inputs, built, 2), "new algorithm")
        self.assertEqual(generations.rebuild_reason(inputs, {**built, "library_generation": 2}, 1), "library changed")
        self.assertEqual(generations.rebuild_reason(inputs, {**built, "analysis_generation": 4}, 1), "sound analysis changed")
        self.assertEqual(generations.rebuild_reason(inputs, {**built, "library_generation": 2, "analysis_generation": 4}, 1),
                         "library changed and sound analysis changed")
        self.assertIsNone(generations.rebuild_reason(Inputs("L", 1, 10, "", 0, 0), {}, 1))  # nothing analysed yet

    def test_round_trip(self):
        inputs = Inputs("L", 3, 10, "A", 5, 8)
        self.assertEqual(Inputs.from_json(inputs.to_json()), inputs)
        self.assertEqual(Inputs.from_json(None), Inputs())


class StatusTest(unittest.TestCase):
    def test_profile_counts(self):
        self.assertEqual(status.profile_counts(10, 6, 3, 2),
                         {"songs": 10, "ready": 6, "failed": 3, "to_measure": 1, "asked_for": 2})
        self.assertEqual(status.profile_counts(5, 9, 9)["ready"], 5)  # never above the library

    def test_profile_labels(self):
        self.assertEqual(status.profiles(status.profile_counts(0, 0, 0), {}).label, "Waiting for library")
        self.assertEqual(status.profiles(status.profile_counts(10, 10, 0), {}).label, "Ready")
        running = status.profiles(status.profile_counts(10, 4, 0), {"status": "running"})
        self.assertEqual((running.label, running.percent), ("Measuring", 40))
        self.assertEqual(status.profiles(status.profile_counts(10, 7, 3), {}).label, "Needs attention")
        self.assertEqual(status.profiles(status.profile_counts(10, 7, 1), {}).label, "Not complete")

    def test_similar_labels(self):
        inputs = Inputs("L", 3, 10, "A", 5, 8)
        built = {"library_generation": 3, "analysis_generation": 5, "algorithm_version": 1, "albums": 4, "artists": 2}
        self.assertEqual(status.similar(Inputs(), {}, {}, 1).label, "Waiting for inputs")
        self.assertEqual(status.similar(inputs, built, {}, 1).label, "Up to date")
        pending = status.similar(inputs, {**built, "analysis_generation": 4}, {}, 1)
        self.assertEqual(pending.label, "Update pending")
        self.assertIn("stay in use", pending.detail)
        building = status.similar(inputs, built, {"status": "running", "done": 1, "total": 4, "phase": "Albums"}, 1)
        self.assertEqual((building.label, building.percent), ("Building", 25))
        failed = status.similar(inputs, {}, {"status": "failed", "error": "boom"}, 1)
        self.assertEqual(failed.label, "Needs attention")

    def test_the_parts_are_independent(self):
        # Issue #188: the profiles' status comes from their counts and their own job only, and the similar
        # lists' from their inputs, last build and own job only. Whatever one part does, the other says the same.
        saved = {}
        state = {"inputs": Inputs("L", 3, 10, "A", 5, 8).to_json(), "profiles_job": {}, "similar_job": {},
                 "similar_built": {"library_generation": 3, "analysis_generation": 5, "algorithm_version": 1}}

        def get_state(name, default=None, db=None):
            return state.get(name, default)

        for name, fake in (("get_state", get_state), ("profile_counts", lambda db: (10, 4, 0, 0))):
            saved[name] = getattr(web.store, name)
            setattr(web.store, name, fake)
        try:
            baseline = web.collect(None)
            for job in ({"status": "running", "done": 1, "total": 9}, {"status": "failed", "error": "x"}):
                state["similar_job"] = job
                self.assertEqual(web.collect(None)["profiles"], baseline["profiles"])
            state["similar_job"] = {}
            for job in ({"status": "running"}, {"status": "failed", "error": "x"}):
                state["profiles_job"] = job
                self.assertEqual(web.collect(None)["similar"], baseline["similar"])
            state["profiles_job"] = {}
            state["inputs"] = Inputs("L", 3, 10, "A2", 6, 8).to_json()  # the analysis moved on
            self.assertEqual(web.collect(None)["similar"].label, "Update pending")
            self.assertEqual(web.collect(None)["profiles"], baseline["profiles"])
        finally:
            for name, original in saved.items():
                setattr(web.store, name, original)


class PageTest(unittest.TestCase):
    def state(self, profiles_job=None, similar_job=None):
        inputs = Inputs("L", 3, 10, "A", 5, 8)
        counts = status.profile_counts(10, 4, 1, 2)
        built = {"library_generation": 2, "analysis_generation": 5, "algorithm_version": 1}
        return {
            "inputs": inputs, "counts": counts, "built": built,
            "profiles_job": profiles_job or {}, "similar_job": similar_job or {},
            "profiles": status.profiles(counts, profiles_job or {}),
            "similar": status.similar(inputs, built, similar_job or {}, 1),
            "failures": [("t1", "Song <1>", "Ann", "no audio", 2)],
        }

    def test_three_sections_with_their_own_status(self):
        page = web.render_settings(self.state(), 40)
        self.assertIn("1. Library", page)
        self.assertIn("2. Volume &amp; fade profiles", page)
        self.assertIn("3. Similar albums &amp; artists", page)
        self.assertIn("4 of 10 songs measured", page)
        self.assertIn("Update pending", page)
        self.assertIn("library generation 2 of\n      3", page)
        self.assertIn("Song &lt;1&gt;", page)  # escaped
        self.assertNotIn("location.reload", page)

    def test_reloads_while_working(self):
        page = web.render_settings(self.state(profiles_job={"status": "running"}), 40)
        self.assertIn("location.reload", page)


if __name__ == "__main__":
    unittest.main()
