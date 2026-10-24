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


class FlowTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        server.DATA_FILE = os.path.join(self.dir, "signups.jsonl")
        server.DECISIONS_FILE = os.path.join(self.dir, "decisions.jsonl")
        server.TESTERS_FILE = os.path.join(self.dir, "testers.json")
        server.LOCK_FILE = os.path.join(self.dir, "state.lock")
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

    def test_approve_fdroid_makes_private_link(self):
        rec, state = self.signup(1)
        self.assertEqual(state, "new")
        self.assertIn("Approved", server.decide(rec, "approve"))
        body = self.sent[0][2]
        url = next(w for w in body.split() if w.startswith("https://siansa.app/fdroid/t/"))
        path = url[len("https://siansa.app"):]
        self.assertTrue(server.valid_link(path + "/entry.jar"))
        self.assertTrue(server.valid_link(path + "/index-v1.jar?x=1"))
        self.assertFalse(server.valid_link(path.replace("/repo", "") + "/x"))
        self.assertFalse(server.valid_link(path[:-6] + "AAAA/repo/entry.jar"))
        self.assertFalse(server.valid_link("/fdroid/repo/entry.jar"))
        self.assertFalse(server.valid_link(None))
        self.assertNotIn(url.split("/t/")[1].split("/")[0], open(server.TESTERS_FILE).read())  # only a hash is kept

    def test_decision_happens_once(self):
        rec, _ = self.signup(1)
        self.assertIsNotNone(server.decide(rec, "deny"))
        self.assertIsNone(server.decide(rec, "approve"))
        self.assertEqual(server.status_of(rec["id"]), "denied")
        self.assertEqual(server.load_testers(), {})

    def test_play_approval_makes_no_link_and_reminds_owner(self):
        rec, _ = self.signup(1, "play")
        self.assertIn("Play Console", server.decide(rec, "approve"))
        self.assertEqual(server.load_testers(), {})

    def test_full_beta_puts_people_on_waiting_list(self):
        a, _ = self.signup(1); b, _ = self.signup(2)
        c, state = self.signup(3)
        self.assertEqual(state, "waiting")
        self.assertEqual(server.counts(), {"limit": 2, "taken": 2, "waiting": 1})
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
        self.assertTrue(any("waiting list" in m[1] for m in self.sent))  # the owner is told
        self.assertEqual(server.counts()["taken"], 2)

    def test_deny_frees_place_for_waiting(self):
        a, _ = self.signup(1); b, _ = self.signup(2); c, _ = self.signup(3)
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

    def test_link_expires(self):
        rec, _ = self.signup(1)
        self.assertFalse(server.expired(rec))
        self.assertTrue(server.expired(rec, __import__("time").time() + 15 * 86400))


if __name__ == "__main__":
    unittest.main()
