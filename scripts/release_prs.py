#!/usr/bin/env python3
"""RELEASES.md: every release and the pull requests in it, newest first.

  python3 scripts/release_prs.py   Rebuilds RELEASES.md from the published releases, their tags and the
                                   merged pull requests, using `gh api` (GH_TOKEN). The release job runs it
                                   after each release, so the page also fills in older releases.

A release holds the commits between the previous release's tag and its own. A pull request belongs to the
release whose commits include its merge commit. Commits that came from no pull request (pushed straight to
main) are counted, and the release job's own "Release x [skip ci]" commits are left out.
"""
import json
import os
import re
import subprocess
import sys
import time

OUT = "RELEASES.md"


def gh(path, run=subprocess.run, wait=time.sleep, tries=4):
    """`gh api` output for a listing. gh now and then fails one call of the hundreds made here
    ("unexpected end of JSON input"), which failed the release job after 0.9.0beta, so it's tried again."""
    for attempt in range(1, tries + 1):
        result = run(["gh", "api", "--paginate", "--slurp", path], capture_output=True, text=True)
        if result.returncode == 0:
            return result.stdout
        print(f"gh api {path} failed (try {attempt} of {tries}): {result.stderr.strip()}", file=sys.stderr)
        if attempt < tries:
            wait(2 * attempt)
    raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)


def api(path):
    """Every item of a GitHub REST listing, following its pages."""
    pages = json.loads(gh(path))
    items = []
    for page in pages:
        items += page["commits"] if isinstance(page, dict) and "commits" in page else page
    return items


def repo():
    """owner/name, from Actions' GITHUB_REPOSITORY or the origin remote."""
    if os.environ.get("GITHUB_REPOSITORY"):
        return os.environ["GITHUB_REPOSITORY"]
    url = subprocess.run(
        ["git", "remote", "get-url", "origin"], check=True, capture_output=True, text=True
    ).stdout.strip()
    return re.sub(r"(\.git)?$", "", re.split(r"github\.com[:/]", url)[-1])


def is_release_commit(commit):
    message = commit["commit"]["message"]
    return message.startswith("Release ") and "[skip ci]" in message


def releases_with_commits(name):
    """Releases oldest first, each with the commits since the release before it."""
    releases = [r for r in api(f"repos/{name}/releases?per_page=100") if not r["draft"]]
    releases.sort(key=lambda r: r["published_at"])
    previous = None
    for r in releases:
        tag = r["tag_name"]
        if previous:
            r["commits"] = api(f"repos/{name}/compare/{previous}...{tag}?per_page=100")
        else:
            r["commits"] = api(f"repos/{name}/commits?sha={tag}&per_page=100")
        previous = tag
    return releases


def merged_pulls(name):
    return [p for p in api(f"repos/{name}/pulls?state=closed&per_page=100") if p["merged_at"]]


def pulls_of_commit(name, sha):
    """Merged pull requests a commit came from (catches the earlier commits of a rebase merge)."""
    return [p for p in api(f"repos/{name}/commits/{sha}/pulls") if p["merged_at"]]


def group(releases, pulls, lookup=lambda sha: []):
    """For each release (oldest first): its pull requests and how many commits came from none."""
    by_merge = {p["merge_commit_sha"]: p for p in pulls if p.get("merge_commit_sha")}
    seen = set()
    result = []
    for r in releases:
        found, direct = [], 0
        for c in r["commits"]:
            if is_release_commit(c):
                continue
            prs = [by_merge[c["sha"]]] if c["sha"] in by_merge else lookup(c["sha"])
            if not prs:
                direct += 1
            for p in prs:
                if p["number"] not in seen:
                    seen.add(p["number"])
                    found.append(p)
        found.sort(key=lambda p: p["number"])
        result.append((r, found, direct))
    return result


def render(grouped):
    lines = [
        "# Releases",
        "",
        "Every release of Siansa and the pull requests in it, newest first. CI updates this page after each",
        "release (`scripts/release_prs.py`); what changed for the app's user is in `CHANGELOG.md`.",
        "",
    ]
    for r, prs, direct in reversed(grouped):
        version = r["tag_name"].removeprefix("v")
        lines += [f"## [{version}]({r['html_url']}) — {r['published_at'][:10]}", ""]
        for p in prs:
            title = p["title"].replace("\n", " ").strip()
            lines.append(f"- [#{p['number']}]({p['html_url']}) {title} (@{p['user']['login']})")
        if direct:
            s = "" if direct == 1 else "s"
            lines.append(f"- {direct} commit{s} pushed straight to main")
        if not prs and not direct:
            lines.append("- No changes since the previous release")
        lines.append("")
    return "\n".join(lines)


def main():
    name = repo()
    grouped = group(releases_with_commits(name), merged_pulls(name), lambda sha: pulls_of_commit(name, sha))
    open(OUT, "w", encoding="utf-8").write(render(grouped))
    print(f"{OUT}: {len(grouped)} releases")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        sys.exit(__doc__)
    main()
