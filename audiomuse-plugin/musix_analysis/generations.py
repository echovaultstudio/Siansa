"""Input generations: when similar albums and artists must be built again.

The build reads two inputs:

* the **library**: which songs there are and their album and artist;
* the **sound analysis**: AudioMuse-AI's embedding of each song.

Each input is summed up by a digest of exactly what the build reads. When a digest changes, that input's
generation goes up by one. A build remembers the two generations (and the algorithm version) it was made
from, so it is out of date as soon as either generation moves on, and only then. An analysis run that
changes nothing leaves the digest, and so the generation, as it was: no needless rebuild.

Everything here is plain data, so it is tested without a database.
"""

from dataclasses import asdict, dataclass


@dataclass
class Inputs:
    library_digest: str = ""
    library_generation: int = 0
    library_songs: int = 0
    analysis_digest: str = ""
    analysis_generation: int = 0
    analysis_songs: int = 0

    def to_json(self):
        return asdict(self)

    @classmethod
    def from_json(cls, value):
        value = value or {}
        return cls(**{k: value[k] for k in cls.__dataclass_fields__ if k in value})


def advance(stored, library_digest, library_songs, analysis_digest, analysis_songs):
    """The inputs after a fresh look: a generation goes up only when its digest changed."""
    out = Inputs(**asdict(stored))
    if library_digest != stored.library_digest:
        out.library_digest = library_digest
        out.library_generation = stored.library_generation + 1
    if analysis_digest != stored.analysis_digest:
        out.analysis_digest = analysis_digest
        out.analysis_generation = stored.analysis_generation + 1
    out.library_songs = library_songs
    out.analysis_songs = analysis_songs
    return out


def rebuild_reason(inputs, built, algorithm_version):
    """Why the similar lists must be built (again), or ``None`` when they are current.

    ``built`` is what the last finished build recorded: ``library_generation``, ``analysis_generation`` and
    ``algorithm_version`` (an empty dict when there was none).
    """
    if inputs.library_songs == 0 or inputs.analysis_songs == 0:
        return None  # nothing to build from yet: the page says it is waiting for inputs
    built = built or {}
    if not built.get("library_generation"):
        return "first build"
    if built.get("algorithm_version") != algorithm_version:
        return "new algorithm"
    changed = []
    if built.get("library_generation") != inputs.library_generation:
        changed.append("library changed")
    if built.get("analysis_generation") != inputs.analysis_generation:
        changed.append("sound analysis changed")
    return " and ".join(changed) or None
