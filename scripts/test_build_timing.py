import unittest

from build_timing import seconds, slowest, total

PROFILE = """
<tr><td>Total Build Time</td><td class="numeric">9m12.40s</td></tr>
<tr><td>:app</td><td class="numeric">8m1.00s</td><td>(total)</td></tr>
<tr><td class="indentPath">:app:minifyReleaseWithR8</td><td class="numeric">2m30.10s</td><td></td></tr>
<tr><td class="indentPath">:app:compileDebugKotlin</td><td class="numeric">1m5.00s</td><td>FROM-CACHE</td></tr>
<tr><td class="indentPath">:app:lintAnalyzeDebug</td><td class="numeric">1m40.25s</td><td></td></tr>
<tr><td class="indentPath">:app:preBuild</td><td class="numeric">0.001s</td><td>UP-TO-DATE</td></tr>
"""


class BuildTimingTest(unittest.TestCase):
    def test_durations_read(self):
        self.assertEqual(seconds("1m2.50s"), 62.5)
        self.assertEqual(seconds("0.003s"), 0.003)
        self.assertEqual(seconds("1h0m1.00s"), 3601)

    def test_slowest_first_and_projects_skipped(self):
        top = slowest(PROFILE, 3)
        self.assertEqual([p for p, _, _ in top], [":app:minifyReleaseWithR8", ":app:lintAnalyzeDebug", ":app:compileDebugKotlin"])
        self.assertEqual(top[2][2], "FROM-CACHE")

    def test_total(self):
        self.assertAlmostEqual(total(PROFILE), 552.4)
        self.assertIsNone(total("<p>no profile</p>"))


if __name__ == "__main__":
    unittest.main()
