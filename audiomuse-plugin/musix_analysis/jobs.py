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

from plugin.api import active_server_id, get_db, get_setting, logger

from . import generations, loudness, ramps, similarity, status, store

HOOK_BUDGET_SECONDS = 60
DEFAULT_BATCH = 40


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def profile_of(measurement):
    """A song's stored profile from its :class:`loudness.Measurement`."""
    relative = measurement.relative_levels()
    return {
        "ref_lufs": None if measurement.lufs is None else round(measurement.lufs, 2),
        "start_ramp": ramps.encode(ramps.start_ramp(relative)),
        "end_ramp": ramps.encode(ramps.end_ramp(relative)),
        "duration_ms": measurement.duration_ms,
    }


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
        logger.debug("musix_analysis measured %s in %.1f s", canonical, time.monotonic() - started)
    except Exception as e:  # never break the analysis; the tick tries again later
        db.rollback()
        logger.warning("musix_analysis couldn't measure %s after analysis: %s", canonical, e)
        store.save_failure(db, canonical, e)


class NotOnServer(Exception):
    """The song is on another music server; that server's run measures it."""


def _download(item_id, title, folder):
    """Fetches one song's audio from the music server of this run; returns the file path or ``None``."""
    from tasks.mediaserver import download_track
    from tasks.mediaserver.registry import translate_ids

    provider_id = translate_ids([item_id], active_server_id()).get(item_id)
    if not provider_id:
        raise NotOnServer(item_id)
    # The keys each server's download reads: Navidrome 'id', Jellyfin/Emby 'Id', names for the log.
    item = {"id": provider_id, "Id": provider_id, "title": title or item_id, "Name": title or item_id}
    return download_track(folder, item)


def measure_all_on(db):
    return bool((store.get_state("measure_all", {}, db) or {}).get("on"))


def measure_missing(budget_seconds=None, batch=None):
    """Measures songs without a profile, a round at a time.

    A normal round measures one batch (the "Songs per run" setting) within :data:`status.NORMAL_ROUND_SECONDS`.
    With "Measure all songs" on, a round goes on until :data:`status.ALL_ROUND_SECONDS`, and the scheduled tick
    keeps starting rounds until every song is measured or it is stopped. The rounds end with a few minutes
    free, because AudioMuse-AI doesn't start its own jobs (such as analysis) while a plugin job runs.
    """
    db = get_db()
    if not store.try_lock(db, "profiles"):
        return {"status": "busy"}
    started = time.monotonic()
    measured = failed = skipped = 0
    all_on = measure_all_on(db)
    budget = budget_seconds or status.round_seconds(all_on)
    folder = tempfile.mkdtemp(prefix="musix_analysis_")
    rate = store.get_state("profiles_rate", {}, db) or {}
    try:
        limit = 10_000 if all_on else max(1, min(int(batch or get_setting("batch_size", DEFAULT_BATCH) or DEFAULT_BATCH), 500))
        store.set_state("profiles_job", {"status": "running", "started_at": now_iso(), "all": all_on, "measured": 0,
                                         "error": None}, db)
        tried = set()
        stopped = False
        while len(tried) < limit and not stopped:
            rows = [r for r in store.songs_to_measure(db, min(25, limit - len(tried)) + len(tried), include_imported=all_on)
                    if r[0] not in tried]
            if not rows:
                break
            for item_id, title in rows:
                left = budget - (time.monotonic() - started)
                if left <= 5:
                    stopped = True
                    break
                if all_on and not measure_all_on(db):  # "Stop" on the settings page
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
                except Exception as e:
                    db.rollback()
                    failed += 1
                    logger.info("musix_analysis couldn't measure %s: %s", item_id, e)
                    store.save_failure(db, item_id, e)
                finally:
                    if path and os.path.exists(path):
                        os.remove(path)
                if measured and measured % 10 == 0:
                    store.set_state("profiles_job", {"status": "running", "started_at": now_iso(), "all": all_on,
                                                     "measured": measured, "error": None}, db)
        seconds = round(time.monotonic() - started, 1)
        store.set_state("profiles_rate", status.next_rate(rate, measured + failed, seconds), db)
        if all_on and not store.songs_to_measure(db, 1, include_imported=True):
            store.set_state("measure_all", {"on": False, "finished_at": now_iso()}, db)
            logger.info("musix_analysis: every song is measured")
        result = {"status": "idle", "finished_at": now_iso(), "measured": measured, "failed": failed,
                  "other_server": skipped, "seconds": seconds, "error": None}
        store.set_state("profiles_job", result, db)
        logger.info("musix_analysis profiles: %s measured, %s failed in %.0f s", measured, failed, seconds)
        return result
    except Exception as e:
        db.rollback()
        store.set_state("profiles_job", {"status": "failed", "finished_at": now_iso(), "error": str(e)[:300]}, db)
        logger.exception("musix_analysis profile run failed")
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
        logger.info("musix_analysis inputs: library generation %s, analysis generation %s",
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
        logger.info("musix_analysis building similar albums and artists: %s", reason)

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
        logger.info("musix_analysis similar lists: %s albums, %s artists in %.0f s",
                    result["albums"], result["artists"], result["seconds"])
        return {"status": "built", **result}
    except Exception as e:
        db.rollback()
        store.set_state("similar_job", {"status": "failed", "finished_at": now_iso(), "error": str(e)[:300]}, db)
        logger.exception("musix_analysis similar build failed")
        return {"status": "failed", "error": str(e)}
    finally:
        store.unlock(db, "similar")


# ---- the tick ------------------------------------------------------------------------------------------------

def tick():
    """The scheduled task: keeps both parts current, each on its own."""
    result = {}
    for name, job in (("similar", build_similar), ("profiles", measure_missing)):
        try:
            result[name] = job()
        except Exception as e:  # one part failing must not stop the other
            logger.exception("musix_analysis %s failed", name)
            result[name] = {"status": "failed", "error": str(e)}
    return result


def prepare_now():
    return measure_missing()


def rebuild_now():
    return build_similar(force=True)
