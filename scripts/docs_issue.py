#!/usr/bin/env python3
"""Asks for the docs to be brought up to date after a release (the owner, 17 Nov 2026).

  python3 scripts/docs_issue.py 1.2.0 notes/1.2.0.md [--play-held] > body.md

Prints the description of an issue, labelled `claude` by the release, that the owner's hourly scheduled task picks
up like any other: it reads what the release changed and checks `docs/` against it (docs/MAINTAINING.md says which
page holds what), then opens one pull request against main with the pages brought up to date. The pull request is
never merged by the task: the owner reviews it. The issue's title, from :func:`title`, lets the release ask only
once per version.
"""
import argparse
import re
import sys


def title(version):
    return f"Docs: bring them up to date with Siansa {version}"


def changes(notes):
    """The release notes' lines a user would notice in the docs: those under Added and Changed, and Fixed ones too
    (a fix can change what a page says), as they're written, without the headings."""
    out, section = [], None
    for line in notes.splitlines():
        heading = re.match(r"#+\s*(Added|Changed|Fixed|Removed)\b", line.strip(), re.I)
        if heading:
            section = heading.group(1).capitalize()
            continue
        if line.startswith("#"):
            section = None
        elif section and line.strip():
            out.append((section, line.rstrip()))
    return out


def body(version, notes, play_held=False):
    found = changes(notes)
    listed = "\n".join(f"- ({section}) {line.lstrip('-* ').strip()}" for section, line in found) or "- (nothing in its notes)"
    stores = "the F-Droid repo" + ("" if play_held else " and Google Play")
    return f"""Siansa {version} is out on {stores}. Bring the user docs in `docs/` up to date with it.

## What changed in {version}

{listed}

## What to do

1. For each change above, read the pull request it came from (RELEASES.md lists them) and what the app does now.
2. Check the page it belongs to (`docs/MAINTAINING.md`, *Where things go*), and [Settings](docs/settings.md) for any
   setting. Change what's wrong or missing; leave alone what's already right.
3. Also look for pages that still describe things this release changed or removed elsewhere (screens, setting
   names, defaults, steps).
4. One pull request against `main` with `Fixes #<this issue>`, listing the pages changed and why. No changelog note:
   the docs aren't part of the app.
5. **Don't merge it.** The owner reviews it.

Write as `docs/MAINTAINING.md` says: plain, short English, names exactly as the app shows them, nothing private.
"""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("version")
    parser.add_argument("notes", help="the release's notes file")
    parser.add_argument("--play-held", action="store_true", help="Google Play was held back (.play-hold)")
    parser.add_argument("--title", action="store_true", help="print the issue's title instead")
    args = parser.parse_args(argv)
    if args.title:
        print(title(args.version))
        return 0
    with open(args.notes, encoding="utf-8") as f:
        sys.stdout.write(body(args.version, f.read(), args.play_held))
    return 0


if __name__ == "__main__":
    sys.exit(main())
