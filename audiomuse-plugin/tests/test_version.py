import json
import os
import unittest

import support  # noqa: F401  (import path and the plugin.api stand-in)
from siansa_analysis import web


class VersionTest(unittest.TestCase):
    def test_health_reports_the_newest_version_in_plugin_json(self):
        # The Siansa app shows the version the plugin reports; it said 0.3.3 long after 0.5 (Siansa issue #860).
        path = os.path.join(os.path.dirname(__file__), "..", "plugin.json")
        newest = json.load(open(path))["versions"][0]["version"]
        self.assertEqual(newest, web.VERSION)


if __name__ == "__main__":
    unittest.main()
