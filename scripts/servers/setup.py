#!/usr/bin/env python3
"""Takes freshly started Navidrome, Emby and Jellyfin through their first-run setup: user max with password sesame,
the test library at /music, and a scan. Waits until each lists every song in library.json (scripts/servers/start.sh).

    python3 scripts/servers/setup.py <library.json>
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

USER, PASSWORD = "max", "sesame"
NAVIDROME, EMBY, JELLYFIN = "http://127.0.0.1:4533", "http://127.0.0.1:8096", "http://127.0.0.1:8097"


def call(method, url, body=None, headers=None, ok_codes=(200, 204)):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode() or "null"
            return r.status, json.loads(text) if text.strip()[:1] in "{[" else text
    except urllib.error.HTTPError as e:
        if e.code in ok_codes:
            return e.code, None
        raise RuntimeError(f"{method} {url} → {e.code}: {e.read().decode()[:300]}") from None


def wait(what, check, seconds=300):
    start = time.time()
    last = None
    while time.time() - start < seconds:
        try:
            got = check()
            if got:
                print(f"{what}: ready after {time.time() - start:.0f}s")
                return got
        except Exception as e:  # noqa: BLE001 - still starting
            last = e
        time.sleep(2)
    raise SystemExit(f"{what}: not ready after {seconds}s ({last})")


def navidrome(songs):
    wait("Navidrome", lambda: call("GET", NAVIDROME + "/ping")[0] == 200)
    call("POST", NAVIDROME + "/auth/createAdmin", {"username": USER, "password": PASSWORD})
    q = urllib.parse.urlencode({"u": USER, "p": PASSWORD, "v": "1.16.1", "c": "ci", "f": "json"})
    call("GET", f"{NAVIDROME}/rest/startScan?{q}")

    def count():
        _, r = call("GET", f"{NAVIDROME}/rest/search3?query=&songCount=500&{q}")
        return len(r["subsonic-response"].get("searchResult3", {}).get("song", [])) >= songs
    wait(f"Navidrome scan ({songs} songs)", count)


def emby_like(name, base, auth_header, scheme):
    device = f'{scheme} Client="Siansa CI", Device="CI", DeviceId="siansa-ci", Version="1"'
    head = {auth_header: device}
    wait(name, lambda: call("GET", base + "/System/Info/Public")[0] == 200)
    # The first-run wizard: language, the first user, remote access, done.
    call("GET", base + "/Startup/Configuration", headers=head)
    call("POST", base + "/Startup/Configuration", {"UICulture": "en-US", "MetadataCountryCode": "US", "PreferredMetadataLanguage": "en"}, head)
    wait(f"{name} first user", lambda: call("GET", base + "/Startup/User", headers=head)[0] == 200)
    call("POST", base + "/Startup/User", {"Name": USER, "Password": PASSWORD}, head)
    try:
        call("POST", base + "/Startup/RemoteAccess", {"EnableRemoteAccess": True, "EnableAutomaticPortMapping": False}, head)
    except RuntimeError as e:  # Emby doesn't have this step
        print(f"{name}: {e}")
    call("POST", base + "/Startup/Complete", headers=head)

    def login():
        _, r = call("POST", base + "/Users/AuthenticateByName", {"Username": USER, "Pw": PASSWORD}, head)
        return r["AccessToken"], r["User"]["Id"]
    token, user = wait(f"{name} login", login)
    head = {auth_header: device + f', Token="{token}"', "X-Emby-Token": token}
    q = urllib.parse.urlencode({"name": "Music", "collectionType": "music", "paths": "/music", "refreshLibrary": "true"})
    call("POST", f"{base}/Library/VirtualFolders?{q}", {"LibraryOptions": {"PathInfos": [{"Path": "/music"}]}}, head)
    return head, user


def emby_count(base, head, user, songs):
    def count():
        q = urllib.parse.urlencode({"IncludeItemTypes": "Audio", "Recursive": "true", "Limit": "0"})
        _, r = call("GET", f"{base}/Users/{user}/Items?{q}", headers=head)
        return r["TotalRecordCount"] >= songs
    return count


def main(library):
    songs = len(json.load(open(library))["songs"])
    navidrome(songs)
    for name, base, header, scheme in (("Emby", EMBY, "X-Emby-Authorization", "Emby"), ("Jellyfin", JELLYFIN, "Authorization", "MediaBrowser")):
        head, user = emby_like(name, base, header, scheme)
        wait(f"{name} scan ({songs} songs)", emby_count(base, head, user, songs))


if __name__ == "__main__":
    main(sys.argv[1])
