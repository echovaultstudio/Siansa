"""Tests for beta_notify.py (issue #433)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import beta_notify as bn  # noqa: E402


class BetaNotify(unittest.TestCase):
    def test_only_fixed_beta_issues_are_found(self):
        bodies = [
            "Fixes echovaultstudio/siansa-beta-testers#8\nRefs echovaultstudio/siansa-beta-testers#4",
            "closes echovaultstudio/siansa-beta-testers#10 and Resolved echovaultstudio/siansa-beta-testers#8",
            "Fixes #427",
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
