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
            raw = r.read()
            if not r.headers.get("Content-Type", "").startswith(("application/json", "text/")):
                return r.status, raw
            text = raw.decode("utf-8-sig") or "null"
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
    # The library as the server's own web page makes it: with each kind of item's default metadata and picture
    # readers switched on (without them Emby read no cover.jpg or folder.jpg at all).
    _, available = call("GET", f"{base}/Libraries/AvailableOptions?LibraryContentType=music&IsNewLibrary=true", headers=head)
    options = {"PathInfos": [{"Path": "/music"}], "EnableRealtimeMonitor": False, "TypeOptions": [
        {"Type": t["Type"],
         "MetadataFetchers": [f["Name"] for f in t.get("MetadataFetchers", []) if f.get("DefaultEnabled")],
         "ImageFetchers": [f["Name"] for f in t.get("ImageFetchers", []) if f.get("DefaultEnabled")],
         "ImageOptions": t.get("DefaultImageOptions", [])}
        for t in available.get("TypeOptions", [])]}
    print(f"{name} library options: " + "; ".join(f"{t['Type']}: images {t['ImageFetchers']}" for t in options["TypeOptions"]))
    q = urllib.parse.urlencode({"name": "Music", "collectionType": "music", "paths": "/music", "refreshLibrary": "true"})
    call("POST", f"{base}/Library/VirtualFolders?{q}", {"LibraryOptions": options}, head)
    return head, user


def emby_count(base, head, user, songs):
    def count():
        q = urllib.parse.urlencode({"IncludeItemTypes": "Audio", "Recursive": "true", "Limit": "0"})
        _, r = call("GET", f"{base}/Users/{user}/Items?{q}", headers=head)
        return r["TotalRecordCount"] >= songs
    return count


def probe_newest():
    """For a failed run on new versions: Jellyfin's version answer as it is, and which way Emby renames a playlist."""
    for name, base in (("Emby", EMBY), ("Jellyfin", JELLYFIN)):
        try:
            print(f"{name} /System/Info/Public: {str(call('GET', base + '/System/Info/Public')[1])[:400]}")
        except Exception as e:  # noqa: BLE001
            print(f"{name} /System/Info/Public: {e}")
    device = 'Emby Client="Siansa CI", Device="CI", DeviceId="siansa-ci", Version="1"'
    _, r = call("POST", EMBY + "/Users/AuthenticateByName", {"Username": USER, "Pw": PASSWORD}, {"X-Emby-Authorization": device})
    head = {"X-Emby-Token": r["AccessToken"]}
    user = r["User"]["Id"]
    q = urllib.parse.urlencode({"IncludeItemTypes": "Audio", "Recursive": "true", "Limit": "1"})
    song = call("GET", f"{EMBY}/Users/{user}/Items?{q}", headers=head)[1]["Items"][0]["Id"]
    made = call("POST", f"{EMBY}/Playlists?" + urllib.parse.urlencode({"Name": "Probe", "Ids": song, "UserId": user, "MediaType": "Audio"}), headers=head)[1]
    pid = made["Id"]
    def name_now():
        return call("GET", f"{EMBY}/Users/{user}/Items/{pid}", headers=head)[1].get("Name")
    item = call("GET", f"{EMBY}/Users/{user}/Items/{pid}", headers=head)[1]
    tries = [
        ("POST /Items/{id} with the whole item", lambda n: call("POST", f"{EMBY}/Items/{pid}", {**item, "Name": n}, head)),
        ("POST /Items/{id} with the item and Name locked", lambda n: call("POST", f"{EMBY}/Items/{pid}", {**item, "Name": n, "LockedFields": ["Name"], "LockData": False}, head)),
        ("POST /Playlists/{id} {Name}", lambda n: call("POST", f"{EMBY}/Playlists/{pid}", {"Name": n}, head)),
        ("POST /Items/{id}?Name=", lambda n: call("POST", f"{EMBY}/Items/{pid}?" + urllib.parse.urlencode({"Name": n}), {}, head)),
    ]
    for i, (how, do) in enumerate(tries):
        want = f"Probe {i}"
        try:
            code = do(want)[0]
        except Exception as e:  # noqa: BLE001
            print(f"Emby rename by {how}: {e}")
            continue
        time.sleep(3)
        print(f"Emby rename by {how}: {code}, name now {name_now()!r} (wanted {want!r})")


def describe():
    """What Emby and Jellyfin hold for the album with a cover and the song with lyrics: for reading a failed run."""
    for name, base, header, scheme in (("Emby", EMBY, "X-Emby-Authorization", "Emby"), ("Jellyfin", JELLYFIN, "Authorization", "MediaBrowser")):
        device = f'{scheme} Client="Siansa CI", Device="CI", DeviceId="siansa-ci", Version="1"'
        _, r = call("POST", base + "/Users/AuthenticateByName", {"Username": USER, "Pw": PASSWORD}, {header: device})
        head = {"X-Emby-Token": r["AccessToken"]}
        user = r["User"]["Id"]
        for kind, term in (("MusicAlbum", ""), ("Audio", "Second Glow")):
            q = urllib.parse.urlencode({"IncludeItemTypes": kind, "Recursive": "true", **({"SearchTerm": term} if term else {}),
                                        "Fields": "MediaSources,MediaStreams,Path,ImageTags"})
            _, found = call("GET", f"{base}/Users/{user}/Items?{q}", headers=head)
            for item in found.get("Items", []):
                streams = [(s.get("Type"), s.get("Codec"), s.get("IsExternal"), s.get("Path")) for src in item.get("MediaSources") or [] for s in src.get("MediaStreams") or []]
                print(f"{name} {kind} {item.get('Name')}: id {item.get('Id')} images {item.get('ImageTags')} "
                      f"album image {item.get('AlbumPrimaryImageTag')} streams {streams or item.get('MediaStreams')}")
                if kind == "MusicAlbum":
                    st, _ = call("GET", f"{base}/Items/{item['Id']}/Images/Primary?maxWidth=300", headers=head, ok_codes=(404,))
                    print(f"{name} album picture: {st}")
                for src in item.get("MediaSources") or []:
                    for st in src.get("MediaStreams") or []:
                        if st.get("Type") in ("Subtitle", "Lyrics"):
                            for route in ("Items", "Videos", "Audio"):
                                for fmt in ("lrc", "txt", "srt", "vtt", "js"):
                                    url = f"{base}/{route}/{item['Id']}/{src['Id']}/Subtitles/{st.get('Index')}/Stream.{fmt}"
                                    try:
                                        req = urllib.request.Request(url, headers=head)
                                        with urllib.request.urlopen(req, timeout=30) as r:
                                            body = r.read()
                                            print(f"{name} lyrics {route} .{fmt}: {r.status} {r.headers.get('Content-Type')} {len(body)} bytes {body[:80]!r}")
                                    except Exception as e:  # noqa: BLE001
                                        print(f"{name} lyrics {route} .{fmt}: {e}")


def refresh_emby_pictures():
    """Asks Emby to refresh every album's pictures, then shows what it has: is it only slow to read them?"""
    device = 'Emby Client="Siansa CI", Device="CI", DeviceId="siansa-ci", Version="1"'
    _, r = call("POST", EMBY + "/Users/AuthenticateByName", {"Username": USER, "Pw": PASSWORD}, {"X-Emby-Authorization": device})
    head = {"X-Emby-Token": r["AccessToken"]}
    user = r["User"]["Id"]
    q = urllib.parse.urlencode({"IncludeItemTypes": "MusicAlbum,Audio", "Recursive": "true", "Fields": "ImageTags"})
    _, found = call("GET", f"{EMBY}/Users/{user}/Items?{q}", headers=head)
    for item in found["Items"]:
        print(f"Emby before: {item['Type']} {item['Name']} images {item.get('ImageTags')} album image {item.get('AlbumPrimaryImageTag')}")
        if item["Type"] == "MusicAlbum":
            rq = urllib.parse.urlencode({"Recursive": "true", "ImageRefreshMode": "FullRefresh", "MetadataRefreshMode": "FullRefresh", "ReplaceAllImages": "true"})
            call("POST", f"{EMBY}/Items/{item['Id']}/Refresh?{rq}", headers=head)
    time.sleep(20)
    _, found = call("GET", f"{EMBY}/Users/{user}/Items?{q}", headers=head)
    for item in found["Items"]:
        print(f"Emby after refresh: {item['Type']} {item['Name']} images {item.get('ImageTags')} album image {item.get('AlbumPrimaryImageTag')}")
    # Emby keeps an item for each folder on disk too: is cover.jpg on the folder rather than the album?
    q = urllib.parse.urlencode({"IncludeItemTypes": "Folder", "Recursive": "true", "Fields": "Path,ImageTags"})
    _, folders = call("GET", f"{EMBY}/Users/{user}/Items?{q}", headers=head)
    for item in folders["Items"]:
        print(f"Emby folder: {item.get('Path')} id {item['Id']} images {item.get('ImageTags')}")
    for item in found["Items"]:
        if item["Type"] == "MusicAlbum":
            _, images = call("GET", f"{EMBY}/Items/{item['Id']}/Images", headers=head)
            _, full = call("GET", f"{EMBY}/Users/{user}/Items/{item['Id']}?Fields=Path,ParentId", headers=head)
            print(f"Emby album {item['Name']}: path {full.get('Path')} parent {full.get('ParentId')} images list {images}")
    _, tasks = call("GET", f"{EMBY}/ScheduledTasks", headers=head)
    for t in tasks:
        print(f"Emby task: {t.get('Name')} {t.get('State')} last {(t.get('LastExecutionResult') or {}).get('Status')}")


def versions():
    """The versions running, as the servers say them: for the run's summary (versions.txt next to the library)."""
    found = []
    q = urllib.parse.urlencode({"u": USER, "p": PASSWORD, "v": "1.16.1", "c": "ci", "f": "json"})
    try:
        found.append("Navidrome " + call("GET", f"{NAVIDROME}/rest/ping?{q}")[1]["subsonic-response"].get("serverVersion", "?"))
    except Exception as e:  # noqa: BLE001
        found.append(f"Navidrome ? ({e})")
    for name, base in (("Emby", EMBY), ("Jellyfin", JELLYFIN)):
        try:
            info = call("GET", base + "/System/Info/Public")[1]
            found.append(f"{name} " + str(info.get("Version") or info.get("version") or "? (" + ",".join(list(info)[:8]) + ")"))
        except Exception as e:  # noqa: BLE001
            found.append(f"{name} ? ({e})")
    return ", ".join(found)


def main(library):
    songs = len(json.load(open(library))["songs"])
    import os
    with open(os.path.join(os.path.dirname(os.path.dirname(library)), "versions.txt"), "w") as f:
        # Written before the setup, so a server whose setup fails still shows which version it was.
        wait("servers answering", lambda: all(call("GET", u)[0] == 200 for u in (NAVIDROME + "/ping", EMBY + "/System/Info/Public", JELLYFIN + "/System/Info/Public")))
        f.write(versions())
    print(open(os.path.join(os.path.dirname(os.path.dirname(library)), "versions.txt")).read())
    navidrome(songs)
    for name, base, header, scheme in (("Emby", EMBY, "X-Emby-Authorization", "Emby"), ("Jellyfin", JELLYFIN, "Authorization", "MediaBrowser")):
        head, user = emby_like(name, base, header, scheme)
        wait(f"{name} scan ({songs} songs)", emby_count(base, head, user, songs))


if __name__ == "__main__":
    if sys.argv[1] == "--describe":
        try:
            probe_newest()
        except Exception as e:  # noqa: BLE001
            print(f"probe: {e}")
        describe()
        refresh_emby_pictures()
    else:
        main(sys.argv[1])
