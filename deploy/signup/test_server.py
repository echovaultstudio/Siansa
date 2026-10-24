import os, sys, unittest
sys.path.insert(0, os.path.dirname(__file__))
import server

OK = {"email": ["Max@Example.com "], "route": ["play"], "android": ["yes"], "server": ["navidrome"],
      "reach": ["yes"], "days": ["yes"], "feedback": ["yes"], "privacy": ["yes"], "device": ["Pixel 8"]}


class CheckTest(unittest.TestCase):
    def test_accepts_complete_form(self):
        rec, problems = server.check(OK, 0)
        self.assertEqual(problems, [])
        self.assertEqual(rec["email"], "max@example.com")
        self.assertEqual(rec["time"], "1970-01-01T00:00:00Z")

    def test_each_requirement_is_checked(self):
        for key, bad in [("email", ["nope"]), ("route", ["x"]), ("android", ["no"]), ("server", ["none"]),
                         ("reach", ["no"]), ("days", [""]), ("feedback", [""]), ("privacy", [""])]:
            rec, problems = server.check({**OK, key: bad}, 0)
            self.assertIsNone(rec, key)
            self.assertEqual(len(problems), 1, key)

    def test_fdroid_does_not_need_the_14_days(self):
        rec, problems = server.check({**OK, "route": ["fdroid"], "days": [""]}, 0)
        self.assertEqual(problems, [])

    def test_emby_is_accepted(self):
        self.assertEqual(server.check({**OK, "server": ["emby"]}, 0)[1], [])

    def test_rate_limit_per_client_then_total(self):
        server._recent.clear(); server._accepted.clear()
        self.assertFalse(any(server.rate_limited("a", 100 + i) for i in range(server.PER_CLIENT)))
        self.assertTrue(server.rate_limited("a", 200))
        self.assertFalse(server.rate_limited("b", 105))
        server._accepted.extend([100] * server.PER_HOUR_TOTAL)
        self.assertTrue(server.rate_limited("c", 110))
        self.assertFalse(server.rate_limited("d", 100 + 3700))


import tempfile


CODE = "Abcdefghijklmnopqrstuvwxyz012345"


class FlowTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        server.DATA_FILE = os.path.join(self.dir, "signups.jsonl")
        server.DECISIONS_FILE = os.path.join(self.dir, "decisions.jsonl")
        server.CODE_FILE = os.path.join(self.dir, "repo_code")
        server.LOCK_FILE = os.path.join(self.dir, "state.lock")
        open(server.CODE_FILE, "w").write(CODE + "\n")
        server.SECRET = "s3cret"
        server.LIMIT = 2
        server._fails.clear()
        os.environ["NOTIFY_TO"] = "me@example.com"
        self.sent = []
        server.send_mail = lambda to, subject, body: self.sent.append((to, subject, body)) or True

    def signup(self, n=0, route="fdroid"):
        rec, _ = server.check({**OK, "email": [f"p{n}@example.com"], "route": [route]})
        return server.accept(rec)

    def test_links_are_signed_per_action(self):
        a, d = server.sign("abc", "approve"), server.sign("abc", "deny")
        self.assertNotEqual(a, d)
        self.assertTrue(server.verify("abc", "approve", a))
        self.assertFalse(server.verify("abc", "approve", d))
        self.assertFalse(server.verify("abd", "approve", a))
        self.assertFalse(server.verify("abc", "delete", a))
        server.SECRET = ""
        self.assertFalse(server.verify("abc", "approve", a))

    def test_only_the_current_code_opens_the_repo(self):
        base = f"/fdroid/t/{CODE}/repo"
        self.assertTrue(server.valid_link(base + "/entry.jar"))
        self.assertTrue(server.valid_link(base + "/index-v1.jar?x=1"))
        self.assertTrue(server.valid_link(base))
        self.assertFalse(server.valid_link(base.replace("Abc", "Abd") + "/entry.jar"))
        self.assertFalse(server.valid_link(f"/fdroid/t/{CODE}/entry.jar"))
        self.assertFalse(server.valid_link("/fdroid/repo/entry.jar"))
        self.assertFalse(server.valid_link(None))
        os.remove(server.CODE_FILE)
        self.assertFalse(server.valid_link(base + "/entry.jar"))

    def test_approve_fdroid_emails_the_shared_link_and_issues_page(self):
        rec, state = self.signup(1)
        self.assertEqual(state, "new")
        line = server.decide(rec, "approve")
        self.assertIn("Approved", line)
        self.assertIn("1 of 2 have the link", line)
        to, subject, body = self.sent[0]
        self.assertEqual(to, "p1@example.com")
        self.assertIn(f"https://siansa.app/fdroid/t/{CODE}/repo", body)
        self.assertIn(server.ISSUES_URL, body)

    def test_play_welcome_has_issues_page_too_and_no_link(self):
        rec, _ = self.signup(1, "play")
        self.assertIn("Play Console", server.decide(rec, "approve"))
        body = self.sent[0][2]
        self.assertIn(server.ISSUES_URL, body)
        self.assertNotIn("/fdroid/t/", body)

    def test_no_link_yet_means_no_email_and_a_hint(self):
        os.remove(server.CODE_FILE)
        rec, _ = self.signup(1)
        self.assertIn("scripts/repo-link", server.decide(rec, "approve"))
        self.assertEqual(self.sent, [])

    def test_owner_email_has_the_places_count(self):
        rec, _ = self.signup(1)
        server.notify_owner(rec, wait=True)
        self.assertIn("Beta places: 0 of 2 have the link, 1 waiting for your decision, 0 on the waiting list.", self.sent[0][2])

    def test_decision_happens_once(self):
        rec, _ = self.signup(1)
        self.assertIsNotNone(server.decide(rec, "deny"))
        self.assertIsNone(server.decide(rec, "approve"))
        self.assertEqual(server.status_of(rec["id"]), "denied")

    def test_full_beta_puts_people_on_waiting_list(self):
        self.signup(1); self.signup(2)
        c, state = self.signup(3)
        self.assertEqual(state, "waiting")
        self.assertEqual(server.counts(), {"limit": 2, "approved": 0, "pending": 2, "taken": 2, "waiting": 1})
        self.assertEqual(server.status_of(c["id"]), "waiting")

    def test_free_place_approves_oldest_waiting_automatically(self):
        a, _ = self.signup(1); b, _ = self.signup(2)
        c, _ = self.signup(3); d, _ = self.signup(4)
        server.decide(a, "approve"); server.decide(b, "approve")
        self.sent.clear()
        n, promoted = server.revoke("P1@example.com")
        self.assertEqual(n, 1)
        self.assertEqual(len(promoted), 1)
        self.assertEqual(server.status_of(c["id"]), "approved")  # oldest waiting
        self.assertEqual(server.status_of(d["id"]), "waiting")
        self.assertIn("p3@example.com", [m[0] for m in self.sent])
        owner = [m for m in self.sent if "waiting list" in m[1]]
        self.assertTrue(owner and "2 of 2 have the link" in owner[0][2])

    def test_deny_frees_place_for_waiting(self):
        a, _ = self.signup(1); self.signup(2); c, _ = self.signup(3)
        self.assertIn("Waiting list", server.decide(a, "deny"))
        self.assertEqual(server.status_of(c["id"]), "approved")

    def test_raised_limit_moves_waiting_up(self):
        for n in range(1, 4):
            self.signup(n)
        server.LIMIT = 3
        self.assertEqual(len(server.promote()), 1)

    def test_same_email_twice_is_not_a_second_place(self):
        self.signup(1)
        again, state = self.signup(1)
        self.assertEqual(state, "again")
        self.assertEqual(len(server.read_lines(server.DATA_FILE)), 1)
        server.decide(again, "deny")
        self.assertEqual(self.signup(1)[1], "new")  # after a no, they may ask again

    def test_resend_mails_only_undecided(self):
        a, _ = self.signup(1); b, _ = self.signup(2)
        server.decide(a, "deny"); self.sent.clear()
        self.assertTrue(server.cli(["server.py", "resend"]))
        self.assertEqual(len(self.sent), 1)
        self.assertIn(server.link(b["id"], "approve"), self.sent[0][2])

    def test_new_link_goes_to_approved_fdroid_testers_only(self):
        a, _ = self.signup(1, "fdroid"); b, _ = self.signup(2, "play")
        server.decide(a, "approve"); server.decide(b, "approve"); self.sent.clear()
        open(server.CODE_FILE, "w").write("Zyxwvutsrqponmlkjihgfedcba987654\n")
        self.assertTrue(server.cli(["server.py", "notify-new-link"]))
        self.assertEqual([m[0] for m in self.sent], ["p1@example.com"])
        self.assertIn("/fdroid/t/Zyxwvutsrqponmlkjihgfedcba987654/repo", self.sent[0][2])
        self.assertFalse(server.valid_link(f"/fdroid/t/{CODE}/repo/entry.jar"))  # the old address is dead

    def test_cli_approve_prints_link_and_count_without_email(self):
        import io, contextlib
        rec, _ = self.signup(1)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            server.cli(["server.py", "approve", "P1@example.com"])
            server.cli(["server.py", "link", "p1@example.com"])
        self.assertEqual(out.getvalue().count(f"https://siansa.app/fdroid/t/{CODE}/repo"), 2)
        self.assertIn("1 of 2 have the link", out.getvalue())
        self.assertEqual(server.status_of(rec["id"]), "approved")
        self.assertEqual(self.sent, [])

    def test_link_expires(self):
        rec, _ = self.signup(1)
        self.assertFalse(server.expired(rec))
        self.assertTrue(server.expired(rec, __import__("time").time() + 15 * 86400))


class ReportTest(unittest.TestCase):
    def setUp(self):
        import base64, io, json
        self.b64, self.io, self.json = base64, io, json
        self.dir = tempfile.mkdtemp()
        server.REPORTS_DIR = self.dir

    def header(self, **kw):
        d = {"happened": "Song stops @max #1", "expected": "Plays", "app": "0.28.1", "android": "15", "device": "Pixel", "source": "EMBY"}
        d.update(kw)
        return self.b64.urlsafe_b64encode(self.json.dumps(d).encode()).decode().rstrip("=")

    def test_fields_need_words_and_are_cut(self):
        self.assertIsNone(server.report_fields("!!!"))
        self.assertIsNone(server.report_fields(self.header(happened="no")))
        self.assertEqual(len(server.report_fields(self.header(happened="x" * 5000))["happened"]), 1000)

    def test_issue_text_cannot_ping_or_link(self):
        title, body = server.issue_text("ab12cd34", server.report_fields(self.header()))
        for t in (title, body):
            self.assertNotIn("@max", t)
            self.assertNotIn("#1", t)
        self.assertIn("ab12cd34", body)
        self.assertIn("> Song stops", body)

    def test_saves_a_zip_privately(self):
        data = b"PK\x03\x04" + b"x" * 100
        rid = server.save_report(self.io.BytesIO(data), len(data), {"happened": "x"}, 0)
        self.assertEqual(open(f"{self.dir}/{rid}.zip", "rb").read(), data)
        self.assertEqual(oct(os.stat(f"{self.dir}/{rid}.zip").st_mode & 0o777), "0o600")

    def test_refuses_non_zip_short_and_too_big(self):
        for data, length in [(b"hello world", 11), (b"PK\x03\x04abc", 40), (b"PK", 2), (b"PK\x03\x04", server.REPORT_MAX_ZIP + 1)]:
            self.assertIsNone(server.save_report(self.io.BytesIO(data), length, {}, 0))
        self.assertEqual(os.listdir(self.dir), [])

    def test_no_token_means_no_issue(self):
        server.GITHUB_TOKEN = ""
        self.assertEqual(server.open_issue("t", "b"), (None, None))

    def test_report_rate_limit(self):
        server._reports.clear()
        self.assertFalse(any(server.report_limited("a", 100 + i) for i in range(server.REPORTS_PER_HOUR)))
        self.assertTrue(server.report_limited("a", 200))
        self.assertFalse(server.report_limited("a", 4000))


class EmailTemplateTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        server.EMAILS_DIR = self.dir

    def write(self, name, text):
        open(os.path.join(self.dir, name + ".txt"), "w").write(text)

    def test_file_is_used_with_placeholders_and_shared_tail(self):
        self.write("welcome_fdroid", "Subject: Hi $email\n\nUse $repo_url\n\n$tail\n")
        self.write("tail.txt".removesuffix(".txt"), "Report: $issues_url\nCosts $$5\n")
        subject, body = server.template("welcome_fdroid", "d", "d", repo_url="https://r", email="a@b.c", issues_url="https://i")
        self.assertEqual(subject, "Hi a@b.c")
        self.assertIn("Use https://r", body)
        self.assertIn("Report: https://i\nCosts $5", body)

    def test_missing_or_broken_file_falls_back(self):
        self.assertEqual(server.template("nope", "S", "B"), ("S", "B"))
        self.write("deny", "no subject line here\n\nbody")
        self.assertEqual(server.template("deny", "S", "B"), ("S", "B"))
        self.write("deny", "Subject: X\n\n")
        self.assertEqual(server.template("deny", "S", "B"), ("S", "B"))

    def test_unknown_placeholders_are_left_alone(self):
        self.write("deny", "Subject: S\n\nHello $nobody")
        self.assertEqual(server.template("deny", "d", "d")[1], "Hello $nobody")

    def test_the_shipped_templates_are_valid(self):
        server.EMAILS_DIR = os.path.join(os.path.dirname(__file__), "..", "emails")
        for name in ("welcome_fdroid", "welcome_play", "new_link", "deny"):
            subject, body = server.template(name, "FALLBACK", "FALLBACK", repo_url="https://r", beta_repo_url="b", issues_url="i", email="e")
            self.assertNotEqual(subject, "FALLBACK", name)
            self.assertNotIn("$", body, name)


if __name__ == "__main__":
    unittest.main()
