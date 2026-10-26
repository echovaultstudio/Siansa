"""The slowest steps of a Gradle build, from its --profile report, as notes on the CI run's summary.

Usage: python3 scripts/build_timing.py build/reports/profile/profile-*.html
"""
import re
import sys

ROW = re.compile(r'<td(?: class="[^"]*")?>(:[^<]+:[^<]+)</td>\s*<td class="numeric">([^<]+)</td>\s*<td>([^<]*)</td>')
PART = re.compile(r"(\d+(?:\.\d+)?)([hms])")


def seconds(text):
    """'1m2.50s' → 62.5; '0.003s' → 0.003; '1h0m1.00s' → 3601."""
    scale = {"h": 3600, "m": 60, "s": 1}
    return sum(float(n) * scale[u] for n, u in PART.findall(text))


def slowest(html, count=10):
    """The [count] slowest tasks as (path, seconds, result), slowest first. A task listed twice keeps its longest."""
    tasks = {}
    for path, took, result in ROW.findall(html):
        s = seconds(took)
        if s >= tasks.get(path, (0, ""))[0]:
            tasks[path] = (s, result.strip())
    ranked = sorted(tasks.items(), key=lambda t: -t[1][0])[:count]
    return [(path, s, result) for path, (s, result) in ranked]


def total(html):
    """The build's whole time from the profile's summary, or None."""
    m = re.search(r"<td>Total Build Time</td>\s*<td class=\"numeric\">([^<]+)</td>", html)
    return seconds(m.group(1)) if m else None


def main(paths):
    for path in paths:
        with open(path, encoding="utf-8") as f:
            html = f.read()
        whole = total(html)
        lines = [f"{p} {s:.0f} s{(' (' + r + ')') if r else ''}" for p, s, r in slowest(html)]
        head = f"Build took {whole:.0f} s. " if whole else ""
        print(f"::notice title=Build timing::{head}Slowest: " + "; ".join(lines))


if __name__ == "__main__":
    main(sys.argv[1:])
