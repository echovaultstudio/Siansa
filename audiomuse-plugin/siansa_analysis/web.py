"""The plugin's pages and its API for the Siansa app, mounted at ``/plugins/siansa_analysis/``.

API (JSON, for any logged-in user or API token):

* ``GET api/health``: the plugin is there and working;
* ``GET api/profiles?ids=a,b,…``: loudness and fade profiles of up to 500 songs (the layout Siansa reads);
* ``GET api/status``: how far each part is;
* ``GET api/similar/albums?track_id=…``: albums that sound like the album of that song;
* ``GET api/similar/artists?track_id=…`` or ``?artist=…``: artists that sound like that one.

Song ids may be the music server's ids or AudioMuse-AI's own; answers use the ids the app asked with, and
the asked music server's ids for songs in the similar lists. Like AudioMuse's own endpoints, each takes an optional
``server`` (a server's display name or id, docs/MULTI_SERVER.md); without it, the default server.
"""

import json
from html import escape

from flask import Blueprint, jsonify, redirect, request, url_for

from plugin.api import enqueue, get_db, get_setting, logger, manage_plugins_url, render_page, set_setting

from . import generations, jobs, similarity, status, store

PLUGIN_ID = "siansa_analysis"
VERSION = "0.2.3"
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
    return {
        "inputs": inputs,
        "counts": counts,
        "profiles_job": profiles_job,
        "similar_job": similar_job,
        "built": built,
        "profiles": status.profiles(counts, profiles_job),
        "similar": status.similar(inputs, built, similar_job, similarity.ALGORITHM_VERSION),
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
        logger.warning("siansa_analysis: couldn't look up server %r: %s", asked, e)
        return None
    if not server:
        logger.warning("siansa_analysis: AudioMuse has no music server named %r; using the default one", asked)
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
        })
    except Exception as e:
        logger.exception("siansa_analysis health check failed")
        return jsonify({"plugin": PLUGIN_ID, "plugin_version": VERSION, "status": "error", "error": str(e)[:200]})


@bp.route("/api/profiles")
def profiles():
    asked = [i for i in dict.fromkeys(p.strip() for p in request.args.get("ids", "").split(",")) if i]
    if len(asked) > MAX_IDS:
        return _error(f"At most {MAX_IDS} ids at a time", 400)
    db = get_db()
    canonical = _canonical(asked)
    found = store.profiles_for(db, set(canonical.values()))
    out, pending = [], []
    for asked_id in asked:
        profile = found.get(canonical.get(asked_id, asked_id))
        if profile is None:
            pending.append(asked_id)
        else:
            out.append({"track_id": asked_id, **profile})
    if pending:
        try:
            store.mark_wanted(db, {canonical.get(i, i) for i in pending})
        except Exception:
            db.rollback()
            logger.warning("siansa_analysis couldn't note asked-for songs", exc_info=True)
    return jsonify({"schema_version": SCHEMA_VERSION, "profiles": out, "pending": pending})


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


def render_settings(state, batch_size, message=""):
    inputs, counts, built = state["inputs"], state["counts"], state["built"]
    p, sim = state["profiles"], state["similar"]
    working = "working" in (p.kind, sim.kind) and (
        state["profiles_job"].get("status") == "running" or state["similar_job"].get("status") == "running")
    refresh = "<script>setTimeout(function(){location.reload()},15000)</script>" if working else ""
    note = f'<p class="mx-help"><strong>{escape(message)}</strong></p>' if message else ""
    failures = ""
    if state.get("failures"):
        items = "".join(
            f"<li>{escape(str(t or i))} – {escape(str(a or ''))}: {escape(str(e or ''))} (tried {n}×)</li>"
            for i, t, a, e, n in state["failures"])
        failures = f"<details><summary>Songs that couldn't be read</summary><ul>{items}</ul></details>"
    profiles_error = state["profiles_job"].get("error")
    measure_all = bool(state.get("measure_all"))
    profiles_running = state["profiles_job"].get("status") == "running"
    busy = " disabled" if profiles_running else ""
    left = status.time_left(counts["to_measure"], state.get("rate"), measure_all, int(batch_size))
    left_line = f'<p class="mx-help">At this pace, every song is measured in {escape(left)}.</p>' if left else ""
    if measure_all:
        all_block = f"""<div class="mx-warning" role="status"><strong>Measuring every song.</strong> Rounds of
      {status.ALL_ROUND_SECONDS // 60} minutes every 15 minutes until all are done. While a round runs, AudioMuse
      waits with its own jobs (analysis, clustering, scheduled tasks).
      <form method="post" class="mx-actions"><button type="submit" name="action" value="stop_all">Stop measuring all</button></form></div>"""
    elif counts["to_measure"]:
        warning = ("Measuring every song downloads each one from your music server and reads it in full. "
                   "With a large library this takes hours or days and keeps the AudioMuse worker busy. "
                   "While a round runs (12 of every 15 minutes), AudioMuse waits with its own jobs such as "
                   "analysis, clustering and scheduled tasks. You can stop at any time; measured songs are kept.")
        all_block = f"""<div class="mx-warning"><strong>Measure all songs at once</strong>
      <p class="mx-help">{escape(warning)}</p>
      <form method="post" class="mx-actions" onsubmit="return confirm({escape(json.dumps(warning + ' Start now?'))});">
        <button type="submit" name="action" value="measure_all"{busy}>Measure all {counts['to_measure']:,} songs</button>
      </form></div>"""
    else:
        all_block = ""
    similar_error = state["similar_job"].get("error")
    return f"""{_STYLE}{refresh}{note}
<section class="mx-section" aria-label="Library">
  <div class="mx-head"><h3>1. Library</h3></div>
  <p>{inputs.library_songs:,} songs in AudioMuse, {inputs.analysis_songs:,} with sound analysis.</p>
  <p class="mx-help">Both parts below read from here and keep their own progress.</p>
</section>

<section class="mx-section" aria-label="Volume and fade profiles">
  <div class="mx-head"><h3>2. Volume &amp; fade profiles</h3>{_badge(p)}</div>
  {_meter(p, "Songs measured")}
  <p><strong>{escape(p.detail)}.</strong> {counts['to_measure']:,} still to measure{f", {counts['asked_for']:,} asked for by the app first" if counts['asked_for'] else ""}.</p>
  <p class="mx-help">Loudness levels the volume and the fade profile lets Siansa fade songs the way they
    start and end (SmoothFade). New songs are measured right after AudioMuse analyses them; older ones a
    batch at a time every 15 minutes. This runs on its own: it doesn't wait for the similar lists, and
    they don't wait for it.</p>
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

<section class="mx-section" aria-label="Similar albums and artists">
  <div class="mx-head"><h3>3. Similar albums &amp; artists</h3>{_badge(sim)}</div>
  {_meter(sim, "Build progress")}
  <p><strong>{escape(sim.detail)}.</strong></p>
  <p class="mx-help">Built from the library and AudioMuse's sound analysis. It builds again by itself
    when either of them changes, and the last lists stay in use until a new build is finished.</p>
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
    "all": "Measuring every song. It goes on in rounds until all are done; you can stop it here.",
    "all_waiting": "Measuring every song starts with the next round, within 15 minutes: another AudioMuse job is running.",
    "stopped": "Stopped. The songs measured so far are kept; the rest are measured a batch at a time again.",
    "building": "Build started. This page updates by itself.",
    "busy": "Another AudioMuse job is running, so this waits for the next round (within 15 minutes).",
}


def _start(func):
    """Queues a job; ``False`` when AudioMuse-AI is busy with another job (it refuses to start one then)."""
    try:
        enqueue(func, queue="high")
        return True
    except Exception as e:  # AudioMuse's "Another queue job is already running"
        logger.info("siansa_analysis job not started now: %s", e)
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
        db = get_db()
        if action == "measure":
            note = "measuring" if _start(jobs.prepare_now) else "busy"
        elif action == "measure_all":
            store.set_state("measure_all", {"on": True, "started_at": jobs.now_iso()}, db)
            note = "all" if _start(jobs.prepare_now) else "all_waiting"
        elif action == "stop_all":
            store.set_state("measure_all", {"on": False, "stopped_at": jobs.now_iso()}, db)
            note = "stopped"
        elif action == "rebuild":
            note = "building" if _start(jobs.rebuild_now) else "busy"
        elif action == "save":
            return redirect(manage_plugins_url())
        return redirect(url_for(f"{PLUGIN_ID}.settings", note=note))
    db = get_db()
    try:
        jobs.refresh_inputs(db)  # cheap: two digests; keeps the generations shown here current
    except Exception:
        db.rollback()
        logger.warning("siansa_analysis couldn't read the inputs", exc_info=True)
    state = collect(db)
    state["failures"] = store.recent_failures(db)
    state["measure_all"] = bool((store.get_state("measure_all", {}, db) or {}).get("on"))
    state["rate"] = store.get_state("profiles_rate", {}, db) or {}
    note = MESSAGES.get(request.args.get("note", ""), "").replace("{n}", f"{request.args.get('n', 0, type=int):,}")
    body = render_settings(state, get_setting("batch_size", jobs.DEFAULT_BATCH), note)
    return render_page(body, title="Siansa Analysis")
