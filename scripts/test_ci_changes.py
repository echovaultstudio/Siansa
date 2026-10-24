import unittest

from ci_changes import app_needed, outputs, plugin_needed


class CiChangesTest(unittest.TestCase):
    """Fewer Actions minutes: the Android build only for changes that can reach the app."""

    def test_app_and_build_changes_need_the_build(self):
        self.assertTrue(app_needed(["app/src/main/java/app/siansa/ui/HomeScreen.kt"]))
        self.assertTrue(app_needed(["build.gradle.kts"]))
        self.assertTrue(app_needed(["gradle.properties"]))
        self.assertTrue(app_needed([".github/workflows/ci.yml"]))
        self.assertTrue(app_needed(["cast-receiver/index.html"]))
        self.assertTrue(app_needed(["version.txt"]))
        self.assertTrue(app_needed(["deploy/site/index.html", "app/src/main/AndroidManifest.xml"]))

    def test_website_docs_plugin_and_notes_dont(self):
        self.assertFalse(app_needed(["deploy/site/index.html", "deploy/signup/server.py", "deploy/Caddyfile"]))
        self.assertFalse(app_needed(["README.md", "CLAUDE.md", "CHANGELOG.md", "RELEASES.md", "docs/logging.md"]))
        self.assertFalse(app_needed(["changelog.d/issue-1.md", "audiomuse-plugin/siansa_analysis/jobs.py"]))
        self.assertFalse(app_needed([".github/workflows/site.yml", "scripts/beta_notify.py", ".gitleaks.toml"]))
        self.assertFalse(app_needed([]))

    def test_the_test_server_still_needs_the_build(self):
        self.assertTrue(app_needed(["scripts/test-server.sh"]))

    def test_plugin(self):
        self.assertTrue(plugin_needed(["audiomuse-plugin/plugin.json"]))
        self.assertTrue(plugin_needed([".github/workflows/ci.yml"]))
        self.assertFalse(plugin_needed(["app/src/main/java/X.kt"]))

    def test_outputs(self):
        self.assertEqual(outputs(["deploy/site/style.css"]), {"app": False, "emulator": False, "plugin": False})
        self.assertEqual(outputs(["app/src/main/res/values/strings.xml"]), {"app": True, "emulator": True, "plugin": False})


if __name__ == "__main__":
    unittest.main()
