import math
import unittest

import numpy as np

import support  # noqa: F401  (import path and the plugin.api stand-in)
from musix_analysis import jobs, loudness, ramps


def sine(seconds, peak, rate=48_000, freq=997.0, channels=2):
    t = np.arange(int(seconds * rate)) / rate
    wave = peak * np.sin(2 * math.pi * freq * t)
    return np.vstack([wave] * channels)


class KWeightingTest(unittest.TestCase):
    def test_48k_matches_the_bs1770_table(self):
        (b1, a1), (b2, a2) = loudness.k_weighting(48_000)
        np.testing.assert_allclose(b1, [1.53512485958697, -2.69169618940638, 1.19839281085285], atol=1e-8)
        np.testing.assert_allclose(a1, [1.0, -1.69065929318241, 0.73248077421585], atol=1e-8)
        np.testing.assert_allclose(b2, [1.0, -2.0, 1.0])
        np.testing.assert_allclose(a2, [1.0, -1.99004745483398, 0.99007225036621], atol=1e-8)


class LoudnessTest(unittest.TestCase):
    def test_full_scale_997_hz_in_one_channel_is_minus_3_lufs(self):
        # BS.1770's own check: a 0 dBFS 997 Hz sine in one channel of a stereo pair reads -3.01 LKFS.
        audio = sine(10, 1.0)
        audio[1] = 0
        self.assertAlmostEqual(loudness.measure_array(audio, 48_000).lufs, -3.01, delta=0.05)

    def test_level_follows_the_signal(self):
        loud = loudness.measure_array(sine(5, 0.5), 48_000).lufs
        quiet = loudness.measure_array(sine(5, 0.05), 48_000).lufs
        self.assertAlmostEqual(loud - quiet, 20.0, delta=0.05)

    def test_other_sample_rates_agree(self):
        a = loudness.measure_array(sine(5, 0.3, rate=48_000), 48_000).lufs
        b = loudness.measure_array(sine(5, 0.3, rate=44_100), 44_100).lufs
        self.assertAlmostEqual(a, b, delta=0.1)

    def test_silence_is_gated_out(self):
        # Half silence: the gate ignores it, so the song reads about as loud as its music (only the few
        # blocks across the edge count a little), not 3 dB quieter as a plain average would.
        music = sine(5, 0.3)
        padded = np.hstack([music, np.zeros_like(music)])
        self.assertAlmostEqual(loudness.measure_array(padded, 48_000).lufs,
                               loudness.measure_array(music, 48_000).lufs, delta=0.2)
        self.assertIsNone(loudness.measure_array(np.zeros((2, 48_000)), 48_000).lufs)

    def test_duration_and_chunks(self):
        m = loudness.measure_array(sine(2.05, 0.3), 48_000)
        self.assertEqual(m.duration_ms, 2050)
        self.assertEqual(len(m.chunk_energy), 21)  # 20 whole chunks and a short last one


try:
    import av  # noqa: F401
    HAVE_AV = True
except ImportError:
    HAVE_AV = False


@unittest.skipUnless(HAVE_AV, "PyAV is in AudioMuse-AI and in CI, not always here")
class FileTest(unittest.TestCase):
    def test_a_wav_file_reads_like_the_samples(self):
        import os
        import tempfile
        import wave

        audio = sine(6, 0.25, rate=44_100)
        pcm = (audio.T * 32767).astype("<i2").tobytes()
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "tone.wav")
            with wave.open(path, "wb") as w:
                w.setnchannels(2)
                w.setsampwidth(2)
                w.setframerate(44_100)
                w.writeframes(pcm)
            from_file = loudness.measure_file(path)
        self.assertAlmostEqual(from_file.lufs, loudness.measure_array(audio, 44_100).lufs, delta=0.1)
        self.assertAlmostEqual(from_file.duration_ms, 6000, delta=30)


class RampTest(unittest.TestCase):
    def test_slow_intro_and_fade_out(self):
        rate = 48_000
        intro = np.zeros((2, 3 * rate))  # 3 s of silence
        body = sine(20, 0.3)
        fade = sine(4, 0.3) * np.linspace(1, 0, 4 * rate)  # 4 s fade-out
        profile = jobs.profile_of(loudness.measure_array(np.hstack([intro, body, fade]), rate))
        start = dict(ramps.decode(profile["start_ramp"]))
        end = dict(ramps.decode(profile["end_ramp"]))
        self.assertEqual(start[-18], 30)  # the song gets going after 3 s (30 chunks)
        self.assertEqual(start[0], 30)
        self.assertNotIn(6, start)  # never 6 dB above its own loudness
        self.assertGreater(end[-18], 2)  # quiet for a while before the end
        self.assertLess(end[-18], 40)
        self.assertEqual(profile["duration_ms"], 27_000)

    def test_hard_start_and_end(self):
        profile = jobs.profile_of(loudness.measure_array(sine(10, 0.3), 48_000))
        self.assertEqual(dict(ramps.decode(profile["start_ramp"]))[-18], 0)
        self.assertEqual(dict(ramps.decode(profile["end_ramp"]))[-18], 0)

    def test_blob_layout_is_what_musix_reads(self):
        # Musix's RampCodec: signed int8 dB, then a little-endian uint16 count of 100 ms chunks.
        blob = ramps.encode([(-18, 300), (6, 1)])
        import base64
        self.assertEqual(base64.b64decode(blob), bytes([0xEE, 0x2C, 0x01, 0x06, 0x01, 0x00]))
        self.assertEqual(ramps.decode(blob), [(-18, 300), (6, 1)])

    def test_ramps_fit_and_stop_at_unreached_levels(self):
        self.assertLessEqual(len(ramps.LEVELS), ramps.MAX_LEVELS)
        self.assertIn(-18, ramps.LEVELS)  # Crossfade.QUIET_DB in the app
        self.assertEqual(ramps.start_ramp([-70, -50, -20]), [(-60, 1), (-48, 2), (-36, 2), (-30, 2), (-24, 2), (-21, 2)])
        self.assertEqual(ramps.end_ramp([-20, -50, -70]), [(-60, 1), (-48, 2), (-36, 2), (-30, 2), (-24, 2), (-21, 2)])
        self.assertEqual(ramps.start_ramp([]), [])
        with self.assertRaises(ValueError):
            ramps.encode([(0, 0)] * 16)


if __name__ == "__main__":
    unittest.main()
