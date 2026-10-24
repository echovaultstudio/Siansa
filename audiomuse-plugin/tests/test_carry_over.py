import unittest

import support  # noqa: F401  (import path and the plugin.api stand-in)
from siansa_analysis import store


class FakeCursor:
    """Answers the queries carry_over makes, from a dict of tables (name -> (columns, row count))."""

    def __init__(self, tables, log):
        self.tables, self.log, self.answer, self.rowcount = tables, log, None, 0

    def execute(self, sql, args=()):
        self.log.append((" ".join(sql.split()), args))
        if sql.startswith("SELECT to_regclass"):
            self.answer = [(args[0] if args[0] in self.tables else None,)]
        elif sql.startswith("SELECT EXISTS"):
            name = sql.split("FROM ")[1].split(")")[0].strip()
            self.answer = [(self.tables[name][1] > 0,)]
        elif "information_schema.columns" in sql:
            self.answer = [(c,) for c in self.tables[args[0]][0]]
        elif sql.startswith("INSERT INTO"):
            new, old = sql.split()[2], sql.split("FROM ")[1].split()[0]
            self.rowcount = self.tables[old][1]
            self.tables[new] = (self.tables[new][0], self.rowcount)

    def fetchone(self):
        return self.answer[0]

    def fetchall(self):
        return self.answer

    def close(self):
        pass


class FakeDb:
    def __init__(self, tables):
        self.tables, self.log = tables, []

    def cursor(self):
        return FakeCursor(self.tables, self.log)


def new(name):
    return f"plugin_siansa_analysis__{name}"


def old(name):
    return f"plugin_musix_analysis__{name}"


class CarryOverTest(unittest.TestCase):
    def tables(self, with_legacy=True, filled_new=()):
        t = {}
        for name in store.CARRIED:
            cols = ["item_id", "status", "source"] if name == "profiles" else ["name", "value"]
            t[new(name)] = (cols, 5 if name in filled_new else 0)
            if with_legacy:
                t[old(name)] = (cols[:2] if name == "profiles" else cols, 3)
        return t

    def test_what_the_earlier_plugin_gathered_is_copied(self):
        db = FakeDb(self.tables())
        copied = store.carry_over(db)
        self.assertEqual(set(store.CARRIED), set(copied))
        inserts = [sql for sql, _ in db.log if sql.startswith("INSERT")]
        # Columns both tables have; one added later keeps its default.
        self.assertIn(f"INSERT INTO {new('profiles')} (item_id, status) SELECT item_id, status FROM {old('profiles')}", inserts[0])
        self.assertIn(("DELETE FROM cron WHERE task_type=%s", (store.LEGACY_TICK,)), db.log)

    def test_nothing_without_the_earlier_plugin(self):
        db = FakeDb(self.tables(with_legacy=False))
        self.assertEqual({}, store.carry_over(db))
        self.assertFalse(any(sql.startswith(("INSERT", "DELETE")) for sql, _ in db.log))

    def test_a_table_already_in_use_is_left_alone(self):
        db = FakeDb(self.tables(filled_new=("profiles",)))
        copied = store.carry_over(db)
        self.assertNotIn("profiles", copied)
        self.assertIn("state", copied)


if __name__ == "__main__":
    unittest.main()
