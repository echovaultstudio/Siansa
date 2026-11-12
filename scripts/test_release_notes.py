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


class DevBuilds(unittest.TestCase):
    """The changelog of a Siansa Dev build: notes main hasn't released, the newest since the last build first."""

    def setUp(self):
        import subprocess
        self.here = os.getcwd()
        self.repo = tempfile.mkdtemp()
        os.chdir(self.repo)
        self.addCleanup(os.chdir, self.here)
        self.run_git = lambda *a: subprocess.run(["git", *a], check=True, capture_output=True, text=True).stdout.strip()
        self.run_git("init", "-q", "-b", "main")
        self.run_git("config", "user.email", "t@example.com")
        self.run_git("config", "user.name", "T")
        os.makedirs("changelog.d")
        open("version.txt", "w").write("1.1.0\n")
        self.add("README.md", "notes\n", when=1_700_000_000)

    def add(self, name, text, when):
        open(os.path.join("changelog.d", name) if name.endswith(".md") and name != "version.txt" else name, "w").write(text)
        self.run_git("add", "-A")
        env = dict(os.environ, GIT_AUTHOR_DATE=f"@{when} +0000", GIT_COMMITTER_DATE=f"@{when} +0000")
        import subprocess
        subprocess.run(["git", "commit", "-q", "-m", name], check=True, env=env)
        return self.run_git("rev-parse", "HEAD")

    def test_unreleased_notes_newest_first_and_new_ones_on_top(self):
        self.add("fix-a.md", "### Fixed\n- **A fix.** Out on main.\n", when=1_762_800_000)  # 10 Nov 2025
        self.run_git("checkout", "-q", "-b", "dev")
        self.add("old.md", "### Added\n- **Old feature.** Text.\n", when=1_762_900_000)
        last_build = self.add("older.md", "### Changed\n- **Older change.**\n  more of it\n", when=1_762_950_000)
        self.add("new.md", "<!-- release: minor -->\n### Added\n- **New feature.** Text.\n", when=1_763_000_000)
        # main releases fix-a: its note is taken out there; dev still has it until main is merged into dev.
        self.run_git("checkout", "-q", "main")
        self.run_git("rm", "-q", "changelog.d/fix-a.md")
        self.run_git("commit", "-q", "-m", "release")
        self.run_git("checkout", "-q", "dev")

        out = rn.dev_notes(main="main", since=last_build, released="1.1.0")
        self.assertTrue(out.startswith("## What's coming since Siansa 1.1.0\n"))
        self.assertNotIn("A fix.", out)  # released
        self.assertNotIn("release: minor", out)
        new_at, old_at, older_at = out.index("New feature"), out.index("Old feature"), out.index("Older change")
        self.assertLess(out.index("### New in this build"), new_at)
        self.assertLess(new_at, older_at)
        self.assertLess(older_at, old_at)  # newest first
        self.assertIn("- Added · **New feature.** Text.", out)
        self.assertIn("- Changed · **Older change.**\n  more of it", out)  # a note's further lines are kept
        self.assertIn("### 12 Nov 2025", out)

    def test_without_a_last_build_or_notes(self):
        self.run_git("checkout", "-q", "-b", "dev")
        self.assertIn("Nothing yet", rn.dev_notes(main="main", since=None, released="1.1.0"))
        self.add("one.md", "### Fixed\n- **One.**\n", when=1_763_000_000)
        out = rn.dev_notes(main="main", since=None)
        self.assertNotIn("New in this build", out)
        self.assertIn("- Fixed · **One.**", out)
        # A last build git doesn't know (history too short): every note counts as new, none is lost.
        self.assertIn("New in this build", rn.dev_notes(main="main", since="0" * 40))
