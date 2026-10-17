"""Tests for release_prs.py (run in CI: python3 -m unittest discover -s scripts)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import release_prs as rp  # noqa: E402


def commit(sha, message="Change"):
    return {"sha": sha, "commit": {"message": message}}


def release(tag, *commits):
    return {"tag_name": tag, "html_url": f"https://x/{tag}", "published_at": "2026-10-17T07:00:00Z",
            "commits": list(commits)}


def pull(number, sha, title="A change"):
    return {"number": number, "merge_commit_sha": sha, "title": title, "html_url": f"https://x/pull/{number}",
            "user": {"login": "someone"}, "merged_at": "2026-10-17T06:00:00Z"}


class Grouping(unittest.TestCase):
    def test_pull_goes_to_release_with_its_merge_commit(self):
        grouped = rp.group([release("v1", commit("a")), release("v2", commit("b"))], [pull(2, "b"), pull(1, "a")])
        self.assertEqual([[p["number"] for p in prs] for _, prs, _ in grouped], [[1], [2]])

    def test_direct_pushes_counted_and_release_commits_skipped(self):
        r = release("v1", commit("a"), commit("b"), commit("c", "Release 0.1.0 [skip ci]"))
        _, prs, direct = rp.group([r], [pull(1, "a")])[0]
        self.assertEqual(([p["number"] for p in prs], direct), ([1], 1))

    def test_rebase_merge_listed_once(self):
        r = release("v1", commit("a"), commit("b"))
        _, prs, direct = rp.group([r], [pull(1, "b")], lambda sha: [pull(1, "b")])[0]
        self.assertEqual(([p["number"] for p in prs], direct), ([1], 0))


class Page(unittest.TestCase):
    def test_newest_first_with_links(self):
        text = rp.render(rp.group([release("v0.1.0", commit("a")), release("v0.2.0", commit("b"))],
                                  [pull(7, "b", "Shiny")]))
        self.assertLess(text.index("## [0.2.0]"), text.index("## [0.1.0]"))
        self.assertIn("- [#7](https://x/pull/7) Shiny (@someone)", text)
        self.assertIn("- 1 commit pushed straight to main", text)


if __name__ == "__main__":
    unittest.main()
