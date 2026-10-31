"""The beat grid at each end of a song, for beat-matched blends in Siansa's DJ radio (Siansa issue #646).

AudioMuse-AI keeps one tempo per song, averaged over the whole of it. Mixing needs more: the exact tempo of the
end and of the start (an outro or an intro often runs at another tempo, or has no beat at all), *where* the
beats fall, so two songs' beats can be laid on each other, and whether the tempo holds steady (a drum machine)
or drifts (a live drummer). This module measures that from the first and last :data:`SECONDS` of a song, from
the same decoded audio the loudness is measured from.

How it works, the usual way (Ellis 2007, as in librosa):

1. an onset envelope: how much the sound changes from one 10 ms frame to the next, across 40 bands;
2. the tempo: the lag at which the envelope repeats best (its autocorrelation), with a mild preference for
   tempos near 120 BPM, read between 60 and 200 BPM;
3. the beats: the frames that follow that tempo and sit on the loudest onsets (dynamic programming);
4. a straight line through those beats gives the tempo to a fraction of a BPM and a beat position; how far
   the beats stray from the line says how steady the tempo is;
5. the downbeat: the beat of each bar of four on which the low end (the kick) and the spectrum change most;
6. the phrase (Siansa issue #757): the bar of each 4 or 8 on which the sound changes most as a bar starts (new
   instruments, the low end coming in, a drop), the same idea one level up. A DJ mixes phrase on phrase, so the
   outro is read over the last :data:`OUTRO_SECONDS`, long enough to hold a few phrases.

Each end gives :class:`Grid`: ``bpm``, ``beat_ms`` (a beat near that end, in ms from the start of the song),
``bar_ms`` (a downbeat, or ``None`` when the bars aren't clear), ``phrase_ms`` and ``phrase_bars`` (a phrase
start near that end and how many bars a phrase is, or ``None`` when the phrases aren't clear), ``rhythm``
(0..1, how beat-driven the sound is; a drum outro is near 1, an ambient tail or a spoken intro near 0) and
``steady`` (0..1). Only numpy and scipy are needed.
"""

import math

import numpy as np

SECONDS = 30.0  # of each end of the song, for the beat grid
INTRO_SECONDS = 40.0  # of its start, for the phrase (all that's kept: loudness.KEEP_SECONDS)
OUTRO_SECONDS = 60.0  # of its end, for the phrase: a few phrases, so the pattern shows (8 bars at 90 BPM is 21 s)
RATE = 48_000  # the samples are at this rate (loudness.RATE)
FRAME = 1024
HOP = 480  # 10 ms at 48 kHz: 100 frames a second
BANDS = 40
MIN_BPM, MAX_BPM = 60.0, 200.0
PRIOR_BPM = 120.0  # mild preference, one octave wide, as librosa's
TIGHTNESS = 100.0  # how strictly the beats keep to the tempo
MIN_BEATS = 12  # fewer, and there's no grid worth reporting
SILENT_DB = -48.0  # below this (relative to the song) the end is dead air, trimmed before measuring
BEATS_PER_BAR = 4
ONSET_FLOOR = 0.5  # the onset strength beats must have, on average, to count as a rhythm at all
BAR_CONFIDENCE = 0.5  # below this the bars aren't clear and only the beats are reported
CHANGE_WEIGHT = 3.0  # a change of the spectrum over a bar, against the sound's level, next to a kick's weight of 1
PHRASE_BARS = (8, 4)  # the phrase lengths tried, the usual one first
PHRASE_CONFIDENCE = 0.45  # below this the phrases aren't clear and only the bars are reported
PHRASE_SAMPLES = 3  # phrases a stretch must hold for a length to be tried: two is a coin toss on most music
INTRO_PHRASE_SAMPLES = 2  # the start is shorter, and only its first phrase start matters, not the length
PICKUP_BARS = 2  # an intro's first phrase may start this many bars in (a pickup), else at the first downbeat
DEBUG = False


class Grid:
    """The beat grid at one end of a song (see the module notes)."""

    def __init__(self, bpm, beat_ms, bar_ms, rhythm, steady, phrase_ms=None, phrase_bars=None):
        self.bpm = float(bpm)
        self.beat_ms = float(beat_ms)
        self.bar_ms = None if bar_ms is None else float(bar_ms)
        self.rhythm = float(rhythm)
        self.steady = float(steady)
        self.phrase_ms = None if phrase_ms is None else float(phrase_ms)
        self.phrase_bars = None if phrase_bars is None else int(phrase_bars)

    def as_dict(self, prefix):
        out = {
            f"{prefix}_bpm": round(self.bpm, 2),
            f"{prefix}_beat_ms": round(self.beat_ms, 1),
            f"{prefix}_rhythm": round(self.rhythm, 3),
            f"{prefix}_steady": round(self.steady, 3),
        }
        if self.bar_ms is not None:
            out[f"{prefix}_bar_ms"] = round(self.bar_ms, 1)
        if self.phrase_ms is not None:
            out[f"{prefix}_phrase_ms"] = round(self.phrase_ms, 1)
            out[f"{prefix}_phrase_bars"] = self.phrase_bars
        return out

    def __repr__(self):
        return (f"Grid({self.bpm:.2f} bpm, beat {self.beat_ms:.0f} ms, bar {self.bar_ms}, phrase {self.phrase_ms} "
                f"of {self.phrase_bars} bars, rhythm {self.rhythm:.2f}, steady {self.steady:.2f})")


# ---- the onset envelope ------------------------------------------------------------------------------------------

def _filterbank(rate=RATE, frame=FRAME, bands=BANDS):
    """Triangular bands spaced like the ear hears them (mel), from 40 Hz to 16 kHz, over the rfft bins."""
    bins = frame // 2 + 1
    freqs = np.arange(bins) * rate / frame
    mel = lambda f: 2595 * np.log10(1 + f / 700)  # noqa: E731
    edges = 700 * (10 ** (np.linspace(mel(40), mel(min(16_000, rate / 2)), bands + 2) / 2595) - 1)
    bank = np.zeros((bands, bins), dtype=np.float32)
    for b in range(bands):
        lo, mid, hi = edges[b], edges[b + 1], edges[b + 2]
        up = (freqs - lo) / max(mid - lo, 1e-6)
        down = (hi - freqs) / max(hi - mid, 1e-6)
        bank[b] = np.clip(np.minimum(up, down), 0, None)
    return bank, freqs


def onset_envelope(samples, rate=RATE):
    """``(onset, low, spectra)`` at 100 frames a second from mono ``samples``: how much the sound changes
    frame to frame across the bands, the same for the low end alone (kicks), and the band spectrogram."""
    x = np.asarray(samples, dtype=np.float32)
    if x.size < FRAME:
        return np.zeros(0), np.zeros(0), np.zeros((0, BANDS))
    frames = 1 + (x.size - FRAME) // HOP
    window = np.hanning(FRAME).astype(np.float32)
    strided = np.lib.stride_tricks.as_strided(x, shape=(frames, FRAME), strides=(x.strides[0] * HOP, x.strides[0]))
    spectrum = np.abs(np.fft.rfft(strided * window, axis=1)).astype(np.float32)
    bank, freqs = _filterbank(rate)
    bands = np.log1p(1000.0 * (spectrum @ bank.T))  # (frames, bands), compressed like loudness is heard
    flux = np.diff(bands, axis=0, prepend=bands[:1])
    flux = np.maximum(flux, 0)  # only sound arriving counts, not sound dying away
    onset = flux.mean(axis=1)
    low = spectrum[:, freqs < 150].sum(axis=1)  # linear, so a louder kick (the downbeat's) stands out
    low = np.maximum(np.diff(low, prepend=low[:1]), 0)
    return onset, low, bands


# ---- tempo ----------------------------------------------------------------------------------------------------

def tempo(onset, fps=RATE / HOP):
    """``(bpm, strength)``: the tempo the onset envelope repeats at, and how clearly (0..1)."""
    if onset.size < fps * 4:
        return None, 0.0
    x = onset - onset.mean()
    if not np.any(x):
        return None, 0.0
    n = x.size
    spectrum = np.fft.rfft(x, 2 * n)
    acf = np.fft.irfft(spectrum * np.conj(spectrum))[:n]
    acf = acf / (acf[0] + 1e-9)
    lags = np.arange(n)
    with np.errstate(divide="ignore"):
        bpm = 60.0 * fps / np.where(lags > 0, lags, 1)
    prior = np.exp(-0.5 * (np.log2(bpm / PRIOR_BPM)) ** 2)  # one octave wide
    weighted = np.where((bpm >= MIN_BPM) & (bpm <= MAX_BPM), acf * prior, -np.inf)
    weighted[:2] = -np.inf
    lag = int(np.argmax(weighted))
    if not np.isfinite(weighted[lag]) or acf[lag] <= 0:
        return None, 0.0
    # Parabolic interpolation on the peak: tempo to a fraction of a BPM.
    if 1 <= lag < n - 1:
        a, b, c = acf[lag - 1], acf[lag], acf[lag + 1]
        shift = 0.5 * (a - c) / (a - 2 * b + c) if (a - 2 * b + c) != 0 else 0.0
        lag_f = lag + float(np.clip(shift, -0.5, 0.5))
    else:
        lag_f = float(lag)
    return 60.0 * fps / lag_f, float(np.clip(acf[lag], 0, 1))


# ---- beats ----------------------------------------------------------------------------------------------------

def track_beats(onset, bpm, fps=RATE / HOP, tightness=TIGHTNESS):
    """The frames of the beats at ``bpm`` that best follow the onsets (Ellis's dynamic programming)."""
    period = 60.0 * fps / bpm
    n = onset.size
    if n == 0 or period < 2:
        return np.zeros(0, dtype=int)
    norm = onset / (onset.std() + 1e-9)
    window = np.arange(-int(round(2 * period)), -int(round(period / 2)) + 1)
    penalty = -tightness * (np.log(-window / period)) ** 2
    score = np.zeros(n)
    back = np.full(n, -1, dtype=int)
    first = int(-window[0])
    for i in range(n):
        if i < first:
            score[i] = norm[i]
            continue
        candidates = score[i + window] + penalty
        k = int(np.argmax(candidates))
        score[i] = norm[i] + candidates[k]
        back[i] = i + window[k]
    # Start from the best of the last beat's worth of frames, then walk back.
    tail = score[max(0, n - int(period)):]
    i = int(np.argmax(tail)) + max(0, n - int(period))
    beats = []
    while i >= 0:
        beats.append(i)
        i = back[i]
    return np.array(beats[::-1], dtype=int)


def refine(onset, beats):
    """Each beat frame moved to the peak of the onset around it (parabolic), as a float frame index."""
    out = []
    for b in beats:
        if 1 <= b < onset.size - 1:
            a, m, c = onset[b - 1], onset[b], onset[b + 1]
            d = a - 2 * m + c
            shift = 0.5 * (a - c) / d if d < 0 else 0.0
            out.append(b + float(np.clip(shift, -0.5, 0.5)))
        else:
            out.append(float(b))
    return np.array(out)


def fit_line(times_ms):
    """``(period_ms, first_ms, rms_ms)``: the straight line through the beat times, and how far they stray."""
    k = np.arange(times_ms.size)
    period, first = np.polyfit(k, times_ms, 1)
    residual = times_ms - (first + period * k)
    return float(period), float(first), float(np.sqrt(np.mean(residual ** 2)))


def spectral_change(bands, frames, window, overall=False):
    """How much the sound changes at each of ``frames``: the mean spectrum of the ``window`` frames after it against
    the ``window`` before (``overall``: only how much sound there is). A frame whose windows run past the stretch's
    ends gets the others' median, so a short window at the edge (against nothing) can't stand out."""
    change = np.full(frames.size, np.nan)
    for i, f in enumerate(frames):
        if f - window < 0 or f + window > bands.shape[0]:
            continue
        before, after = bands[f - window:f], bands[f:f + window]
        change[i] = abs(after.mean() - before.mean()) if overall else np.abs(after.mean(axis=0) - before.mean(axis=0)).mean()
    whole = change[~np.isnan(change)]
    fill = float(np.median(whole)) if whole.size else 0.0
    return np.where(np.isnan(change), fill, change)


def at_frames(values, frames, reach=2):
    """``values`` at each of ``frames``, the most within ``reach`` frames either side: a beat falls between two 10 ms
    frames, and reading one frame caught more or less of a kick's rise from beat to beat."""
    out = np.zeros(frames.size)
    for i, f in enumerate(frames):
        out[i] = values[max(0, f - reach):f + reach + 1].max() if values.size else 0.0
    return out


def downbeat(beats, low, bands, per_bar=BEATS_PER_BAR):
    """``(offset, confidence)``: which of the first ``per_bar`` beats starts a bar, from where the low end
    hits and the spectrum changes most; the confidence is how clearly one beat wins (0..1)."""
    if beats.size < per_bar * 2:
        return 0, 0.0
    frames = np.clip(np.round(beats).astype(int), 0, bands.shape[0] - 1)
    # What changes over a bar: the sound of the bar after this beat against the bar before it. A bar's worth, not
    # a few frames: a hat or a snare on some beats makes every other beat "change" too, and that's not a downbeat.
    bar_frames = max(1, int(round(np.median(np.diff(frames)) * per_bar))) if frames.size > 1 else 1
    change = spectral_change(bands, frames, bar_frames)
    lowness = at_frames(low, frames)
    # The change against the sound's own level, not against its mean: when nothing changes from bar to bar the
    # term is near zero everywhere (not noise blown up to 1), and the kicks decide.
    score = lowness / (lowness.mean() + 1e-9) + CHANGE_WEIGHT * change / (bands.mean() + 1e-9)
    totals = np.array([score[p::per_bar].mean() for p in range(per_bar)])
    order = np.argsort(totals)[::-1]
    best, second = totals[order[0]], totals[order[1]]
    DEBUG and print("bar totals", np.round(totals, 3))
    confidence = float(np.clip((best - second) / (best + 1e-9) * 3, 0, 1))
    return int(order[0]), confidence


def phrase(beats, phase, low, bands, onset, per_bar=BEATS_PER_BAR, choices=PHRASE_BARS, samples=PHRASE_SAMPLES):
    """``(bars, offset, confidence)``: how many bars a phrase is and which bar (counted from the first downbeat
    ``phase``) starts one, from the bar starts on which the sound changes most: what comes in or drops out over the
    bar, the low end, and how much is going on. A length is only tried when the stretch holds ``samples`` phrases
    of it. ``bars`` is ``None`` when the phrases aren't clear."""
    starts = np.arange(phase, beats.size, per_bar)
    if starts.size < samples * min(choices):
        return None, 0, 0.0
    frames = np.clip(np.round(beats[starts]).astype(int), 0, bands.shape[0] - 1)
    bar_frames = int(round(np.median(np.diff(frames)))) if frames.size > 1 else 1
    # A change in what plays (the spectrum over the whole bar), and more or less sound overall.
    change = spectral_change(bands, frames, bar_frames) + spectral_change(bands, frames, bar_frames, overall=True)
    score = CHANGE_WEIGHT * change / (bands.mean() + 1e-9)
    lowness, hits = at_frames(low, frames), at_frames(onset, frames)
    score += lowness / (lowness.mean() + 1e-9) * 0.5 + hits / (hits.mean() + 1e-9) * 0.5
    best = (None, 0, 0.0)
    for bars in choices:
        if starts.size < samples * bars:
            continue
        totals = np.array([score[p::bars].mean() for p in range(bars)])
        order = np.argsort(totals)[::-1]
        confidence = float(np.clip((totals[order[0]] - totals[order[1]]) / (totals[order[0]] + 1e-9) * 3, 0, 1))
        DEBUG and print("phrase totals", bars, np.round(totals, 3))
        if confidence > best[2]:
            best = (bars, int(order[0]), confidence)
    return best


# ---- one end of a song --------------------------------------------------------------------------------------------

def grid(samples, offset_ms, rate=RATE, from_end=False):
    """The :class:`Grid` of a stretch of mono ``samples`` that starts ``offset_ms`` into the song, or ``None``
    when it has no beat worth reporting. ``from_end`` reports the beat position nearest the end of the stretch
    (an outro), else nearest its start (an intro)."""
    onset, low, bands = onset_envelope(samples, rate)
    fps = rate / HOP
    bpm, strength = tempo(onset, fps)
    if bpm is None:
        return None
    beats = track_beats(onset, bpm, fps)
    if beats.size < MIN_BEATS:
        return None
    exact = refine(onset, beats)
    times = (exact * HOP + FRAME / 2) * 1000.0 / rate
    period, first, rms = fit_line(times)
    if period <= 0:
        return None
    bpm = 60_000.0 / period
    # How beat-driven: the onsets at the beats against the onsets in general (1 = no more than anywhere else).
    at_beats = onset[np.clip(beats, 0, onset.size - 1)].mean()
    # The floor keeps a pad or a held note, whose onsets are tiny everywhere, from looking rhythmic.
    contrast = at_beats / (onset.mean() + ONSET_FLOOR / 2)
    rhythm = float(np.clip((contrast - 1.0) / 1.5, 0, 1)) * (0.6 + 0.4 * strength)
    rhythm *= float(np.clip(at_beats / ONSET_FLOOR, 0, 1))
    DEBUG and print(f"at_beats {at_beats:.3f} contrast {contrast:.2f} strength {strength:.2f} rms {rms:.1f}")
    steady = float(np.clip(1.0 - rms / 30.0, 0, 1)) ** 2
    phase, confidence = downbeat(exact, low, bands)
    k_ref = beats.size - 1 if from_end else 0
    beat_ms = offset_ms + first + period * k_ref
    bar_ms = phrase_ms = phrase_bars = None
    if confidence >= BAR_CONFIDENCE:
        # The downbeat nearest the reference beat, on the bar grid from ``phase``.
        k_bar = phase + BEATS_PER_BAR * round((k_ref - phase) / BEATS_PER_BAR)
        bar_ms = offset_ms + first + period * k_bar
        bars, offset, sure = phrase(exact, phase, low, bands, onset, samples=PHRASE_SAMPLES if from_end else INTRO_PHRASE_SAMPLES)
        if bars is not None and sure >= PHRASE_CONFIDENCE:
            # An outro's last phrase start; an intro's first: the first downbeat, or a bar start up to PICKUP_BARS in
            # when the music starts in earnest there (a pickup), never a phrase deep into the song.
            per_phrase = BEATS_PER_BAR * bars
            k_first = phase + BEATS_PER_BAR * offset
            if from_end:
                k_phrase = k_first + per_phrase * math.floor((k_ref - k_first) / per_phrase)
            else:
                k_phrase = k_first if offset <= PICKUP_BARS else phase
            phrase_ms = offset_ms + first + period * k_phrase
            phrase_bars = bars
    return Grid(bpm, beat_ms, bar_ms, rhythm, steady, phrase_ms, phrase_bars)


def same_grid(a, b, bpm_tolerance=0.005, ms_tolerance=40.0):
    """Whether two grids of the same end agree: the same tempo (within half a percent) and the same downbeats."""
    if a.bar_ms is None or b.bar_ms is None or abs(a.bpm - b.bpm) > a.bpm * bpm_tolerance:
        return False
    bar = 60_000.0 / a.bpm * BEATS_PER_BAR
    off = (b.bar_ms - a.bar_ms) % bar
    return min(off, bar - off) <= ms_tolerance


def ends(head, tail, tail_offset_frames, relative_levels, rate=RATE, seconds=SECONDS, outro_seconds=OUTRO_SECONDS):
    """``(intro, outro)`` grids from the song's first and last stretch of mono samples (``tail`` starts
    ``tail_offset_frames`` into the song); ``relative_levels`` (100 ms chunks, dB) trims dead air at either end."""
    chunk_ms = 100
    levels = np.asarray(relative_levels, dtype=np.float64) if len(relative_levels) else np.zeros(0)
    loud = np.flatnonzero(levels > SILENT_DB) if levels.size else np.zeros(0, dtype=int)
    intro = outro = None
    n = int(seconds * rate)
    if head is not None and head.size:
        start_ms = int(loud[0]) * chunk_ms if loud.size else 0
        start = min(int(start_ms * rate / 1000), max(0, head.size - rate * 8))
        intro = grid(head[start:start + n], start * 1000.0 / rate, rate)
        # The phrase from all that's kept of the start, when it's the same grid.
        if intro is not None and intro.bar_ms is not None:
            longer = grid(head[start:start + int(INTRO_SECONDS * rate)], start * 1000.0 / rate, rate)
            intro.phrase_ms = intro.phrase_bars = None
            if longer is not None and longer.phrase_ms is not None and same_grid(intro, longer):
                intro.phrase_ms, intro.phrase_bars = longer.phrase_ms, longer.phrase_bars
    if tail is not None and tail.size:
        end_ms = (int(loud[-1]) + 1) * chunk_ms if loud.size else None
        end = tail.size if end_ms is None else int(np.clip(end_ms * rate / 1000 - tail_offset_frames, rate * 8, tail.size))
        piece = tail[max(0, end - n):end]
        outro = grid(piece, (tail_offset_frames + max(0, end - n)) * 1000.0 / rate, rate, from_end=True)
        # The phrase from a longer stretch, when its beat grid is the same one: a minute holds a few phrases, but
        # may also reach back into a part at another tempo, whose grid would say nothing about the end.
        m = int(outro_seconds * rate)
        if outro is not None and outro.bar_ms is not None:
            longer = grid(tail[max(0, end - m):end], (tail_offset_frames + max(0, end - m)) * 1000.0 / rate, rate, from_end=True)
            # Half a minute holds too few phrases to trust: only the minute's phrase counts.
            outro.phrase_ms = outro.phrase_bars = None
            if longer is not None and longer.phrase_ms is not None and same_grid(outro, longer):
                outro.phrase_ms, outro.phrase_bars = longer.phrase_ms, longer.phrase_bars
    return intro, outro


def fields(intro, outro):
    """The profile fields for the two grids (empty when neither end has one)."""
    out = {}
    if intro is not None:
        out.update(intro.as_dict("intro"))
    if outro is not None:
        out.update(outro.as_dict("outro"))
    return out
