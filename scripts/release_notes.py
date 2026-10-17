#!/usr/bin/env python3
"""Changelog notes per change, combined into a release.

Every pull request adds one small file to changelog.d/ (e.g. changelog.d/issue-42.md) with its
"### Fixed" / "### Added" / "### Changed" notes, and leaves CHANGELOG.md and version.txt alone. That way
pull requests never conflict with each other over the changelog or the version number.

  prepare  Used by CI on every build. If changelog.d/ has notes, the next version is version.txt's with
           its last number raised by one; version.txt and CHANGELOG.md are updated in the working copy
           (so the APK carries the new version), notes/<version>.md is written for the release, and
           notes/fragments.txt lists the files used. Prints "version=..." for $GITHUB_OUTPUT.
           Without notes, version.txt's version is used with its existing CHANGELOG.md section.
  apply    Used by the release job: applies the same update to a fresh checkout for the files listed
           in notes/fragments.txt and deletes them, ready to commit.
"""
import datetime
import glob
import os
import re
import sys

ORDER = ["Fixed", "Added", "Changed"]
FRAGMENTS = "changelog.d"


def fragments():
    return sorted(
        f for f in glob.glob(os.path.join(FRAGMENTS, "*.md"))
        if os.path.basename(f).lower() != "readme.md"
    )


def bump(version):
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)(.*)$", version)
    if not m:
        sys.exit(f"version.txt holds '{version}', expected something like 0.0.25beta")
    return f"{m[1]}.{m[2]}.{int(m[3]) + 1}{m[4]}"


def combine(files):
    """One section from several notes files, grouped under Fixed / Added / Changed."""
    groups = {}
    for f in files:
        current = "Changed"
        for line in open(f, encoding="utf-8").read().splitlines():
            heading = re.match(r"^#{2,4}\s+(\w+)", line)
            if heading:
                current = heading[1].capitalize()
                continue
            if line.strip():
                groups.setdefault(current, []).append(line.rstrip())
    out = []
    for name in ORDER + sorted(k for k in groups if k not in ORDER):
        if groups.get(name):
            out += [f"### {name}", *groups[name], ""]
    return "\n".join(out).strip() + "\n"


def write_changelog(version, body):
    text = open("CHANGELOG.md", encoding="utf-8").read()
    section = f"## {version} — {datetime.date.today().isoformat()}\n\n{body}\n"
    first = text.find("\n## ")
    if first < 0:
        text = text.rstrip() + "\n\n" + section
    else:
        text = text[: first + 1] + section + text[first + 1 :]
    open("CHANGELOG.md", "w", encoding="utf-8").write(text)


def existing_section(version):
    text = open("CHANGELOG.md", encoding="utf-8").read()
    m = re.search(rf"^## {re.escape(version)}\b[^\n]*\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    return m[1].strip() + "\n" if m else None


def prepare():
    os.makedirs("notes", exist_ok=True)
    current = open("version.txt").read().strip()
    files = fragments()
    if files:
        version = bump(current)
        body = combine(files)
        open("version.txt", "w").write(version + "\n")
        write_changelog(version, body)
    else:
        version = current
        body = existing_section(version) or ""
    open(f"notes/{version}.md", "w", encoding="utf-8").write(body)
    open("notes/fragments.txt", "w").write("\n".join(files) + ("\n" if files else ""))
    open("notes/version.txt", "w").write(version + "\n")
    print(f"version={version}")
    print(f"notes={'true' if body.strip() else 'false'}")


def apply(listing):
    files = [l.strip() for l in open(listing) if l.strip()]
    files = [f for f in files if os.path.exists(f)]
    if not files:
        print("No changelog notes to record.")
        return
    # The version the APK was built with, so the changelog and the release always match.
    version = open("notes/version.txt").read().strip()
    write_changelog(version, combine(files))
    open("version.txt", "w").write(version + "\n")
    for f in files:
        os.remove(f)
    print(f"version={version}")


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "prepare":
        prepare()
    elif len(sys.argv) >= 3 and sys.argv[1] == "apply":
        apply(sys.argv[2])
    else:
        sys.exit(__doc__)
