"""Whether changed files (one path per line on stdin) need the emulator tests (issue #241).

The emulator runs the app: its code, screens, resources, build and the Navidrome test server. Screen-only
changes need it too, because the spacing checks and the tour run there. Changelog notes, docs, the AudioMuse
plugin and other workflows don't. Exit code 0: run them; 1: skip.
"""

import re
import sys

NEEDED = re.compile(
    r"^(app/|gradle/|scripts/test-server|cast-receiver/)"
    r"|\.gradle\.kts$|^gradle\.properties$|^\.github/workflows/ci\.yml$"
)


def needed(paths):
    return any(NEEDED.search(p.strip()) for p in paths if p.strip())


if __name__ == "__main__":
    sys.exit(0 if needed(sys.stdin.read().splitlines()) else 1)
