"""Tests for release_notes.py (run in CI: python3 -m unittest discover -s scripts)."""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import release_notes as rn  # noqa: E402


def notes(*texts):
    folder = tempfile.mkdtemp()
    files = []
    for i, text in enumerate(texts):
        path = os.path.join(folder, f"n{i}.md")
        open(path, "w", encoding="utf-8").write(text)
        files.append(path)
    return files


class Numbering(unittest.TestCase):
    def test_patch(self):
        self.assertEqual(rn.bump("0.1.3", "patch"), "0.1.4")

    def test_minor_resets_patch(self):
        self.assertEqual(rn.bump("0.1.3", "minor"), "0.2.0")

    def test_major_resets_both(self):
        self.assertEqual(rn.bump("0.9.4", "major"), "1.0.0")

    def test_suffix_kept(self):
        self.assertEqual(rn.bump("0.0.26beta", "minor"), "0.1.0beta")


class Size(unittest.TestCase):
    def test_fixes_are_patch(self):
        self.assertEqual(rn.level_of(notes("### Fixed\n- a\n", "### Changed\n- b\n")), "patch")

    def test_new_feature_is_minor(self):
        self.assertEqual(rn.level_of(notes("### Fixed\n- a\n", "### Added\n- b\n")), "minor")

    def test_note_can_ask_for_minor(self):
        self.assertEqual(rn.level_of(notes("<!-- release: minor -->\n### Changed\n- big rework\n")), "minor")

    def test_major_only_when_asked(self):
        self.assertEqual(rn.level_of(notes("### Added\n- a\n", "<!-- release: major -->\n### Changed\n- 1.0\n")), "major")

    def test_mark_not_in_notes(self):
        text = rn.combine(notes("<!-- release: minor -->\n### Changed\n- big rework\n"))
        self.assertNotIn("release:", text)
        self.assertIn("- big rework", text)


if __name__ == "__main__":
    unittest.main()
