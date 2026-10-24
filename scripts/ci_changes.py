"""What a change needs CI to run (changed files, one path per line on stdin), to keep Actions minutes down.

  git diff --name-only BASE HEAD | python3 scripts/ci_changes.py   prints app=…, emulator=…, plugin=…

- app: the Android build, Lint and unit tests. Not for changes that can't reach the app: the website and server
  kit (deploy/), docs, Markdown, changelog notes, the AudioMuse plugin, other workflows and the release scripts
  (their own tests run in the quick checks every time).
- emulator: the emulator tests (scripts/emulator_needed.py, issue #241).
- plugin: the AudioMuse plugin's tests.
"""

import re
import sys

from emulator_needed import needed as emulator_needed

NOT_APP = re.compile(
    r"^(deploy/|docs/|audiomuse-plugin/|changelog\.d/|scripts/(?!test-server)|\.github/workflows/(?!ci\.yml$))"
    r"|^[^/]+\.(md|txt)$|^\.gitleaks\.toml$|^LICENSE"
)
PLUGIN = re.compile(r"^audiomuse-plugin/|^\.github/workflows/ci\.yml$")


def _paths(paths):
    return [p.strip() for p in paths if p.strip()]


def app_needed(paths):
    """True when any file could change the app (or the build). version.txt is the app's version, so it counts."""
    return any(not NOT_APP.search(p) or p == "version.txt" for p in _paths(paths))


def plugin_needed(paths):
    return any(PLUGIN.search(p) for p in _paths(paths))


def outputs(paths):
    return {
        "app": app_needed(paths),
        "emulator": emulator_needed(paths),
        "plugin": plugin_needed(paths),
    }


if __name__ == "__main__":
    for key, value in outputs(sys.stdin.read().splitlines()).items():
        print(f"{key}={'true' if value else 'false'}")
