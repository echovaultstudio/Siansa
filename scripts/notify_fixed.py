#!/usr/bin/env python3
"""Tells people their report is fixed (issues #433, #606).

  python3 scripts/notify_fixed.py v0.29.2beta

After a release, every issue in this repository that one of its pull requests fixes ("Fixes #N" in the pull
request's description) gets a comment naming the version with the fix and how to update, and is closed if it's
still open. GitHub already closes such an issue when the pull request is merged; the comment is what tells the
person when they can have it. Uses the workflow's own token (GH_TOKEN, with issues: write); nothing else is needed.
A version is only announced once per issue.
"""
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
import release_prs as rp  # noqa: E402

# "Fixes #12" or "Fixes echovaultstudio/Siansa#12"; an issue of another repository is not this one.
FIXES = re.compile(r"(?i)\b(?:fix(?:es|ed)?|close[sd]?|resolve[sd]?)\s+(?:echovaultstudio/Siansa)?#(\d+)\b")


def fixed_issues(bodies):
    """The issue numbers the pull request descriptions say they fix, in order, once each."""
    seen = []
    for body in bodies:
        for number in FIXES.findall(body or ""):
            if int(number) not in seen:
                seen.append(int(number))
    return seen


def marker(tag):
    return f"<!-- siansa-release:{tag} -->"


def message(tag):
    version = tag.removeprefix("v")
    return (f"Fixed in **Siansa {version}**. In the F-Droid app, pull down on the Siansa repository to refresh and "
            f"update; on Google Play it arrives with the next update there. Thanks for reporting it!\n\n{marker(tag)}")


def plan(issue, comments, tag):
    """What to do for one issue: ("comment", close?) or None when this version was already announced there."""
    if any(marker(tag) in (c.get("body") or "") for c in comments):
        return None
    return ("comment", issue.get("state") == "open")


def api(method, path, body=None):
    args = ["gh", "api", "-X", method, path]
    if body is not None:
        args += ["--input", "-"]
    result = subprocess.run(args, input=None if body is None else json.dumps(body), capture_output=True, text=True,
                            env=os.environ)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout or "null")


def main(tag):
    name = rp.repo()
    grouped = rp.group(rp.releases_with_commits(name), rp.merged_pulls(name), lambda sha: rp.pulls_of_commit(name, sha))
    release = next(((r, prs) for r, prs, _ in grouped if r["tag_name"] == tag), None)
    if release is None:
        print(f"::warning::Release {tag} not found; nothing sent.")
        return
    numbers = fixed_issues(p.get("body") for p in release[1])
    for number in numbers:
        try:
            issue = api("GET", f"repos/{name}/issues/{number}")
            if issue.get("pull_request"):  # a pull request number, not an issue
                continue
            comments = api("GET", f"repos/{name}/issues/{number}/comments?per_page=100")
            todo = plan(issue, comments, tag)
            if todo is None:
                continue
            api("POST", f"repos/{name}/issues/{number}/comments", {"body": message(tag)})
            if todo[1]:
                api("PATCH", f"repos/{name}/issues/{number}", {"state": "closed", "state_reason": "completed"})
            print(f"#{number}: told it's fixed in {tag}")
        except RuntimeError as e:  # one issue failing mustn't stop the others
            print(f"::warning::#{number}: {e}")
    print(f"{len(numbers)} issue(s) in {tag}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
