"""Song loudness (ITU-R BS.1770 / EBU R128) and how each song starts and ends, from the audio itself.

The audio is K-weighted and cut into 100 ms chunks. From the chunks come:

* the integrated loudness in LUFS, gated as BS.1770-4 says (400 ms blocks, 75 % overlap, an absolute gate at
  -70 LUFS and a relative gate 10 LU below), which levels the volume;
* each chunk's own loudness relative to that, which the ramps in :mod:`ramps` turn into fade shapes;
* the first and last :data:`KEEP_SECONDS` of the sound itself, in mono, which :mod:`beats` reads for the beat
  grid at each end (Siansa issue #646).

Only numpy and scipy are needed for the maths; decoding uses PyAV (built into AudioMuse-AI) and is imported
only when a file is read.
"""

import math
import shutil
import subprocess
import tempfile
import time

import numpy as np
from scipy.signal import lfilter

CHUNK_SECONDS = 0.1
RATE = 48_000
LUFS_OFFSET = -0.691
ABSOLUTE_GATE = -70.0
RELATIVE_GATE = -10.0
MAX_BAD_PACKETS = 20  # damaged packets a song may have before it counts as unreadable…
MAX_BAD_SHARE = 0.02  # …and at most this share of all packets
BLOCK_CHUNKS = 4  # a 400 ms gating block is four 100 ms chunks, stepped by one (75 % overlap)
KEEP_SECONDS = 40  # of mono audio kept from each end of the song for the beat grid (beats.SECONDS plus dead air)


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
        self.skipped_packets = 0
        # The song's first and last KEEP_SECONDS in mono (float32: 7.7 MB each at most), for the beat grid.
        self.keep = int(KEEP_SECONDS * rate)
        self.head = []
        self.head_frames = 0
        self.tail = []
        self.tail_frames = 0
        self.tail_offset = 0  # where in the song (frames) the kept tail starts

    def _keep(self, block):
        mono = block.mean(axis=0).astype(np.float32)
        if self.head_frames < self.keep:
            self.head.append(mono[:self.keep - self.head_frames])
            self.head_frames += self.head[-1].size
        self.tail.append(mono)
        self.tail_frames += mono.size
        while self.tail and self.tail_frames - self.tail[0].size >= self.keep:
            dropped = self.tail.pop(0)
            self.tail_frames -= dropped.size
            self.tail_offset += dropped.size

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
        self._keep(block)
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
        result = Measurement(self.energy, int(round(self.frames * 1000 / self.rate)))
        result.skipped_packets = self.skipped_packets
        result.rate = self.rate
        result.head = np.concatenate(self.head) if self.head else np.zeros(0, dtype=np.float32)
        result.tail = np.concatenate(self.tail) if self.tail else np.zeros(0, dtype=np.float32)
        result.tail_offset = self.tail_offset
        return result


class Measurement:
    """What one song measured: its integrated loudness and each chunk's level relative to it."""

    def __init__(self, chunk_energy, duration_ms):
        self.chunk_energy = list(chunk_energy)
        self.skipped_packets = 0
        # Which decoder read it: PyAV, or the ffmpeg tool when PyAV couldn't (issue #642).
        self.decoder = "pyav"
        self.duration_ms = duration_ms
        self.lufs = integrated(self.chunk_energy)
        # The first and last stretch of the sound, mono, for the beat grid (:func:`beat_grids`).
        self.rate = RATE
        self.head = np.zeros(0, dtype=np.float32)
        self.tail = np.zeros(0, dtype=np.float32)
        self.tail_offset = 0

    def beat_grids(self):
        """``(intro, outro)`` :class:`beats.Grid` of the song's start and end, either ``None`` without a beat."""
        from . import beats

        return beats.ends(self.head, self.tail, self.tail_offset, self.relative_levels(), self.rate)

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
    """Decodes ``path`` and measures it. Raises ``TimeoutError`` after ``deadline_seconds``.

    PyAV first (it ships with AudioMuse-AI). Its own copy of FFmpeg can lack something a song needs ("Not yet
    implemented in FFmpeg, patches welcome", issue #642), while AudioMuse-AI analysed the same file with the
    ``ffmpeg`` tool, so then the ``ffmpeg`` tool decodes it instead, when it's installed."""
    started = time.monotonic()
    try:
        return _measure_pyav(path, deadline_seconds)
    except TimeoutError:
        raise
    except Exception as first:
        tool = shutil.which("ffmpeg")
        left = deadline_seconds - (time.monotonic() - started)
        if not tool or left <= 1:
            raise
        try:
            result = _measure_ffmpeg(tool, path, left)
        except TimeoutError:
            raise
        except Exception as second:
            raise ValueError(f"{first} (the ffmpeg tool couldn't read it either: {second})") from first
        result.decoder = "ffmpeg"
        return result


def _measure_pyav(path, deadline_seconds):
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

        # Packet by packet, so one damaged packet (common in ripped or edited files) is skipped, as players
        # do, instead of failing the song. Only a file that is mostly unreadable fails. A packet using something
        # this FFmpeg can't decode ("patches welcome") counts the same way (issue #642).
        errors = getattr(av, "error", None)
        bad_errors = tuple(e for e in (getattr(errors, "InvalidDataError", None), getattr(errors, "PatchWelcomeError", None)) if e) or (ValueError,)
        packets = bad = 0
        for packet in container.demux(stream):
            packets += 1
            try:
                frames = packet.decode()
            except bad_errors as e:
                bad += 1
                if bad > MAX_BAD_PACKETS and bad > packets * MAX_BAD_SHARE:
                    codec = getattr(context, "name", None) or "?"
                    raise ValueError(f"too much of the file can't be read ({bad} of {packets} packets so far, codec {codec}): {e}") from e
                continue
            for frame in frames:
                feed(resampler.resample(frame))
            if time.monotonic() - started > deadline_seconds:
                raise TimeoutError(f"measuring took longer than {deadline_seconds:.0f} s")
        feed(resampler.resample(None))
        if meter.frames == 0:
            raise ValueError("no audio could be read from the file")
        meter.skipped_packets = bad
    return meter.finish()


def _channels_by_ffprobe(path):
    """1 or 2: the first audio stream's channels as ``ffprobe`` sees them; 2 when it can't tell."""
    probe = shutil.which("ffprobe")
    if not probe:
        return 2
    try:
        out = subprocess.run([probe, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels",
                              "-of", "csv=p=0", path], capture_output=True, text=True, timeout=20).stdout.strip()
        return 1 if out.splitlines() and out.splitlines()[0].strip() == "1" else 2
    except (OSError, subprocess.SubprocessError, ValueError):
        return 2


def _measure_ffmpeg(tool, path, deadline_seconds):
    """Decodes ``path`` with the ``ffmpeg`` tool to 48 kHz float samples on a pipe and measures them."""
    started = time.monotonic()
    channels = _channels_by_ffprobe(path)
    command = [tool, "-nostdin", "-v", "error", "-i", path, "-map", "0:a:0", "-vn",
               "-f", "f32le", "-acodec", "pcm_f32le", "-ac", str(channels), "-ar", str(RATE), "pipe:1"]
    meter = Meter(RATE, channels)
    block = RATE * channels * 4  # one second
    with tempfile.TemporaryFile() as errors:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=errors, stdin=subprocess.DEVNULL)
        try:
            rest = b""
            while True:
                data = process.stdout.read(block)
                if not data:
                    break
                data = rest + data
                usable = len(data) - len(data) % (4 * channels)
                rest = data[usable:]
                if usable:
                    samples = np.frombuffer(data[:usable], dtype="<f4").reshape(-1, channels).T
                    meter.add(samples)
                if time.monotonic() - started > deadline_seconds:
                    raise TimeoutError(f"measuring took longer than {deadline_seconds:.0f} s")
            code = process.wait(timeout=30)
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
            process.stdout.close()
        if code != 0 or meter.frames == 0:
            errors.seek(0)
            said = errors.read().decode("utf-8", "replace").strip().splitlines()
            raise ValueError(said[-1] if said else f"ffmpeg ended with code {code} and no audio")
    return meter.finish()
