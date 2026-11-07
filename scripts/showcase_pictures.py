#!/usr/bin/env python3
"""The showcase screenshots out of an emulator's log (issue #741): .github/workflows/showcase.yml.

ShowcaseTest (the phone) and TvShowcaseTest (the TV) write each picture into the log in pieces, as lines
"SHOWCASE name part/parts base64". This puts them back together as OUT/name.jpg, says on the run's page what
went wrong (each "Showcase: FAILED" line) and what was taken, and keeps every "Showcase:" line in OUT/NOTES.

Usage: showcase_pictures.py LOGCAT OUT [NOTES]   (NOTES defaults to notes.txt)
"""
import base64
import collections
import os
import re
import sys


def main(logcat: str, out: str, notes_name: str = "notes.txt") -> int:
    os.makedirs(out, exist_ok=True)
    parts, totals = collections.defaultdict(dict), {}
    notes = []
    for line in open(logcat, errors="ignore"):
        m = re.search(r"SHOWCASE (\S+) (\d+)/(\d+) (\S+)", line)
        if m:
            parts[m[1]][int(m[2])] = m[4]
            totals[m[1]] = int(m[3])
        elif (n := re.search(r"System\.out[^:]*: (Showcase: .*)", line)):
            # "I System.out: Showcase: …" (a managed device's log) or "I/System.out( 123): Showcase: …" (adb's).
            notes.append(n[1].strip())
        elif re.search(r" [WE] Siansa/|Siansa/(Books|Library|Audiobookshelf)", line):
            notes.append("app: " + line.strip()[:300])
    for name, d in sorted(parts.items()):
        if len(d) == totals[name]:
            with open(os.path.join(out, f"{name}.jpg"), "wb") as f:
                f.write(base64.b64decode("".join(d[i] for i in sorted(d))))
        else:
            print(f"::warning::{name}: only {len(d)} of {totals[name]} parts")
    for n in [n for n in notes if "FAILED" in n][:9]:
        print("::error::" + n)
    print(f"::notice::{len(parts)} screenshots: {', '.join(sorted(parts))}")
    for n in [n for n in notes if "FAILED" not in n and not n.startswith("app: ")][:9]:
        print("::notice::" + n)
    # All of them, with the pictures: GitHub shows only ten notes of a step.
    with open(os.path.join(out, notes_name), "w") as f:
        f.write("\n".join(notes) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
