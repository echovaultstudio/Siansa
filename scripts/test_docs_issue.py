"""Tests for docs_issue.py: the issue that asks for the docs after a release."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import docs_issue as di  # noqa: E402

NOTES = """## Siansa 1.2.0

### Added

- Song ratings in About this song.

### Fixed

- Lyrics: a swipe down closes only the lyrics.

### Changed

- DJ mixes: a short blend near the end.
"""


class DocsIssue(unittest.TestCase):
    def test_every_change_is_listed_with_its_kind(self):
        self.assertEqual(di.changes(NOTES), [
            ("Added", "- Song ratings in About this song."),
            ("Fixed", "- Lyrics: a swipe down closes only the lyrics."),
            ("Changed", "- DJ mixes: a short blend near the end."),
        ])

    def test_the_issue_asks_for_one_pull_request_not_merged(self):
        body = di.body("1.2.0", NOTES)
        self.assertIn("- (Added) Song ratings in About this song.", body)
        self.assertIn("F-Droid repo and Google Play", body)
        self.assertIn("Don't merge it", body)
        self.assertIn("Fixes #<this issue>", body)

    def test_google_play_held_back_is_not_named(self):
        self.assertNotIn("Google Play", di.body("1.2.0", NOTES, play_held=True).split("\n")[0])

    def test_one_title_per_version(self):
        self.assertEqual(di.title("1.2.0"), "Docs: bring them up to date with Siansa 1.2.0")

    def test_notes_with_nothing_in_them(self):
        self.assertIn("(nothing in its notes)", di.body("1.2.1", "## Siansa 1.2.1\n"))


if __name__ == "__main__":
    unittest.main()
