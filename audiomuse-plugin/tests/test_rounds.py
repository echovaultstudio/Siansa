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
        line, attention = status.round_status({"status": "running", "started_at": at(1), "measured": 120}, {}, {}, None, None, NOW)
        self.assertEqual(line, "A round is running: 120 measured so far.")
        self.assertFalse(attention)

    def test_between_rounds_says_when_the_next_starts(self):
        # The owner's case: a round of 12 minutes finished at 16:56:56; the next is due at about 17:00.
        tick = {"started_at": at(13.5), "finished_at": at(1.5)}
        line, attention = status.round_status({"status": "idle"}, {}, tick, {"enabled": True}, None, NOW)
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
                                      {"task_type": "plugin.siansa_analysis.tick", "status": "STARTED"}, NOW)
        self.assertTrue(line.startswith("Between rounds"), line)

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


if __name__ == "__main__":
    unittest.main()
