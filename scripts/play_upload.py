#!/usr/bin/env python3
"""Uploads a release's app bundle to Google Play (issue #557).

  python3 scripts/play_upload.py siansa-0.37.0beta.aab 0.37.0beta notes.md

The bundle goes to the testing track in PLAY_TRACK ("alpha", Play's name for closed testing, unless set otherwise),
as a release named after the version, with the version's notes as "What's new" (plain text, at most 500 characters).
Closed testing still goes through Google's review; the upload just saves doing it by hand.

Needs PLAY_SERVICE_ACCOUNT_JSON: a Google Cloud service account's JSON key, invited in Play Console with
"Release apps to testing tracks" for Siansa only. Without it nothing is sent. The key stays in that secret; this
script never prints it.

A version Play already has (its version code was used) isn't an error: the script says so and stops, so running it
twice for one release is harmless.
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


def upload(aab, version, notes_md, track, key_json):
    tok = token(key_json)
    edit = call("POST", f"{API}/edits", tok, body={})["id"]
    print(f"Upload {aab} ({os.path.getsize(aab) // 1024} KB) for {version} to {track}")
    try:
        with open(aab, "rb") as f:
            bundle = call("POST", f"{UPLOAD}/edits/{edit}/bundles?uploadType=media", tok, data=f.read(), content_type="application/octet-stream")
    except PlayError as e:
        if already_uploaded(str(e)):
            print(f"::notice::Google Play already has {version} (its version code was used); nothing to upload.")
            call("DELETE", f"{API}/edits/{edit}", tok)
            return "already"
        raise
    code = bundle["versionCode"]
    notes = plain_notes(notes_md)
    status = "completed"
    try:
        call("PUT", f"{API}/edits/{edit}/tracks/{track}", tok, body=track_body(track, version, code, notes, status))
        call("POST", f"{API}/edits/{edit}:commit", tok)
    except PlayError as e:
        if not draft_only(str(e)):
            raise
        # An app Play still treats as a draft takes draft releases only: one to roll out by hand in Play Console.
        # Same edit, so the bundle uploaded to it is still there.
        status = "draft"
        call("PUT", f"{API}/edits/{edit}/tracks/{track}", tok, body=track_body(track, version, code, notes, status))
        call("POST", f"{API}/edits/{edit}:commit", tok)
    print(f"::notice::Google Play: {version} (version code {code}) is on the {track} track as {status}."
          + (" Roll it out in Play Console." if status == "draft" else " Closed testing goes through Google's review first."))
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
    track = os.environ.get("PLAY_TRACK", "").strip() or "alpha"
    try:
        upload(aab, version, notes_md, track, key)
    except PlayError as e:
        print(f"::error::Google Play refused the upload: {e}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
