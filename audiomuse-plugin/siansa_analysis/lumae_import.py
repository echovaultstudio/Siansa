"""One-time import of the volume and fade profiles the Lumae plugin already measured.

TEMPORARY: remove this module, its section on the settings page and its route in plugin 0.1.3 (issue #225).
By then everyone moving from Lumae has imported, and Lumae's tables may be gone.

Both plugins keep their data in AudioMuse-AI's database, so the import is one SQL statement:

* Lumae files a profile per music source under the song's id on that server (``source_profiles``); AudioMuse-AI's
  ``track_server_map`` turns that into its own id (``score.item_id``), and on the default server the ids are the
  same anyway.
* Loudness is BS.1770 in both. A ramp is the same 3-byte layout in both (int8 dB, little-endian uint16 chunks);
  Lumae records other levels, which Siansa reads just as well (it looks up −18 dB, which both have).
* Only songs this plugin hasn't measured itself are filled in. Imported rows say ``source='lumae'``, so
  "Measure all songs" can measure them again here.
"""

from plugin.api import logger

from . import store

LUMAE_PROFILES = "plugin_lumae_analysis__source_profiles"
LUMAE_SOURCES = "plugin_lumae_analysis__catalog_sources"
LUMAE_READY_ANALYZER = 1
LUMAE_READY_SCHEMA = 1


def available(db):
    """Lumae's profiles are still in the database, with how many are ready; 0 when its tables are gone."""
    cur = db.cursor()
    try:
        cur.execute("SELECT to_regclass(%s), to_regclass(%s)", (LUMAE_PROFILES, LUMAE_SOURCES))
        profiles, sources = cur.fetchone()
        if profiles is None or sources is None:
            return 0
        cur.execute(f"SELECT count(*) FROM {LUMAE_PROFILES} WHERE status='ready'")
        return int(cur.fetchone()[0] or 0)
    except Exception:
        db.rollback()
        logger.warning("siansa_analysis couldn't look for Lumae's profiles", exc_info=True)
        return 0
    finally:
        cur.close()


def run(db):
    """Copies Lumae's ready profiles of songs not measured here; returns how many songs were filled in."""
    table = store.table("profiles")
    cur = db.cursor()
    try:
        cur.execute(f"""
            WITH lumae AS (
                SELECT DISTINCT ON (canon.item_id)
                       canon.item_id, p.ref_lufs, p.start_ramp, p.end_ramp, p.duration_ms
                  FROM {LUMAE_PROFILES} p
                  JOIN {LUMAE_SOURCES} src USING (catalog_instance_id)
                  CROSS JOIN LATERAL (
                      SELECT COALESCE(
                          (SELECT m.item_id FROM track_server_map m
                            WHERE m.server_id = src.current_core_server_id AND m.provider_track_id = p.track_id
                            LIMIT 1),
                          (SELECT s.item_id FROM score s WHERE s.item_id = p.track_id)) AS item_id
                  ) canon
                 WHERE p.status = 'ready' AND p.analyzer_ver = %s AND p.profile_schema_ver = %s
                   AND canon.item_id IS NOT NULL
                 ORDER BY canon.item_id, p.analyzed_at DESC
            )
            INSERT INTO {table}
                (item_id, status, ref_lufs, start_ramp, end_ramp, duration_ms, analyzer_version, attempts,
                 retry_after, error, source, updated_at)
            SELECT item_id, 'ready', ref_lufs,
                   replace(encode(start_ramp, 'base64'), E'\\n', ''),
                   replace(encode(end_ramp, 'base64'), E'\\n', ''),
                   duration_ms, %s, 0, NULL, NULL, 'lumae', now()
              FROM lumae
            ON CONFLICT (item_id) DO UPDATE SET
                status='ready', ref_lufs=EXCLUDED.ref_lufs, start_ramp=EXCLUDED.start_ramp,
                end_ramp=EXCLUDED.end_ramp, duration_ms=EXCLUDED.duration_ms,
                analyzer_version=EXCLUDED.analyzer_version, attempts=0, retry_after=NULL, error=NULL,
                source='lumae', updated_at=now()
              WHERE {table}.status <> 'ready' OR {table}.source = 'lumae'""",
            (LUMAE_READY_ANALYZER, LUMAE_READY_SCHEMA, store.ANALYZER_VERSION))
        count = cur.rowcount
        cur.execute(f"DELETE FROM {store.table('wanted')} w USING {table} p "
                    "WHERE p.item_id = w.item_id AND p.status = 'ready'")
        db.commit()
        logger.info("siansa_analysis imported %s profiles from Lumae", count)
        return count
    except Exception:
        db.rollback()
        raise
    finally:
        cur.close()
