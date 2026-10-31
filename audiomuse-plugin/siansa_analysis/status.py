"""What the settings page and ``/api/status`` say about each part, worked out from plain data.

The parts are independent on purpose (issue #188): the volume and fade profiles report only their own
counts and their own job, and the similar albums and artists only their inputs, their last build and their
own job. A stuck build never shows up as a profile problem, or the other way round.
"""

from dataclasses import asdict, dataclass

from .generations import rebuild_reason

READY, WORKING, ATTENTION = "ready", "working", "attention"

# How long one round of measuring may run. AudioMuse-AI starts none of its own jobs (analysis, clustering,
# scheduled tasks) while a plugin job runs, so every round ends well before the next 15-minute tick.
TICK_SECONDS = 15 * 60
NORMAL_ROUND_SECONDS = 4 * 60
ALL_ROUND_SECONDS = 12 * 60

# How "Measure all" runs (Siansa issue #765): 0 is without a break, until every song is measured (AudioMuse's own
# jobs wait meanwhile); otherwise rounds of that many minutes every 15, so AudioMuse's jobs get a turn between them.
ALL_ROUND_CHOICES = (0, 12, 10, 5)
DEFAULT_ALL_ROUND_MINUTES = 0


def all_round_minutes(value):
    """The "Measure all" round setting, kept to :data:`ALL_ROUND_CHOICES`."""
    try:
        value = int(value)
    except (TypeError, ValueError):
        return DEFAULT_ALL_ROUND_MINUTES
    return value if value in ALL_ROUND_CHOICES else DEFAULT_ALL_ROUND_MINUTES


def round_seconds(measure_all, all_minutes=DEFAULT_ALL_ROUND_MINUTES):
    """How long a round may run: a normal round, or while measuring all, the set rounds or ``None`` (no limit)."""
    if not measure_all:
        return NORMAL_ROUND_SECONDS
    minutes = all_round_minutes(all_minutes)
    return minutes * 60 if minutes else None


def all_round_text(all_minutes):
    """How measuring all runs, in words for the page."""
    minutes = all_round_minutes(all_minutes)
    if not minutes:
        return ("It goes on without a break until every song is measured. Meanwhile AudioMuse waits with its own jobs "
                "(analysis of new songs, clustering, scheduled tasks).")
    return (f"It goes in rounds of {minutes} minutes every 15 until every song is measured. While a round runs, "
            "AudioMuse waits with its own jobs (analysis, clustering, scheduled tasks); between rounds they run.")


def next_rate(rate, songs, seconds, keep=500):
    """The running time per song, over about the last ``keep`` songs."""
    total_songs = float((rate or {}).get("songs") or 0) + songs
    total_seconds = float((rate or {}).get("seconds") or 0) + seconds
    if total_songs > keep:
        scale = keep / total_songs
        total_songs, total_seconds = keep, total_seconds * scale
    return {"songs": round(total_songs, 2), "seconds": round(total_seconds, 2)}


def duration_text(seconds):
    """"about 40 minutes", "about 3 hours", "about 2 days"."""
    minutes = max(1, int(round(seconds / 60)))
    if minutes < 90:
        return f"about {minutes} minute{'s' if minutes != 1 else ''}"
    hours = int(round(minutes / 60))
    if hours < 48:
        return f"about {hours} hours"
    return f"about {int(round(hours / 24))} days"


def time_left(to_measure, rate, measure_all, batch, all_minutes=DEFAULT_ALL_ROUND_MINUTES):
    """How long measuring every song still takes, in words, or ``None`` before anything was timed."""
    rate = rate or {}
    if to_measure <= 0 or not rate.get("songs"):
        return None
    per_song = float(rate["seconds"]) / float(rate["songs"])
    if measure_all:
        # Without a break, or rounds of the set minutes, one per tick.
        round_ = round_seconds(True, all_minutes)
        return duration_text(to_measure * per_song * (TICK_SECONDS / round_ if round_ else 1))
    per_tick = max(1, min(batch, int(NORMAL_ROUND_SECONDS // max(per_song, 0.1))))
    return duration_text(to_measure / per_tick * TICK_SECONDS)


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
    running = job_running(job, _utcnow())
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


def beats_counts(done, with_beat, failed, waiting, elsewhere=0):
    """Counts for the beat grids, over the songs that have a volume and fade profile. Songs that failed are tried
    again later, so they count as still to measure, like the profiles' (issue #657); songs on none of the music
    servers are counted apart and don't keep the part from being ready."""
    done, with_beat, failed, waiting, elsewhere = (max(int(v), 0) for v in (done, with_beat, failed, waiting, elsewhere))
    return {"songs": done + failed + waiting, "done": done, "with_beat": min(with_beat, done), "failed": failed,
            "waiting": waiting, "to_measure": waiting + failed, "elsewhere": elsewhere}


def beats(counts, job):
    """The beat grids: ``counts`` from :func:`beats_counts`, ``job`` their own job state."""
    songs, done, failed = counts["songs"], counts["done"], counts["failed"]
    running = job_running(job, _utcnow())
    share = percent(done, songs)
    detail = f"{done:,} of {songs:,} songs measured for their beats, {counts['with_beat']:,} with a beat at either end"
    if failed:
        detail += f", {failed:,} couldn't be read yet"
    if songs == 0:
        return Status("Waiting for profiles", WORKING, 0, "Songs get their beat grids with their volume and fade profile")
    if counts["to_measure"] == 0:
        return Status("Ready", READY, 100, detail)
    if running:
        return Status("Measuring", WORKING, share, detail)
    if failed and counts["waiting"] == 0:
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


def _utcnow():
    from datetime import datetime, timezone

    return datetime.now(timezone.utc)


# ---- where the rounds are (Siansa issue #765) ---------------------------------------------------------------------

# A job still "running" this long after its last word (it writes every 10 songs, and when it starts) has stopped:
# the worker was restarted or the round was cut off. Its state is then shown as stopped, so the page doesn't wait
# on it for ever and the buttons work again.
STALE_SECONDS = ALL_ROUND_SECONDS + 5 * 60


def _seconds_since(iso, now):
    from datetime import datetime

    if not iso:
        return None
    try:
        then = datetime.fromisoformat(str(iso))
    except ValueError:
        return None
    return (now - then).total_seconds()


def job_running(job, now):
    """Whether ``job`` (a part's job state) is really running at ``now``: "running", and heard from lately."""
    job = job or {}
    if job.get("status") != "running":
        return False
    since = _seconds_since(job.get("started_at"), now)
    return since is None or since < STALE_SECONDS


def _ago(seconds):
    minutes = int(max(seconds, 0) // 60)
    if minutes < 1:
        return "just now"
    if minutes < 90:
        return f"{minutes} minute{'s' if minutes != 1 else ''} ago"
    return f"{int(round(minutes / 60))} hours ago"


def _in(seconds):
    minutes = int(max(seconds, 0) // 60) + 1
    return f"in about {minutes} minute{'s' if minutes != 1 else ''}"


def last_round_text(tick, now):
    """"Last round, 3 minutes ago: 542 songs measured for their beats in 12 minutes." from the tick's record, or "".
    Says so when the beat grids got no time because the parts before them used the round."""
    tick = tick or {}
    if not tick.get("finished_at"):
        return ""
    since = _seconds_since(tick.get("finished_at"), now)
    parts = tick.get("parts") or {}
    said = []
    beats = parts.get("beats") or {}
    if beats.get("note") == "no time left this round":
        said.append("no time was left for beat grids")
    elif beats.get("status") == "busy":
        said.append("beat grids waited: another measuring job held the songs")
    elif "measured" in beats:
        said.append(f"{int(beats.get('measured') or 0):,} songs measured for their beats in "
                    f"{duration_text(float(beats.get('seconds') or 0)).removeprefix('about ')}")
    profiles = parts.get("profiles") or {}
    if int(profiles.get("measured") or 0):
        said.append(f"{int(profiles['measured']):,} volume and fade profiles")
    similar = parts.get("similar") or {}
    if similar.get("status") == "built":
        said.append(f"the similar lists were built again in {duration_text(float(similar.get('seconds') or 0)).removeprefix('about ')}")
    when = _ago(since) if since is not None else "earlier"
    return f"Last round, {when}: " + ("; ".join(said) if said else "nothing to do") + "."


def round_status(beats_job, profiles_job, tick, cron, blocking, now, all_minutes=DEFAULT_ALL_ROUND_MINUTES):
    """Where measuring every song stands, in a line for the page, and whether it needs attention:
    a round running, between rounds (and when the next starts), waiting for one of AudioMuse's own jobs, or no round
    starting at all (the scheduled task switched off, or something holding the queue)."""
    for job in (beats_job, profiles_job):
        job = job or {}
        if job_running(job, now):
            if not all_round_minutes(all_minutes):
                return f"Measuring without a break: {int(job.get('measured') or 0):,} measured so far.", False
            return f"A round is running: {int(job.get('measured') or 0):,} measured so far.", False
    stale = next((j for j in (beats_job, profiles_job) if (j or {}).get("status") == "running"), None)
    if stale:
        since = _seconds_since(stale.get("started_at"), now)
        lead = (f"The last round stopped without finishing, {_ago(since)} (AudioMuse's worker may have restarted). "
                if since is not None else "The last round stopped without finishing. ")
    else:
        lead = ""
    if cron is not None and not cron.get("enabled"):
        return lead + ("No rounds start: this plugin's scheduled task is switched off. Switch it on under "
                       "Administration › Scheduled Tasks."), True
    if blocking and not str(blocking.get("task_type") or "").startswith("plugin.siansa_analysis"):
        name = str(blocking.get("task_type") or "another job").replace("_", " ")
        return lead + f"Waiting for AudioMuse's own job ({name}) to finish; the rounds carry on after it.", False
    started = _seconds_since((tick or {}).get("started_at"), now)
    if started is None:
        return lead + "The first round starts with the next scheduled run, within 15 minutes.", False
    until = TICK_SECONDS - started
    if until > -120 and not all_round_minutes(all_minutes):
        return lead + f"Starts again with the next scheduled run, {_in(until)}.", False
    if until > -120:
        return lead + f"Between rounds: the next one starts {_in(until)}.", False
    return lead + (f"No round has started since {_ago(started)}. AudioMuse may be holding its queue for its own "
                   "jobs; check Administration › Scheduled Tasks and the task list."), True
