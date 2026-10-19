"""What the settings page and ``/api/status`` say about each part, worked out from plain data.

The parts are independent on purpose (issue #188): the volume and fade profiles report only their own
counts and their own job, and the similar albums and artists only their inputs, their last build and their
own job. A stuck build never shows up as a profile problem, or the other way round.
"""

from dataclasses import asdict, dataclass

from .generations import rebuild_reason

READY, WORKING, ATTENTION = "ready", "working", "attention"


@dataclass
class Status:
    label: str
    kind: str  # READY, WORKING or ATTENTION: the badge colour
    percent: int
    detail: str

    def to_json(self):
        return asdict(self)


def percent(part, whole):
    if whole <= 0:
        return 0
    return max(0, min(100, int(part * 100 // whole)))


def profile_counts(library_songs, ready, failed, wanted=0):
    """Counts for the profiles part. ``failed`` songs are tried again later, so they still count as to do."""
    library_songs = max(int(library_songs), 0)
    ready = min(max(int(ready), 0), library_songs)
    failed = min(max(int(failed), 0), library_songs - ready)
    return {
        "songs": library_songs,
        "ready": ready,
        "failed": failed,
        "to_measure": library_songs - ready - failed,
        "asked_for": max(int(wanted), 0),
    }


def profiles(counts, job):
    """The volume and fade profiles: ``counts`` from :func:`profile_counts`, ``job`` their own job state."""
    songs, ready, failed = counts["songs"], counts["ready"], counts["failed"]
    running = (job or {}).get("status") == "running"
    share = percent(ready, songs)
    detail = f"{ready:,} of {songs:,} songs measured"
    if failed:
        detail += f", {failed:,} couldn't be read yet"
    if songs == 0:
        return Status("Waiting for library", WORKING, 0, "No analysed songs in AudioMuse yet")
    if ready >= songs:
        return Status("Ready", READY, 100, detail)
    if running:
        return Status("Measuring", WORKING, share, detail)
    if failed and counts["to_measure"] == 0:
        return Status("Needs attention", ATTENTION, share, detail)
    return Status("Not complete", WORKING, share, detail)


def similar(inputs, built, job, algorithm_version):
    """The similar albums and artists: their inputs, the last finished build, and their own job."""
    job = job or {}
    built = built or {}
    if inputs.library_songs == 0 or inputs.analysis_songs == 0:
        return Status("Waiting for inputs", WORKING, 0, "Builds once AudioMuse has analysed songs")
    if job.get("status") == "running":
        done, total = int(job.get("done") or 0), int(job.get("total") or 0)
        return Status("Building", WORKING, percent(done, total), job.get("phase") or "Building")
    reason = rebuild_reason(inputs, built, algorithm_version)
    counts = f"{int(built.get('albums') or 0):,} albums and {int(built.get('artists') or 0):,} artists"
    if job.get("status") == "failed" and reason:
        return Status("Needs attention", ATTENTION, 0, f"The last build failed: {job.get('error') or 'unknown error'}")
    if reason:
        kept = f"; the last lists ({counts}) stay in use until then" if built.get("library_generation") else ""
        return Status("Update pending", WORKING, 0, f"Builds again soon: {reason}{kept}")
    return Status("Up to date", READY, 100, counts)
