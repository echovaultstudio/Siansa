"""Tests for notify_fixed.py (issues #433, #606)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import notify_fixed as bn  # noqa: E402


class NotifyFixed(unittest.TestCase):
    def test_only_fixed_issues_of_this_repository_are_found(self):
        bodies = [
            "Fixes #8\nRefs #4",
            "closes echovaultstudio/Siansa#10 and Resolved #8",
            "Fixes echovaultstudio/siansa-beta-testers#9",
            None,
        ]
        self.assertEqual(bn.fixed_issues(bodies), [8, 10])

    def test_a_version_is_announced_once(self):
        tag = "v0.29.2beta"
        self.assertEqual(bn.plan({"state": "closed"}, [], tag), ("comment", False))
        self.assertEqual(bn.plan({"state": "open"}, [{"body": "thanks"}], tag), ("comment", True))
        self.assertIsNone(bn.plan({"state": "closed"}, [{"body": bn.message(tag)}], tag))

    def test_message_names_the_version(self):
        self.assertIn("Siansa 0.29.2beta", bn.message("v0.29.2beta"))


if __name__ == "__main__":
    unittest.main()
