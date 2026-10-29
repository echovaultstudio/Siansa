"""The beat grid at each end of a song (Siansa issue #646): tempo, beat position, bars, rhythm, steadiness."""
import unittest

import numpy as np

import support  # noqa: F401
from siansa_analysis import beats, jobs, loudness

RATE = 48_000


def kick(freq=55.0, seconds=0.12):
    n = int(seconds * RATE)
    return np.sin(2 * np.pi * freq * np.arange(n) / RATE) * np.exp(-np.arange(n) / (0.03 * RATE))


def drums(bpm, seconds=30.0, first=0.3, accent=True, noise=0.0, drift=0.0, seed=1):
    """A kick on every beat (louder on the first of four with ``accent``), a hat on every other one."""
    rng = np.random.default_rng(seed)
    x = rng.normal(0, noise, int(seconds * RATE)) if noise else np.zeros(int(seconds * RATE))
    t, k = first, 0
    while t < seconds:
        period = 60 / bpm * (1 + drift * np.sin(k / 7))
        s = int(t * RATE)
        hit = kick() * (1.0 if accent and k % 4 == 0 else 0.6)
        x[s:s + hit.size] += hit[:x.size - s]
        if k % 2 == 1:
            hat = rng.normal(0, 0.1, 2000) * np.exp(-np.arange(2000) / 400)
            x[s:s + 2000] += hat[:x.size - s]
        t += period
        k += 1
    return x.astype(np.float32)


class GridTest(unittest.TestCase):
    def test_tempo_to_a_fraction_and_the_beats_to_a_few_ms(self):
        g = beats.grid(drums(128.0), 0)
        self.assertAlmostEqual(g.bpm, 128.0, delta=0.05)
        # The first beat is at 300 ms; the one reported is on that grid, 468.75 ms apart.
        self.assertAlmostEqual((g.beat_ms - 300) % 468.75 if (g.beat_ms - 300) % 468.75 < 234 else 468.75 - (g.beat_ms - 300) % 468.75, 0, delta=12)
        self.assertGreater(g.rhythm, 0.8)
        self.assertGreater(g.steady, 0.7)
        g = beats.grid(drums(92.5, first=0.5), 0)
        self.assertAlmostEqual(g.bpm, 92.5, delta=0.05)

    def test_the_downbeat_is_the_accented_beat(self):
        g = beats.grid(drums(128.0), 0)
        self.assertIsNotNone(g.bar_ms)
        off = (g.bar_ms - 300) % 1875  # bars are 4 × 468.75 ms from 300 ms
        self.assertAlmostEqual(min(off, 1875 - off), 0, delta=15)

    def test_an_offset_and_the_end_of_a_stretch(self):
        g = beats.grid(drums(128.0), 5000, from_end=True)
        self.assertAlmostEqual(g.bpm, 128.0, delta=0.05)
        self.assertGreater(g.beat_ms, 5000 + 28_000)  # a beat near the end, in song time
        off = (g.beat_ms - 5300) % 468.75
        self.assertAlmostEqual(min(off, 468.75 - off), 0, delta=12)

    def test_noise_and_a_pad_have_no_rhythm_and_a_drifting_drummer_isnt_steady(self):
        rng = np.random.default_rng(3)
        g = beats.grid(rng.normal(0, 0.1, 30 * RATE).astype(np.float32), 0)
        self.assertTrue(g is None or g.rhythm < 0.2)
        t = np.arange(30 * RATE) / RATE
        pad = (0.3 * np.sin(2 * np.pi * 220 * t) * (1 + 0.3 * np.sin(2 * np.pi * 0.1 * t))).astype(np.float32)
        g = beats.grid(pad, 0)
        self.assertTrue(g is None or g.rhythm < 0.2)
        g = beats.grid(drums(128.0, drift=0.08), 0)
        self.assertLess(g.steady, 0.3)

    def test_too_short_for_a_grid(self):
        self.assertIsNone(beats.grid(drums(128.0, seconds=3.0), 0))
        self.assertIsNone(beats.grid(np.zeros(100, dtype=np.float32), 0))


class EndsTest(unittest.TestCase):
    def test_a_song_measured_end_to_end(self):
        # 70 s: 1 s of silence, drums at 120 for 30 s, a quiet pad, drums at 100 for the last 25 s, 3 s of silence.
        song = np.zeros(70 * RATE, dtype=np.float32)
        song[RATE:31 * RATE] = drums(120.0, 30.0, first=0.2)
        t = np.arange(10 * RATE) / RATE
        song[31 * RATE:41 * RATE] = (0.05 * np.sin(2 * np.pi * 220 * t)).astype(np.float32)
        song[42 * RATE:67 * RATE] = drums(100.0, 25.0, first=0.0, seed=2)
        m = loudness.measure_array(np.vstack([song, song]), RATE)
        self.assertEqual(m.head.size, 40 * RATE)
        self.assertEqual(m.tail.size, 40 * RATE)
        self.assertEqual(m.tail_offset, 30 * RATE)
        intro, outro = m.beat_grids()
        self.assertAlmostEqual(intro.bpm, 120.0, delta=0.1)
        off = (intro.beat_ms - 1200) % 500
        self.assertAlmostEqual(min(off, 500 - off), 0, delta=12)
        self.assertAlmostEqual(outro.bpm, 100.0, delta=0.1)
        self.assertGreater(outro.beat_ms, 60_000)
        off = (outro.beat_ms - 42_000) % 600
        self.assertAlmostEqual(min(off, 600 - off), 0, delta=12)
        fields = jobs.profile_of(m)["beats"]
        self.assertEqual(fields["intro_bpm"], round(intro.bpm, 2))
        self.assertIn("outro_beat_ms", fields)

    def test_a_song_without_a_beat_has_no_grid_fields(self):
        t = np.arange(40 * RATE) / RATE
        pad = (0.2 * np.sin(2 * np.pi * 110 * t)).astype(np.float32)
        m = loudness.measure_array(pad, RATE)
        intro, outro = m.beat_grids()
        self.assertTrue(intro is None or intro.rhythm < 0.2)
        fields = jobs.profile_of(m)["beats"]
        self.assertTrue(fields.get("intro_rhythm", 0) < 0.2)
        self.assertTrue(fields.get("outro_rhythm", 0) < 0.2)


if __name__ == "__main__":
    unittest.main()
