#!/usr/bin/env python3
"""Changelog notes per change, combined into a release.

Every pull request adds one small file to changelog.d/ (e.g. changelog.d/issue-42.md) with its
"### Fixed" / "### Added" / "### Changed" notes, and leaves CHANGELOG.md and version.txt alone. That way
pull requests never conflict with each other over the changelog or the version number.

  prepare  Used by CI on every build. If changelog.d/ has notes, the next version is version.txt's
           raised by the size of the change (see level_of: fixes 0.1.3 → 0.1.4, new features 0.1.3 →
           0.2.0, 1.0 only when a note asks for it); version.txt and CHANGELOG.md are updated in the working copy
           (so the APK carries the new version), notes/<version>.md is written for the release, and
           notes/fragments.txt lists the files used. Prints "version=..." for $GITHUB_OUTPUT.
           Without notes, version.txt's version is used with its existing CHANGELOG.md section.
  apply    Used by the release job: applies the same update to a fresh checkout for the files listed
           in notes/fragments.txt and deletes them, ready to commit.
  dev      Used by the Siansa Dev and Siansa TV Dev builds: prints their release notes' changelog. With
           --since <commit> (the previous build), only the notes added after it, under "New in this build", and
           one line saying how many earlier ones wait for the next release (the owner, 12 Nov: the whole list was
           too much to read). Without it (a release's first build), every note waiting in changelog.d/ that main
           hasn't released yet, newest first. Needs the history of both branches (fetch-depth: 0).
"""
import datetime
import glob
import os
import re
import subprocess
import sys

ORDER = ["Fixed", "Added", "Changed"]
FRAGMENTS = "changelog.d"


def fragments():
    return sorted(
        f for f in glob.glob(os.path.join(FRAGMENTS, "*.md"))
        if os.path.basename(f).lower() != "readme.md"
    )


LEVELS = ["patch", "minor", "major"]


def bump(version, level="patch"):
    """0.1.3 → 0.1.4 (patch: fixes and small changes), 0.2.0 (minor: new features), 1.0.0 (major).
    Anything after the numbers (e.g. "beta") is kept."""
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)(.*)$", version)
    if not m:
        sys.exit(f"version.txt holds '{version}', expected something like 0.1.0beta")
    major, minor, patch, rest = int(m[1]), int(m[2]), int(m[3]), m[4]
    if level == "major":
        return f"{major + 1}.0.0{rest}"
    if level == "minor":
        return f"{major}.{minor + 1}.0{rest}"
    return f"{major}.{minor}.{patch + 1}{rest}"


RELEASE_MARK = re.compile(r"^\s*<!--\s*release:\s*(patch|minor|major|\d+\.\d+\.\d+)\s*-->\s*$", re.I)


def level_of(files):
    """How big the release is: major only when a note asks for it (<!-- release: major -->), minor when
    any note adds something (### Added) or asks for minor, otherwise patch."""
    level = "patch"
    for f in files:
        text = open(f, encoding="utf-8").read()
        asked = [RELEASE_MARK.match(l)[1].lower() for l in text.splitlines() if RELEASE_MARK.match(l)]
        asked = [a for a in asked if a in LEVELS]  # a note that names the version itself: see named()
        found = asked + (["minor"] if re.search(r"^#{2,4}\s+Added\b", text, re.M | re.I) else [])
        for l in found:
            if LEVELS.index(l) > LEVELS.index(level):
                level = l
    return level


def named(files, current):
    """The version a note names itself (<!-- release: 1.5.0 -->, the owner's choice of number), when it's above
    [current]; the highest when several do. None when no note names one."""
    def parts(v):
        m = re.match(r"^(\d+)\.(\d+)\.(\d+)", v)
        return tuple(int(x) for x in m.groups()) if m else None
    found = []
    for f in files:
        for line in open(f, encoding="utf-8").read().splitlines():
            m = RELEASE_MARK.match(line)
            if m and parts(m[1]) and parts(current) and parts(m[1]) > parts(current):
                found.append(m[1])
    return max(found, key=parts) if found else None


def combine(files):
    """One section from several notes files, grouped under Fixed / Added / Changed."""
    groups = {}
    for f in files:
        current = "Changed"
        for line in open(f, encoding="utf-8").read().splitlines():
            if RELEASE_MARK.match(line):
                continue
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
        version = named(files, current) or bump(current, level_of(files))
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


def git(*args):
    """git's answer, or None when it fails (an unknown commit, no history)."""
    done = subprocess.run(["git", *args], capture_output=True, text=True)
    return done.stdout.strip() if done.returncode == 0 else None


def added_by(path):
    """The commit that added a note and when (a Unix time), from the history of what's checked out."""
    line = git("log", "--diff-filter=A", "--format=%H %ct", "-1", "--", path)
    if not line:
        return None, 0
    sha, when = line.split()
    return sha, int(when)


def released_on(path, main):
    """Whether [main] has released a note: a release takes its notes out of changelog.d/ on main."""
    return bool(git("log", main, "--diff-filter=D", "--format=%H", "-1", "--", path))


def note_lines(path):
    """A note's lines, each marked with its kind: "- **Fixed** · …"."""
    out, kind = [], "Changed"
    for line in open(path, encoding="utf-8").read().splitlines():
        if RELEASE_MARK.match(line) or not line.strip():
            continue
        heading = re.match(r"^#{2,4}\s+(\w+)", line)
        if heading:
            kind = heading[1].capitalize()
            continue
        bullet = re.match(r"^(\s*)[-*]\s+(.*)$", line)
        if bullet and not bullet[1]:
            out.append(f"- {kind} · {bullet[2]}")
        else:
            out.append(line.rstrip())
    return out


def dev_notes(main="origin/main", since=None, released=None, most=60):
    """The changelog of a dev build. With [since] (the build before it), only the notes added after that build, and
    how many earlier ones wait for the next release. Without it, everything changelog.d/ holds that [main] hasn't
    released, newest first, grouped by the day each note was added. [released] is main's last version, for the
    headings."""
    notes = []
    for path in fragments():
        if released_on(path, main):
            continue  # released on main already, and still here until main is merged into dev
        sha, when = added_by(path)
        new = since is not None and (sha is None or git("merge-base", "--is-ancestor", sha, since) is None)
        notes.append((new, when, path))
    notes.sort(key=lambda n: (not n[0], -n[1], n[2]))
    after = f" since Siansa {released}" if released else ""
    if not notes:
        return f"## What's coming{after}\n\nNothing yet: this build is the same as the last release.\n"
    fresh = [n for n in notes if n[0]]
    if since is not None:
        out = ["## New in this build", ""]
        for _, _, path in fresh:
            out += note_lines(path)
        if not fresh:
            out.append("Nothing new in the changelog since the last build.")
        earlier = len(notes) - len(fresh)
        if earlier:
            out += ["", f"Also in this build, from earlier ones: {earlier} more change{'s' if earlier != 1 else ''}{after} "
                        "([all of them](https://github.com/echovaultstudio/Siansa/tree/dev/changelog.d))."]
        return "\n".join(out).strip() + "\n"
    out = [f"## What's coming{after}", ""]
    day = None
    shown = [n for n in notes if not n[0]][: max(0, most - len(fresh))]
    for _, when, path in shown:
        on = datetime.datetime.fromtimestamp(when, datetime.timezone.utc).strftime("%-d %b %Y") if when else "Not yet committed"
        if on != day:
            out += ([""] if day else []) + [f"### {on}", ""]
            day = on
        out += note_lines(path)
    left = len(notes) - len(fresh) - len(shown)
    if left > 0:
        out += ["", f"…and {left} earlier notes, in changelog.d/ on dev."]
    return "\n".join(out).strip() + "\n"


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "prepare":
        prepare()
    elif len(sys.argv) >= 3 and sys.argv[1] == "apply":
        apply(sys.argv[2])
    elif len(sys.argv) >= 2 and sys.argv[1] == "dev":
        args = dict(zip(sys.argv[2::2], sys.argv[3::2]))
        main = args.get("--main", "origin/main")
        since = args.get("--since") or None
        released = (git("show", f"{main}:version.txt") or "").strip() or None
        sys.stdout.write(dev_notes(main, since, released))
    else:
        sys.exit(__doc__)
