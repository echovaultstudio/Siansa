"""The plugin's page and its API for Siansa Dev, mounted at ``/plugins/siansa_beats/``.

API (JSON, for any logged-in user or API token), the same layout as the Siansa plugin's so the app reads it alike:

* ``GET api/health``: the plugin is there, and whether Siansa Beats and the Siansa plugin's measurements are;
* ``GET api/profiles?ids=a,b,…``: up to 500 songs' profiles: the Siansa plugin's loudness and ramps, with the beat
  grid at each end (``intro_bpm``, ``intro_beat_ms``, ``intro_bar_ms``, ``intro_phrase_ms``, ``intro_phrase_bars``,
  ``intro_rhythm``, ``intro_steady``, the same for ``outro``, and ``beats_until_ms``) from Siansa Beats.

A song Siansa Beats hasn't analysed yet gets the Siansa plugin's beat grid, or none (the page's choice). Song ids are
the music server's (Siansa Beats knows the Navidrome ids it read) or AudioMuse-AI's own; each takes an optional
``server`` like AudioMuse's own endpoints.
"""

import base64
import json
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from html import escape

from flask import Blueprint, jsonify, redirect, request, url_for

from plugin.api import get_db, get_setting, logger, render_page, set_setting

PLUGIN_ID = "siansa_beats"
VERSION = "0.1.0"  # the newest in plugin.json (test_version.py)
SCHEMA_VERSION = 1
MAX_IDS = 500

# The Siansa plugin's table, read only: its loudness and ramps (and its own beat grids, for songs Siansa Beats
# hasn't analysed). AudioMuse-AI names a plugin's tables plugin_<id>__<name>.
SIANSA_PROFILES = "plugin_siansa_analysis__profiles"
SIANSA_ANALYZER_VERSION = 1  # siansa_analysis.store.ANALYZER_VERSION: its rows of another version aren't ready

GRIDS_PATH = "/api/grids"
TIMEOUT = 5.0
URL_BUDGET = 3500  # a request line stays well within what any web server takes
FOUND_SECONDS = 600  # a grid is asked for again after this, in case Siansa Beats analysed it again
MISSING_SECONDS = 120  # a song Siansa Beats hasn't done, sooner: it may be analysing it now

MISSING_CHOICES = {
    "plugin": "Use the Siansa plugin's beat grid",
    "none": "No beat grid (a plain smart fade)",
}

bp = Blueprint(PLUGIN_ID, __name__)


# ---- settings ----------------------------------------------------------------------------------------------------

def beats_url():
    return (get_setting("beats_url", "") or "").strip().rstrip("/")


def missing_mode():
    value = get_setting("missing", "plugin") or "plugin"
    return value if value in MISSING_CHOICES else "plugin"


# ---- Siansa Beats ------------------------------------------------------------------------------------------------

class BeatsError(Exception):
    pass


def _beats_get(path):
    """The JSON answer of Siansa Beats at ``path``, or :class:`BeatsError` saying what to check."""
    base = beats_url()
    if not base:
        raise BeatsError("Siansa Beats' address isn't set: enter it on this plugin's page")
    parts = urllib.parse.urlsplit(base)
    if parts.scheme not in ("http", "https") or not parts.hostname:
        raise BeatsError("Siansa Beats' address needs to start with http:// or https://")
    req = urllib.request.Request(base + path, headers={"Accept": "application/json"})
    password = get_setting("beats_password", "") or ""
    if password:
        req.add_header("Authorization", "Basic " + base64.b64encode(f"siansa:{password}".encode()).decode())
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            return json.load(response)
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            raise BeatsError("Siansa Beats wants its password: enter it on this plugin's page") from None
        raise BeatsError(f"Siansa Beats answered HTTP {e.code}") from None
    except urllib.error.URLError as e:
        if isinstance(e.reason, ConnectionRefusedError):
            raise BeatsError(f"Nothing answers at {parts.hostname}:{parts.port or ''}: is Siansa Beats running?") from None
        raise BeatsError(f"Couldn't reach Siansa Beats: {e.reason}") from None
    except (TimeoutError, OSError) as e:
        raise BeatsError(f"Couldn't reach Siansa Beats: {e}") from None
    except ValueError:
        raise BeatsError("The answer isn't Siansa Beats': check its address") from None


def batches(ids, budget=URL_BUDGET, max_ids=MAX_IDS):
    """``ids`` split so each request's address stays within ``budget`` bytes and ``max_ids`` ids."""
    out, current, length = [], [], len(GRIDS_PATH) + len("?ids=")
    start = length
    for i in ids:
        size = len(urllib.parse.quote(i, safe="")) + (1 if current else 0)
        if current and (length + size > budget or len(current) >= max_ids):
            out.append(current)
            current, length, size = [], start, size - 1
        current.append(i)
        length += size
    if current:
        out.append(current)
    return out


class GridCache:
    """Siansa Beats' grids by song id, kept a while: ``{id: (until, grid or None)}``."""

    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.lock = threading.Lock()
        self.rows = {}
        self.problem = None
        self.back_off_until = 0.0

    def grids(self, ids, fetch):
        """``{id: grid}`` for those of ``ids`` Siansa Beats has, asking it (with ``fetch(path)``) for the ones not
        kept. While Siansa Beats can't be reached, nothing is asked for a minute; the songs just have no grid."""
        now = self.clock()
        with self.lock:
            need = [i for i in ids if i not in self.rows or self.rows[i][0] <= now]
            asking = now >= self.back_off_until
        if need and asking:
            for batch in batches(need):
                try:
                    answer = fetch(GRIDS_PATH + "?ids=" + ",".join(urllib.parse.quote(i, safe="") for i in batch))
                except BeatsError as e:
                    with self.lock:
                        if self.problem != str(e):
                            logger.warning("siansa_beats: %s", e)
                        self.problem = str(e)
                        self.back_off_until = self.clock() + 60
                    break
                found = {g.get("track_id"): g for g in answer.get("grids") or [] if isinstance(g, dict)}
                with self.lock:
                    self.problem = None
                    for i in batch:
                        grid = found.get(i)
                        self.rows[i] = (now + (FOUND_SECONDS if grid else MISSING_SECONDS), grid)
                    if len(self.rows) > 50_000:
                        self.rows = {k: v for k, v in self.rows.items() if v[0] > now}
        with self.lock:
            return {i: self.rows[i][1] for i in ids if i in self.rows and self.rows[i][1]}

    def clear(self):
        with self.lock:
            self.rows.clear()
            self.back_off_until = 0.0


CACHE = GridCache()


# ---- the Siansa plugin's measurements, read only -----------------------------------------------------------------

def siansa_plugin_installed(db):
    cur = db.cursor()
    try:
        cur.execute("SELECT to_regclass(%s)", (SIANSA_PROFILES,))
        return cur.fetchone()[0] is not None
    finally:
        cur.close()


def siansa_profiles(db, item_ids):
    """The Siansa plugin's ready profiles of the given canonical ids, ``{item_id: profile}``, as it sends them."""
    if not item_ids or not siansa_plugin_installed(db):
        return {}
    cur = db.cursor()
    try:
        cur.execute(f"""
            SELECT item_id, ref_lufs, start_ramp, end_ramp, duration_ms, beats FROM {SIANSA_PROFILES}
             WHERE item_id = ANY(%s) AND status='ready' AND analyzer_version=%s""",
            (list(item_ids), SIANSA_ANALYZER_VERSION))
        out = {}
        for item_id, ref_lufs, start, end, duration, beats in cur.fetchall():
            profile = {"ref_lufs": ref_lufs, "start_ramp": start or "", "end_ramp": end or "", "duration_ms": duration or 0}
            grids = beats if isinstance(beats, dict) else (json.loads(beats) if beats else {})
            profile.update(grids or {})
            out[item_id] = profile
        return out
    finally:
        cur.close()


def is_beat_field(key):
    """A field of the beat grid (the Siansa plugin's or Siansa Beats'), not of loudness or ramps."""
    return key.startswith("intro_") or key.startswith("outro_") or key.startswith("beats_until")


def merge(siansa, grid, missing="plugin"):
    """One song's profile: the Siansa plugin's loudness and ramps (``siansa``, or None) with Siansa Beats' ``grid``
    (or None). Without a grid, the plugin's own beat grid stays (``missing`` "plugin") or goes ("none"). None when
    there's neither."""
    if siansa is None and grid is None:
        return None
    drop_plugin_beats = grid is not None or missing == "none"
    out = {k: v for k, v in (siansa or {}).items() if not (drop_plugin_beats and is_beat_field(k))}
    if siansa is None:
        out.update({"ref_lufs": None, "start_ramp": "", "end_ramp": "", "duration_ms": 0})
    if grid is not None:
        out.update({k: v for k, v in grid.items() if is_beat_field(k) and v is not None})
        out["beat_grid"] = "siansa_beats"
    elif siansa is not None and missing == "plugin" and any(is_beat_field(k) for k in siansa):
        out["beat_grid"] = "siansa_analysis"
    return out


# ---- the music server's ids, as the Siansa plugin reads them -----------------------------------------------------

def _server():
    asked = (request.args.get("server") or request.args.get("server_id") or "").strip()
    if not asked:
        return None
    try:
        from tasks.mediaserver import registry

        server = registry.get_server(asked) or registry.get_server_by_name(asked)
    except Exception as e:  # an AudioMuse without the multi-server registry
        logger.warning("siansa_beats: couldn't look up server %r: %s", asked, e)
        return None
    if not server:
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
            except TypeError:
                pass
        return canonical_input_ids(ids)
    except Exception:
        return {i: i for i in ids}


def _error(message, code):
    return jsonify({"error": message}), code


# ---- API ---------------------------------------------------------------------------------------------------------

@bp.route("/api/health")
def health():
    answer = {"plugin": PLUGIN_ID, "plugin_version": VERSION, "schema_version": SCHEMA_VERSION, "status": "ok"}
    try:
        answer["siansa_plugin"] = siansa_plugin_installed(get_db())
    except Exception:
        logger.exception("siansa_beats couldn't look for the Siansa plugin's table")
        answer["siansa_plugin"] = False
    try:
        beats = _beats_get("/api/health")
        answer["beats"] = {"reachable": True, "version": beats.get("version")}
    except BeatsError as e:
        answer["beats"] = {"reachable": False, "problem": str(e)}
    return jsonify(answer)


@bp.route("/api/profiles")
def profiles():
    asked = [i for i in dict.fromkeys(p.strip() for p in request.args.get("ids", "").split(",")) if i]
    if len(asked) > MAX_IDS:
        return _error(f"At most {MAX_IDS} ids at a time", 400)
    canonical = _canonical(asked)
    try:
        measured = siansa_profiles(get_db(), set(canonical.values()))
    except Exception:
        logger.exception("siansa_beats couldn't read the Siansa plugin's profiles")
        measured = {}
    grids = CACHE.grids(asked, _beats_get) if beats_url() else {}
    mode = missing_mode()
    out, pending = [], []
    for asked_id in asked:
        profile = merge(measured.get(canonical.get(asked_id, asked_id)), grids.get(asked_id), mode)
        if profile is None:
            pending.append(asked_id)
        else:
            out.append({"track_id": asked_id, **profile})
    return jsonify({"schema_version": SCHEMA_VERSION, "profiles": out, "pending": pending})


# ---- the page ----------------------------------------------------------------------------------------------------

def render_settings(url, has_password, mode, message="", beats=None, siansa_plugin=None):
    note = f'<p class="mx-help"><strong>{escape(message)}</strong></p>' if message else ""
    if beats is None:
        reach = ""
    elif beats.get("reachable"):
        reach = f'<p>✓ Siansa Beats {escape(str(beats.get("version") or ""))} answers.</p>'
    else:
        problem = beats.get("problem") or "Siansa Beats doesn't answer"
        reach = f"<p>✗ {escape(problem)}</p>"
    if siansa_plugin is False:
        reach += "<p>✗ The Siansa plugin isn't installed: its loudness and fades are what this plugin adds the beats to.</p>"
    options = "".join(
        f'<label><input type="radio" name="missing" value="{key}"{" checked" if key == mode else ""}> {escape(text)}</label><br>'
        for key, text in MISSING_CHOICES.items())
    return f"""
<h2>Siansa Beats (dev)</h2>
<p>For Siansa Dev: each song's loudness and fades as the Siansa plugin measured them, with its tempo, beats, bars and
phrases from <strong>Siansa Beats</strong>. This plugin measures nothing itself; the Siansa plugin goes on as before,
and the Siansa app keeps using it.</p>
{note}{reach}
<form method="post">
  <p><label>Siansa Beats' address<br>
    <input type="url" name="beats_url" value="{escape(url)}" placeholder="http://192.168.1.10:8090" size="40"></label></p>
  <p><label>Its password (only if BEATS_PASSWORD is set)<br>
    <input type="password" name="beats_password" value="" placeholder="{"kept: leave empty to keep it" if has_password else "none"}" size="40"></label>
    {'<br><label><input type="checkbox" name="forget_password" value="1"> Remove the password</label>' if has_password else ""}</p>
  <p>Songs Siansa Beats hasn't analysed yet:<br>{options}</p>
  <p><button type="submit" name="action" value="save">Save and test</button></p>
</form>
"""


@bp.route("/settings", methods=["GET", "POST"])
def settings():
    if request.method == "POST":
        url = (request.form.get("beats_url") or "").strip().rstrip("/")
        set_setting("beats_url", url)
        if request.form.get("forget_password"):
            set_setting("beats_password", "")
        elif request.form.get("beats_password"):
            set_setting("beats_password", request.form.get("beats_password"))
        mode = request.form.get("missing")
        if mode in MISSING_CHOICES:
            set_setting("missing", mode)
        CACHE.clear()
        logger.info("siansa_beats settings saved: Siansa Beats at %s", url or "(none)")
        return redirect(url_for(f"{PLUGIN_ID}.settings", saved=1))
    beats = None
    if beats_url():
        try:
            answer = _beats_get("/api/health")
            beats = {"reachable": True, "version": answer.get("version")}
        except BeatsError as e:
            beats = {"reachable": False, "problem": str(e)}
    try:
        siansa_plugin = siansa_plugin_installed(get_db())
    except Exception:
        siansa_plugin = None
    body = render_settings(beats_url(), bool(get_setting("beats_password", "")), missing_mode(),
                           "Saved." if request.args.get("saved") else "", beats, siansa_plugin)
    return render_page(body, title="Siansa Beats (dev)")
