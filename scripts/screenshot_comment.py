"""The pull request comment that shows the screenshot tests' pictures it changes (issue #710).

.github/workflows/screenshots.yml runs it after recording the pictures:

    python3 scripts/screenshot_comment.py <owner/repo> <pull request> <before sha> <after sha>

<before sha> is where the pull request left main, <after sha> the commit with the new pictures. It posts one comment on
the pull request, or updates the one it posted before, with each picture that differs from main: before and after
side by side, then the new and the removed ones. Merging the pull request approves them.
"""
import json
import os
import subprocess
import sys

FOLDER = "app/src/test/screenshots"
MARKER = "<!-- siansa-screenshots -->"
WIDTH = 300
# GitHub refuses comments over 65,536 characters; a few rows fewer leaves room for the text around the table.
LIMIT = 60000
ORDER = {"M": 0, "A": 1, "D": 2}


def changes(diff):
    """(status, path) per picture from `git diff --name-status`: M changed, A new, D removed. A rename counts as one
    removed and one new picture. Changed pictures first, then new, then removed, each by name."""
    found = []
    for line in diff.splitlines():
        parts = line.split("\t")
        status = parts[0][:1]
        if status == "R" and len(parts) == 3:
            found += [("D", parts[1]), ("A", parts[2])]
        elif status in ORDER and len(parts) == 2:
            found.append((status, parts[1]))
    found = [(s, p) for s, p in found if p.endswith(".png")]
    return sorted(found, key=lambda c: (ORDER[c[0]], c[1]))


def image(repo, sha, path):
    return f'<img src="https://raw.githubusercontent.com/{repo}/{sha}/{path}" width="{WIDTH}">'


def name(path):
    return os.path.basename(path)[: -len(".png")]


def body(repo, before, after, found):
    """The comment's Markdown: what changed, how to approve it, and a table of the pictures."""
    counts = {s: sum(1 for c, _ in found if c == s) for s in ORDER}
    head = [MARKER, "### Screenshots this pull request changes", ""]
    if not found:
        return "\n".join(head + [f"No picture differs from main (recorded from {after[:7]})."]) + "\n"
    head += [
        f"{counts['M']} changed, {counts['A']} new, {counts['D']} removed, recorded from {after[:7]}. "
        "**Merging this pull request approves them**: later pull requests are compared with these. "
        "If one looks wrong, say so instead of merging.",
        "",
        "| Picture | Before (main) | After (this pull request) |",
        "|---|---|---|",
    ]
    rows = []
    for status, path in found:
        old = image(repo, before, path) if status != "A" else "new"
        new = image(repo, after, path) if status != "D" else "removed"
        rows.append(f"| `{name(path)}` | {old} | {new} |")
    text = "\n".join(head + rows)
    shown = len(rows)
    while len(text) > LIMIT and shown > 0:
        shown -= 1
        text = "\n".join(head + rows[:shown] + ["", f"…and {len(rows) - shown} more in **Files changed**."])
    return text + "\n"


def gh(*args, data=None):
    return subprocess.run(["gh", "api", *args], input=data, capture_output=True, text=True, check=True).stdout


def main(repo, pr, before, after):
    diff = subprocess.run(
        ["git", "diff", "--name-status", "--find-renames", before, after, "--", FOLDER],
        capture_output=True, text=True, check=True,
    ).stdout
    found = changes(diff)
    text = body(repo, before, after, found)
    payload = json.dumps({"body": text})
    ids = gh(f"repos/{repo}/issues/{pr}/comments", "--paginate", "--jq",
             f'.[] | select(.body | startswith("{MARKER}")) | .id').split()
    if ids:
        gh("-X", "PATCH", f"repos/{repo}/issues/comments/{ids[0]}", "--input", "-", data=payload)
    else:
        gh("-X", "POST", f"repos/{repo}/issues/{pr}/comments", "--input", "-", data=payload)
    print(f"{len(found)} pictures differ from main; comment {'updated' if ids else 'posted'}.")


if __name__ == "__main__":
    main(*sys.argv[1:5])
