"""The plugin follows AudioMuse's ``server`` parameter (docs/MULTI_SERVER.md) the way AudioMuse's own endpoints do
(app_server_context.resolve_request_server_id), as the app sends it (beta reports 810dc85c, #13)."""

import sys
import types
import unittest

import support  # noqa: F401  (the stand-in plugin.api)
from flask import Flask

from siansa_analysis import web


class FakeRegistry(types.ModuleType):
    """AudioMuse's tasks.mediaserver.registry: servers by id or display name; ids mapped per server."""

    SERVERS = {"srv-1": {"server_id": "srv-1", "name": "Navidrome"}, "srv-2": {"server_id": "srv-2", "name": "Emby"}}

    def __init__(self):
        super().__init__("tasks.mediaserver.registry")
        self.calls = []

    def get_server(self, server_id, conn=None):
        return self.SERVERS.get(server_id)

    def get_server_by_name(self, name, conn=None):
        return next((s for s in self.SERVERS.values() if s["name"].lower() == name.lower()), None)

    def canonical_input_ids(self, item_ids, server_id=None, conn=None):
        self.calls.append(("canonical", server_id))
        known = {"srv-2": {"885028": "fp_a"}}.get(server_id, {})
        return {i: known.get(i, i) for i in item_ids}

    def translate_ids(self, item_ids, server_id=None, conn=None):
        self.calls.append(("translate", server_id))
        on_emby = {"fp_a": "885028", "fp_b": "885099"}
        if server_id == "srv-2":
            return {i: on_emby[i] for i in item_ids if i in on_emby}
        return {i: i for i in item_ids}


class ServerParamTest(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        self.registry = FakeRegistry()
        tasks = types.ModuleType("tasks")
        mediaserver = types.ModuleType("tasks.mediaserver")
        mediaserver.registry = self.registry
        tasks.mediaserver = mediaserver
        self.saved = {k: sys.modules.get(k) for k in ("tasks", "tasks.mediaserver", "tasks.mediaserver.registry")}
        sys.modules.update({"tasks": tasks, "tasks.mediaserver": mediaserver, "tasks.mediaserver.registry": self.registry})

    def tearDown(self):
        for k, v in self.saved.items():
            if v is None:
                sys.modules.pop(k, None)
            else:
                sys.modules[k] = v

    def test_the_display_name_becomes_the_servers_id(self):
        # Before: "Emby" went on as if it were an id, nothing matched, and the raw Emby id stayed (beta #13).
        with self.app.test_request_context("/x?server=Emby"):
            self.assertEqual(web._server(), "srv-2")
            self.assertEqual(web._canonical(["885028"]), {"885028": "fp_a"})
            self.assertEqual(web._server_ids(["fp_a", "fp_c"]), {"fp_a": "885028"})
        self.assertEqual(self.registry.calls, [("canonical", "srv-2"), ("translate", "srv-2")])

    def test_names_ignore_case_and_ids_work_too(self):
        with self.app.test_request_context("/x?server=emby"):
            self.assertEqual(web._server(), "srv-2")
        with self.app.test_request_context("/x?server_id=srv-1"):
            self.assertEqual(web._server(), "srv-1")

    def test_without_a_server_or_an_unknown_one_the_default(self):
        with self.app.test_request_context("/x"):
            self.assertIsNone(web._server())
            self.assertEqual(web._server_ids(["fp_1"]), {"fp_1": "fp_1"})
        with self.app.test_request_context("/x?server=Plex"):
            self.assertIsNone(web._server())


if __name__ == "__main__":
    unittest.main()
