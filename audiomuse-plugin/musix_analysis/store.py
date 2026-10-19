"""The plugin's own tables, and the queries on AudioMuse-AI's tables it reads.

Tables (each named ``plugin_musix_analysis__<name>`` by AudioMuse-AI):

* ``profiles``: one row per song, its loudness and ramps, or why it couldn't be measured;
* ``wanted``: songs the app asked for that have no profile yet, measured first;
* ``state``: small JSON values: the input generations, each job's state and the last finished build;
* ``similar_albums`` / ``similar_artists``: the published similar lists.

Songs are keyed by AudioMuse-AI's canonical id (``score.item_id``).
"""

import json

from plugin.api import get_db, table

ANALYZER_VERSION = 1
RETRY_HOURS = (1, 6, 24, 72)  # wait after the 1st, 2nd, 3rd, later failure


def migrate(db):
    cur = db.cursor()
    cur.execute(f"""
        CREATE TABLE IF NOT EXISTS {table('profiles')} (
            item_id TEXT PRIMARY KEY,
            status TEXT NOT NULL,
            ref_lufs DOUBLE PRECISION,
            start_ramp TEXT,
            end_ramp TEXT,
            duration_ms INTEGER,
            analyzer_version INTEGER NOT NULL DEFAULT 0,
            attempts INTEGER NOT NULL DEFAULT 0,
            retry_after TIMESTAMPTZ,
            error TEXT,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )""")
    # Where a profile came from: 'measured' here, or 'lumae' when imported (lumae_import.py, until 0.1.3).
    cur.execute(f"ALTER TABLE {table('profiles')} ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'measured'")
    cur.execute(f"CREATE INDEX IF NOT EXISTS {table('profiles_status')} ON {table('profiles')} (status, analyzer_version)")
    cur.execute(f"""
        CREATE TABLE IF NOT EXISTS {table('wanted')} (
            item_id TEXT PRIMARY KEY,
            asked_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )""")
    cur.execute(f"""
        CREATE TABLE IF NOT EXISTS {table('state')} (
            name TEXT PRIMARY KEY,
            value JSONB NOT NULL,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )""")
    for kind, key in (("albums", "album_key"), ("artists", "artist_key")):
        cur.execute(f"""
            CREATE TABLE IF NOT EXISTS {table('similar_' + kind)} (
                {key} TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                artist TEXT NOT NULL,
                candidates JSONB NOT NULL,
                generation INTEGER NOT NULL
            )""")
    # The background tick, every 15 minutes. Enabled, so the parts keep themselves current; the admin can
    # change or switch it off under Administration > Scheduled Tasks. Never overwrites the admin's choice.
    cur.execute(
        "INSERT INTO cron (name, task_type, cron_expr, enabled) VALUES (%s, %s, %s, TRUE) "
        "ON CONFLICT (task_type) DO NOTHING",
        ("plugin.musix_analysis.tick", "plugin.musix_analysis.tick", "*/15 * * * *"),
    )
    db.commit()
    cur.close()


# ---- state -------------------------------------------------------------------------------------------------

def get_state(name, default=None, db=None):
    db = db or get_db()
    cur = db.cursor()
    cur.execute(f"SELECT value FROM {table('state')} WHERE name=%s", (name,))
    row = cur.fetchone()
    cur.close()
    if not row:
        return default
    return row[0] if not isinstance(row[0], str) else json.loads(row[0])


def set_state(name, value, db=None, commit=True):
    db = db or get_db()
    cur = db.cursor()
    cur.execute(
        f"INSERT INTO {table('state')} (name, value, updated_at) VALUES (%s, %s::jsonb, now()) "
        "ON CONFLICT (name) DO UPDATE SET value=EXCLUDED.value, updated_at=now()",
        (name, json.dumps(value)),
    )
    cur.close()
    if commit:
        db.commit()


def try_lock(db, name):
    """A session lock so only one worker runs a job at a time; ``False`` when another one holds it."""
    cur = db.cursor()
    cur.execute("SELECT pg_try_advisory_lock(hashtext('musix_analysis'), hashtext(%s))", (name,))
    got = bool(cur.fetchone()[0])
    cur.close()
    db.commit()
    return got


def unlock(db, name):
    db.rollback()
    cur = db.cursor()
    cur.execute("SELECT pg_advisory_unlock(hashtext('musix_analysis'), hashtext(%s))", (name,))
    cur.close()
    db.commit()


# ---- inputs (AudioMuse-AI's own tables, read only) -----------------------------------------------------------

def library_digest(db):
    """Digest and count of what the similar lists read from the library: each song's album and artists."""
    cur = db.cursor()
    cur.execute("""
        SELECT md5(COALESCE(string_agg(
                   md5(concat_ws(chr(31), item_id, album, album_artist, author)), '' ORDER BY item_id), '')),
               count(*)
          FROM score""")
    digest, count = cur.fetchone()
    cur.close()
    return str(digest), int(count)


def analysis_digest(db):
    """Digest and count of the sound analysis the similar lists read: each song's embedding."""
    cur = db.cursor()
    cur.execute("""
        SELECT md5(COALESCE(string_agg(item_id || ':' || md5(embedding), '' ORDER BY item_id), '')),
               count(*)
          FROM embedding WHERE embedding IS NOT NULL""")
    digest, count = cur.fetchone()
    cur.close()
    return str(digest), int(count)


def similarity_tracks(db):
    """``(item_id, album, album_artist, author, embedding bytes)`` of every analysed song, streamed."""
    cur = db.cursor(name="musix_similarity_inputs")  # server-side cursor: rows arrive in batches
    cur.itersize = 2000
    cur.execute("""
        SELECT s.item_id, s.album, s.album_artist, s.author, e.embedding
          FROM score s JOIN embedding e USING (item_id)
         WHERE e.embedding IS NOT NULL""")
    try:
        for row in cur:
            yield row
    finally:
        cur.close()


def song_info(db, item_ids):
    """``{item_id: (title, album, album_artist, author)}`` for the given canonical ids."""
    if not item_ids:
        return {}
    cur = db.cursor()
    cur.execute("SELECT item_id, title, album, album_artist, author FROM score WHERE item_id = ANY(%s)",
                (list(item_ids),))
    out = {row[0]: row[1:] for row in cur.fetchall()}
    cur.close()
    return out


# ---- profiles ------------------------------------------------------------------------------------------------

def save_profile(db, item_id, profile):
    """Stores a measured song (``profile`` from :func:`jobs.profile_of`)."""
    cur = db.cursor()
    cur.execute(f"""
        INSERT INTO {table('profiles')}
            (item_id, status, ref_lufs, start_ramp, end_ramp, duration_ms, analyzer_version, attempts,
             retry_after, error, updated_at)
        VALUES (%s, 'ready', %s, %s, %s, %s, %s, 0, NULL, NULL, now())
        ON CONFLICT (item_id) DO UPDATE SET
            status='ready', ref_lufs=EXCLUDED.ref_lufs, start_ramp=EXCLUDED.start_ramp,
            end_ramp=EXCLUDED.end_ramp, duration_ms=EXCLUDED.duration_ms,
            analyzer_version=EXCLUDED.analyzer_version, attempts=0, retry_after=NULL, error=NULL,
            source='measured', updated_at=now()""",
        (item_id, profile["ref_lufs"], profile["start_ramp"], profile["end_ramp"], profile["duration_ms"],
         ANALYZER_VERSION))
    cur.execute(f"DELETE FROM {table('wanted')} WHERE item_id=%s", (item_id,))
    cur.close()
    db.commit()


def save_failure(db, item_id, error):
    """Notes that a song couldn't be measured; it is tried again later, waiting longer each time."""
    cur = db.cursor()
    cur.execute(f"SELECT attempts FROM {table('profiles')} WHERE item_id=%s", (item_id,))
    row = cur.fetchone()
    attempts = (row[0] if row else 0) + 1
    hours = RETRY_HOURS[min(attempts, len(RETRY_HOURS)) - 1]
    cur.execute(f"""
        INSERT INTO {table('profiles')} (item_id, status, attempts, retry_after, error, updated_at)
        VALUES (%s, 'failed', %s, now() + make_interval(hours => %s), %s, now())
        ON CONFLICT (item_id) DO UPDATE SET
            status='failed', attempts=EXCLUDED.attempts, retry_after=EXCLUDED.retry_after,
            error=EXCLUDED.error, updated_at=now()
          WHERE {table('profiles')}.status <> 'ready'""",
        (item_id, attempts, hours, str(error)[:500]))
    cur.close()
    db.commit()


def profiles_for(db, item_ids):
    """Ready profiles of the given canonical ids, ``{item_id: row dict}``."""
    if not item_ids:
        return {}
    cur = db.cursor()
    cur.execute(f"""
        SELECT item_id, ref_lufs, start_ramp, end_ramp, duration_ms FROM {table('profiles')}
         WHERE item_id = ANY(%s) AND status='ready' AND analyzer_version=%s""",
        (list(item_ids), ANALYZER_VERSION))
    out = {
        row[0]: {"ref_lufs": row[1], "start_ramp": row[2] or "", "end_ramp": row[3] or "", "duration_ms": row[4] or 0}
        for row in cur.fetchall()
    }
    cur.close()
    return out


def mark_wanted(db, item_ids):
    """Remembers songs the app asked for that have no profile yet, so the next run measures them first."""
    if not item_ids:
        return
    cur = db.cursor()
    cur.execute(f"""
        INSERT INTO {table('wanted')} (item_id)
        SELECT item_id FROM score WHERE item_id = ANY(%s)
        ON CONFLICT (item_id) DO UPDATE SET asked_at=now()""", (list(item_ids),))
    cur.close()
    db.commit()


def profile_counts(db):
    """``(songs, ready, failed, wanted, imported)`` across the library; ``imported`` are ready ones from Lumae."""
    cur = db.cursor()
    cur.execute(f"""
        SELECT (SELECT count(*) FROM score),
               count(*) FILTER (WHERE p.status='ready' AND p.analyzer_version=%s),
               count(*) FILTER (WHERE p.status='failed'),
               (SELECT count(*) FROM {table('wanted')}),
               count(*) FILTER (WHERE p.status='ready' AND p.analyzer_version=%s AND p.source='lumae')
          FROM {table('profiles')} p JOIN score s USING (item_id)""", (ANALYZER_VERSION, ANALYZER_VERSION))
    songs, ready, failed, wanted, imported = cur.fetchone()
    cur.close()
    return int(songs or 0), int(ready or 0), int(failed or 0), int(wanted or 0), int(imported or 0)


def songs_to_measure(db, limit, include_imported=False):
    """Up to ``limit`` songs without a current profile: asked-for songs first, then the newest.

    ``include_imported`` also returns songs whose profile was imported from Lumae ("Measure all songs").
    """
    cur = db.cursor()
    cur.execute(f"""
        SELECT s.item_id, s.title
          FROM score s
          LEFT JOIN {table('profiles')} p USING (item_id)
          LEFT JOIN {table('wanted')} w USING (item_id)
         WHERE (p.item_id IS NULL
                OR (p.status='ready' AND p.analyzer_version <> %s)
                OR (p.status='failed' AND (p.retry_after IS NULL OR p.retry_after <= now()))
                OR (%s AND p.status='ready' AND p.source='lumae'))
         ORDER BY w.asked_at DESC NULLS LAST, (p.source='lumae') NULLS FIRST, s.created_at DESC
         LIMIT %s""", (ANALYZER_VERSION, bool(include_imported), int(limit)))
    rows = cur.fetchall()
    cur.close()
    return rows


def recent_failures(db, limit=5):
    cur = db.cursor()
    cur.execute(f"""
        SELECT p.item_id, s.title, s.author, p.error, p.attempts
          FROM {table('profiles')} p LEFT JOIN score s USING (item_id)
         WHERE p.status='failed' ORDER BY p.updated_at DESC LIMIT %s""", (limit,))
    rows = cur.fetchall()
    cur.close()
    return rows


# ---- similar lists -------------------------------------------------------------------------------------------

def publish_similar(db, albums, album_names, artists, artist_names, generation):
    """Swaps in a finished build in one transaction: readers see the old lists until the commit."""
    from psycopg2.extras import execute_values

    cur = db.cursor()
    cur.execute(f"DELETE FROM {table('similar_albums')}")
    execute_values(
        cur,
        f"INSERT INTO {table('similar_albums')} (album_key, name, artist, candidates, generation) VALUES %s",
        [(key, album_names[key][0], album_names[key][1], json.dumps(found), generation) for key, found in albums.items()],
        template="(%s, %s, %s, %s::jsonb, %s)", page_size=1000)
    cur.execute(f"DELETE FROM {table('similar_artists')}")
    execute_values(
        cur,
        f"INSERT INTO {table('similar_artists')} (artist_key, name, artist, candidates, generation) VALUES %s",
        [(key, artist_names[key], artist_names[key], json.dumps(found), generation) for key, found in artists.items()],
        template="(%s, %s, %s, %s::jsonb, %s)", page_size=1000)
    cur.close()


def similar_row(db, kind, key):
    cur = db.cursor()
    column = "album_key" if kind == "albums" else "artist_key"
    cur.execute(f"SELECT name, artist, candidates FROM {table('similar_' + kind)} WHERE {column}=%s", (key,))
    row = cur.fetchone()
    cur.close()
    if not row:
        return None
    candidates = row[2] if not isinstance(row[2], str) else json.loads(row[2])
    return {"name": row[0], "artist": row[1], "candidates": candidates}
