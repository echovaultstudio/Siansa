import unittest

from emulator_needed import needed


class EmulatorNeededTest(unittest.TestCase):
    """Issue #241: the emulator tests run for app changes, screens included, and not for notes or the plugin."""

    def test_app_code_and_screens_need_it(self):
        self.assertTrue(needed(["app/src/main/java/app/musix/ui/CollectionPage.kt"]))
        self.assertTrue(needed(["app/src/main/res/values/strings.xml"]))
        self.assertTrue(needed(["app/build.gradle.kts"]))
        self.assertTrue(needed(["gradle/libs.versions.toml"]))
        self.assertTrue(needed(["scripts/test-server.sh"]))
        self.assertTrue(needed([".github/workflows/ci.yml"]))
        self.assertTrue(needed(["changelog.d/issue-1.md", "app/src/test/java/app/musix/X.kt"]))

    def test_notes_docs_and_plugin_dont(self):
        self.assertFalse(needed(["changelog.d/issue-1.md", "README.md", "CHANGELOG.md", "version.txt"]))
        self.assertFalse(needed(["audiomuse-plugin/musix_analysis/jobs.py", "RELEASES.md"]))
        self.assertFalse(needed([".github/workflows/branch-cleanup.yml", "scripts/release_notes.py"]))
        self.assertFalse(needed(["", "  "]))


if __name__ == "__main__":
    unittest.main()
