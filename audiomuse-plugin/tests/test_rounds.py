"""Where measuring every song stands (Siansa issue #765): a round running, between rounds, waiting and why, and
the last round, so the page never looks stuck while the next round is simply on its way."""
import unittest
from datetime import datetime, timedelta, timezone

import support  # noqa: F401  (import path and the plugin.api stand-in)
from siansa_analysis import status, web
from siansa_analysis.generations import Inputs

NOW = datetime(2026, 10, 31, 16, 58, 30, tzinfo=timezone.utc)


def at(minutes_ago):
    return (NOW - timedelta(minutes=minutes_ago)).isoformat(timespec="seconds")


class RoundStatusTest(unittest.TestCase):
    def test_a_round_running(self):
        line, attention = status.round_status({"status": "running", "started_at": at(1), "measured": 120}, {}, {}, None, None, NOW, 12)
        self.assertEqual(line, "A round is running: 120 measured so far.")
        self.assertFalse(attention)

    def test_between_rounds_says_when_the_next_starts(self):
        # The owner's case: a round of 12 minutes finished at 16:56:56; the next is due at about 17:00.
        tick = {"started_at": at(13.5), "finished_at": at(1.5)}
        line, attention = status.round_status({"status": "idle"}, {}, tick, {"enabled": True}, None, NOW, 12)
        self.assertEqual(line, "Between rounds: the next one starts in about 2 minutes.")
        self.assertFalse(attention)

    def test_a_round_that_never_finished_is_shown_as_stopped(self):
        job = {"status": "running", "started_at": at(40), "measured": 300}
        self.assertFalse(status.job_running(job, NOW))
        line, _ = status.round_status(job, {}, {"started_at": at(41)}, {"enabled": True}, None, NOW)
        self.assertTrue(line.startswith("The last round stopped without finishing, 40 minutes ago"), line)

    def test_the_scheduled_task_switched_off(self):
        line, attention = status.round_status({}, {}, {"started_at": at(60)}, {"enabled": False}, None, NOW)
        self.assertIn("scheduled task is switched off", line)
        self.assertTrue(attention)

    def test_waiting_for_audiomuses_own_job(self):
        line, attention = status.round_status({}, {}, {"started_at": at(20)}, {"enabled": True},
                                              {"task_type": "main_analysis", "status": "PROGRESS"}, NOW)
        self.assertEqual(line, "Waiting for AudioMuse's own job (main analysis) to finish; the rounds carry on after it.")
        self.assertFalse(attention)

    def test_our_own_tick_holding_the_queue_isnt_a_wait(self):
        line, _ = status.round_status({}, {}, {"started_at": at(5)}, {"enabled": True},
                                      {"task_type": "plugin.siansa_analysis.tick", "status": "STARTED"}, NOW, 12)
        self.assertTrue(line.startswith("Between rounds"), line)

    def test_without_a_break(self):
        # Measuring all without a break (the default): one long run, and after a restart it starts again with the
        # next scheduled run.
        line, _ = status.round_status({"status": "running", "started_at": at(1), "measured": 4200}, {}, {}, None, None, NOW)
        self.assertEqual(line, "Measuring without a break: 4,200 measured so far.")
        line, _ = status.round_status({"status": "idle"}, {}, {"started_at": at(13.5)}, {"enabled": True}, None, NOW)
        self.assertEqual(line, "Starts again with the next scheduled run, in about 2 minutes.")

    def test_no_round_for_long_needs_attention(self):
        line, attention = status.round_status({}, {}, {"started_at": at(50)}, {"enabled": True}, None, NOW)
        self.assertTrue(line.startswith("No round has started since 50 minutes ago"), line)
        self.assertTrue(attention)

    def test_before_the_first_round(self):
        line, _ = status.round_status({}, {}, {}, None, None, NOW)
        self.assertIn("first round starts", line)


class LastRoundTest(unittest.TestCase):
    def test_what_the_last_round_did(self):
        tick = {"started_at": at(13.5), "finished_at": at(1.5), "parts": {
            "similar": {"status": "current", "seconds": 0.4},
            "profiles": {"status": "idle", "measured": 0, "seconds": 2},
            "beats": {"status": "idle", "measured": 542, "failed": 0, "seconds": 715}}}
        self.assertEqual(status.last_round_text(tick, NOW), "Last round, 1 minute ago: 542 songs measured for their beats in 12 minutes.")

    def test_says_when_the_beats_got_no_time(self):
        tick = {"finished_at": at(3), "parts": {"similar": {"status": "built", "seconds": 690},
                                                "beats": {"status": "idle", "note": "no time left this round"}}}
        text = status.last_round_text(tick, NOW)
        self.assertIn("no time was left for beat grids", text)
        self.assertIn("the similar lists were built again in 12 minutes", text)

    def test_nothing_before_the_first_round(self):
        self.assertEqual(status.last_round_text({}, NOW), "")
        self.assertEqual(status.last_round_text({"started_at": at(1)}, NOW), "")


class PageTest(unittest.TestCase):
    def state(self, tick):
        inputs = Inputs("L", 3, 10, "A", 5, 8)
        counts = status.profile_counts(10, 10, 0)
        beat_counts = status.beats_counts(3060, 3059, 0, 0)
        return {
            "inputs": inputs, "counts": counts, "built": {}, "profiles_job": {}, "similar_job": {},
            "profiles": status.profiles(counts, {}), "similar": status.similar(inputs, {}, {}, 1),
            "beat_counts": beat_counts, "beats_job": {"status": "idle", "finished_at": tick.get("finished_at")},
            "beats": status.beats(beat_counts, {}), "beats_all": True, "rate": {"songs": 10, "seconds": 13},
            "all_round_minutes": 12,
            "last_tick": tick, "cron": {"enabled": True}, "blocking": None,
        }

    def test_measuring_all_keeps_the_page_current_between_rounds(self):
        tick = {"started_at": (datetime.now(timezone.utc) - timedelta(minutes=13)).isoformat(timespec="seconds"),
                "finished_at": (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat(timespec="seconds"),
                "parts": {"beats": {"measured": 542, "seconds": 715}}}
        page = web.render_settings(self.state(tick), 40, "", 50000)
        self.assertIn("location.reload()}setTimeout(tick,30000)", page)
        # Not while a field is in use or changed: a number being typed would be lost.
        self.assertIn("activeElement", page)
        self.assertIn("dirty=true", page)
        self.assertIn("Between rounds: the next one starts in about", page)
        self.assertIn("542 songs measured for their beats", page)
        self.assertIn('value="stop_beats_all"', page)


class MeasureRunTest(unittest.TestCase):
    """Measure all goes on without a break by default, rounds can be set, and Stop now ends any run (issue #765)."""

    def setUp(self):
        import os
        import tempfile

        from plugin import api
        from siansa_analysis import jobs

        self.api, self.jobs = api, jobs
        self.state, self.measured = {}, []
        self.songs = [f"s{i}" for i in range(30)]
        self.clock = [1000.0]
        self.on_song = None
        folder = tempfile.mkdtemp()

        def download(item_id, title, folder_):
            path = os.path.join(folder, item_id)
            open(path, "w").close()
            return path

        def measure(path, seconds):
            self.clock[0] += 100  # 100 s a song
            if self.on_song:
                self.on_song(len(self.measured) + 1)
            return object()

        store = jobs.store
        self.saved = {
            (store, n): getattr(store, n) for n in ("try_lock", "unlock", "get_state", "set_state", "songs_to_measure",
                                                    "save_profile")}
        self.saved.update({(jobs, n): getattr(jobs, n) for n in ("get_db", "_download", "profile_of")})
        self.saved[(jobs.loudness, "measure_file")] = jobs.loudness.measure_file
        self.saved[(jobs.time, "monotonic")] = jobs.time.monotonic
        store.try_lock = lambda db, name: True
        store.unlock = lambda db, name: None
        store.get_state = lambda name, default=None, db=None: self.state.get(name, default)
        store.set_state = lambda name, value, db=None, commit=True: self.state.__setitem__(name, value)
        store.songs_to_measure = lambda db, n, beats=False: [(i, i) for i in self.songs if i not in self.measured][:n]
        store.save_profile = lambda db, item_id, profile: self.measured.append(item_id)
        jobs.get_db = lambda: None
        jobs._download = download
        jobs.profile_of = lambda m: {}
        jobs.loudness.measure_file = measure
        jobs.time.monotonic = lambda: self.clock[0]

    def tearDown(self):
        for (owner, name), value in self.saved.items():
            setattr(owner, name, value)
        self.api.settings.pop("all_round_minutes", None)

    def test_measure_all_goes_on_until_every_song_is_measured(self):
        self.state["measure_all"] = {"on": True}
        result = self.jobs.measure_missing()
        self.assertEqual(result["measured"], 30)  # 3,000 s: well past a 12-minute round
        self.assertFalse(self.state["measure_all"]["on"])  # done, so it goes off

    def test_rounds_can_be_set(self):
        self.api.settings["all_round_minutes"] = 12
        self.state["measure_all"] = {"on": True}
        result = self.jobs.measure_missing()
        self.assertEqual(result["measured"], 8)  # 12 minutes at 100 s a song
        self.assertTrue(self.state["measure_all"]["on"])  # the next round carries on

    def test_stop_now_ends_a_run_measuring_all(self):
        self.state["measure_all"] = {"on": True}
        self.state["beats_all"] = {"on": True}
        self.on_song = lambda n: n == 3 and self.jobs.cancel()
        result = self.jobs.measure_missing()
        self.assertEqual(result["measured"], 3)
        self.assertEqual(result["note"], "stopped by hand")
        self.assertFalse(self.state["measure_all"]["on"])
        self.assertFalse(self.state["beats_all"]["on"])  # nothing starts again by itself
        self.assertEqual(self.state["profiles_job"]["status"], "idle")

    def test_stop_now_ends_a_normal_batch_too(self):
        self.api.settings["batch_size"] = 20
        self.on_song = lambda n: n == 2 and self.jobs.cancel()
        result = self.jobs.measure_missing()
        self.assertEqual(result["measured"], 2)
        self.assertEqual(result["note"], "stopped by hand")
        self.api.settings.pop("batch_size")

    def test_an_earlier_stop_doesnt_stop_a_new_run(self):
        self.state["cancel"] = {"at": "2026-01-01T00:00:00+00:00"}
        self.api.settings["batch_size"] = 2
        self.assertEqual(self.jobs.measure_missing()["measured"], 2)
        self.api.settings.pop("batch_size")

    def test_the_page_tells_a_long_run_apart_from_a_stopped_one(self):
        # Progress is written at least every 10 songs or every minute, so a run without a break never looks stale.
        writes = []
        set_state = self.jobs.store.set_state
        self.jobs.store.set_state = lambda name, value, db=None, commit=True: (
            writes.append(value) if name == "profiles_job" and value.get("status") == "running" else None,
            set_state(name, value))
        self.state["measure_all"] = {"on": True}
        self.jobs.measure_missing()
        self.assertGreaterEqual(len(writes), 29)  # a song is 100 s here, so after every one


class StopNowPageTest(unittest.TestCase):
    def state(self, job, measure_all=False):
        return {
            "inputs": Inputs("L", 3, 100, "A", 5, 100),
            "counts": status.profile_counts(100, 40, 0), "built": {},
            "profiles": status.profiles(status.profile_counts(100, 40, 0), job), "profiles_job": job,
            "similar": status.Status("Up to date", status.READY, 100, ""), "similar_job": {},
            "beat_counts": status.beats_counts(40, 30, 0, 60), "beats_job": {},
            "measure_all": measure_all, "beats_all": False, "rate": {},
            "last_tick": {}, "cron": {"enabled": True}, "blocking": None,
        }

    def test_stop_now_shows_while_measuring_whatever_started_it(self):
        running = {"status": "running", "started_at": datetime.now(timezone.utc).isoformat(timespec="seconds"), "measured": 5}
        page = web.render_settings(self.state(running), 40)
        self.assertIn('value="cancel"', page)
        self.assertNotIn("Measure all goes off", page)
        self.assertIn("Measure all goes off", web.render_settings(self.state(running, measure_all=True), 40))
        self.assertNotIn('value="cancel"', web.render_settings(self.state({"status": "idle"}), 40))

    def test_the_round_choice_is_on_the_page(self):
        page = web.render_settings(self.state({"status": "idle"}, measure_all=True), 40)
        self.assertIn('name="all_round_minutes"', page)
        self.assertIn('<option value="0" selected>Without a break, until all are done</option>', page)
        self.assertIn("without a break until every song is measured", page)
        page = web.render_settings(dict(self.state({"status": "idle"}, measure_all=True), all_round_minutes=10), 40)
        self.assertIn('<option value="10" selected>', page)
        self.assertIn("rounds of 10 minutes every 15", page)


if __name__ == "__main__":
    unittest.main()
