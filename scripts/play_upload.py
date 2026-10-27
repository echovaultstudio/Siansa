#!/usr/bin/env python3
"""Uploads a release's app bundle to Google Play (issue #557).

  python3 scripts/play_upload.py siansa-0.37.0beta.aab 0.37.0beta notes.md

The bundle goes to the testing tracks in PLAY_TRACK, comma-separated; unset, both closed testing ("alpha", Play's
name for it) and internal testing. It's one release on each, named after the version, with the version's notes as
"What's new" (plain text, at most 500 characters). Internal testing has it at once; closed testing goes through
Google's review.

Needs PLAY_SERVICE_ACCOUNT_JSON: a Google Cloud service account's JSON key, invited in Play Console with
"Release apps to testing tracks" for Siansa only. Without it nothing is sent. The key stays in that secret; this
script never prints it.

A bundle Play already has isn't an error: it's found by its SHA-256 and put on the tracks, so running this again
for a release adds it to a track it wasn't on, and otherwise changes nothing.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

PACKAGE = "app.siansa"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/" + PACKAGE
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
NOTES_MAX = 500
# Closed testing ("alpha" in the API) and internal testing, both with every release (#557).
DEFAULT_TRACKS = ("alpha", "internal")
TRACK_NAMES = {"alpha": "closed testing", "internal": "internal testing", "beta": "open testing", "production": "production"}


def plain_notes(markdown):
    """A version's notes as Play's "What's new": headings and bold gone, list items as "• ", at most 500 characters."""
    lines = []
    for line in markdown.splitlines():
        line = line.rstrip()
        if not line.strip() or line.lstrip().startswith("<!--"):
            continue
        if line.startswith("#"):
            continue
        line = re.sub(r"\*\*(.+?)\*\*", r"\1", line)
        line = re.sub(r"\[(.+?)\]\([^)]*\)", r"\1", line)
        line = re.sub(r"`([^`]*)`", r"\1", line)
        stripped = line.lstrip()
        if stripped.startswith("- ") or stripped.startswith("* "):
            lines.append("• " + stripped[2:].strip())
        elif lines:
            lines[-1] += " " + stripped
        else:
            lines.append(stripped)
    text = "\n".join(lines)
    if len(text) > NOTES_MAX:
        text = text[: NOTES_MAX - 1].rsplit(" ", 1)[0].rstrip(" ,.;:") + "…"
    return text


def track_body(track, version, version_code, notes, status="completed"):
    """The release on [track]: the one bundle, named after the version, with its notes."""
    release = {"name": version, "versionCodes": [str(version_code)], "status": status}
    if notes:
        release["releaseNotes"] = [{"language": "en-US", "text": notes}]
    return {"track": track, "releases": [release]}


def already_uploaded(error_text):
    """Whether Play refused the bundle because it has that version code already (the release was uploaded before)."""
    t = error_text.lower()
    return "version code" in t and ("already been used" in t or "already used" in t)


def draft_only(error_text):
    """Whether Play only takes draft releases, as for an app that was never published."""
    return "draft" in error_text.lower() and "status" in error_text.lower()


def token(key_json):
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_info(json.loads(key_json), scopes=[SCOPE])
    creds.refresh(Request())
    return creds.token


class PlayError(Exception):
    pass


def call(method, url, tok, body=None, data=None, content_type="application/json"):
    payload = data if data is not None else (json.dumps(body).encode() if body is not None else b"")
    req = urllib.request.Request(url, data=payload if method != "GET" else None, method=method)
    req.add_header("Authorization", "Bearer " + tok)
    req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            text = r.read().decode() or "{}"
            return json.loads(text)
    except urllib.error.HTTPError as e:
        raise PlayError(f"HTTP {e.code}: {e.read().decode(errors='replace')[:800]}") from None


def tracks_of(setting):
    """The tracks a release goes to: PLAY_TRACK as "alpha,internal", each once, in order; both when unset (#557)."""
    tracks = []
    for t in (setting or "").split(","):
        t = t.strip()
        if t and t not in tracks:
            tracks.append(t)
    return tracks or list(DEFAULT_TRACKS)


def sha256_of(path):
    import hashlib

    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def code_of_uploaded(bundles, sha256):
    """The version code of the bundle Play already has with this file's SHA-256, or None."""
    for b in bundles:
        if b.get("sha256", "").lower() == sha256.lower():
            return b.get("versionCode")
    return None


def track_has(track, code):
    """Whether a track (as Play's API gives it) has a release with this version code."""
    return any(str(code) in (r.get("versionCodes") or []) for r in track.get("releases", []))


def upload(aab, version, notes_md, tracks, key_json):
    """Uploads [aab] once and puts it on every one of [tracks] in one edit. A bundle Play already has (uploaded for
    an earlier run) is found by its SHA-256 and put on the tracks it isn't on yet."""
    tok = token(key_json)
    edit = call("POST", f"{API}/edits", tok, body={})["id"]
    print(f"Upload {aab} ({os.path.getsize(aab) // 1024} KB) for {version} to {', '.join(tracks)}")
    try:
        with open(aab, "rb") as f:
            code = call("POST", f"{UPLOAD}/edits/{edit}/bundles?uploadType=media", tok, data=f.read(), content_type="application/octet-stream")["versionCode"]
    except PlayError as e:
        if not already_uploaded(str(e)):
            raise
        code = code_of_uploaded(call("GET", f"{API}/edits/{edit}/bundles", tok).get("bundles", []), sha256_of(aab))
        if code is None:
            print(f"::notice::Google Play already has {version}'s version code, from another file; nothing to do.")
            call("DELETE", f"{API}/edits/{edit}", tok)
            return "already"
        # Only the tracks without it: one that has it (perhaps still in review) is left as it is.
        tracks = [t for t in tracks if not track_has(call("GET", f"{API}/edits/{edit}/tracks/{t}", tok), code)]
        if not tracks:
            print(f"::notice::Google Play already has {version} (version code {code}) on every track; nothing to do.")
            call("DELETE", f"{API}/edits/{edit}", tok)
            return "already"
        print(f"Google Play already has {version} (version code {code}); adding it to {', '.join(tracks)}.")
    notes = plain_notes(notes_md)

    def put(status):
        for track in tracks:
            call("PUT", f"{API}/edits/{edit}/tracks/{track}", tok, body=track_body(track, version, code, notes, status))
        call("POST", f"{API}/edits/{edit}:commit", tok)

    status = "completed"
    try:
        put(status)
    except PlayError as e:
        if not draft_only(str(e)):
            raise
        # An app Play still treats as a draft takes draft releases only: one to roll out by hand in Play Console.
        # Same edit, so the bundle uploaded to it is still there.
        status = "draft"
        put(status)
    print(f"::notice::Google Play: {version} (version code {code}) is on {', '.join(TRACK_NAMES.get(t, t) for t in tracks)} as {status}."
          + (" Roll it out in Play Console." if status == "draft" else " Internal testing has it now; closed testing after Google's review."))
    return status


def main(argv):
    key = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not key:
        print("::notice::No Google Play key (PLAY_SERVICE_ACCOUNT_JSON); skipped.")
        return 0
    if len(argv) != 4:
        print(__doc__)
        return 2
    aab, version, notes_path = argv[1:]
    with open(notes_path, encoding="utf-8") as f:
        notes_md = f.read()
    tracks = tracks_of(os.environ.get("PLAY_TRACK", ""))
    try:
        upload(aab, version, notes_md, tracks, key)
    except PlayError as e:
        print(f"::error::Google Play refused the upload: {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
