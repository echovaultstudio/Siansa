#!/usr/bin/env python3
"""Uploads a release's app bundle to Google Play (issue #557).

  python3 scripts/play_upload.py siansa-0.37.0beta.aab 0.37.0beta notes.md
  python3 scripts/play_upload.py --tv siansa-tv-0.37.0beta.aab 0.37.0beta notes.md

The bundle goes to the testing tracks in PLAY_TRACK, comma-separated; unset, both closed testing ("alpha", Play's
name for it) and internal testing. It's one release on each, named after the version, with the version's notes as
"What's new" (plain text, at most 500 characters). Internal testing has it at once; closed testing goes through
Google's review.

Needs PLAY_SERVICE_ACCOUNT_JSON: a Google Cloud service account's JSON key, invited in Play Console with
"Release apps to testing tracks" for Siansa only. Without it nothing is sent. The key stays in that secret; this
script never prints it.

The TV app (--tv, issue #988) is the same app on Play, app.siansa, on Play's own Android TV tracks: one store page,
and Play gives TVs the TV bundle and phones the phone's. Its tracks are PLAY_TV_TRACK, comma-separated; unset, the TV's
internal testing ("tv:internal"). Play Console must have Android TV turned on first (Advanced settings › Form factors), and a
closed test for TVs is a track of its own there, named "tv:" and its name (e.g. "tv:alpha").

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
# The TV app's tracks (issue #988): Play's Android TV form factor, "tv:" and the track. Its internal testing is
# "tv:internal": Play refused "tv:qa" ("Track not found", 1.1.0), though "qa" is an old name for internal testing.
DEFAULT_TV_TRACKS = ("tv:internal",)


def track_name(track):
    """A track in words: "closed testing", or "TV internal testing" for "tv:internal"."""
    if track.startswith("tv:"):
        rest = track[3:]
        return "TV " + {"internal": "internal testing", "qa": "internal testing", "beta": "open testing",
                        "production": "production"}.get(rest, f"closed testing ({rest})")
    return TRACK_NAMES.get(track, track)


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
        raise PlayError(f"{step_of(method, url)}: {play_reason(e.code, e.read().decode(errors='replace'))}") from None


def play_reason(code, body):
    """Play's answer on one line: "HTTP 403 PERMISSION_DENIED: …". Its JSON comes over several lines, and the run's
    log hides any line that is also a line of the key's JSON (a lone "{"), which left only "***" (issue #675)."""
    reason = ""
    try:
        error = json.loads(body).get("error") or {}
        reason = " ".join(x for x in (str(error.get("status") or ""), str(error.get("message") or "")) if x).strip()
    except (ValueError, AttributeError):
        pass
    reason = reason or " ".join(body.split())
    return f"HTTP {code}" + (f" {reason}" if reason else "")[:600]


def step_of(method, url):
    """Which call Play refused, in words: the step tells a missing permission from a wrong upload key."""
    path = url.split("/applications/", 1)[-1].split("?", 1)[0]
    if method == "POST" and path.endswith("/edits"):
        return "Starting an edit"
    if "/bundles" in path and method == "POST":
        return "Uploading the bundle"
    if "/bundles" in path:
        return "Looking up the bundles"
    if "/tracks/" in path:
        return f"{'Reading' if method == 'GET' else 'Setting'} the {path.rsplit('/', 1)[-1]} track"
    if path.endswith(":commit"):
        return "Committing the edit"
    return f"{method} {path}"


def tracks_of(setting, tv=False):
    """The tracks a release goes to: PLAY_TRACK as "alpha,internal", each once, in order; both when unset (#557). For
    the TV app ([tv]), PLAY_TV_TRACK, every one a TV track ("tv:" added where it's left out); its internal testing when
    unset."""
    tracks = []
    for t in (setting or "").split(","):
        t = t.strip()
        if tv and t and not t.startswith("tv:"):
            t = "tv:" + t
        if t and t not in tracks:
            tracks.append(t)
    return tracks or list(DEFAULT_TV_TRACKS if tv else DEFAULT_TRACKS)


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
            try:
                call("PUT", f"{API}/edits/{edit}/tracks/{track}", tok, body=track_body(track, version, code, notes, status))
            except PlayError as e:
                if "not found" not in str(e).lower():
                    raise
                # A track Play doesn't have: say which it has, so a wrong name, or Android TV not yet turned on in
                # Play Console (no "tv:" tracks at all), is plain from the run's log.
                have = [t.get("track") for t in call("GET", f"{API}/edits/{edit}/tracks", tok).get("tracks", [])]
                raise PlayError(f"{e} Play's tracks for {PACKAGE}: {', '.join(have) or 'none'}.") from None
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
    print(f"::notice::Google Play: {version} (version code {code}) is on {', '.join(track_name(t) for t in tracks)} as {status}."
          + (" Roll it out in Play Console." if status == "draft" else " Internal testing has it now; closed testing after Google's review."))
    return status


def main(argv):
    key = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not key:
        print("::notice::No Google Play key (PLAY_SERVICE_ACCOUNT_JSON); skipped.")
        return 0
    tv = "--tv" in argv[1:]
    args = [a for a in argv[1:] if a != "--tv"]
    if len(args) != 3:
        print(__doc__)
        return 2
    aab, version, notes_path = args
    with open(notes_path, encoding="utf-8") as f:
        notes_md = f.read()
    tracks = tracks_of(os.environ.get("PLAY_TV_TRACK" if tv else "PLAY_TRACK", ""), tv=tv)
    try:
        upload(aab, version, notes_md, tracks, key)
    except PlayError as e:
        print(f"::error::Google Play refused the upload: {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
