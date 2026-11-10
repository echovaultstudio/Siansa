"""The plugin's pages and its API for Siansa Dev, mounted at ``/plugins/siansa_beats/``. It began as a copy of the
Siansa plugin's and does all it does; each song's beat grid comes from Siansa Beats (:mod:`.beats_service`).

API (JSON, for any logged-in user or API token):

* ``GET api/health``: the plugin is there and working;
* ``GET api/profiles?ids=a,b,…``: loudness and fade profiles of up to 500 songs (the layout Siansa reads), with
  the beat grid at each end (``intro_bpm``, ``intro_beat_ms``, ``intro_bar_ms``, ``intro_rhythm``,
  ``intro_steady`` and the same for ``outro``) once a song has been measured for them, and ``beats_until_ms`` when
  the beat stops well before the end (the last beat, ms from the start);
* ``GET api/status``: how far each part is;
* ``GET api/similar/albums?track_id=…``: albums that sound like the album of that song;
* ``GET api/similar/artists?track_id=…`` or ``?artist=…``: artists that sound like that one;
* ``POST api/bad-blends``: a song change marked as bad in Siansa Dev, passed on to Siansa Beats' list.

The profiles' beat grids are Siansa Beats' (with its lock-in, bass, energy, drops and breakdowns), and each carries
AudioMuse-AI's key and scale.

Song ids may be the music server's ids or AudioMuse-AI's own; answers use the ids the app asked with, and
the asked music server's ids for songs in the similar lists. Like AudioMuse's own endpoints, each takes an optional
``server`` (a server's display name or id, docs/MULTI_SERVER.md); without it, the default server.
"""

import json
import urllib.parse
from datetime import datetime, timezone
from html import escape

from flask import Blueprint, jsonify, redirect, request, url_for

from plugin.api import enqueue, get_db, get_setting, logger, manage_plugins_url, render_page, set_setting

from . import beats_service, generations, jobs, similarity, status, store

PLUGIN_ID = "siansa_beats"
VERSION = "0.5.2"  # the newest in plugin.json: AudioMuse-AI and the Siansa app show it (test_version.py)
SCHEMA_VERSION = 1
MAX_IDS = 500

bp = Blueprint(PLUGIN_ID, __name__)


# ---- shared ----------------------------------------------------------------------------------------------------

def collect(db):
    """Every part's state, in one place for the page and the API."""
    inputs = generations.Inputs.from_json(store.get_state("inputs", {}, db))
    songs, ready, failed, wanted = store.profile_counts(db)
    counts = status.profile_counts(songs, ready, failed, wanted)
    profiles_job = store.get_state("profiles_job", {}, db) or {}
    similar_job = store.get_state("similar_job", {}, db) or {}
    built = store.get_state("similar_built", {}, db) or {}
    beat_counts = status.beats_counts(*store.beats_counts(db))
    try:
        beat_counts["until_waiting"] = store.until_waiting(db)
    except Exception:  # the page shows without it
        if db is not None:
            db.rollback()
        beat_counts["until_waiting"] = 0
    beats_job = store.get_state("beats_job", {}, db) or {}
    return {
        "inputs": inputs,
        "counts": counts,
        "beat_counts": beat_counts,
        "beats_job": beats_job,
        "beats": status.beats(beat_counts, beats_job),
        "profiles_job": profiles_job,
        "similar_job": similar_job,
        "built": built,
        "profiles": status.profiles(counts, profiles_job),
        "similar": status.similar(inputs, built, similar_job, similarity.ALGORITHM_VERSION),
        # Where the rounds are (Siansa issue #765): the last round, the schedule and what holds AudioMuse's queue.
        "last_tick": store.get_state("last_tick", {}, db) or {},
        "cron": store.scheduled_tick(db),
        "blocking": store.blocking_task(db),
    }


def _server():
    """The music server's id for the server the app asks about, exactly as AudioMuse's own endpoints read it
    (``app_server_context.resolve_request_server_id``): ``server`` or ``server_id``, as its display name or its id.
    None means AudioMuse's default server, and also an unknown name (logged), like AudioMuse answers it."""
    asked = (request.args.get("server") or request.args.get("server_id") or "").strip()
    if not asked:
        return None
    try:
        from tasks.mediaserver import registry

        # The same lookup as AudioMuse's resolve_request_server_id: the id, else the display name (any case).
        server = registry.get_server(asked) or registry.get_server_by_name(asked)
    except Exception as e:  # an AudioMuse without the multi-server registry
        logger.warning("siansa_beats: couldn't look up server %r: %s", asked, e)
        return None
    if not server:
        logger.warning("siansa_beats: AudioMuse has no music server named %r; using the default one", asked)
        return None
    return server["server_id"] if isinstance(server, dict) else getattr(server, "server_id", None)


def _canonical(ids):
    """``{asked id: canonical id}``: the asked server's ids (or canonical ones) to AudioMuse's catalogue ids."""
    try:
        from tasks.mediaserver.registry import canonical_input_ids

        server = _server()
        if server:
            try:
                return canonical_input_ids(ids, server)
            except TypeError:  # an AudioMuse that takes no server here
                pass
        return canonical_input_ids(ids)
    except Exception:
        return {i: i for i in ids}


def _server_ids(canonical_ids):
    """``{canonical id: the asked server's id}`` for the ids on that server (the default one without a server)."""
    try:
        from tasks.mediaserver.registry import translate_ids

        # Only the ids that exist on the asked server (the default server keeps its own and legacy ids).
        return translate_ids(canonical_ids, _server())
    except Exception:
        return {i: i for i in canonical_ids}


def _error(message, code):
    return jsonify({"error": message}), code


# ---- API -------------------------------------------------------------------------------------------------------

@bp.route("/api/health")
def health():
    try:
        state = collect(get_db())
        return jsonify({
            "plugin": PLUGIN_ID, "plugin_version": VERSION, "schema_version": SCHEMA_VERSION, "status": "ok",
            "profiles": state["profiles"].to_json(), "similar": state["similar"].to_json(),
            "beats": beats_service.health() or {"reachable": False, "problem": "Siansa Beats' address isn't set: enter it on this plugin's page"},
        })
    except Exception:
        # The details stay in AudioMuse's log: an error's text can say things about the server (CodeQL).
        logger.exception("siansa_beats health check failed")
        return jsonify({"plugin": PLUGIN_ID, "plugin_version": VERSION, "status": "error",
                        "error": "The plugin's health check failed: the details are in AudioMuse-AI's log"})


@bp.route("/api/profiles")
def profiles():
    asked = [i for i in dict.fromkeys(p.strip() for p in request.args.get("ids", "").split(",")) if i]
    if len(asked) > MAX_IDS:
        return _error(f"At most {MAX_IDS} ids at a time", 400)
    db = get_db()
    canonical = _canonical(asked)
    found = store.profiles_for(db, set(canonical.values()))
    try:
        keys = beats_service.song_keys(db, set(canonical.values()))
    except Exception:
        db.rollback()
        logger.warning("siansa_beats couldn't read AudioMuse-AI's keys", exc_info=True)
        keys = {}
    grids = beats_service.CACHE.grids(asked, beats_service._beats_get) if beats_service.beats_url() else {}
    mode = beats_service.missing_mode()
    out, pending, no_beats = [], [], []
    for asked_id in asked:
        own = found.get(canonical.get(asked_id, asked_id))
        profile = beats_service.merge(own, grids.get(asked_id), mode)
        if own is None:
            no_beats.append(asked_id)  # still to be measured for its loudness (a grid alone is sent, but it's wanted)
        if profile is None:
            pending.append(asked_id)
            continue
        out.append({"track_id": asked_id, **profile, **keys.get(canonical.get(asked_id, asked_id), {})})
        if own is None or asked_id in grids:
            continue  # Siansa Beats' grid stands: no need to read its beats here
        if "intro_bpm" not in own and "outro_bpm" not in own:
            no_beats.append(asked_id)  # measured before the beat tracker, maybe: its beats come first then
        elif store.UNTIL_KEY not in own:
            no_beats.append(asked_id)  # measured before 0.5.0: checked for where its beat stops, as it's about to play
    if pending or no_beats:
        try:
            store.mark_wanted(db, {canonical.get(i, i) for i in pending + no_beats})
        except Exception:
            db.rollback()
            logger.warning("siansa_beats couldn't note asked-for songs", exc_info=True)
    return jsonify({"schema_version": SCHEMA_VERSION, "profiles": out, "pending": pending})


@bp.route("/api/bad-blends", methods=["POST"])
def bad_blend():
    """A song change marked as bad in Siansa Dev, passed on to Siansa Beats' list (the app only knows AudioMuse-AI)."""
    body = request.get_json(silent=True) or {}
    mark = {"from_id": str(body.get("from_id") or "").strip(), "to_id": str(body.get("to_id") or "").strip(),
            "technique": str(body.get("technique") or "")[:80],
            "details": body.get("details") if isinstance(body.get("details"), dict) else {}}
    if not mark["from_id"] or not mark["to_id"]:
        return _error("from_id and to_id needed: the two songs", 400)
    try:
        answer = beats_service._beats_get("/api/bad-blends", mark)
    except beats_service.BeatsError as e:
        logger.warning("siansa_beats: couldn't pass a bad blend on: %s", e)
        return _error(str(e), 502)
    logger.info("siansa_beats: bad blend %s into %s (%s) passed on", mark["from_id"], mark["to_id"], mark["technique"])
    return jsonify({"id": answer.get("id")})


@bp.route("/api/genres")
def genre_list():
    """The genres of the library by Siansa Beats' vote, most songs first: ``{"genres": [{"name", "style", "genre",
    "songs"}], "source"}``. ``name`` is what to ask a radio for. 502 when Siansa Beats doesn't answer, and an empty
    list until its style model has voted: the app then shows the tags' genres."""
    try:
        limit = max(1, min(400, int(request.args.get("limit") or 60)))
    except ValueError:
        return _error("limit is a number", 400)
    try:
        answer = beats_service._beats_get("/api/genres")
    except beats_service.BeatsError as e:
        logger.info("siansa_beats: no genre list from Siansa Beats: %s", e)
        return _error(str(e), 502)
    listed = [{"name": g.get("name") or g.get("style"), "style": g.get("style"), "genre": g.get("genre"),
               "songs": g.get("songs", 0)} for g in (answer.get("genres") or [])[:limit]]
    return jsonify({"genres": listed, "available": bool((answer.get("styles") or {}).get("available")),
                    "source": "siansa_beats"})


@bp.route("/api/genre/about")
def genre_about():
    """A genre's short description with a little history, from Siansa Beats (the opening of its Wikipedia article,
    named and linked): ``{"name", "about": {"summary", "url", "source", "title"} or null}``. 502 when Siansa Beats doesn't
    answer: the app's genre page then shows without it."""
    name = (request.args.get("name") or "").strip()
    if not name:
        return _error("name needed: the genre, as the app shows it", 400)
    try:
        answer = beats_service._beats_get("/api/genre/about?" + urllib.parse.urlencode({"name": name}))
    except beats_service.BeatsError as e:
        logger.info("siansa_beats: no description from Siansa Beats for %s: %s", name, e)
        return _error(str(e), 502)
    return jsonify({"name": name, "about": answer.get("about"), "source": "siansa_beats"})


@bp.route("/api/genre")
def genre_songs():
    """The songs of a genre radio, by Siansa Beats' vote (each song's sound first, its tags second): ``{"ids",
    "styles", "genres", "source"}``. 502 when Siansa Beats doesn't answer: the app then plays by its tags."""
    name = (request.args.get("name") or "").strip()
    if not name:
        return _error("name needed: the genre, as the app shows it", 400)
    try:
        limit = max(1, min(2000, int(request.args.get("limit") or 300)))
    except ValueError:
        return _error("limit is a number", 400)
    try:
        answer = beats_service._beats_get("/api/genre?" + urllib.parse.urlencode({"name": name, "limit": limit}))
    except beats_service.BeatsError as e:
        logger.info("siansa_beats: no genre radio from Siansa Beats for %s: %s", name, e)
        return _error(str(e), 502)
    return jsonify({"name": name, "ids": [str(i) for i in answer.get("ids") or []],
                    "styles": answer.get("styles") or [], "genres": answer.get("genres") or [],
                    "why": answer.get("why"), "source": "siansa_beats"})


@bp.route("/api/status")
def api_status():
    state = collect(get_db())
    inputs, built = state["inputs"], state["built"]
    return jsonify({
        "schema_version": SCHEMA_VERSION,
        "library": {"songs": inputs.library_songs, "analysed": inputs.analysis_songs,
                    "library_generation": inputs.library_generation,
                    "analysis_generation": inputs.analysis_generation},
        "profiles": {**state["profiles"].to_json(), "counts": state["counts"], "job": state["profiles_job"]},
        "beats": {**state["beats"].to_json(), "counts": state["beat_counts"], "job": state["beats_job"]},
        "similar": {**state["similar"].to_json(), "built": built, "job": state["similar_job"]},
    })


def _similar_answer(kind, key, extra):
    db = get_db()
    row = store.similar_row(db, kind, key)
    built = store.get_state("similar_built", {}, db) or {}
    if row is None:
        return jsonify({"schema_version": SCHEMA_VERSION, "generation": built.get("generation", 0),
                        "similar": [], **extra})
    ids = _server_ids([c["track_id"] for c in row["candidates"]])
    # Like AudioMuse's own answers: only albums and artists with a song on the asked server, with that server's id.
    similar = [{**c, "track_id": ids[c["track_id"]]} for c in row["candidates"] if c["track_id"] in ids]
    return jsonify({"schema_version": SCHEMA_VERSION, "generation": built.get("generation", 0),
                    "name": row["name"], "artist": row["artist"], "similar": similar, **extra})


@bp.route("/api/similar/albums")
def similar_albums():
    track_id = request.args.get("track_id", "").strip()
    if not track_id:
        return _error("Give a track_id", 400)
    canonical = _canonical([track_id]).get(track_id, track_id)
    info = store.song_info(get_db(), [canonical]).get(canonical)
    if not info or not info[1]:
        return _error("AudioMuse doesn't know this song or its album", 404)
    _title, album, album_artist, author = info
    key = similarity.album_key(similarity.artist_of(album_artist, author), album)
    return _similar_answer("albums", key, {"track_id": track_id})


@bp.route("/api/similar/artists")
def similar_artists():
    track_id = request.args.get("track_id", "").strip()
    name = request.args.get("artist", "").strip()
    if track_id:
        canonical = _canonical([track_id]).get(track_id, track_id)
        info = store.song_info(get_db(), [canonical]).get(canonical)
        if not info:
            return _error("AudioMuse doesn't know this song", 404)
        name = (info[3] or info[2] or "").strip()
    if not name:
        return _error("Give a track_id or an artist", 400)
    return _similar_answer("artists", similarity.norm(name), {"artist_asked": name})


# ---- settings page ---------------------------------------------------------------------------------------------

_STYLE = """
<style>
  .mx-section { margin: 0 0 24px; padding: 16px; border: 1px solid rgba(127,127,127,.35); border-radius: 12px; }
  .mx-head { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; flex-wrap: wrap; }
  .mx-head h3 { margin: 0; }
  .mx-badge { padding: 2px 10px; border-radius: 999px; font-size: .85em; font-weight: 600; }
  .mx-ready { background: #1b7f3b33; color: #1b7f3b; }
  .mx-working { background: #b8860b33; color: #9a6f00; }
  .mx-attention { background: #c0392b33; color: #c0392b; }
  .mx-meter { height: 8px; border-radius: 4px; background: rgba(127,127,127,.25); margin: 12px 0 8px; overflow: hidden; }
  .mx-fill { height: 100%; background: #1b7f3b; }
  .mx-help { opacity: .8; font-size: .92em; margin: 8px 0; }
  .mx-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 12px; }
  .mx-actions input { width: 6em; }
  .mx-warning { margin-top: 16px; padding: 12px; border-radius: 8px; border: 1px solid #b8860b88; background: #b8860b14; }
  .mx-warning .mx-actions { margin-top: 8px; }
  @media (prefers-color-scheme: dark) { .mx-ready { color: #5fd38a; } .mx-working { color: #f0c04a; } .mx-attention { color: #ff8a7a; } }
</style>
"""


def _badge(s):
    return f'<span class="mx-badge mx-{s.kind}">{escape(s.label)}</span>'


def _meter(s, label):
    return (f'<div class="mx-meter" role="progressbar" aria-label="{escape(label)}" aria-valuemin="0" '
            f'aria-valuemax="100" aria-valuenow="{s.percent}"><div class="mx-fill" style="width:{s.percent}%"></div></div>')


def _reload_script(ms):
    """Reloads the page every ``ms``, but never while a field is in use or changed and not saved: a number being
    typed into "Songs per run" would be lost (Siansa issue #765, now that the page reloads between rounds too)."""
    return ("<script>(function(){var dirty=false;document.addEventListener('input',function(){dirty=true});"
            "function tick(){var a=document.activeElement;"
            "if(dirty||(a&&/^(INPUT|SELECT|TEXTAREA)$/.test(a.tagName))){setTimeout(tick,%d);return}"
            "location.reload()}setTimeout(tick,%d)})()</script>" % (ms, ms))


def _rounds_form(all_minutes):
    """The choice of how "Measure all" runs (Siansa issue #765): without a break, or rounds of so many minutes."""
    chosen = status.all_round_minutes(all_minutes)
    options = "".join(
        f'<option value="{m}"{" selected" if m == chosen else ""}>'
        f'{"Without a break, until all are done" if not m else f"Rounds of {m} minutes every 15"}</option>'
        for m in status.ALL_ROUND_CHOICES)
    return f"""<form method="post" class="mx-actions">
        <label>Measure all runs <select name="all_round_minutes">{options}</select></label>
        <button type="submit" name="action" value="save_rounds">Save</button>
      </form>"""


def _stop_now(measuring, all_on):
    """While a run measures songs, a way to end it now, whatever started it (Siansa issue #765)."""
    if not measuring:
        return ""
    after = " and Measure all goes off" if all_on else ""
    return f"""<div class="mx-warning" role="status"><strong>Measuring songs now.</strong>
  <p class="mx-help">Stop now ends it after the current song{after}. What's measured is kept.</p>
  <form method="post" class="mx-actions"><button type="submit" name="action" value="cancel">Stop now</button></form></div>"""


def _all_block(part, to_measure, busy, on, what, round_note, rounds=("", False), last="",
               all_minutes=status.DEFAULT_ALL_ROUND_MINUTES):
    """The "measure all" box of a part: a warning and a start button, or, while on, where the rounds are (a round
    running, between rounds, or waiting and why: Siansa issue #765), the last round, and a stop button. Either way
    the choice of how it runs: without a break, or in rounds."""
    action = "measure_all" if part == "profiles" else "beats_all"
    stop = "stop_all" if part == "profiles" else "stop_beats_all"
    how = status.all_round_text(all_minutes)
    if on:
        line, attention = rounds
        now_line = (f'<p class="mx-help{" mx-attention" if attention else ""}" data-rounds><strong>{escape(line)}</strong></p>'
                    if line else "")
        last_line = f'<p class="mx-help">{escape(last)}</p>' if last else ""
        return f"""<div class="mx-warning" role="status"><strong>Measuring every song{what}.</strong> {escape(how)}
      {now_line}{last_line}
      {_rounds_form(all_minutes)}
      <form method="post" class="mx-actions"><button type="submit" name="action" value="{stop}">Stop measuring all</button></form></div>"""
    if not to_measure:
        return ""
    warning = ("Each song is downloaded from your music server and read again: hours or days for a large library. "
               + how + " You can stop any time; what's measured is kept." + round_note)
    return f"""<div class="mx-warning"><strong>Measure all songs at once</strong>
      <p class="mx-help">{escape(warning)}</p>
      <form method="post" class="mx-actions" onsubmit="return confirm({escape(json.dumps(warning + ' Start now?'))});">
        <button type="submit" name="action" value="{action}"{busy}>Measure all {to_measure:,} songs{what}</button>
      </form>
      {_rounds_form(all_minutes)}</div>"""


def render_settings(state, batch_size, message="", beats_batch_size=None):
    inputs, counts, built = state["inputs"], state["counts"], state["built"]
    p, sim = state["profiles"], state["similar"]
    b = state.get("beats") or status.beats(status.beats_counts(0, 0, 0, 0), {})
    beat_counts = state.get("beat_counts") or status.beats_counts(0, 0, 0, 0)
    beats_job = state.get("beats_job") or {}
    beats_batch_size = jobs.DEFAULT_BEATS_BATCH if beats_batch_size is None else int(beats_batch_size)
    now = datetime.now(timezone.utc)
    profiles_running = status.job_running(state["profiles_job"], now)
    beats_running = status.job_running(beats_job, now)
    working = "working" in (p.kind, sim.kind, b.kind) and (
        profiles_running or state["similar_job"].get("status") == "running" or beats_running)
    # While every song is being measured, the page keeps itself current between rounds too (issue #765): before,
    # it stopped updating once a round ended, so the count looked stuck though the next round had moved it on.
    any_all = bool(state.get("measure_all")) or bool(state.get("beats_all"))
    refresh = _reload_script(15000 if working else 30000) if working or any_all else ""
    all_minutes = status.all_round_minutes(state.get("all_round_minutes", status.DEFAULT_ALL_ROUND_MINUTES))
    rounds = status.round_status(beats_job, state["profiles_job"], state.get("last_tick"), state.get("cron"),
                                 state.get("blocking"), now, all_minutes)
    last_round = status.last_round_text(state.get("last_tick"), now)
    note = f'<p class="mx-help"><strong>{escape(message)}</strong></p>' if message else ""
    failures = ""
    if state.get("failures"):
        items = "".join(
            f"<li>{escape(str(t or i))} – {escape(str(a or ''))}: {escape(str(e or ''))} (tried {n}×)</li>"
            for i, t, a, e, n in state["failures"])
        failures = f"<details><summary>Songs that couldn't be read</summary><ul>{items}</ul></details>"
    profiles_error = state["profiles_job"].get("error")
    measure_all = bool(state.get("measure_all"))
    # Both parts read songs one at a time under one lock, so neither starts while the other runs.
    measuring = profiles_running or beats_running
    busy = " disabled" if measuring else ""
    left = status.time_left(counts["to_measure"], state.get("rate"), measure_all, int(batch_size), all_minutes)
    left_line = f'<p class="mx-help">At this pace, every song is measured in {escape(left)}.</p>' if left else ""
    all_block = _all_block("profiles", counts["to_measure"], busy, measure_all, "", "", rounds, last_round, all_minutes)
    beats_all = bool(state.get("beats_all"))
    beats_waiting = beat_counts["to_measure"] + beat_counts.get("until_waiting", 0)
    beats_left = status.time_left(beats_waiting, state.get("rate"), beats_all, max(beats_batch_size, 1), all_minutes) if (beats_batch_size or beats_all) else None
    beats_left_line = (f'<p class="mx-help">At this pace, every song has its beat grid in {escape(beats_left)}.</p>'
                       if beats_left else "")
    if not beats_batch_size and not beats_all and beat_counts["to_measure"]:
        beats_left_line = '<p class="mx-help">Songs per run is 0: older songs are measured only when you press a button here (new songs still are).</p>'
    # Songs measured before 0.5.0 wait to be checked for where their beat stops: they count for the buttons too
    # (0.5.0 showed the beat grids as done, with no Measure all).
    beats_open = beat_counts["to_measure"] + beat_counts.get("until_waiting", 0)
    beats_all_block = _all_block("beats", beats_open, busy, beats_all, " for their beats",
                                 " What's measured stays in use meanwhile.", rounds, last_round, all_minutes)
    beats_error = beats_job.get("error")
    beats_failures = ""
    if beat_counts["failed"]:
        # Why, the most common first, then the last few songs (issue #657), and a way to try them all again now.
        reasons = "".join(f"<li>{n:,} songs: {escape(str(r or 'unknown'))}</li>" for r, n in state.get("beats_reasons") or [])
        songs = "".join(f"<li>{escape(str(t or i))} – {escape(str(a or ''))}: {escape(str(e or ''))}</li>"
                        for i, t, a, e in state.get("beats_failures") or [])
        beats_failures = f"""<details><summary>Why {beat_counts['failed']:,} songs couldn't be read yet</summary>
    {f"<ul>{reasons}</ul>" if reasons else ""}{f"<p class='mx-help'>The last ones:</p><ul>{songs}</ul>" if songs else ""}
    <p class="mx-help">Each is tried again after an hour, 6 hours, a day and 3 days.</p>
    <form method="post" class="mx-actions"><button type="submit" name="action" value="retry_beats"{busy}>Try them all again now</button></form>
  </details>"""
    elsewhere_line = ""
    if beat_counts.get("elsewhere"):
        elsewhere_line = (f'<p class="mx-help">{beat_counts["elsewhere"]:,} songs aren\'t on any of AudioMuse\'s music '
                          "servers now (removed, or not matched yet); they're looked at again once a week and don't "
                          "count here.</p>")
    similar_error = state["similar_job"].get("error")
    stop_now = _stop_now(measuring, measure_all or beats_all)
    return f"""{_STYLE}{refresh}{note}{stop_now}
<section class="mx-section" aria-label="Library">
  <div class="mx-head"><h3>1. Library</h3></div>
  <p>{inputs.library_songs:,} songs in AudioMuse, {inputs.analysis_songs:,} with sound analysis.</p>

</section>

<section class="mx-section" aria-label="Volume and fade profiles">
  <div class="mx-head"><h3>2. Volume &amp; fade profiles</h3>{_badge(p)}</div>
  {_meter(p, "Songs measured")}
  <p><strong>{escape(p.detail)}.</strong> {counts['to_measure']:,} still to measure{f", {counts['asked_for']:,} asked for by the app first" if counts['asked_for'] else ""}.</p>
  <p class="mx-help">Loudness for levelling, and how each song starts and ends for Siansa's fades. New songs are
    measured after AudioMuse analyses them, older ones a batch every 15 minutes.</p>
  {f'<p class="mx-help mx-attention">Last run: {escape(profiles_error)}</p>' if profiles_error else ''}
  {failures}
  {left_line}
  <form method="post" class="mx-actions">
    <label>Songs per run <input name="batch_size" value="{int(batch_size)}" inputmode="numeric"></label>
    <button type="submit" name="action" value="save">Save</button>
    <button type="submit" name="action" value="measure"{busy}>Measure missing songs now</button>
  </form>
  {all_block}
</section>

<section class="mx-section" aria-label="Beat grids">
  <div class="mx-head"><h3>3. Beat grids</h3>{_badge(b)}</div>
  {_meter(b, "Songs measured for their beats")}
  <p><strong>{escape(b.detail)}.</strong> {beat_counts['to_measure']:,} still to measure.</p>
  {f"<p>{beat_counts['until_waiting']:,} songs measured before 0.5.0 still to be checked for where their beat stops. Measure all does them all; otherwise each is checked as Siansa is about to play it.</p>" if beat_counts.get('until_waiting') else ""}
  {elsewhere_line}
  <p class="mx-help">The tempo, beats, bars and phrases at each end of a song, and where the beat stops before a
    beatless outro, for Siansa's DJ blends. New songs get them with their profile; older ones a batch every 15
    minutes, songs Siansa is about to play first. What's measured stays in use meanwhile. A round stops when its
    time is up (4 minutes), so a big Songs per run means "as many as fit"; 0 means only when you ask.</p>
  {f'<p class="mx-help mx-attention">Last run: {escape(beats_error)}</p>' if beats_error else ''}
  {beats_failures}
  {beats_left_line}
  <form method="post" class="mx-actions">
    <label>Songs per run <input name="beats_batch_size" value="{beats_batch_size}" inputmode="numeric"></label>
    <button type="submit" name="action" value="save_beats">Save</button>
    <button type="submit" name="action" value="measure_beats"{busy if beats_open else " disabled"}>Measure a batch now</button>
  </form>
  {beats_all_block}
</section>

<section class="mx-section" aria-label="Similar albums and artists">
  <div class="mx-head"><h3>4. Similar albums &amp; artists</h3>{_badge(sim)}</div>
  {_meter(sim, "Build progress")}
  <p><strong>{escape(sim.detail)}.</strong></p>
  <p class="mx-help">Built from the library and AudioMuse's sound analysis, again by itself when either changes.
    The last lists stay in use meanwhile.</p>
  {f'<p class="mx-help mx-attention">Last build: {escape(similar_error)}</p>' if similar_error else ''}
  <details><summary>Technical details</summary>
    <p class="mx-help">Built from library generation {int(built.get('library_generation') or 0)} of
      {inputs.library_generation} and analysis generation {int(built.get('analysis_generation') or 0)} of
      {inputs.analysis_generation}; algorithm version {int(built.get('algorithm_version') or 0)}.
      {escape(str(built.get('finished_at') or 'Not built yet'))}.</p>
  </details>
  <form method="post" class="mx-actions">
    <button type="submit" name="action" value="rebuild"{" disabled" if state["similar_job"].get("status") == "running" else ""}>Build now</button>
  </form>
</section>
"""


MESSAGES = {
    "saved": "Saved.",
    "measuring": "Measuring started. This page updates by itself.",
    "all": "Measuring every song, until all are done. You can stop it here.",
    "cancelled": "Stopping after the current song. What's measured is kept; Measure all is off.",
    "all_waiting": "Another AudioMuse job is running: measuring every song starts with the next round, within 15 minutes.",
    "stopped": "Stopped. What's measured is kept; the rest go a batch at a time again.",
    "beats_measuring": "Measuring a batch for their beats. This page updates by itself.",
    "beats_retry": "{n} songs are tried again for their beats, starting now. This page updates by itself.",
    "beats_retry_waiting": "Another AudioMuse job is running: {n} songs are tried again with the next round, within 15 minutes.",
    "beats_all": "Measuring every song for its beats, until all are done. You can stop it here.",
    "beats_all_waiting": "Another AudioMuse job is running: measuring every song for its beats starts with the next round, within 15 minutes.",
    "building": "Build started. This page updates by itself.",
    "busy": "Another AudioMuse job is running, so this waits for the next round (within 15 minutes).",
}


def _start(func):
    """Queues a job; ``False`` when AudioMuse-AI is busy with another job (it refuses to start one then)."""
    try:
        enqueue(func, queue="high")
        return True
    except Exception as e:  # AudioMuse's "Another queue job is already running"
        logger.info("siansa_beats job not started now: %s", e)
        return False


@bp.route("/settings", methods=["GET", "POST"])
def settings():
    if request.method == "POST":
        # Post, then redirect: reloading the page (it reloads itself while working) never sends the form again.
        action = request.form.get("action")
        note = "saved"
        if action in ("save", "measure"):
            try:
                set_setting("batch_size", max(1, min(int(request.form.get("batch_size", jobs.DEFAULT_BATCH)), 500)))
            except ValueError:
                pass
        if action in ("save_beats", "measure_beats"):
            try:
                set_setting("beats_batch_size", max(0, min(int(request.form.get("beats_batch_size", jobs.DEFAULT_BEATS_BATCH)), jobs.MAX_BEATS_BATCH)))
            except ValueError:
                pass
        db = get_db()
        if action == "measure":
            note = "measuring" if _start(jobs.prepare_now) else "busy"
        elif action == "measure_all":
            store.set_state("measure_all", {"on": True, "started_at": jobs.now_iso()}, db)
            note = "all" if _start(jobs.prepare_now) else "all_waiting"
        elif action == "stop_all":
            store.set_state("measure_all", {"on": False, "stopped_at": jobs.now_iso()}, db)
            note = "stopped"
        elif action == "measure_beats":
            note = "beats_measuring" if _start(jobs.beats_now) else "busy"
        elif action == "beats_all":
            store.set_state("beats_all", {"on": True, "started_at": jobs.now_iso()}, db)
            note = "beats_all" if _start(jobs.beats_now) else "beats_all_waiting"
        elif action == "retry_beats":
            n = store.retry_beats(db)
            logger.info("siansa_beats: %s songs to be measured again for their beats", n)
            note = "beats_retry" if _start(jobs.beats_now) else "beats_retry_waiting"
            return redirect(url_for(f"{PLUGIN_ID}.settings", note=note, n=n))
        elif action == "stop_beats_all":
            store.set_state("beats_all", {"on": False, "stopped_at": jobs.now_iso()}, db)
            note = "stopped"
        elif action == "save_beats":
            note = "saved"
        elif action == "cancel":
            jobs.cancel(db)
            note = "cancelled"
        elif action == "save_rounds":
            set_setting("all_round_minutes", status.all_round_minutes(request.form.get("all_round_minutes")))
            note = "saved"
        elif action == "rebuild":
            note = "building" if _start(jobs.rebuild_now) else "busy"
        elif action == "save_service":
            url = (request.form.get("beats_url") or "").strip().rstrip("/")
            set_setting("beats_url", url)
            if request.form.get("forget_password"):
                set_setting("beats_password", "")
            elif request.form.get("beats_password"):
                set_setting("beats_password", request.form.get("beats_password"))
            mode = request.form.get("missing")
            if mode in beats_service.MISSING_CHOICES:
                set_setting("missing", mode)
            beats_service.CACHE.clear()
            logger.info("siansa_beats settings saved: Siansa Beats at %s", url or "(none)")
            note = "saved"
        elif action == "save":
            return redirect(manage_plugins_url())
        return redirect(url_for(f"{PLUGIN_ID}.settings", note=note))
    db = get_db()
    try:
        jobs.refresh_inputs(db)  # cheap: two digests; keeps the generations shown here current
    except Exception:
        db.rollback()
        logger.warning("siansa_beats couldn't read the inputs", exc_info=True)
    state = collect(db)
    state["failures"] = store.recent_failures(db)
    state["measure_all"] = bool((store.get_state("measure_all", {}, db) or {}).get("on"))
    state["beats_all"] = bool((store.get_state("beats_all", {}, db) or {}).get("on"))
    state["beats_failures"] = store.recent_beats_failures(db)
    state["beats_reasons"] = store.beats_failure_reasons(db)
    state["rate"] = store.get_state("profiles_rate", {}, db) or {}
    state["all_round_minutes"] = jobs.all_round_minutes()
    note = MESSAGES.get(request.args.get("note", ""), "").replace("{n}", f"{request.args.get('n', 0, type=int):,}")
    body = render_service(beats_service.health(), beats_service.beats_url(), bool(get_setting("beats_password", "")),
                          beats_service.missing_mode()) + \
        render_settings(state, get_setting("batch_size", jobs.DEFAULT_BATCH), note, jobs.batch_size("beats"))
    return render_page(body, title="Siansa Beats (dev)")


def render_service(beats, url, has_password, mode):
    """The page's first part: where Siansa Beats is, whether it answers, and what songs it hasn't done get."""
    if beats is None:
        reach = "<p>✗ Siansa Beats' address isn't set: without it, the beat grids are this plugin's own reading.</p>"
    elif beats.get("reachable"):
        reach = f'<p>✓ Siansa Beats {escape(str(beats.get("version") or ""))} answers.</p>'
    else:
        reach = f"<p>✗ {escape(beats.get('problem') or 'Siansa Beats does not answer')}</p>"
    options = "".join(
        f'<label><input type="radio" name="missing" value="{key}"{" checked" if key == mode else ""}> {escape(text)}</label><br>'
        for key, text in beats_service.MISSING_CHOICES.items())
    return f"""
<h2>Siansa Beats (dev)</h2>
<p>For Siansa Dev: each song's loudness and fades, measured here, with its tempo, beats, bars and phrases from
<strong>Siansa Beats</strong> (Beat This!), and similar albums and artists. It does all the Siansa plugin does, on its
own.</p>
{reach}
<form method="post">
  <p><label>Siansa Beats' address<br>
    <input type="url" name="beats_url" value="{escape(url)}" placeholder="http://192.168.1.10:8090" size="40"></label></p>
  <p><label>Its password (only if BEATS_PASSWORD is set)<br>
    <input type="password" name="beats_password" value="" placeholder="{"kept: leave empty to keep it" if has_password else "none"}" size="40"></label>
    {'<br><label><input type="checkbox" name="forget_password" value="1"> Remove the password</label>' if has_password else ""}</p>
  <p>Songs Siansa Beats hasn't analysed yet:<br>{options}</p>
  <p><button type="submit" name="action" value="save_service">Save and test</button></p>
</form>
"""
