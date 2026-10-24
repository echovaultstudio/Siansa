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


if __name__ == "__main__":
    unittest.main()
