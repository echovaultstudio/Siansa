"""Siansa Beats (Beat This!), where each song's tempo, beats, bars and phrases come from (Siansa Dev).

This plugin measures each song's loudness and how it starts and ends, and builds the similar lists, as the Siansa
plugin does (it began as a copy of it). Its beat grids come from Siansa Beats, a service of its own that runs
Beat This!: the plugin asks it for them (``GET /api/grids``) and keeps them a while. A song Siansa Beats hasn't
analysed yet gets this plugin's own beat reading, or none (the page's choice).
"""

import base64
import json
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

from plugin.api import get_setting, logger

MAX_IDS = 500
GRIDS_PATH = "/api/grids"
TIMEOUT = 5.0
URL_BUDGET = 3500  # a request line stays well within what any web server takes
FOUND_SECONDS = 600  # a grid is asked for again after this, in case Siansa Beats analysed it again
MISSING_SECONDS = 120  # a song Siansa Beats hasn't done, sooner: it may be analysing it now

MISSING_CHOICES = {
    "plugin": "Use this plugin's own beat reading (less exact)",
    "none": "No beat grid (a plain smart fade)",
}

# ---- settings ----------------------------------------------------------------------------------------------------

def beats_url():
    return (get_setting("beats_url", "") or "").strip().rstrip("/")


def missing_mode():
    value = get_setting("missing", "plugin") or "plugin"
    return value if value in MISSING_CHOICES else "plugin"


# ---- Siansa Beats ------------------------------------------------------------------------------------------------

class BeatsError(Exception):
    pass


def _beats_get(path, body=None):
    """The JSON answer of Siansa Beats at ``path`` (a POST of ``body`` when given), or :class:`BeatsError` saying
    what to check."""
    base = beats_url()
    if not base:
        raise BeatsError("Siansa Beats' address isn't set: enter it on this plugin's page")
    parts = urllib.parse.urlsplit(base)
    if parts.scheme not in ("http", "https") or not parts.hostname:
        raise BeatsError("Siansa Beats' address needs to start with http:// or https://")
    req = urllib.request.Request(base + path, headers={"Accept": "application/json"},
                                 data=None if body is None else json.dumps(body).encode())
    if body is not None:
        req.add_header("Content-Type", "application/json")
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


def song_keys(db, item_ids):
    """AudioMuse-AI's own key and scale of each song (its ``score`` table), ``{item_id: {"key", "scale"}}``: for the
    app to tell which blends are in key. Read only."""
    if not item_ids:
        return {}
    cur = db.cursor()
    try:
        cur.execute("SELECT item_id, key, scale FROM score WHERE item_id = ANY(%s)", (list(item_ids),))
        return {item_id: {"key": key, "scale": scale} for item_id, key, scale in cur.fetchall() if key}
    finally:
        cur.close()


def is_beat_field(key):
    """A field of the beat grid (the Siansa plugin's or Siansa Beats'), not of loudness or ramps."""
    return key.startswith("intro_") or key.startswith("outro_") or key.startswith("beats_until") or key in GRID_ONLY


GRID_ONLY = ("mix_track", "drops_ms", "breakdowns_ms")  # Siansa Beats' fields that aren't one end's


def merge(siansa, grid, missing="plugin"):
    """One song's profile: this plugin's loudness and ramps (``siansa``, or None) with Siansa Beats' ``grid`` (or
    None). Without a grid, the plugin's own beat reading stays (``missing`` "plugin") or goes ("none"). None when
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
        out["beat_grid"] = "plugin"
    return out




def health():
    """Whether Siansa Beats answers: ``{"reachable", "version"}`` or ``{"reachable": False, "problem"}``; None when its
    address isn't set."""
    if not beats_url():
        return None
    try:
        return {"reachable": True, "version": _beats_get("/api/health").get("version")}
    except BeatsError as e:
        return {"reachable": False, "problem": str(e)}
