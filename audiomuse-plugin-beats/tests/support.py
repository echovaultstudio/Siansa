"""Lets the plugin be imported without AudioMuse-AI: a stand-in ``plugin.api`` and the import path."""

import logging
import os
import sys
import types

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

if "plugin.api" not in sys.modules:
    api = types.ModuleType("plugin.api")
    api.settings = {}
    api.db = None
    api.logger = logging.getLogger("siansa_beats.test")
    api.get_db = lambda: api.db
    api.table = lambda name: f"plugin_siansa_beats__{name}"
    api.get_setting = lambda key, default=None: api.settings.get(key, default)
    api.set_setting = lambda key, value: api.settings.__setitem__(key, value)
    api.render_page = lambda body, title=None: f"<title>{title}</title>{body}"
    package = types.ModuleType("plugin")
    package.api = api
    sys.modules["plugin"] = package
    sys.modules["plugin.api"] = api


class FakeDb:
    """The Siansa plugin's profiles table, as rows ``(item_id, ref_lufs, start_ramp, end_ramp, duration_ms, beats)``;
    ``installed`` False as if the Siansa plugin weren't there. Every statement is kept in ``seen``."""

    def __init__(self, rows=(), installed=True):
        self.rows, self.installed, self.seen = list(rows), installed, []

    def cursor(self):
        return FakeCursor(self)

    def rollback(self):
        pass


class FakeCursor:
    def __init__(self, db):
        self.db, self.result = db, []

    def execute(self, sql, params=()):
        self.db.seen.append(sql)
        if "to_regclass" in sql:
            self.result = [("plugin_siansa_analysis__profiles" if self.db.installed else None,)]
        elif "plugin_siansa_analysis__profiles" in sql:
            assert sql.lstrip().upper().startswith("SELECT"), "the Siansa plugin's table is only ever read"
            wanted = set(params[0])
            self.result = [r for r in self.db.rows if r[0] in wanted]
        else:
            raise AssertionError(f"unexpected SQL: {sql}")

    def fetchone(self):
        return self.result[0]

    def fetchall(self):
        return self.result

    def close(self):
        pass
