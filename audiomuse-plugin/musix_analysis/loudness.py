"""Song loudness (ITU-R BS.1770 / EBU R128) and how each song starts and ends, from the audio itself.

The audio is K-weighted and cut into 100 ms chunks. From the chunks come:

* the integrated loudness in LUFS, gated as BS.1770-4 says (400 ms blocks, 75 % overlap, an absolute gate at
  -70 LUFS and a relative gate 10 LU below), which levels the volume;
* each chunk's own loudness relative to that, which the ramps in :mod:`ramps` turn into fade shapes.

Only numpy and scipy are needed for the maths; decoding uses PyAV (built into AudioMuse-AI) and is imported
only when a file is read.
"""

import math
import time

import numpy as np
from scipy.signal import lfilter

CHUNK_SECONDS = 0.1
RATE = 48_000
LUFS_OFFSET = -0.691
ABSOLUTE_GATE = -70.0
RELATIVE_GATE = -10.0
BLOCK_CHUNKS = 4  # a 400 ms gating block is four 100 ms chunks, stepped by one (75 % overlap)


def k_weighting(rate):
    """The two K-weighting filters (a high shelf, then a high-pass) as ``[(b, a), (b, a)]`` for ``rate``.

    Built from the analogue design by the bilinear transform, so any sample rate works; at 48 kHz the result
    is the coefficient table in BS.1770.
    """
    # Stage 1: the head's acoustic effect, a +4 dB shelf above about 1.7 kHz.
    f0, gain_db, q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    k = math.tan(math.pi * f0 / rate)
    vh = 10 ** (gain_db / 20)
    vb = vh ** 0.4996667741545416
    a0 = 1 + k / q + k * k
    shelf = (
        np.array([(vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0]),
        np.array([1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0]),
    )
    # Stage 2: the RLB high-pass at about 38 Hz.
    f0, q = 38.13547087602444, 0.5003270373238773
    k = math.tan(math.pi * f0 / rate)
    a0 = 1 + k / q + k * k
    highpass = (
        np.array([1.0, -2.0, 1.0]),
        np.array([1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0]),
    )
    return [shelf, highpass]


def to_lufs(mean_square):
    """Loudness of a summed channel mean square; silence is ``-inf``."""
    return LUFS_OFFSET + 10 * math.log10(mean_square) if mean_square > 0 else float("-inf")


class Meter:
    """Feeds audio in blocks and keeps the K-weighted energy of every 100 ms chunk.

    ``add`` takes an array of shape ``(channels, frames)`` (float, -1..1). Left and right count fully, as
    BS.1770 says for stereo; a mono file is one channel.
    """

    def __init__(self, rate, channels):
        self.rate = rate
        self.channels = channels
        self.chunk = max(1, int(round(rate * CHUNK_SECONDS)))
        self.filters = k_weighting(rate)
        self.state = [[np.zeros(2) for _ in self.filters] for _ in range(channels)]
        self.energy = []  # summed mean square per finished chunk
        self.partial = 0.0
        self.filled = 0
        self.frames = 0

    def add(self, block):
        block = np.asarray(block, dtype=np.float64)
        if block.ndim == 1:
            block = block[np.newaxis, :]
        if block.shape[0] != self.channels:
            raise ValueError(f"expected {self.channels} channels, got {block.shape[0]}")
        weighted = np.empty_like(block)
        for c in range(self.channels):
            signal = block[c]
            for i, (b, a) in enumerate(self.filters):
                signal, self.state[c][i] = lfilter(b, a, signal, zi=self.state[c][i])
            weighted[c] = signal
        squares = np.sum(weighted * weighted, axis=0)  # channels summed, per frame
        self.frames += squares.size
        offset = 0
        while offset < squares.size:
            take = min(self.chunk - self.filled, squares.size - offset)
            self.partial += float(np.sum(squares[offset:offset + take]))
            self.filled += take
            offset += take
            if self.filled == self.chunk:
                self.energy.append(self.partial / self.chunk)
                self.partial = 0.0
                self.filled = 0

    def finish(self):
        """Closes the last, shorter chunk and returns the result as a :class:`Measurement`."""
        if self.filled:
            self.energy.append(self.partial / self.filled)
            self.partial = 0.0
            self.filled = 0
        return Measurement(self.energy, int(round(self.frames * 1000 / self.rate)))


class Measurement:
    """What one song measured: its integrated loudness and each chunk's level relative to it."""

    def __init__(self, chunk_energy, duration_ms):
        self.chunk_energy = list(chunk_energy)
        self.duration_ms = duration_ms
        self.lufs = integrated(self.chunk_energy)

    def relative_levels(self):
        """Each 100 ms chunk's loudness minus the song's, in dB (``-inf`` for digital silence)."""
        if self.lufs is None:
            return []
        return [to_lufs(e) - self.lufs for e in self.chunk_energy]


def integrated(chunk_energy):
    """Gated integrated loudness of a song from its chunk energies, or ``None`` when it is (near) silent."""
    if not chunk_energy:
        return None
    energy = np.asarray(chunk_energy, dtype=np.float64)
    if energy.size < BLOCK_CHUNKS:
        blocks = np.array([energy.mean()])  # shorter than one block: the whole song is the block
    else:
        window = np.ones(BLOCK_CHUNKS) / BLOCK_CHUNKS
        blocks = np.convolve(energy, window, mode="valid")
    with np.errstate(divide="ignore"):
        loudness = LUFS_OFFSET + 10 * np.log10(blocks)
    kept = blocks[loudness > ABSOLUTE_GATE]
    if kept.size == 0:
        return None
    relative = to_lufs(float(kept.mean())) + RELATIVE_GATE
    with np.errstate(divide="ignore"):
        kept = kept[LUFS_OFFSET + 10 * np.log10(kept) > relative]
    if kept.size == 0:
        return None
    return to_lufs(float(kept.mean()))


def measure_array(samples, rate):
    """Measures audio already in memory: ``samples`` is ``(channels, frames)`` or one channel."""
    samples = np.asarray(samples, dtype=np.float64)
    if samples.ndim == 1:
        samples = samples[np.newaxis, :]
    meter = Meter(rate, samples.shape[0])
    step = rate  # one second at a time, as a decoder would hand it over
    for start in range(0, samples.shape[1], step):
        meter.add(samples[:, start:start + step])
    return meter.finish()


def measure_file(path, deadline_seconds=120.0):
    """Decodes ``path`` with PyAV and measures it. Raises ``TimeoutError`` after ``deadline_seconds``."""
    import av  # AudioMuse-AI ships PyAV; imported here so the maths can be tested without it

    started = time.monotonic()
    with av.open(path) as container:
        stream = next((s for s in container.streams if s.type == "audio"), None)
        if stream is None:
            raise ValueError("the file has no audio")
        context = stream.codec_context
        count = getattr(context, "channels", None) or len(getattr(context.layout, "channels", ()) or ()) or 2
        channels = 1 if count == 1 else 2
        resampler = av.AudioResampler(format="fltp", layout="mono" if channels == 1 else "stereo", rate=RATE)
        meter = Meter(RATE, channels)

        def feed(frames):
            for out in frames:
                if out is not None and out.samples:
                    meter.add(out.to_ndarray().reshape(channels, -1))

        for frame in container.decode(stream):
            feed(resampler.resample(frame))
            if time.monotonic() - started > deadline_seconds:
                raise TimeoutError(f"measuring took longer than {deadline_seconds:.0f} s")
        feed(resampler.resample(None))
    return meter.finish()
