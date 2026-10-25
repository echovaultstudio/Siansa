"""The plugin follows AudioMuse's ``server`` parameter (docs/MULTI_SERVER.md), as the app sends it (beta report 810dc85c)."""

import sys
import types
import unittest

import support  # noqa: F401  (the stand-in plugin.api)
from flask import Flask

from siansa_analysis import web


class FakeRegistry(types.ModuleType):
    def __init__(self):
        super().__init__("tasks.mediaserver.registry")
        self.calls = []

    def resolve_server_id(self, name):
        return {"Emby": "srv-2"}.get(name)

    def canonical_input_ids(self, ids, server=None):
        self.calls.append(("canonical", server))
        return {i: "fp_" + i for i in ids}

    def translate_ids(self, ids, server=None):
        self.calls.append(("translate", server))
        return {i: (server or "default") + ":" + i for i in ids}


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

    def test_the_server_name_is_followed_both_ways(self):
        with self.app.test_request_context("/x?server=Emby"):
            self.assertEqual(web._server(), "srv-2")
            self.assertEqual(web._canonical(["886400"]), {"886400": "fp_886400"})
            self.assertEqual(web._server_ids(["fp_1"]), {"fp_1": "srv-2:fp_1"})
        self.assertEqual(self.registry.calls, [("canonical", "srv-2"), ("translate", "srv-2")])

    def test_without_a_server_the_default_one(self):
        with self.app.test_request_context("/x"):
            self.assertIsNone(web._server())
            self.assertEqual(web._server_ids(["fp_1"]), {"fp_1": "default:fp_1"})

    def test_the_older_server_id_still_works(self):
        with self.app.test_request_context("/x?server_id=srv-9"):
            self.assertEqual(web._server(), "srv-9")


if __name__ == "__main__":
    unittest.main()
