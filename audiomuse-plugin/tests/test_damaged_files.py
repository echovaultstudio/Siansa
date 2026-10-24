import sys
import types
import unittest

import numpy as np

import support  # noqa: F401  (import path and the plugin.api stand-in)
from siansa_analysis import loudness


class InvalidDataError(ValueError):
    pass


class FakeFrame:
    def __init__(self, samples):
        self.samples = samples


class FakeResampler:
    """Hands frames straight through as 48 kHz stereo blocks of a 997 Hz tone."""

    def __init__(self, **_):
        self.t = 0

    def resample(self, frame):
        if frame is None:
            return []
        n = frame.samples
        t = (np.arange(n) + self.t) / 48_000
        self.t += n
        wave = 0.3 * np.sin(2 * np.pi * 997 * t)
        out = types.SimpleNamespace(samples=n, to_ndarray=lambda: np.vstack([wave, wave]).astype(np.float32))
        return [out]


class FakePacket:
    def __init__(self, bad):
        self.bad = bad

    def decode(self):
        if self.bad:
            raise InvalidDataError("Invalid data found when processing input: 'avcodec_send_packet()'")
        return [FakeFrame(4800)]


class FakeContainer:
    def __init__(self, bad_every):
        self.bad_every = bad_every
        context = types.SimpleNamespace(channels=2, layout=None)
        self.streams = [types.SimpleNamespace(type="audio", codec_context=context)]

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def demux(self, _stream):
        for i in range(300):  # 30 s of 100 ms packets
            yield FakePacket(bool(self.bad_every) and i % self.bad_every == 5 % self.bad_every)


class DamagedFileTest(unittest.TestCase):
    """Issue: a few songs failed with "Invalid data found when processing input" because of one bad packet."""

    def measure(self, bad_every):
        fake = types.ModuleType("av")
        fake.error = types.SimpleNamespace(InvalidDataError=InvalidDataError)
        fake.AudioResampler = FakeResampler
        fake.open = lambda path: FakeContainer(bad_every)
        saved = sys.modules.get("av")
        sys.modules["av"] = fake
        try:
            return loudness.measure_file("song.m4a")
        finally:
            if saved is None:
                del sys.modules["av"]
            else:
                sys.modules["av"] = saved

    def test_a_few_bad_packets_are_skipped(self):
        clean = self.measure(0)
        damaged = self.measure(100)  # 3 bad packets of 300
        self.assertEqual(damaged.skipped_packets, 3)
        self.assertAlmostEqual(damaged.lufs, clean.lufs, delta=0.1)
        self.assertEqual(damaged.duration_ms, clean.duration_ms - 300)

    def test_a_mostly_broken_file_still_fails(self):
        with self.assertRaises(ValueError):
            self.measure(2)  # every other packet


if __name__ == "__main__":
    unittest.main()
