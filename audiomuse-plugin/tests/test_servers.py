"""Several music servers in AudioMuse-AI (Siansa issue #659): a song is fetched from whichever server has it, and
the 15-minute task does its work once, not once per server."""
import contextlib
import sys
import types
import unittest

import support  # noqa: F401
from siansa_analysis import jobs


class FakeServers:
    """Three servers; each maps some canonical ids to its own ids, and remembers which server downloads use."""

    def __init__(self, maps, active=None):
        self.maps = maps  # {server_id: {item_id: provider_id}}
        self.active = active
        self.bound = None
        self.downloads = []

    def install(self):
        mediaserver = types.ModuleType("tasks.mediaserver")
        registry = types.ModuleType("tasks.mediaserver.registry")
        registry.translate_ids = lambda ids, server_id=None: {
            i: self.maps.get(server_id or "nav", {}).get(i) for i in ids if self.maps.get(server_id or "nav", {}).get(i)}

        def download_track(folder, item):
            self.downloads.append((self.bound or self.active or "nav", item["id"]))
            return f"{folder}/{item['id']}.flac"

        mediaserver.download_track = download_track
        mediaserver.registry = registry
        tasks = types.ModuleType("tasks")
        tasks.mediaserver = mediaserver
        self.saved = {k: sys.modules.get(k) for k in ("tasks", "tasks.mediaserver", "tasks.mediaserver.registry")}
        sys.modules.update({"tasks": tasks, "tasks.mediaserver": mediaserver, "tasks.mediaserver.registry": registry})

        @contextlib.contextmanager
        def use_server(server_id):
            self.bound = server_id
            try:
                yield
            finally:
                self.bound = None

        self.saved_api = (jobs.active_server_id, jobs.list_servers, jobs.use_server)
        jobs.active_server_id = lambda: self.active
        jobs.list_servers = lambda: [{"server_id": s, "name": s} for s in self.maps]
        jobs.use_server = use_server

    def remove(self):
        for k, v in self.saved.items():
            if v is None:
                sys.modules.pop(k, None)
            else:
                sys.modules[k] = v
        jobs.active_server_id, jobs.list_servers, jobs.use_server = self.saved_api


class DownloadTest(unittest.TestCase):
    def setUp(self):
        self.servers = FakeServers({
            "nav": {"a": "nav-a"},
            "emby": {"b": "emby-b", "a": "emby-a"},
            "jelly": {"c": "jelly-c"},
        }, active="nav")
        self.servers.install()

    def tearDown(self):
        self.servers.remove()

    def test_the_runs_own_server_first(self):
        jobs._download("a", "Song A", "/tmp")
        self.assertEqual(self.servers.downloads, [("nav", "nav-a")])

    def test_a_song_only_on_another_server_comes_from_that_server(self):
        jobs._download("b", "Song B", "/tmp")
        jobs._download("c", "Song C", "/tmp")
        self.assertEqual(self.servers.downloads, [("emby", "emby-b"), ("jelly", "jelly-c")])
        self.assertIsNone(self.servers.bound)  # the run's own server is back afterwards

    def test_a_song_on_no_server(self):
        with self.assertRaises(jobs.NotOnServer):
            jobs._download("z", "Gone", "/tmp")


class OncePerTaskTest(unittest.TestCase):
    def test_the_tick_works_once_per_task(self):
        state = {}
        saved = (jobs.store.get_state, jobs.store.set_state, jobs.get_db)
        jobs.store.get_state = lambda name, default=None, db=None: state.get(name, default)
        jobs.store.set_state = lambda name, value, db=None, commit=True: state.__setitem__(name, value)
        jobs.get_db = lambda: None
        queue = types.ModuleType("taskqueue")
        current = {"id": "task-1"}
        queue.current_task_id = lambda: current["id"]
        sys.modules["taskqueue"] = queue
        try:
            self.assertFalse(jobs._same_task_again())  # the first server's run works
            self.assertTrue(jobs._same_task_again())   # the second and third don't
            self.assertTrue(jobs._same_task_again())
            current["id"] = "task-2"
            self.assertFalse(jobs._same_task_again())  # the next tick works again
            current["id"] = None
            self.assertFalse(jobs._same_task_again())  # outside a task (a test, a hook): always
            self.assertEqual(jobs.tick.__name__, "tick")
        finally:
            sys.modules.pop("taskqueue", None)
            jobs.store.get_state, jobs.store.set_state, jobs.get_db = saved


if __name__ == "__main__":
    unittest.main()
