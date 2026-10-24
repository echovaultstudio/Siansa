#!/usr/bin/env python3
"""Tells beta testers their report is fixed (issue #433).

  python3 scripts/beta_notify.py v0.29.2beta

After a release, every beta testers issue that one of its pull requests fixes ("Fixes
echovaultstudio/siansa-beta-testers#N" in the pull request's description) gets a comment naming the version with
the fix and how to update, and is closed if it's still open. GitHub already closes such an issue when the pull
request is merged; the comment is what tells the tester when they can have it.

Needs BETA_ISSUES_TOKEN: a fine-grained token owned by echovaultstudio for the beta testers repo only, with
Issues: Read and write. Without it nothing is sent. A version is only announced once per issue.
"""
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
import release_prs as rp  # noqa: E402

BETA_REPO = "echovaultstudio/siansa-beta-testers"
FIXES = re.compile(r"(?i)\b(?:fix(?:es|ed)?|close[sd]?|resolve[sd]?)\s+" + re.escape(BETA_REPO) + r"#(\d+)\b")


def fixed_issues(bodies):
    """The beta testers issue numbers the pull request descriptions say they fix, in order, once each."""
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


def beta_api(method, path, body=None, token=None):
    args = ["gh", "api", "-X", method, path]
    if body is not None:
        args += ["--input", "-"]
    result = subprocess.run(args, input=None if body is None else json.dumps(body), capture_output=True, text=True,
                            env={**os.environ, "GH_TOKEN": token})
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout or "null")


def main(tag):
    token = os.environ.get("BETA_ISSUES_TOKEN", "")
    if not token:
        print("::notice::No BETA_ISSUES_TOKEN; beta testers issues not told about this release.")
        return
    name = rp.repo()
    grouped = rp.group(rp.releases_with_commits(name), rp.merged_pulls(name), lambda sha: rp.pulls_of_commit(name, sha))
    release = next(((r, prs) for r, prs, _ in grouped if r["tag_name"] == tag), None)
    if release is None:
        print(f"::warning::Release {tag} not found; nothing sent.")
        return
    numbers = fixed_issues(p.get("body") for p in release[1])
    for number in numbers:
        try:
            issue = beta_api("GET", f"repos/{BETA_REPO}/issues/{number}", token=token)
            comments = beta_api("GET", f"repos/{BETA_REPO}/issues/{number}/comments?per_page=100", token=token)
            todo = plan(issue, comments, tag)
            if todo is None:
                continue
            beta_api("POST", f"repos/{BETA_REPO}/issues/{number}/comments", {"body": message(tag)}, token=token)
            if todo[1]:
                beta_api("PATCH", f"repos/{BETA_REPO}/issues/{number}", {"state": "closed", "state_reason": "completed"}, token=token)
            print(f"Beta testers #{number}: told it's fixed in {tag}")
        except RuntimeError as e:  # one issue failing mustn't stop the others
            print(f"::warning::Beta testers #{number}: {e}")
    print(f"{len(numbers)} beta testers issue(s) in {tag}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
