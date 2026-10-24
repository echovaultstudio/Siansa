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


import base64, json, tempfile


class FlowTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        server.DATA_FILE = os.path.join(self.dir, "signups.jsonl")
        server.DECISIONS_FILE = os.path.join(self.dir, "decisions.jsonl")
        server.TESTERS_FILE = os.path.join(self.dir, "testers.json")
        server.SECRET = "s3cret"
        server._ok_cache.clear(); server._fails.clear()
        self.sent = []
        server.send_mail = lambda to, subject, body: self.sent.append((to, subject, body)) or True

    def signup(self, route="fdroid"):
        rec, _ = server.check({**OK, "route": [route]})
        server.save(rec)
        return rec

    def test_links_are_signed_per_action(self):
        a, d = server.sign("abc", "approve"), server.sign("abc", "deny")
        self.assertNotEqual(a, d)
        self.assertTrue(server.verify("abc", "approve", a))
        self.assertFalse(server.verify("abc", "approve", d))
        self.assertFalse(server.verify("abd", "approve", a))
        self.assertFalse(server.verify("abc", "delete", a))
        server.SECRET = ""
        self.assertFalse(server.verify("abc", "approve", a))

    def test_approve_fdroid_makes_login_and_emails_it(self):
        rec = self.signup("fdroid")
        line = server.decide(rec, "approve")
        self.assertIn("Approved", line)
        to, subject, body = self.sent[0]
        self.assertEqual(to, "max@example.com")
        user = body.split("User name: ")[1].split("\n")[0]
        password = body.split("Password: ")[1].split("\n")[0]
        header = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()
        self.assertTrue(server.check_login(header))
        self.assertNotIn(password, open(server.TESTERS_FILE).read())  # only a hash is kept
        bad = "Basic " + base64.b64encode(f"{user}:wrong".encode()).decode()
        self.assertFalse(server.check_login(bad))
        self.assertFalse(server.check_login(None))
        self.assertFalse(server.check_login("Basic !!!"))

    def test_decision_happens_once(self):
        rec = self.signup()
        self.assertIsNotNone(server.decide(rec, "deny"))
        self.assertIsNone(server.decide(rec, "approve"))
        self.assertEqual(server.decision_of(rec["id"]), "denied")
        self.assertEqual(len(self.sent), 1)
        self.assertEqual(server.load_testers(), {})

    def test_play_approval_makes_no_login_and_reminds_owner(self):
        rec = self.signup("play")
        self.assertIn("Play Console", server.decide(rec, "approve"))
        self.assertEqual(server.load_testers(), {})

    def test_revoke_removes_login(self):
        rec = self.signup("fdroid")
        server.decide(rec, "approve")
        user = server.load_testers()
        header_user = next(iter(user))
        self.assertEqual(server.remove_tester("MAX@example.com"), 1)
        self.assertEqual(server.load_testers(), {})
        self.assertFalse(header_user in server.load_testers())

    def test_new_login_replaces_old_for_same_email(self):
        server.add_tester("a@b.co"); server.add_tester("a@b.co")
        self.assertEqual(len(server.load_testers()), 1)

    def test_resend_mails_only_undecided(self):
        server.NOTIFY_TO = None
        os.environ["NOTIFY_TO"] = "me@example.com"
        a = self.signup(); b = self.signup()
        server.decide(a, "deny"); self.sent.clear()
        self.assertTrue(server.cli(["server.py", "resend"]))
        self.assertEqual(len(self.sent), 1)
        self.assertIn(server.link(b["id"], "approve"), self.sent[0][2])

    def test_link_expires(self):
        rec = self.signup()
        self.assertFalse(server.expired(rec))
        self.assertTrue(server.expired(rec, __import__("time").time() + 15 * 86400))


if __name__ == "__main__":
    unittest.main()
