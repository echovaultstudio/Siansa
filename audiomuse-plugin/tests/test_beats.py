"""The beat grid at each end of a song (Siansa issue #646): tempo, beat position, bars, rhythm, steadiness."""
import unittest

import numpy as np

import support  # noqa: F401
from siansa_analysis import beats, jobs, loudness

RATE = 48_000


def kick(freq=55.0, seconds=0.12):
    n = int(seconds * RATE)
    return np.sin(2 * np.pi * freq * np.arange(n) / RATE) * np.exp(-np.arange(n) / (0.03 * RATE))


def drums(bpm, seconds=30.0, first=0.3, accent=True, noise=0.0, drift=0.0, seed=1, phrase=0, phrase_from=0):
    """A kick on every beat (louder on the first of four with ``accent``), a hat on every other one. With
    ``phrase`` (bars), every other phrase from bar ``phrase_from`` adds a sustained chord, as a song's parts come
    and go: the phrases start where the chord comes in or drops out."""
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
        bar = k // 4
        if phrase and k % 4 == 0 and bar >= phrase_from and ((bar - phrase_from) // phrase) % 2 == 1:
            n = int(period * 4 * RATE)  # the chord held over the whole bar
            tt = np.arange(n) / RATE
            chord = 0.15 * (np.sin(2 * np.pi * 330 * tt) + np.sin(2 * np.pi * 415 * tt) + np.sin(2 * np.pi * 494 * tt))
            x[s:s + n] += chord[:x.size - s]
        t += period
        k += 1
    return x.astype(np.float32)


def pattern(bpm, hits, seconds=30.0, seed=3):
    """A drum pattern looped for ``seconds``: ``hits`` maps "kick", "snare" or "hat" to beat positions in a bar of
    four (a two-step drum and bass break is kicks on 0 and 2.5, snares on 1 and 3)."""
    rng = np.random.default_rng(seed)
    x = np.zeros(int(seconds * RATE))
    n = int(0.25 * RATE)
    t = np.arange(n) / RATE
    sounds = {
        "kick": np.sin(2 * np.pi * (50 + 80 * np.exp(-t * 30)) * t) * np.exp(-t * 12),
        "snare": rng.normal(0, 1, n) * np.exp(-t * 25) * 0.7 + np.sin(2 * np.pi * 190 * t) * np.exp(-t * 20) * 0.4,
        "hat": np.diff(rng.normal(0, 1, n), prepend=0) * np.exp(-t * 80) * 0.15,
    }
    beat = 60 / bpm
    for bar in range(int(seconds / (4 * beat)) + 1):
        for kind, positions in hits.items():
            for p in positions:
                i = int((bar * 4 + p) * beat * RATE)
                if i + n < x.size:
                    x[i:i + n] += sounds[kind]
    return x.astype(np.float32)


TWO_STEP = {"kick": [0, 2.5], "snare": [1, 3], "hat": [i / 2 for i in range(8)]}
ROLLING = {"kick": [0, 0.75, 2.5, 2.75], "snare": [1, 3], "hat": [i / 4 for i in range(16)]}
HOUSE = {"kick": [0, 1, 2, 3], "snare": [1, 3], "hat": [0.5, 1.5, 2.5, 3.5]}
HIP_HOP = {"kick": [0, 1.75, 2.5], "snare": [1, 3], "hat": [i / 2 for i in range(8)]}


def mixes_with(bpm, true_bpm):
    """Whether ``bpm`` is ``true_bpm`` or double or half of it, within 1 %: the same for a beat-matched blend."""
    return any(abs(bpm / (true_bpm * r) - 1) <= 0.01 for r in (1, 2, 0.5))


class RelatedTempoTest(unittest.TestCase):
    """Siansa issue #802: drum and bass read at two thirds of its tempo (113 for 170), beats off the music."""

    def test_a_break_read_at_two_thirds_is_read_at_its_tempo_or_half(self):
        x = pattern(170, TWO_STEP)
        onset, _, _ = beats.onset_envelope(x)
        found, _ = beats.tempo(onset)
        self.assertAlmostEqual(found, 170 * 2 / 3, delta=1.5)  # how it read before
        g = beats.grid(x, 0)
        self.assertTrue(mixes_with(g.bpm, 170), g)
        self.assertGreater(g.steady, 0.7)

    def test_other_misreadings_of_breaks(self):
        # Four sevenths (100 for 175), and half time that read as unsteady at 87.5.
        for hits, bpm in ((ROLLING, 175), (ROLLING, 170), (TWO_STEP, 175)):
            g = beats.grid(pattern(bpm, hits), 0)
            self.assertTrue(mixes_with(g.bpm, bpm), (bpm, g))
            self.assertGreater(g.steady, 0.7, (bpm, g))

    def test_four_to_the_floor_and_hip_hop_keep_their_tempo(self):
        for hits, bpm in ((HOUSE, 120), (HOUSE, 124), (HOUSE, 128), (HIP_HOP, 90), (HIP_HOP, 95)):
            g = beats.grid(pattern(bpm, hits), 0)
            self.assertAlmostEqual(g.bpm, bpm, delta=0.5, msg=str(g))
        # House at 132 read as 66 before: now its own tempo.
        self.assertAlmostEqual(beats.grid(pattern(132, HOUSE), 0).bpm, 132, delta=0.5)

    def test_families_are_double_and_half(self):
        self.assertTrue(beats._same_family(86.0, 172.0))
        self.assertTrue(beats._same_family(62.0, 124.0))
        self.assertFalse(beats._same_family(115.0, 172.0))
        self.assertFalse(beats._same_family(100.0, 175.0))


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

    def test_the_phrase_is_where_the_parts_come_and_go(self):
        # 8-bar phrases from bar 2 (a 2-bar pickup): at 128 BPM a bar is 1875 ms, a phrase 15 s.
        g = beats.grid(drums(128.0, 60.0, first=0.3, phrase=8, phrase_from=2), 0, from_end=True)
        self.assertEqual(g.phrase_bars, 8)
        off = (g.phrase_ms - 300 - 2 * 1875) % 15_000
        self.assertAlmostEqual(min(off, 15_000 - off), 0, delta=20)
        self.assertGreater(g.phrase_ms, 60_000 - 15_000 - 100)  # the last phrase start
        # From the start: the first phrase starts at the first bar unless the music says a later one (within a phrase).
        g = beats.grid(drums(128.0, 40.0, first=0.3, phrase=8, phrase_from=2), 0)
        self.assertEqual(g.phrase_bars, 8)
        self.assertAlmostEqual(g.phrase_ms, 300 + 2 * 1875, delta=20)
        # Without any change from bar to bar there's no phrase to report, only the bars.
        g = beats.grid(drums(128.0, 60.0), 0, from_end=True)
        self.assertIsNotNone(g.bar_ms)
        self.assertIsNone(g.phrase_ms)

    def test_drums_that_vary_hit_to_hit_have_bars_but_no_phrase(self):
        # Every hit a little louder or softer, as a drummer plays: the bars are still clear, and nothing from bar to
        # bar says "phrase", so none is reported (a false phrase would cut a song's ending).
        rng = np.random.default_rng(5)
        for bpm in (90.0, 128.0):
            x = np.zeros(60 * RATE)
            t, k = 0.3, 0
            while t < 60:
                s = int(t * RATE)
                hit = kick() * (1.0 if k % 4 == 0 else 0.6) * rng.uniform(0.8, 1.2)
                x[s:s + hit.size] += hit[:x.size - s]
                t += 60 / bpm
                k += 1
            g = beats.grid(x.astype(np.float32), 0, from_end=True)
            self.assertIsNotNone(g.bar_ms, bpm)
            self.assertIsNone(g.phrase_ms, bpm)
        # A kick on every beat with no accent: no bar to find either.
        self.assertIsNone(beats.grid(drums(128.0, accent=False), 0).bar_ms)

    def test_an_intros_phrase_starts_at_the_first_bar_or_a_short_pickup(self):
        # The parts change from bar 6 on: too far in for a pickup, so the first phrase is the first downbeat the
        # grid has (the very first kick, at 300 ms, comes before the tracker locks on: bar 1 at 2175 ms).
        g = beats.grid(drums(128.0, 40.0, first=0.3, phrase=8, phrase_from=6), 0)
        self.assertEqual(g.phrase_bars, 8)
        self.assertAlmostEqual(g.phrase_ms, 300 + 1875, delta=20)

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
        self.assertEqual(m.tail.size, 70 * RATE)
        self.assertEqual(m.tail_offset, 0)
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

    def test_the_outros_phrase_comes_from_the_last_minute_when_its_the_same_grid(self):
        # 8-bar phrases at 128 over the last 65 s (4 phrases), after 20 s at another tempo: the 30 s grid gives the
        # tempo, the minute the phrase; the part at 100 BPM doesn't get in the way.
        song = np.concatenate([drums(100.0, 20.0, seed=3), drums(128.0, 65.0, first=0.0, phrase=8, seed=4)])
        m = loudness.measure_array(np.vstack([song, song]), RATE)
        intro, outro = m.beat_grids()
        self.assertAlmostEqual(outro.bpm, 128.0, delta=0.1)
        self.assertEqual(outro.phrase_bars, 8)
        off = (outro.phrase_ms - 20_000) % 15_000
        self.assertAlmostEqual(min(off, 15_000 - off), 0, delta=25)
        fields = jobs.profile_of(m)["beats"]
        self.assertEqual(fields["outro_phrase_bars"], 8)
        self.assertNotIn("intro_phrase_ms", fields)  # the 100 BPM start has no phrase structure

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
