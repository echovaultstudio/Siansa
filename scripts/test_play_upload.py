import unittest

from play_upload import already_uploaded, code_of_uploaded, track_has, draft_only, plain_notes, track_body, tracks_of


class PlayUploadTest(unittest.TestCase):
    """Issue #557: what goes to Google Play with a release."""

    def test_notes_become_plain_whats_new(self):
        md = "### Added\n- **About in Settings.** It shows\n  the version.\n\n### Fixed\n- **Home** no longer jumps.\n<!-- release: minor -->\n"
        self.assertEqual("• About in Settings. It shows the version.\n• Home no longer jumps.", plain_notes(md))

    def test_notes_stop_at_play_limit(self):
        text = plain_notes("- " + "word " * 300)
        self.assertLessEqual(len(text), 500)
        self.assertTrue(text.endswith("…"))

    def test_links_and_code_keep_their_words(self):
        self.assertEqual("• See the docs and app.siansa", plain_notes("- See [the docs](https://x) and `app.siansa`"))

    def test_release_on_the_track(self):
        body = track_body("alpha", "0.37.0beta", 1234, "• Fixes", "completed")
        self.assertEqual("alpha", body["track"])
        release = body["releases"][0]
        self.assertEqual(["1234"], release["versionCodes"])
        self.assertEqual("0.37.0beta", release["name"])
        self.assertEqual("completed", release["status"])
        self.assertEqual([{"language": "en-US", "text": "• Fixes"}], release["releaseNotes"])
        self.assertNotIn("releaseNotes", track_body("alpha", "v", 1, "")["releases"][0])

    def test_play_answers_are_told_apart(self):
        self.assertTrue(already_uploaded('HTTP 403: {"error": {"message": "APK specifies a version code that has already been used."}}'))
        self.assertFalse(already_uploaded("HTTP 401: invalid credentials"))
        self.assertTrue(draft_only("HTTP 400: Only releases with status draft may be created on draft app."))
        self.assertFalse(draft_only("HTTP 400: Track not found"))

    def test_open_testing_unless_set(self):
        self.assertEqual(["beta"], tracks_of(""))
        self.assertEqual(["beta"], tracks_of(None))
        self.assertEqual(["internal"], tracks_of(" internal "))
        self.assertEqual(["alpha", "internal"], tracks_of("alpha, internal,alpha"))

    def test_an_uploaded_bundle_is_found_by_its_file(self):
        bundles = [{"versionCode": 866, "sha256": "aa"}, {"versionCode": 899, "sha256": "BB"}]
        self.assertEqual(899, code_of_uploaded(bundles, "bb"))
        self.assertIsNone(code_of_uploaded(bundles, "cc"))

    def test_a_track_with_the_release_is_left_alone(self):
        alpha = {"track": "alpha", "releases": [{"versionCodes": ["899"], "status": "inProgress"}]}
        self.assertTrue(track_has(alpha, 899))
        self.assertFalse(track_has({"track": "internal", "releases": [{"versionCodes": ["866"]}]}, 899))
        self.assertFalse(track_has({"track": "internal"}, 899))


if __name__ == "__main__":
    unittest.main()
