import os
import shutil
import sys
import tempfile
import types
import unittest
import wave

import numpy as np

import support  # noqa: F401  (import path and the plugin.api stand-in)
from siansa_analysis import loudness


class PatchWelcomeError(Exception):
    """What PyAV raises for "Not yet implemented in FFmpeg, patches welcome" (issue #642)."""


class InvalidDataError(ValueError):
    pass


def tone_wav(path, seconds=12, channels=2, amplitude=0.3):
    t = np.arange(48_000 * seconds) / 48_000
    wave_ = amplitude * np.sin(2 * np.pi * 997 * t)
    frames = np.repeat(wave_[:, None], channels, axis=1)
    with wave.open(path, "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(2)
        w.setframerate(48_000)
        w.writeframes((frames * 32767).astype("<i2").tobytes())
    return wave_


class FailingContainer:
    def __init__(self):
        context = types.SimpleNamespace(channels=2, layout=None, name="aac")
        self.streams = [types.SimpleNamespace(type="audio", codec_context=context)]

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def demux(self, _stream):
        for _ in range(300):
            yield types.SimpleNamespace(decode=self.fail)

    @staticmethod
    def fail():
        raise PatchWelcomeError("[Errno 1163346256] Not yet implemented in FFmpeg, patches welcome: 'avcodec_send_packet()'")


class FfmpegFallbackTest(unittest.TestCase):
    """Songs PyAV can't decode are read by the ffmpeg tool instead (issue #642)."""

    def with_failing_pyav(self, fn):
        fake = types.ModuleType("av")
        fake.error = types.SimpleNamespace(InvalidDataError=InvalidDataError, PatchWelcomeError=PatchWelcomeError)
        fake.AudioResampler = lambda **_: types.SimpleNamespace(resample=lambda frame: [])
        fake.open = lambda path: FailingContainer()
        saved = sys.modules.get("av")
        sys.modules["av"] = fake
        try:
            return fn()
        finally:
            if saved is None:
                del sys.modules["av"]
            else:
                sys.modules["av"] = saved

    def test_unsupported_packets_count_as_unreadable_not_as_a_crash(self):
        saved = shutil.which
        shutil.which = lambda name: None  # no ffmpeg tool: the PyAV error is what's reported
        try:
            with self.assertRaises(ValueError) as caught:
                self.with_failing_pyav(lambda: loudness.measure_file("song.m4a"))
        finally:
            shutil.which = saved
        self.assertIn("codec aac", str(caught.exception))
        self.assertIn("patches welcome", str(caught.exception))

    @unittest.skipUnless(shutil.which("ffmpeg"), "needs the ffmpeg tool")
    def test_the_ffmpeg_tool_measures_what_pyav_cannot(self):
        folder = tempfile.mkdtemp()
        path = os.path.join(folder, "song.wav")
        tone = tone_wav(path)
        result = self.with_failing_pyav(lambda: loudness.measure_file(path))
        self.assertEqual(result.decoder, "ffmpeg")
        expected = loudness.measure_array(np.vstack([tone, tone]), 48_000)
        self.assertAlmostEqual(result.lufs, expected.lufs, delta=0.2)
        self.assertAlmostEqual(result.duration_ms, 12_000, delta=50)

    @unittest.skipUnless(shutil.which("ffmpeg"), "needs the ffmpeg tool")
    def test_a_mono_song_is_measured_as_mono(self):
        folder = tempfile.mkdtemp()
        path = os.path.join(folder, "mono.wav")
        tone = tone_wav(path, channels=1)
        result = self.with_failing_pyav(lambda: loudness.measure_file(path))
        self.assertAlmostEqual(result.lufs, loudness.measure_array(tone, 48_000).lufs, delta=0.2)

    @unittest.skipUnless(shutil.which("ffmpeg"), "needs the ffmpeg tool")
    def test_when_both_fail_both_reasons_are_given(self):
        folder = tempfile.mkdtemp()
        path = os.path.join(folder, "broken.m4a")
        with open(path, "wb") as f:
            f.write(b"not audio at all" * 100)
        with self.assertRaises(ValueError) as caught:
            self.with_failing_pyav(lambda: loudness.measure_file(path))
        self.assertIn("patches welcome", str(caught.exception))
        self.assertIn("ffmpeg tool couldn't read it either", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
