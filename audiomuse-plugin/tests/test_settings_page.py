import unittest

from flask import Flask

import support  # noqa: F401  (import path and the plugin.api stand-in)
from plugin import api
from siansa_analysis import status, web
from siansa_analysis.generations import Inputs


class TimingTest(unittest.TestCase):
    def test_rounds_leave_room_for_audiomuse_jobs(self):
        for minutes in (12, 10, 5):
            self.assertLess(status.round_seconds(True, minutes), status.TICK_SECONDS)
        self.assertLess(status.round_seconds(False), status.round_seconds(True, 12))

    def test_measure_all_goes_on_without_a_break_by_default(self):
        # Siansa issue #765: measuring all just carries on until every song is measured, unless rounds are set.
        self.assertIsNone(status.round_seconds(True))
        self.assertEqual(status.round_seconds(True, 10), 600)
        self.assertEqual(status.round_seconds(False, 0), status.NORMAL_ROUND_SECONDS)
        self.assertEqual(status.all_round_minutes("7"), 0)  # not a choice: the default
        self.assertEqual(status.all_round_minutes(None), 0)
        self.assertEqual(status.all_round_minutes("5"), 5)
        self.assertIn("without a break", status.all_round_text(0))
        self.assertIn("rounds of 10 minutes every 15", status.all_round_text(10))

    def test_rate_keeps_the_recent_pace(self):
        rate = status.next_rate({}, 10, 50)
        self.assertEqual(rate, {"songs": 10, "seconds": 50})
        rate = status.next_rate({"songs": 500, "seconds": 5000}, 100, 100)
        self.assertEqual(rate["songs"], 500)  # older songs fade out
        self.assertLess(rate["seconds"], 5000)

    def test_time_left(self):
        self.assertIsNone(status.time_left(100, {}, False, 40))
        self.assertIsNone(status.time_left(0, {"songs": 1, "seconds": 5}, True, 40))
        # 5 s a song, 1,000 songs, 12 of every 15 minutes: 5000 s * 15/12 = about 104 minutes -> 2 hours.
        self.assertEqual(status.time_left(1000, {"songs": 10, "seconds": 50}, True, 40, 12), "about 2 hours")
        # Without a break: 5000 s, about 83 minutes.
        self.assertEqual(status.time_left(1000, {"songs": 10, "seconds": 50}, True, 40), "about 83 minutes")
        # Normally 40 songs per 15-minute tick: 1,000 songs = 25 ticks = about 6 hours.
        self.assertEqual(status.time_left(1000, {"songs": 10, "seconds": 50}, False, 40), "about 6 hours")
        self.assertEqual(status.duration_text(30), "about 1 minute")
        self.assertEqual(status.duration_text(3 * 86400), "about 3 days")


class SettingsPostTest(unittest.TestCase):
    """Issue: reloading the page after a button sent the form again and AudioMuse refused a second job."""

    def setUp(self):
        self.app = Flask(__name__)
        self.app.register_blueprint(web.bp, url_prefix="/plugins/siansa_analysis")
        self.client = self.app.test_client()
        self.states = {}
        self.saved = web.store.set_state
        web.store.set_state = lambda name, value, db=None, commit=True: self.states.__setitem__(name, value)
        self.enqueue = api.enqueue

    def tearDown(self):
        web.store.set_state = self.saved
        web.enqueue = self.enqueue

    def post(self, action):
        return self.client.post("/plugins/siansa_analysis/settings", data={"action": action, "batch_size": "40"})

    def test_buttons_redirect_so_a_reload_sends_nothing(self):
        queued = []
        web.enqueue = lambda func, **k: queued.append(func.__name__)
        answer = self.post("measure")
        self.assertEqual(answer.status_code, 302)
        self.assertTrue(answer.headers["Location"].endswith("/settings?note=measuring"))
        self.assertEqual(queued, ["prepare_now"])

    def test_a_busy_queue_is_a_note_not_an_error(self):
        def busy(func, **k):
            raise RuntimeError("Another queue job is already running")
        web.enqueue = busy
        self.assertTrue(self.post("rebuild").headers["Location"].endswith("note=busy"))
        answer = self.post("measure_all")
        self.assertTrue(answer.headers["Location"].endswith("note=all_waiting"))
        self.assertTrue(self.states["measure_all"]["on"])  # the tick starts it anyway
        self.post("stop_all")
        self.assertFalse(self.states["measure_all"]["on"])


class MeasureAllPageTest(unittest.TestCase):
    def state(self, measure_all=False, running=False):
        inputs = Inputs("L", 3, 10, "A", 5, 8)
        counts = status.profile_counts(10, 4, 0)
        job = {"status": "running"} if running else {}
        return {
            "inputs": inputs, "counts": counts, "built": {}, "profiles_job": job, "similar_job": {},
            "profiles": status.profiles(counts, job), "similar": status.similar(inputs, {}, {}, 1),
            "measure_all": measure_all, "rate": {"songs": 10, "seconds": 50},
        }

    def test_offer_with_a_warning_and_a_confirm(self):
        page = web.render_settings(self.state(), 40)
        self.assertIn("Measure all 6 songs", page)
        self.assertIn("return confirm(", page)
        self.assertIn("waits with its own jobs", page)
        self.assertIn("every song is measured in about", page)

    def test_running_shows_stop_and_disables_buttons(self):
        page = web.render_settings(self.state(measure_all=True, running=True), 40)
        self.assertIn('value="stop_all"', page)
        self.assertNotIn('value="measure_all"', page)
        self.assertIn('value="measure" disabled', page)

    def test_notes_are_shown(self):
        self.assertIn(web.MESSAGES["busy"], web.render_settings(self.state(), 40, web.MESSAGES["busy"]))


class BeatGridsTest(unittest.TestCase):
    """Beat grids have their own part on the page (Siansa #646): counts, a batch setting, measure now and all."""

    def state(self, beats_all=False, running=False, waiting=30, failures=(), failed=10, elsewhere=0, reasons=()):
        inputs = Inputs("L", 3, 10, "A", 5, 8)
        counts = status.profile_counts(100, 100, 0)
        beat_counts = status.beats_counts(60, 45, failed, waiting, elsewhere)
        job = {"status": "running"} if running else {}
        return {
            "inputs": inputs, "counts": counts, "built": {}, "profiles_job": {}, "similar_job": {},
            "profiles": status.profiles(counts, {}), "similar": status.similar(inputs, {}, {}, 1),
            "beat_counts": beat_counts, "beats_job": job, "beats": status.beats(beat_counts, job),
            "beats_all": beats_all, "beats_failures": list(failures), "beats_reasons": list(reasons),
            "rate": {"songs": 10, "seconds": 50},
        }

    def test_counts_and_labels(self):
        c = status.beats_counts(60, 45, 10, 30, 5)
        self.assertEqual(c, {"songs": 100, "done": 60, "with_beat": 45, "failed": 10, "waiting": 30, "to_measure": 40, "elsewhere": 5})
        self.assertEqual(status.beats(c, {}).label, "Not complete")
        self.assertEqual(status.beats(c, {}).percent, 60)
        self.assertEqual(status.beats(c, {"status": "running"}).label, "Measuring")
        # Issue #657: songs that failed are still to measure, so it isn't Ready, and with only those left it needs attention.
        self.assertEqual(status.beats(status.beats_counts(90, 70, 10, 0), {}).label, "Needs attention")
        self.assertEqual(status.beats_counts(90, 70, 10, 0)["to_measure"], 10)
        self.assertEqual(status.beats(status.beats_counts(100, 70, 0, 0, 40000), {}).label, "Ready")  # other servers don't count
        self.assertEqual(status.beats(status.beats_counts(0, 0, 0, 0), {}).label, "Waiting for profiles")
        self.assertIn("45 with a beat at either end", status.beats(c, {}).detail)
        self.assertIn("10 couldn't be read yet", status.beats(c, {}).detail)

    def test_its_own_section_with_settings_and_buttons(self):
        page = web.render_settings(self.state(), 40, "", 20)
        self.assertIn("3. Beat grids", page)
        self.assertIn("4. Similar albums", page)
        self.assertIn("40 still to measure", page)
        self.assertIn('name="beats_batch_size" value="20"', page)
        self.assertIn('value="measure_beats"', page)
        self.assertIn("Measure all 40 songs for their beats", page)
        self.assertIn("every song has its beat grid in about", page)
        self.assertIn("Their loudness and fades stay in use meanwhile", page)

    def test_measuring_all_shows_stop_and_nothing_left_hides_the_offer(self):
        page = web.render_settings(self.state(beats_all=True, running=True), 40, "", 20)
        self.assertIn('value="stop_beats_all"', page)
        self.assertNotIn('value="beats_all"', page)
        self.assertIn('value="measure_beats" disabled', page)
        self.assertIn('value="measure" disabled', page)  # one song at a time across both parts
        done = web.render_settings(self.state(waiting=0, failed=0), 40, "", 20)
        self.assertNotIn("for their beats</button>", done)
        self.assertIn('value="measure_beats" disabled', done)

    def test_zero_per_run_means_only_when_asked(self):
        page = web.render_settings(self.state(), 40, "", 0)
        self.assertIn("only when you press a button here", page)
        self.assertNotIn("every song has its beat grid in", page)

    def test_failures_are_listed_with_their_reasons_and_a_retry(self):
        page = web.render_settings(self.state(failures=[("x", "Song", "Artist", "unreadable")], reasons=[("unreadable", 9), ("timeout", 1)]), 40, "", 20)
        self.assertIn("Why 10 songs couldn&#x27;t be read yet", page.replace("'", "&#x27;"))
        self.assertIn("9 songs: unreadable", page)
        self.assertIn("Song – Artist: unreadable", page)
        self.assertIn('value="retry_beats"', page)
        self.assertNotIn('value="retry_beats"', web.render_settings(self.state(failed=0), 40, "", 20))

    def test_songs_on_no_server_are_said_apart(self):
        page = web.render_settings(self.state(elsewhere=41027), 40, "", 20)
        self.assertIn("41,027 songs aren&#x27;t on any of AudioMuse", page.replace("'", "&#x27;"))
        self.assertNotIn("on any of AudioMuse", web.render_settings(self.state(), 40, "", 20))

    def test_any_number_of_songs_per_run(self):
        page = web.render_settings(self.state(), 40, "", 50000)
        self.assertIn('name="beats_batch_size" value="50000"', page)
        self.assertIn("as many as fit", page)

    def test_batch_size_allows_zero_for_beats_only(self):
        from siansa_analysis import jobs
        self.assertEqual(jobs.batch_size("beats", 0), 0)
        self.assertEqual(jobs.batch_size("profiles", 0), 1)
        self.assertEqual(jobs.batch_size("beats", 900), 900)
        self.assertEqual(jobs.batch_size("beats", 10 ** 9), jobs.MAX_BEATS_BATCH)
        self.assertEqual(jobs.batch_size("profiles", 900), 500)
        self.assertEqual(jobs.batch_size("beats", "x"), jobs.DEFAULT_BEATS_BATCH)


class BeatButtonsTest(SettingsPostTest):
    def test_measure_save_all_and_stop(self):
        queued = []
        web.enqueue = lambda func, **k: queued.append(func.__name__)
        answer = self.post_beats("measure_beats", "7")
        self.assertTrue(answer.headers["Location"].endswith("note=beats_measuring"))
        self.assertEqual(queued, ["beats_now"])
        self.assertEqual(api.settings["beats_batch_size"], 7)
        self.assertTrue(self.post_beats("save_beats", "0").headers["Location"].endswith("note=saved"))
        self.assertEqual(api.settings["beats_batch_size"], 0)
        self.assertTrue(self.post_beats("beats_all").headers["Location"].endswith("note=beats_all"))
        self.assertTrue(self.states["beats_all"]["on"])
        self.assertNotIn("measure_all", self.states)  # the other part's switch is left alone
        self.post_beats("stop_beats_all")
        self.assertFalse(self.states["beats_all"]["on"])
        self.post_beats("save_beats", "1000000000")
        self.assertEqual(api.settings["beats_batch_size"], jobs_module().MAX_BEATS_BATCH)
        saved = web.store.retry_beats
        web.store.retry_beats = lambda db: 41027
        try:
            answer = self.post_beats("retry_beats")
            self.assertIn("note=beats_retry", answer.headers["Location"])
            self.assertIn("n=41027", answer.headers["Location"])
        finally:
            web.store.retry_beats = saved

    def post_beats(self, action, size="20"):
        return self.client.post("/plugins/siansa_analysis/settings", data={"action": action, "beats_batch_size": size})


def jobs_module():
    from siansa_analysis import jobs
    return jobs


class TickTest(unittest.TestCase):
    def test_beats_get_the_time_the_profiles_round_left(self):
        from siansa_analysis import jobs
        calls = {}
        saved = {n: getattr(jobs, n) for n in ("build_similar", "measure_missing", "measure_beats", "measure_all_on")}
        clock = [1000.0]
        jobs.build_similar = lambda: {"status": "up to date"}

        def profiles():
            clock[0] += 100  # the profiles round took 100 s
            return {"status": "idle"}

        jobs.measure_missing = profiles
        jobs.measure_beats = lambda budget_seconds=None: calls.setdefault("budget", budget_seconds) and {"status": "idle"}
        jobs.measure_all_on = lambda db, part="profiles": part == "beats" and calls.get("all", False)
        real = jobs.time.monotonic
        jobs.time.monotonic = lambda: clock[0]
        api.settings["all_round_minutes"] = 12  # rounds, as before Siansa issue #765
        try:
            jobs.tick()
            self.assertAlmostEqual(calls["budget"], status.NORMAL_ROUND_SECONDS - 100)
            calls.clear()
            calls["all"] = True
            clock[0] = 1000.0
            jobs.tick()
            self.assertAlmostEqual(calls["budget"], status.ALL_ROUND_SECONDS - 100)
            calls.clear()
            clock[0] = 1000.0
            jobs.measure_missing = lambda: clock.__setitem__(0, clock[0] + status.NORMAL_ROUND_SECONDS) or {}
            self.assertEqual(jobs.tick()["beats"]["note"], "no time left this round")
            self.assertNotIn("budget", calls)
            # Without a break (the default since issue #765), the beat grids measured all get no time limit.
            api.settings.pop("all_round_minutes")
            calls.clear()
            calls["all"] = True
            clock[0] = 1000.0
            jobs.measure_beats = lambda budget_seconds=None: calls.__setitem__("budget", budget_seconds) or {"status": "idle"}
            jobs.tick()
            self.assertIn("budget", calls)
            self.assertIsNone(calls["budget"])
            # Stop now during the profiles: the beat grids don't start.
            calls.clear()
            jobs.measure_missing = lambda: {"status": "idle", "note": "stopped by hand"}
            self.assertEqual(jobs.tick()["beats"]["note"], "stopped by hand")
            self.assertNotIn("budget", calls)
        finally:
            api.settings.pop("all_round_minutes", None)
            jobs.time.monotonic = real
            for n, f in saved.items():
                setattr(jobs, n, f)


if __name__ == "__main__":
    unittest.main()
