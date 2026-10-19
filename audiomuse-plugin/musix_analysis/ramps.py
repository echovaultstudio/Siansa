"""How a song starts and ends (the MixRamp idea from the MPD player), and the small blobs Musix reads.

A ramp is a list of ``(level, chunk)`` pairs. ``level`` is in dB relative to the song's own loudness;
``chunk`` counts 100 ms chunks:

* the start ramp says how soon the song first gets up to each level (a hard hit is at 0, a slow intro later);
* the end ramp says how long before the end the song is last at each level (a long fade-out is quiet for a
  while before the end).

On the wire each pair is 3 bytes, a signed 8-bit level and a little-endian unsigned 16-bit chunk index,
base64-encoded: the layout Musix has read since issue #60 (``RampCodec`` in the app).
"""

import base64
import struct

# The levels a ramp records, quietest first. Musix fades on -18 dB (Crossfade.QUIET_DB); the others let it
# pick another level later without measuring again. At most 15 levels fit a ramp.
LEVELS = (-60, -48, -36, -30, -24, -21, -18, -15, -12, -9, -6, -3, 0, 3, 6)
MAX_LEVELS = 15
MAX_CHUNK = 0xFFFF


def start_ramp(relative):
    """For each level, the first chunk at or above it. Stops at the first level the song never reaches."""
    out = []
    for level in LEVELS:
        index = next((i for i, value in enumerate(relative) if value >= level), None)
        if index is None:
            break
        out.append((level, min(index, MAX_CHUNK)))
    return out


def end_ramp(relative):
    """For each level, how many chunks before the end the song is last at or above it."""
    out = []
    last = len(relative) - 1
    for level in LEVELS:
        index = next((i for i in range(last, -1, -1) if relative[i] >= level), None)
        if index is None:
            break
        out.append((level, min(last - index, MAX_CHUNK)))
    return out


def encode(pairs):
    """``[(level, chunk), …]`` as the base64 blob Musix decodes."""
    if len(pairs) > MAX_LEVELS:
        raise ValueError(f"a ramp holds at most {MAX_LEVELS} levels")
    raw = b"".join(struct.pack("<bH", int(level), int(chunk)) for level, chunk in pairs)
    return base64.b64encode(raw).decode("ascii")


def decode(blob):
    """The other way round; used by the tests and the settings page."""
    raw = base64.b64decode(blob) if blob else b""
    if len(raw) % 3:
        raise ValueError("a ramp is a whole number of 3-byte entries")
    return [struct.unpack_from("<bH", raw, i) for i in range(0, len(raw), 3)]
