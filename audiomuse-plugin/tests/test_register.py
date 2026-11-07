import unittest

import support  # noqa: F401  (import path and the plugin.api stand-in)
import siansa_analysis
from siansa_analysis import jobs


class Ctx:
    """What AudioMuse-AI's plugin context records, as far as register() uses it."""

    def __init__(self, descriptions=True):
        self.descriptions = descriptions
        self.tasks, self.cron_tasks = {}, {}

    def add_blueprint(self, bp): pass
    def on_install(self, f): pass
    def on_song_analyzed(self, f): pass
    def add_task(self, name, func, queue="default"): self.tasks[name] = func

    def add_cron_task(self, name, func, queue="default", **kwargs):
        if kwargs and not self.descriptions:
            raise TypeError("unexpected keyword argument 'description'")
        self.cron_tasks[name] = (func, kwargs.get("description"))


class RegisterTest(unittest.TestCase):
    def test_onlyTheTickIsAScheduledTaskWithADescription(self):
        ctx = Ctx()
        siansa_analysis.register(ctx)
        self.assertEqual({}, ctx.tasks)
        self.assertEqual(["tick"], list(ctx.cron_tasks))
        func, description = ctx.cron_tasks["tick"]
        self.assertIs(jobs.tick, func)
        self.assertTrue(description and len(description) <= 300, description)

    def test_anAudioMuseWithoutDescriptionsStillGetsTheTick(self):
        ctx = Ctx(descriptions=False)
        siansa_analysis.register(ctx)
        self.assertEqual((jobs.tick, None), ctx.cron_tasks["tick"])


if __name__ == "__main__":
    unittest.main()
