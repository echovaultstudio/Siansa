"""Tests for screenshot_comment.py (run in CI: python3 -m unittest discover -s scripts)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import screenshot_comment as sc  # noqa: E402

D = "app/src/test/screenshots"
REPO = "echovaultstudio/Siansa"
BEFORE = "a" * 40
AFTER = "b" * 40


class Changes(unittest.TestCase):
    def test_changed_then_new_then_removed_each_by_name(self):
        diff = f"A\t{D}/z_new.png\nM\t{D}/tabs.png\nD\t{D}/old.png\nM\t{D}/banner.png\n"
        self.assertEqual(sc.changes(diff), [
            ("M", f"{D}/banner.png"), ("M", f"{D}/tabs.png"), ("A", f"{D}/z_new.png"), ("D", f"{D}/old.png"),
        ])

    def test_a_rename_is_removed_and_new(self):
        self.assertEqual(sc.changes(f"R087\t{D}/tabs.png\t{D}/bottom_tabs.png\n"),
                         [("A", f"{D}/bottom_tabs.png"), ("D", f"{D}/tabs.png")])

    def test_only_pictures(self):
        self.assertEqual(sc.changes(f"M\t{D}/notes.txt\n\nM\t{D}/a.png\n"), [("M", f"{D}/a.png")])


class Body(unittest.TestCase):
    def test_before_from_main_and_after_from_the_new_commit(self):
        text = sc.body(REPO, BEFORE, AFTER, [("M", f"{D}/tabs_light_130.png")])
        self.assertTrue(text.startswith(sc.MARKER))
        self.assertIn("1 changed, 0 new, 0 removed, recorded from bbbbbbb", text)
        self.assertIn(f"https://raw.githubusercontent.com/{REPO}/{BEFORE}/{D}/tabs_light_130.png", text)
        self.assertIn(f"https://raw.githubusercontent.com/{REPO}/{AFTER}/{D}/tabs_light_130.png", text)
        self.assertIn("| `tabs_light_130` |", text)
        self.assertIn("Merging this pull request approves them", text)

    def test_new_has_no_before_and_removed_no_after(self):
        text = sc.body(REPO, BEFORE, AFTER, [("A", f"{D}/new.png"), ("D", f"{D}/old.png")])
        self.assertNotIn(f"{BEFORE}/{D}/new.png", text)
        self.assertIn("| `new` | new |", text)
        self.assertNotIn(f"{AFTER}/{D}/old.png", text)
        self.assertIn("| removed |", text)

    def test_nothing_differs(self):
        text = sc.body(REPO, BEFORE, AFTER, [])
        self.assertIn("No picture differs from main", text)
        self.assertNotIn("<img", text)

    def test_stays_under_githubs_limit(self):
        many = [("M", f"{D}/scene_{i:04d}_light_100.png") for i in range(400)]
        text = sc.body(REPO, BEFORE, AFTER, many)
        self.assertLessEqual(len(text), sc.LIMIT + 100)
        self.assertIn("more in **Files changed**", text)


if __name__ == "__main__":
    unittest.main()
