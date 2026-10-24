"""Tests for beta_changelog.py (issue #435)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import beta_changelog as bc  # noqa: E402


class BetaChangelog(unittest.TestCase):
    def test_testers_get_every_version_without_the_developer_notes(self):
        text = bc.for_testers("# Changelog\n\nChanges add a note in `changelog.d/`.\n\n## 0.30.0beta — 2026-10-25\n\n- A\n\n## 0.29.1beta\n\n- B\n")
        self.assertTrue(text.startswith("# Siansa changelog"))
        self.assertNotIn("changelog.d", text)
        self.assertIn("## 0.30.0beta", text)
        self.assertIn("## 0.29.1beta", text)


if __name__ == "__main__":
    unittest.main()
