"""The plugin's work: measuring songs, and building the similar lists. Runs on the AudioMuse-AI worker.

Two independent jobs, each with its own state, lock and errors (issue #188):

* **profiles**: a song is measured right after AudioMuse-AI analyses it (from the audio file it already has);
  songs analysed before the plugin was installed, or that failed, are fetched and measured by the tick, a
  few at a time within a time budget;
* **similar**: the tick looks at the two inputs (library and sound analysis); when either input generation
  moved on since the last build, it builds the lists again and swaps them in at once.

``tick`` runs every 15 minutes (a scheduled task), and does both. A failure in one never stops the other.
"""

import os
import shutil
import tempfile
import time
from datetime import datetime, timezone

import numpy as np

from plugin.api import active_server_id, get_db, get_setting, list_servers, logger, use_server

from . import beats, generations, loudness, ramps, similarity, status, store

HOOK_BUDGET_SECONDS = 60
DEFAULT_BATCH = 40
# Songs measured again for their beat grids per 15-minute round (Siansa issue #646); 0 only when asked.
DEFAULT_BEATS_BATCH = 20

# The two measuring parts: volume and fade profiles for songs without one, and beat grids for songs measured
# before the beat tracker. They share the decoding and the lock (one song is read at a time), but each has its
# own batch, its own "measure all" switch and its own job state, so the page shows and steers them apart.
PARTS = {
    "profiles": {"job": "profiles_job", "all": "measure_all", "batch": "batch_size", "default": DEFAULT_BATCH, "beats": False},
    "beats": {"job": "beats_job", "all": "beats_all", "batch": "beats_batch_size", "default": DEFAULT_BEATS_BATCH, "beats": True},
}


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def profile_of(measurement):
    """A song's stored profile from its :class:`loudness.Measurement`: loudness, ramps, and the beat grid at
    each end (``intro_*`` / ``outro_*``, Siansa issue #646; left out for an end without a beat)."""
    relative = measurement.relative_levels()
    profile = {
        "ref_lufs": None if measurement.lufs is None else round(measurement.lufs, 2),
        "start_ramp": ramps.encode(ramps.start_ramp(relative)),
        "end_ramp": ramps.encode(ramps.end_ramp(relative)),
        "duration_ms": measurement.duration_ms,
    }
    try:
        intro, outro = measurement.beat_grids()
        profile["beats"] = beats.fields(intro, outro)
    except Exception as e:  # the loudness still counts; a song without a grid gets plain fades
        logger.warning("siansa_analysis couldn't find the beats: %s", e)
        profile["beats"] = {}
    return profile


# ---- profiles ------------------------------------------------------------------------------------------------

def on_song_analyzed(song):
    """AudioMuse-AI hook: measures the song from the file analysis just used, before it is deleted."""
    path = song.get("audio_path")
    item_id = song.get("item_id")
    if not path or not item_id:
        return
    try:
        from tasks.mediaserver.registry import canonical_input_ids

        canonical = canonical_input_ids([item_id], song.get("server_id")).get(str(item_id), str(item_id))
    except Exception:
        canonical = str(item_id)
    db = get_db()
    try:
        started = time.monotonic()
        store.save_profile(db, canonical, profile_of(loudness.measure_file(path, HOOK_BUDGET_SECONDS)))
        logger.debug("siansa_analysis measured %s in %.1f s", canonical, time.monotonic() - started)
    except Exception as e:  # never break the analysis; the tick tries again later
        db.rollback()
        logger.warning("siansa_analysis couldn't measure %s after analysis: %s", canonical, e)
        store.save_failure(db, canonical, e)


class NotOnServer(Exception):
    """The song isn't on any music server AudioMuse-AI knows (it was removed, or isn't mapped yet)."""


def _download(item_id, title, folder):
    """Fetches one song's audio; returns the file path or ``None``.

    From the music server of this run when it has the song, else from another of AudioMuse-AI's servers that does
    (Siansa issue #659): with several servers, a song only on one of them was only ever looked for on the server
    the run was for, and a run for another server put it off, so it was never measured."""
    from tasks.mediaserver import download_track
    from tasks.mediaserver.registry import translate_ids

    active = active_server_id()
    provider_id = translate_ids([item_id], active).get(item_id)
    if provider_id:
        return download_track(folder, _item(provider_id, item_id, title))
    for server in _servers():
        server_id = server.get("server_id")
        if not server_id or server_id == active:
            continue
        provider_id = translate_ids([item_id], server_id).get(item_id)
        if provider_id:
            with use_server(server_id):  # the download talks to that server
                return download_track(folder, _item(provider_id, item_id, title))
    raise NotOnServer(item_id)


def _item(provider_id, item_id, title):
    # The keys each server's download reads: Navidrome 'id', Jellyfin/Emby 'Id', names for the log.
    return {"id": provider_id, "Id": provider_id, "title": title or item_id, "Name": title or item_id}


def _servers():
    """AudioMuse-AI's music servers, as dicts with ``server_id``; none when it can't say."""
    try:
        return [s for s in (list_servers() or []) if isinstance(s, dict)]
    except Exception as e:
        logger.warning("siansa_analysis couldn't list the music servers: %s", e)
        return []


def measure_all_on(db, part="profiles"):
    return bool((store.get_state(PARTS[part]["all"], {}, db) or {}).get("on"))


# Songs per round of beat grids at most: as many as a round's time allows (issue #657), in practice no limit.
MAX_BEATS_BATCH = 100_000


def batch_size(part, batch=None):
    """Songs per normal round of ``part``: 1–500 for profiles; 0 up to :data:`MAX_BEATS_BATCH` for beats, where 0
    means only when asked. A round stops when its time is up whatever the number."""
    spec = PARTS[part]
    value = batch if batch is not None else get_setting(spec["batch"], spec["default"])
    try:
        value = int(value)
    except (TypeError, ValueError):
        value = spec["default"]
    return max(0 if spec["beats"] else 1, min(value, MAX_BEATS_BATCH if spec["beats"] else 500))


def measure_missing(budget_seconds=None, batch=None):
    """Measures songs without a volume and fade profile, a round at a time (see :func:`measure_part`)."""
    return measure_part("profiles", budget_seconds, batch)


def measure_beats(budget_seconds=None, batch=None):
    """Measures songs again for their beat grids, a round at a time (see :func:`measure_part`)."""
    return measure_part("beats", budget_seconds, batch)


def measure_part(part, budget_seconds=None, batch=None):
    """Measures the songs ``part`` still needs ("profiles" or "beats"), a round at a time.

    A normal round measures one batch (that part's "Songs per run" setting) within
    :data:`status.NORMAL_ROUND_SECONDS`. With that part's "Measure all" on, a round goes on until
    :data:`status.ALL_ROUND_SECONDS`, and the scheduled tick keeps starting rounds until every song is measured or
    it is stopped. The rounds end with a few minutes free, because AudioMuse-AI doesn't start its own jobs (such as
    analysis) while a plugin job runs. Both parts measure a song in full, so a song measured for its beats gets
    its loudness and ramps again too (the same values).
    """
    spec = PARTS[part]
    db = get_db()
    if not store.try_lock(db, "profiles"):
        return {"status": "busy"}
    started = time.monotonic()
    measured = failed = skipped = 0
    all_on = measure_all_on(db, part)
    budget = budget_seconds or status.round_seconds(all_on)
    folder = tempfile.mkdtemp(prefix="siansa_analysis_")
    rate = store.get_state("profiles_rate", {}, db) or {}
    try:
        limit = 10_000_000 if all_on else batch_size(part, batch)
        if limit <= 0:
            return {"status": "idle", "measured": 0, "note": "only when asked"}
        store.set_state(spec["job"], {"status": "running", "started_at": now_iso(), "all": all_on, "measured": 0,
                                      "error": None}, db)
        tried = set()
        stopped = False
        while len(tried) < limit and not stopped:
            rows = [r for r in store.songs_to_measure(db, min(25, limit - len(tried)) + len(tried), beats=spec["beats"])
                    if r[0] not in tried]
            if not rows:
                break
            for item_id, title in rows:
                left = budget - (time.monotonic() - started)
                if left <= 5:
                    stopped = True
                    break
                if all_on and not measure_all_on(db, part):  # "Stop" on the settings page
                    all_on, stopped = False, True
                    break
                tried.add(item_id)
                path = None
                try:
                    path = _download(item_id, title, folder)
                    if not path:
                        raise RuntimeError("the music server didn't send the song")
                    store.save_profile(db, item_id, profile_of(loudness.measure_file(path, min(left, 180))))
                    measured += 1
                except NotOnServer:
                    skipped += 1
                    if spec["beats"]:
                        store.save_beats_failure(db, item_id, store.ELSEWHERE, elsewhere=True)
                    else:
                        store.save_elsewhere(db, item_id)
                except Exception as e:
                    db.rollback()
                    failed += 1
                    logger.info("siansa_analysis couldn't measure %s%s: %s", item_id, " for its beats" if spec["beats"] else "", e)
                    if spec["beats"]:
                        store.save_beats_failure(db, item_id, e)  # its loudness stays as it was
                    else:
                        store.save_failure(db, item_id, e)
                finally:
                    if path and os.path.exists(path):
                        os.remove(path)
                if measured and measured % 10 == 0:
                    store.set_state(spec["job"], {"status": "running", "started_at": now_iso(), "all": all_on,
                                                  "measured": measured, "error": None}, db)
        seconds = round(time.monotonic() - started, 1)
        store.set_state("profiles_rate", status.next_rate(rate, measured + failed, seconds), db)
        if all_on and not store.songs_to_measure(db, 1, beats=spec["beats"]):
            store.set_state(spec["all"], {"on": False, "finished_at": now_iso()}, db)
            logger.info("siansa_analysis: every song is measured%s", " for its beats" if spec["beats"] else "")
        result = {"status": "idle", "finished_at": now_iso(), "measured": measured, "failed": failed,
                  "other_server": skipped, "seconds": seconds, "error": None}
        store.set_state(spec["job"], result, db)
        logger.info("siansa_analysis %s: %s measured, %s failed in %.0f s", part, measured, failed, seconds)
        return result
    except Exception as e:
        db.rollback()
        store.set_state(spec["job"], {"status": "failed", "finished_at": now_iso(), "error": str(e)[:300]}, db)
        logger.exception("siansa_analysis %s run failed", part)
        return {"status": "failed", "error": str(e)}
    finally:
        shutil.rmtree(folder, ignore_errors=True)
        store.unlock(db, "profiles")


# ---- similar albums and artists ---------------------------------------------------------------------------------

def refresh_inputs(db=None):
    """Looks at the library and the sound analysis; a generation goes up when what the build reads changed."""
    db = db or get_db()
    stored = generations.Inputs.from_json(store.get_state("inputs", {}, db))
    library, songs = store.library_digest(db)
    analysis, analysed = store.analysis_digest(db)
    current = generations.advance(stored, library, songs, analysis, analysed)
    if current != stored:
        store.set_state("inputs", current.to_json(), db)
        logger.info("siansa_analysis inputs: library generation %s, analysis generation %s",
                    current.library_generation, current.analysis_generation)
    return current


def build_similar(force=False):
    """Builds the similar lists when an input generation moved on (or ``force``), and publishes them at once."""
    db = get_db()
    if not store.try_lock(db, "similar"):
        return {"status": "busy"}
    started = time.monotonic()
    try:
        inputs = refresh_inputs(db)
        built = store.get_state("similar_built", {}, db) or {}
        reason = generations.rebuild_reason(inputs, built, similarity.ALGORITHM_VERSION)
        if force and inputs.analysis_songs:
            reason = reason or "asked for"
        if not reason:
            return {"status": "current"}
        logger.info("siansa_analysis building similar albums and artists: %s", reason)

        def progress(phase):
            def report(done, total):
                store.set_state("similar_job", {"status": "running", "phase": phase, "done": done, "total": total,
                                                "started_at": started_at}, db)
            return report

        started_at = now_iso()
        progress("Reading the sound analysis")(0, 1)
        tracks = []
        for item_id, album, album_artist, author, blob in store.similarity_tracks(db):
            vector = np.frombuffer(bytes(blob), dtype=np.float32) if blob else None
            tracks.append((item_id, album, album_artist, author, vector))
        db.commit()
        dims = {t[4].size for t in tracks if t[4] is not None}
        if len(dims) > 1:  # mixed models mid re-analysis: use the most common size
            common = max(dims, key=lambda d: sum(1 for t in tracks if t[4] is not None and t[4].size == d))
            tracks = [t for t in tracks if t[4] is not None and t[4].size == common]
        albums, artists = similarity.group_tracks(tracks)
        album_lists = similarity.rank_albums(albums, progress=progress("Albums: finding similar ones"))
        artist_lists = similarity.rank_artists(artists, progress=progress("Artists: finding similar ones"))

        # The inputs may have moved on while this ran; then publish anyway (better than nothing) and the
        # next tick builds again, because the recorded generations are the ones read at the start.
        generation = int(built.get("generation") or 0) + 1
        store.publish_similar(
            db, album_lists, {k: (g.name, g.artist) for k, g in albums.items()},
            artist_lists, {k: g.name for k, g in artists.items()}, generation)
        result = {
            "generation": generation, "library_generation": inputs.library_generation,
            "analysis_generation": inputs.analysis_generation,
            "algorithm_version": similarity.ALGORITHM_VERSION, "albums": len(album_lists),
            "artists": len(artist_lists), "finished_at": now_iso(), "reason": reason,
            "seconds": round(time.monotonic() - started, 1),
        }
        store.set_state("similar_built", result, db, commit=False)
        store.set_state("similar_job", {"status": "idle", "finished_at": result["finished_at"]}, db, commit=False)
        db.commit()
        logger.info("siansa_analysis similar lists: %s albums, %s artists in %.0f s",
                    result["albums"], result["artists"], result["seconds"])
        return {"status": "built", **result}
    except Exception as e:
        db.rollback()
        store.set_state("similar_job", {"status": "failed", "finished_at": now_iso(), "error": str(e)[:300]}, db)
        logger.exception("siansa_analysis similar build failed")
        return {"status": "failed", "error": str(e)}
    finally:
        store.unlock(db, "similar")


# ---- the tick ------------------------------------------------------------------------------------------------

def _same_task_again():
    """True when this task already ran the tick: AudioMuse-AI runs a plugin's scheduled task once per music
    server, one after another, and the plugin reads songs from any server (:func:`_download`), so the first run
    does the work and the others would only make the tick three times as long (Siansa issue #659)."""
    try:
        import taskqueue

        task_id = taskqueue.current_task_id()
    except Exception:
        return False
    if not task_id:
        return False
    db = get_db()
    if (store.get_state("tick_task", {}, db) or {}).get("id") == task_id:
        return True
    store.set_state("tick_task", {"id": task_id, "at": now_iso()}, db)
    return False


def tick():
    """The scheduled task: keeps every part current, each on its own. The beat grids get the time the profiles
    round left over (or a whole round while "Measure all" is on for them), so one tick never runs past its slot."""
    if _same_task_again():
        return {"status": "done", "note": "already ran for another music server in this task"}
    result = {}
    started = time.monotonic()
    for name, job in (("similar", build_similar), ("profiles", measure_missing)):
        try:
            result[name] = job()
        except Exception as e:  # one part failing must not stop the other
            logger.exception("siansa_analysis %s failed", name)
            result[name] = {"status": "failed", "error": str(e)}
    try:
        db = get_db()
        beats_all, profiles_all = measure_all_on(db, "beats"), measure_all_on(db, "profiles")
        spent = time.monotonic() - started
        left = (status.ALL_ROUND_SECONDS if beats_all and not profiles_all else status.round_seconds(profiles_all)) - spent
        if left >= MIN_BEATS_ROUND_SECONDS:
            result["beats"] = measure_beats(budget_seconds=left)
        else:
            result["beats"] = {"status": "idle", "note": "no time left this round"}
    except Exception as e:
        logger.exception("siansa_analysis beats failed")
        result["beats"] = {"status": "failed", "error": str(e)}
    return result


# The beat grids start no round with less than this left of the tick's slot.
MIN_BEATS_ROUND_SECONDS = 30


def prepare_now():
    return measure_missing()


def beats_now():
    return measure_beats()


def rebuild_now():
    return build_similar(force=True)
