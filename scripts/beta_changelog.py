#!/usr/bin/env python3
"""Keeps every version's changes in the beta testers repository (issue #435).

  python3 scripts/beta_changelog.py

The app's What's new shows only the newest version and links here. After each release, CHANGELOG.md is copied to
echovaultstudio/siansa-beta-testers as CHANGELOG.md, with a heading for testers instead of the notes for
developers. Needs BETA_REPO_TOKEN (fine-grained, owned by echovaultstudio, only the beta testers repo, Contents:
Read and write); without it nothing is sent.
"""
import base64
import json
import os
import subprocess
import sys

BETA_REPO = "echovaultstudio/siansa-beta-testers"
HEADER = ("# Siansa changelog\n\nWhat changed in each version of Siansa, newest first. "
          "In the app, Settings › What's new shows the newest version.\n\n")


def for_testers(changelog):
    """CHANGELOG.md from its first version on, under a heading for testers."""
    start = changelog.find("\n## ")
    body = changelog[start + 1:] if start >= 0 else ""
    return HEADER + body.rstrip("\n") + "\n"


def gh(args, token, body=None):
    result = subprocess.run(["gh", "api", *args] + (["--input", "-"] if body is not None else []),
                            input=None if body is None else json.dumps(body), capture_output=True, text=True,
                            env={**os.environ, "GH_TOKEN": token})
    return result


def main():
    token = os.environ.get("BETA_REPO_TOKEN", "")
    if not token:
        print("::notice::No BETA_REPO_TOKEN; the changelog wasn't copied to the beta testers repo.")
        return
    text = for_testers(open("CHANGELOG.md", encoding="utf-8").read())
    current = gh([f"repos/{BETA_REPO}/contents/CHANGELOG.md"], token)
    sha = json.loads(current.stdout).get("sha") if current.returncode == 0 else None
    if sha and base64.b64decode(json.loads(current.stdout).get("content", "")).decode("utf-8", "replace") == text:
        print("The beta testers changelog is already up to date.")
        return
    body = {"message": "Changelog", "content": base64.b64encode(text.encode()).decode()}
    if sha:
        body["sha"] = sha
    result = gh(["-X", "PUT", f"repos/{BETA_REPO}/contents/CHANGELOG.md"], token, body)
    if result.returncode != 0:
        sys.exit(f"Couldn't update the beta testers changelog: {result.stderr.strip()}")
    print("Copied CHANGELOG.md to the beta testers repo.")


if __name__ == "__main__":
    if len(sys.argv) > 1:
        sys.exit(__doc__)
    main()
