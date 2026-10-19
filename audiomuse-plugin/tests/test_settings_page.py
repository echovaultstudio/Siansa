import unittest

from flask import Flask

import support  # noqa: F401  (import path and the plugin.api stand-in)
from plugin import api
from musix_analysis import status, web
from musix_analysis.generations import Inputs


class TimingTest(unittest.TestCase):
    def test_rounds_leave_room_for_audiomuse_jobs(self):
        self.assertLess(status.round_seconds(True), status.TICK_SECONDS)
        self.assertLess(status.round_seconds(False), status.round_seconds(True))

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
        self.assertEqual(status.time_left(1000, {"songs": 10, "seconds": 50}, True, 40), "about 2 hours")
        # Normally 40 songs per 15-minute tick: 1,000 songs = 25 ticks = about 6 hours.
        self.assertEqual(status.time_left(1000, {"songs": 10, "seconds": 50}, False, 40), "about 6 hours")
        self.assertEqual(status.duration_text(30), "about 1 minute")
        self.assertEqual(status.duration_text(3 * 86400), "about 3 days")


class SettingsPostTest(unittest.TestCase):
    """Issue: reloading the page after a button sent the form again and AudioMuse refused a second job."""

    def setUp(self):
        self.app = Flask(__name__)
        self.app.register_blueprint(web.bp, url_prefix="/plugins/musix_analysis")
        self.client = self.app.test_client()
        self.states = {}
        self.saved = web.store.set_state
        web.store.set_state = lambda name, value, db=None, commit=True: self.states.__setitem__(name, value)
        self.enqueue = api.enqueue

    def tearDown(self):
        web.store.set_state = self.saved
        web.enqueue = self.enqueue

    def post(self, action):
        return self.client.post("/plugins/musix_analysis/settings", data={"action": action, "batch_size": "40"})

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


if __name__ == "__main__":
    unittest.main()
