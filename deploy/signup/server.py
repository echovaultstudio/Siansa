#!/usr/bin/env python3
"""Beta sign-up handler for the Siansa website (issue #410).

Standard library only.
- POST /signup: checks the form from site/index.html against the beta's minimum requirements, saves accepted
  sign-ups (signups.jsonl) and emails the owner two signed links, Approve and Deny.
- /signup/decide: the page behind those links. A link only shows a page; the decision is made by a button
  (POST), so mail scanners that open links can't approve anyone.
- Approve: F-Droid testers get the beta's one private repo link in a welcome email; Play testers get a welcome
  email and the owner adds them to the Play Console tester list. Deny sends a polite no. The link is one secret
  code (repo_code, made by scripts/repo-link, which also puts it in the F-Droid index); `rotate` makes a new one
  and emails it to everyone approved.
- Places: BETA_LIMIT (12) testers at a time, Play and F-Droid together. When the beta is full, new sign-ups go on a waiting list and are
  approved automatically, oldest first, when a place frees up (a deny or `revoke`).
- GET /signup/count: places taken, for the home page.
- POST /report (issue #414): a problem report from the app. The body is the diagnostics ZIP, the X-Report header
  is base64 JSON with what happened, what was expected and the app and phone details. The ZIP is kept privately
  in REPORTS_DIR (<id>.zip, readable by the owner only); a public issue with the words, the details and the id is
  opened in the beta testers repo with GITHUB_TOKEN (Issues: write on that repo only). No link to the log is public.
- GET /auth: Caddy asks this before serving /fdroid/t/<code>/repo (forward_auth); 200 for the current code, else 404.
No IP addresses are stored, no cookies, no third parties. Caddy passes /signup*, and /auth for its own checks.
"""
import base64
import calendar
import fcntl
import hashlib
import hmac
import contextlib
import html
import json
import os
import re
import secrets
import smtplib
import string
import ssl
import sys
import threading
import time
import urllib.error
import urllib.request
import email.utils
from email.message import EmailMessage
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

DATA_FILE = os.environ.get("SIGNUPS_FILE", "/data/signups.jsonl")
DATA_DIR = os.path.dirname(DATA_FILE)
DECISIONS_FILE = os.path.join(DATA_DIR, "decisions.jsonl")
CODE_FILE = os.path.join(DATA_DIR, "repo_code")
LOCK_FILE = os.path.join(DATA_DIR, "state.lock")
SECRET = os.environ.get("SIGNUP_SECRET", "")
PUBLIC_URL = os.environ.get("PUBLIC_URL", "https://siansa.app").rstrip("/")
LINK_DAYS = 14
# Problem reports and ideas are issues in the Siansa repository itself; the install guide is in its docs.
ISSUES_URL = "https://github.com/echovaultstudio/Siansa/issues/new/choose"
BETA_REPO_URL = "https://github.com/echovaultstudio/Siansa/blob/main/docs/testing.md"
FAILS_PER_HOUR = 30
LIMIT = int(os.environ.get("BETA_LIMIT", "12") or 12)
MAX_BODY = 4096
REPORT_MAX_ZIP = 40 * 1024 * 1024
REPORTS_MAX_TOTAL = 2 * 1024 * 1024 * 1024
REPORTS_DIR = os.environ.get("REPORTS_DIR", os.path.join(DATA_DIR, "reports"))
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
BETA_REPO = os.environ.get("ISSUES_REPO") or os.environ.get("BETA_REPO") or "echovaultstudio/Siansa"  # BETA_REPO: the old name
# A private repository that keeps a copy of each report's log, so it can be read next to its issue (with a token
# that can write to that repository only). Empty = logs stay on this server only.
REPORTS_REPO = os.environ.get("REPORTS_REPO", "")
REPORTS_TOKEN = os.environ.get("REPORTS_TOKEN", "") or os.environ.get("GITHUB_TOKEN", "")
REPORTS_PER_HOUR = 5
_reports = {}
EMAIL_RE = re.compile(r"^[^@\s]{1,64}@[^@\s]{1,255}\.[^@\s]{2,63}$")
# Abuse limits: 10 tries per address-hash per hour, and 60 accepted sign-ups per hour in total.
PER_CLIENT = 10
PER_HOUR_TOTAL = 60
_recent = {}  # client hash -> [timestamps]
_accepted = []  # timestamps


def page(title, body, status=200):
    doc = (
        '<!doctype html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        f"<title>{html.escape(title)}</title>"
        '<link rel="icon" href="/favicon.ico" sizes="48x48"><link rel="icon" type="image/png" sizes="32x32" href="/favicon-32.png">'
        '<link rel="stylesheet" href="/style.css"></head><body><main>'
        f"<h1>{html.escape(title)}</h1>{body}"
        '<p><a href="/">Back to the home page</a></p></main></body></html>'
    )
    return status, doc.encode()


def problems_page(problems):
    items = "".join(f"<li>{html.escape(p)}</li>" for p in problems)
    return page("Not quite yet", f"<ul>{items}</ul><p>Go back, change your answers and send it again.</p>", 400)


def check(form, now=None):
    """Return (record, problems). record is None when there are problems."""
    def one(name, limit=200):
        return (form.get(name, [""])[0] or "").strip()[:limit]

    problems = []
    email = one("email", 254).lower()
    if not EMAIL_RE.match(email):
        problems.append("Enter a valid email address.")
    route = one("route", 10)
    if route not in ("play", "fdroid"):
        problems.append("Choose how you want to install Siansa.")
    if one("android") != "yes":
        problems.append("Siansa needs Android 8.0 or newer.")
    server = one("server", 10)
    if server not in ("navidrome", "emby"):
        problems.append("Siansa needs your own Navidrome or Emby server. Come back when you have one.")
    if one("reach") != "yes":
        problems.append("Your phone must be able to reach your server (at home on Wi-Fi is fine).")
    if route == "play" and one("days") != "yes":
        problems.append("Google Play needs testers to stay in the test for 14 days in a row.")
    if one("feedback") != "yes":
        problems.append("Testers are asked to tell us about problems they find.")
    if one("privacy") != "yes":
        problems.append("Please agree to us keeping your email for the beta (see the privacy policy).")
    if problems:
        return None, problems
    return {
        "id": secrets.token_hex(8),
        "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() if now is None else now)),
        "email": email,
        "route": route,
        "server": server,
        "device": one("device", 80),
    }, []


def rate_limited(client, now):
    cutoff = now - 3600
    _accepted[:] = [t for t in _accepted if t > cutoff]
    tries = [t for t in _recent.get(client, []) if t > cutoff]
    tries.append(now)
    _recent[client] = tries
    for k in [k for k, v in _recent.items() if not v or v[-1] <= cutoff]:
        del _recent[k]
    return len(tries) > PER_CLIENT or len(_accepted) >= PER_HOUR_TOTAL


def report_limited(client, now):
    cutoff = now - 3600
    tries = [t for t in _reports.get(client, []) if t > cutoff]
    tries.append(now)
    _reports[client] = tries
    for k in [k for k, v in _reports.items() if not v or v[-1] <= cutoff]:
        del _reports[k]
    return len(tries) > REPORTS_PER_HOUR


def report_fields(header):
    """The words and details from the X-Report header (base64 JSON), cut to size; None if it isn't valid."""
    try:
        raw = base64.urlsafe_b64decode(header + "=" * (-len(header) % 4))
        data = json.loads(raw)
    except Exception:
        return None
    if not isinstance(data, dict):
        return None

    def one(name, limit):
        v = data.get(name, "")
        return v.strip()[:limit] if isinstance(v, str) else ""
    fields = {"happened": one("happened", 1000), "expected": one("expected", 1000), "app": one("app", 40),
              "android": one("android", 20), "device": one("device", 80), "source": one("source", 20),
              "session": one("session", 64)}
    if len(fields["happened"]) < 5:
        return None
    return fields


def quote(text):
    """Text for a markdown quote that can't ping anyone, open a link preview or break out of the quote."""
    text = text.replace("@", "@\u200b").replace("#", "#\u200b")
    return "\n".join("> " + line for line in text.splitlines()) or "> -"


def report_path(rec_id, now=None):
    """Where a report's log goes in REPORTS_REPO, without the extension: reports/<yyyy-mm>/<id>."""
    return f"reports/{time.strftime('%Y-%m', time.gmtime(time.time() if now is None else now))}/{rec_id}"


def issue_text(rec_id, f, now=None):
    title = "Report " + rec_id + ": " + " ".join(f["happened"].split())[:70]
    body = ("**What happened**\n" + quote(f["happened"]) + "\n\n**What was expected**\n" + quote(f["expected"] or "-") +
            "\n\n| | |\n|---|---|\n| Report id | `" + rec_id + "` |\n| App | " + f["app"].replace("|", "/") +
            " |\n| Android | " + f["android"].replace("|", "/") + " |\n| Phone | " + f["device"].replace("|", "/") +
            " |\n| Server type | " + f["source"].replace("|", "/") +
            " |\n\nThe log is kept privately by the Siansa team; quote the report id if you add details." +
            (f"\n\nLog (Siansa team only): https://github.com/{REPORTS_REPO}/blob/main/{report_path(rec_id, now)}.zip"
             if REPORTS_REPO and REPORTS_TOKEN else ""))
    return title.replace("@", "@\u200b").replace("#", "#\u200b"), body


def open_issue(title, body):
    """Opens the issue; returns (number, url) or (None, None) if GitHub is unreachable or no token is set."""
    if not GITHUB_TOKEN:
        return None, None
    req = urllib.request.Request(
        "https://api.github.com/repos/" + BETA_REPO + "/issues", method="POST",
        data=json.dumps({"title": title, "body": body, "labels": ["report"]}).encode(),
        headers={"Authorization": "Bearer " + GITHUB_TOKEN, "Accept": "application/vnd.github+json",
                 "User-Agent": "siansa-beta", "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            out = json.load(r)
        return out.get("number"), out.get("html_url")
    except Exception as e:  # the report is kept either way
        print("report issue failed:", type(e).__name__, flush=True)
        return None, None


def github(method, path, body=None, token=None):
    """One GitHub API call; returns the JSON answer. Raises on failure."""
    req = urllib.request.Request(
        "https://api.github.com/" + path, method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Authorization": "Bearer " + (token or REPORTS_TOKEN), "Accept": "application/vnd.github+json",
                 "User-Agent": "siansa-beta", "Content-Type": "application/json", "X-GitHub-Api-Version": "2022-11-28"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def store_report_privately(rec_id, issue=None, now=None, call=None):
    """Copies the report (its ZIP and details) into REPORTS_REPO as reports/<yyyy-mm>/<id>.zip and .json, in one
    commit on main. True when stored; False when not set up or GitHub refused (the copy here stays either way)."""
    if not (REPORTS_REPO and REPORTS_TOKEN):
        return False
    call = call or github
    base = os.path.join(REPORTS_DIR, rec_id)
    try:
        with open(base + ".json", encoding="utf-8") as f:
            meta = json.load(f)
        meta["issue"] = issue
        path = report_path(rec_id, now)
        repo = "repos/" + REPORTS_REPO
        with open(base + ".zip", "rb") as f:
            zip_blob = call("POST", repo + "/git/blobs", {"content": base64.b64encode(f.read()).decode(), "encoding": "base64"})
        json_blob = call("POST", repo + "/git/blobs", {"content": json.dumps(meta, indent=2, ensure_ascii=False) + "\n", "encoding": "utf-8"})
        for attempt in range(3):  # another report may land at the same moment
            head = call("GET", repo + "/git/ref/heads/main")["object"]["sha"]
            tree = call("GET", repo + "/git/commits/" + head)["tree"]["sha"]
            new_tree = call("POST", repo + "/git/trees", {"base_tree": tree, "tree": [
                {"path": path + ".zip", "mode": "100644", "type": "blob", "sha": zip_blob["sha"]},
                {"path": path + ".json", "mode": "100644", "type": "blob", "sha": json_blob["sha"]}]})
            # The full reference links the commit to the public issue (shown only to people who can see this repo).
            commit = call("POST", repo + "/git/commits", {"message": f"Report {rec_id}" + (f" ({BETA_REPO}#{issue})" if issue else ""),
                                                           "tree": new_tree["sha"], "parents": [head]})
            try:
                call("PATCH", repo + "/git/refs/heads/main", {"sha": commit["sha"]})
                return True
            except urllib.error.HTTPError as e:
                if e.code != 422 or attempt == 2:
                    raise
        return False
    except Exception as e:  # never log the content; GitHub's status and message are safe and say why
        why = type(e).__name__
        if isinstance(e, urllib.error.HTTPError):
            try:
                why = f"HTTP {e.code} {json.load(e).get('message', '')}"
            except Exception:
                why = f"HTTP {e.code}"
        print("report copy failed:", why, flush=True)
        return False


def reports_total():
    try:
        return sum(e.stat().st_size for e in os.scandir(REPORTS_DIR) if e.is_file())
    except OSError:
        return 0


def save_report(stream, length, fields, now=None):
    """Writes the ZIP (private) and returns the report id; None if the body isn't a ZIP of the allowed size."""
    if length <= 4 or length > REPORT_MAX_ZIP:
        return None
    os.makedirs(REPORTS_DIR, mode=0o700, exist_ok=True)
    rec_id = secrets.token_hex(4)
    path = os.path.join(REPORTS_DIR, rec_id + ".zip")
    first = True
    left = length
    with open(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as out:
        while left > 0:
            chunk = stream.read(min(65536, left))
            if not chunk:
                break
            if first and not chunk.startswith(b"PK"):
                out.close()
                os.remove(path)
                return None
            first = False
            out.write(chunk)
            left -= len(chunk)
    if left:
        os.remove(path)
        return None
    with open(os.path.join(REPORTS_DIR, rec_id + ".json"), "w") as meta:
        os.chmod(meta.name, 0o600)
        json.dump({**fields, "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() if now is None else now))}, meta)
    return rec_id


def save(record):
    line = json.dumps(record, ensure_ascii=False) + "\n"
    fd = os.open(DATA_FILE, os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        os.write(fd, line.encode())
    finally:
        os.close(fd)


# ---------- state: who is pending, waiting, approved, denied, revoked ----------

@contextlib.contextmanager
def locked():
    """One writer at a time, across threads and processes (the owner's CLI runs beside the service)."""
    fd = os.open(LOCK_FILE, os.O_WRONLY | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        yield
    finally:
        os.close(fd)


def read_lines(path):
    try:
        with open(path) as f:
            return [json.loads(x) for x in f if x.strip()]
    except FileNotFoundError:
        return []


def append_event(rec_id, status, now=None):
    fd = os.open(DECISIONS_FILE, os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o600)
    try:
        os.write(fd, (json.dumps({"id": rec_id, "status": status,
                                  "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now or time.time()))}) + "\n").encode())
    finally:
        os.close(fd)


def statuses():
    """id -> latest status: waiting, approved, denied or revoked. No entry means pending (waiting for the owner)."""
    out = {}
    for d in read_lines(DECISIONS_FILE):
        out[d["id"]] = d["status"]
    return out


def status_of(rec_id):
    return statuses().get(rec_id)


def find_record(rec_id):
    for r in read_lines(DATA_FILE):
        if r.get("id") == rec_id:
            return r
    return None


def counts():
    st = statuses()
    c = {"approved": 0, "pending": 0, "waiting": 0}
    for r in read_lines(DATA_FILE):
        s = st.get(r["id"], "pending")
        if s in c:
            c[s] += 1
    return {"limit": LIMIT, "approved": c["approved"], "pending": c["pending"],
            "taken": c["approved"] + c["pending"], "waiting": c["waiting"]}


def live_record_for(email):
    """The sign-up of this email that is still alive (pending, waiting or approved), if any."""
    st = statuses()
    for r in reversed(read_lines(DATA_FILE)):
        if r["email"] == email and st.get(r["id"], "pending") in ("pending", "waiting", "approved"):
            return r, st.get(r["id"], "pending")
    return None, None


def accept(record, now=None):
    """Save a checked sign-up. Returns (record, state): state is 'new', 'waiting' (beta full) or 'again'
    (this email is already signed up, nothing saved)."""
    with locked():
        old, _ = live_record_for(record["email"])
        if old:
            return old, "again"
        full = counts()["taken"] >= LIMIT
        save(record)
        if full:
            append_event(record["id"], "waiting", now)
        return record, "waiting" if full else "new"


# ---------- signed links for the owner ----------

def sign(rec_id, action):
    return hmac.new(SECRET.encode(), f"{rec_id}:{action}".encode(), hashlib.sha256).hexdigest()[:32]


def verify(rec_id, action, token):
    return bool(SECRET) and action in ("approve", "deny") and hmac.compare_digest(sign(rec_id, action), token or "")


def link(rec_id, action):
    return f"{PUBLIC_URL}/signup/decide?id={rec_id}&a={action}&t={sign(rec_id, action)}"


def expired(rec, now=None):
    t = calendar.timegm(time.strptime(rec["time"], "%Y-%m-%dT%H:%M:%SZ"))
    return (time.time() if now is None else now) - t > LINK_DAYS * 86400


# ---------- the beta's private F-Droid link ----------

def current_code():
    try:
        with open(CODE_FILE) as f:
            code = f.read().strip()
    except FileNotFoundError:
        return None
    return code if re.fullmatch(r"[A-Za-z0-9_-]{20,}", code) else None


def repo_url(code=None):
    code = code or current_code()
    return f"{PUBLIC_URL}/fdroid/t/{code}/repo" if code else None


LINK_PATH = re.compile(r"^/fdroid/t/([A-Za-z0-9_-]{20,})/repo(?:/|$)")


PUBLIC_PATH = re.compile(r"^/fdroid/repo(?:/|$)")  # the open address (issue #608): every request passes, and is counted


def valid_link(uri):
    if PUBLIC_PATH.match((uri or "").split("?", 1)[0]):
        return True
    m = LINK_PATH.match((uri or "").split("?", 1)[0])
    code = current_code()
    return bool(m and code) and hmac.compare_digest(m.group(1), code)


# ---- F-Droid stats and the daily summary (owner only) ----
# Every F-Droid request passes /auth, so it's counted here: APK downloads per build, and devices checking the repo.
# "Devices" are told apart by their address, scrambled with a key that only lives in memory for one day; only the
# day's totals are written down, never an address or anything made from one.
STATS_FILE = os.path.join(DATA_DIR, "stats.json")
DIGEST_UTC = os.environ.get("DIGEST_UTC", "21:55")  # 23:55 in Helsinki in winter, 00:55 in summer
INDEX_FILES = {"entry.jar", "index-v1.jar", "index.jar"}  # one of these per repo check by a phone
APK_NAME = re.compile(r"^app\.siansa_(\d+)\.apk$")
_stats_lock = threading.Lock()
_today = {"day": None, "key": b"", "seen": {}}


def utc_day(now=None):
    return time.strftime("%Y-%m-%d", time.gmtime(time.time() if now is None else now))


def load_stats():
    try:
        with open(STATS_FILE, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {"days": {}}


def save_stats(stats):
    tmp = STATS_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(stats, f)
    os.replace(tmp, STATS_FILE)


def fetch_kind(uri):
    """What an F-Droid request under the beta link is, for counting: "repo", "apk:<build>" or None."""
    path = (uri or "").split("?", 1)[0]
    name = path.rsplit("/repo/", 1)[-1] if "/repo/" in path else ""
    m = APK_NAME.match(name)
    if m:
        return "apk:" + m.group(1)
    return "repo" if name in INDEX_FILES else None


def record_fetch(uri, client, now=None):
    """Counts one F-Droid request under the beta link (after the link was checked). [client] is the address."""
    kind = fetch_kind(uri)
    if not kind:
        return
    day = utc_day(now)
    with _stats_lock:
        if _today["day"] != day:
            _today.update(day=day, key=secrets.token_bytes(32), seen={})
        visitor = hmac.new(_today["key"], client.encode(), hashlib.sha256).digest()
        seen = _today["seen"].setdefault(kind, set())
        stats = load_stats()
        entry = stats.setdefault("days", {}).setdefault(day, {}).setdefault(kind, {"requests": 0, "devices": 0})
        entry["requests"] += 1
        if visitor not in seen:
            seen.add(visitor)
            entry["devices"] += 1
        for old in sorted(stats["days"])[:-400]:  # about a year is plenty
            del stats["days"][old]
        save_stats(stats)


def _since(path, day, key="time"):
    return sum(1 for r in read_lines(path) if str(r.get(key, "")).startswith(day))


def digest_text(day, now=None):
    """The owner's summary of [day] (UTC): sign-ups, places, F-Droid downloads and devices, problem reports."""
    stats = load_stats().get("days", {})
    today = stats.get(day, {})
    events = [r for r in read_lines(DECISIONS_FILE) if str(r.get("time", "")).startswith(day)]
    lines = [f"Siansa on {day} (UTC)", "",
             f"Sign-ups: {_since(DATA_FILE, day)} new, "
             f"{sum(1 for e in events if e.get('status') == 'approved')} approved, "
             f"{sum(1 for e in events if e.get('status') == 'denied')} denied, "
             f"{sum(1 for e in events if e.get('status') == 'waiting')} put on the waiting list.",
             places_line(), ""]
    repo = today.get("repo", {"devices": 0, "requests": 0})
    lines.append(f"F-Droid: {repo['devices']} device(s) checked the beta repo ({repo['requests']} checks).")
    apks = sorted((k for k in today if k.startswith("apk:")), key=lambda k: int(k[4:]), reverse=True)
    for k in apks:
        lines.append(f"  build {k[4:]}: downloaded {today[k]['requests']} time(s) by {today[k]['devices']} device(s)")
    if not apks:
        lines.append("  No downloads today.")
    week = sorted(stats)[-7:]
    if week:
        lines.append("Devices checking the repo, last 7 days: " +
                     ", ".join(f"{d[5:]} {stats[d].get('repo', {}).get('devices', 0)}" for d in week))
    totals = {}
    for d in stats.values():
        for k, v in d.items():
            if k.startswith("apk:"):
                totals[k] = totals.get(k, 0) + v["requests"]
    if totals:
        lines.append("Downloads so far: " + ", ".join(f"build {k[4:]} {n}" for k, n in sorted(totals.items(), key=lambda kv: -int(kv[0][4:]))))
    reports = 0
    try:
        for e in os.scandir(REPORTS_DIR):
            if e.name.endswith(".json"):
                try:
                    with open(e.path, encoding="utf-8") as f:
                        reports += str(json.load(f).get("time", "")).startswith(day)
                except (OSError, ValueError):
                    pass
    except OSError:
        pass
    lines += ["", f"Problem reports from the app: {reports}.", "",
              "Devices are told apart by their internet address on the day, so phones sharing Wi-Fi count once and a "
              "phone changing networks can count twice. No addresses are kept."]
    return "\n".join(lines) + "\n"


def send_digest(day=None):
    to = mail_settings()["NOTIFY_TO"]
    if not to:
        return False
    day = day or utc_day()
    return send_mail(to, f"Siansa daily summary {day}", digest_text(day))


def digest_due(now, sent_day, at=None):
    """Whether today's summary should go now: past [at] (HH:MM, UTC) and not sent yet today."""
    at = at or DIGEST_UTC
    return time.strftime("%H:%M", time.gmtime(now)) >= at and sent_day != utc_day(now)


def digest_loop():
    while True:
        try:
            now = time.time()
            with _stats_lock:
                stats = load_stats()
                due = digest_due(now, stats.get("digest_sent"))
                if due:
                    stats["digest_sent"] = utc_day(now)
                    save_stats(stats)
            if due:
                print("daily summary", "sent" if send_digest(utc_day(now)) else "not sent", flush=True)
        except Exception as e:  # never stop the loop
            print("daily summary failed:", type(e).__name__, flush=True)
        time.sleep(60)


_fails = {}


def too_many_fails(client, now):
    recent = [x for x in _fails.get(client, []) if x > now - 3600]
    recent.append(now)
    _fails[client] = recent
    return len(recent) > FAILS_PER_HOUR


# ---------- email ----------

def mail_settings():
    return {k: os.environ.get(k, "") for k in ("SMTP_HOST", "SMTP_PORT", "SMTP_USER", "SMTP_PASSWORD", "SMTP_FROM", "NOTIFY_TO")}


def send_mail(to, subject, body):
    cfg = mail_settings()
    if not (cfg["SMTP_HOST"] and cfg["SMTP_FROM"]):
        print("mail not set up (SMTP_HOST, SMTP_FROM): not sent:", subject, flush=True)
        return False
    msg = EmailMessage()
    msg["From"], msg["To"], msg["Subject"] = cfg["SMTP_FROM"], to, subject
    # Required by RFC 5322; mail without them is marked as spam or refused by bigger providers.
    msg["Date"] = email.utils.formatdate(localtime=True)
    msg["Message-ID"] = email.utils.make_msgid(domain=email.utils.parseaddr(cfg["SMTP_FROM"])[1].partition("@")[2] or None)
    msg.set_content(body)
    try:
        with smtplib.SMTP(cfg["SMTP_HOST"], int(cfg["SMTP_PORT"] or 587), timeout=20) as s:
            s.starttls(context=ssl.create_default_context())
            if cfg["SMTP_USER"]:
                s.login(cfg["SMTP_USER"], cfg["SMTP_PASSWORD"])
            s.send_message(msg)
        return True
    except Exception as e:  # never log the message: it can hold a private link
        print("mail failed:", type(e).__name__, flush=True)
        return False


def places_line():
    c = counts()
    return (f"Beta places: {c['approved']} of {c['limit']} have the link, {c['pending']} waiting for your decision, "
            f"{c['waiting']} on the waiting list.")


def notify_owner(rec, wait=False):
    to = mail_settings()["NOTIFY_TO"]
    if not to:
        return
    body = (f"New beta sign-up\n\nEmail: {rec['email']}\nInstall: {'Google Play' if rec['route'] == 'play' else 'F-Droid'}\n"
            f"Server: {rec['server']}\nPhone: {rec['device'] or '-'}\n\n"
            f"Approve: {link(rec['id'], 'approve')}\n\nDeny: {link(rec['id'], 'deny')}\n\n"
            f"{places_line()}\n\n"
            f"The links work for {LINK_DAYS} days. Each opens a page with a button; nothing happens until you press it.\n")
    args = (to, f"Siansa beta sign-up: {rec['email']}", body)
    if wait:
        return send_mail(*args)
    threading.Thread(target=send_mail, args=args, daemon=True).start()


EMAILS_DIR = os.environ.get("EMAILS_DIR", "/emails")


def template(name, default_subject, default_body, **values):
    """(subject, body) from emails/<name>.txt: 'Subject: …', a blank line, then the message with $placeholders.
    A missing or broken file means the built-in text, so a typo can't stop an email from going out."""
    try:
        with open(os.path.join(EMAILS_DIR, name + ".txt"), encoding="utf-8") as f:
            head, _, body = f.read().partition("\n\n")
        if not head.lower().startswith("subject:") or not body.strip():
            raise ValueError("no subject or body")
        values.setdefault("tail", "")
        if "$tail" in body:
            values["tail"] = _tail(values)
        return string.Template(head[8:].strip()).safe_substitute(values), string.Template(body).safe_substitute(values)
    except (OSError, ValueError):
        return default_subject, default_body


def _tail(values):
    try:
        with open(os.path.join(EMAILS_DIR, "tail.txt"), encoding="utf-8") as f:
            return string.Template(f.read()).safe_substitute(values).rstrip("\n") + "\n"
    except OSError:
        return welcome_tail()


def welcome_tail():
    return ("Setting up your servers: " + BETA_REPO_URL + "\n"
            "Found a problem or have an idea? Report it here: " + ISSUES_URL + "\n"
            "(Don't post passwords or your server's address. You can also write to help@siansa.app.)\n\n"
            "Thank you for testing Siansa.\n")


def welcome_mail(rec):
    values = dict(repo_url=repo_url() or "", beta_repo_url=BETA_REPO_URL, issues_url=ISSUES_URL, email=rec["email"])
    subject = "Welcome to the Siansa beta"
    return template("welcome_" + ("fdroid" if rec["route"] == "fdroid" else "play"), subject, welcome_text(rec), **values)


def welcome_text(rec):
    if rec["route"] == "fdroid":
        return ("Hello,\n\nYou're in the Siansa beta. To install it:\n\n1. In the F-Droid app, go to Settings > Repositories > +.\n"
                f"2. Paste the beta address:\n   {repo_url()}\n3. Search for Siansa and install it.\n\n"
                "The address is private to the beta: please don't share it.\n\n" + welcome_tail())
    return ("Hello,\n\nYou're in the Siansa beta. We're adding your Google account email to the test on Google Play. "
            "You'll get a link in a separate message; open it, tap Become a tester, then install Siansa from Play. "
            "Please stay in the test for 14 days.\n\n" + welcome_tail())


def new_link_mail(email=""):
    values = dict(repo_url=repo_url() or "", beta_repo_url=BETA_REPO_URL, issues_url=ISSUES_URL, email=email)
    return template("new_link", "The Siansa beta address has changed", new_link_text(), **values)


def new_link_text():
    return ("Hello,\n\nThe Siansa beta address has changed. In the F-Droid app, remove the old Siansa beta repository "
            "(Settings > Repositories) and add this one:\n\n   " + repo_url() + "\n\nThe old address no longer works. "
            "Please don't share the new one.\n\n" + welcome_tail())


def open_repo_mail(email=""):
    values = dict(repo_url=PUBLIC_URL + "/fdroid/repo", beta_repo_url=BETA_REPO_URL, issues_url=ISSUES_URL, email=email)
    return template("open_repo", "Siansa is now open on F-Droid: please reinstall once", open_repo_text(), **values)


def open_repo_text():
    # Sent once by `repo-link public` (issue #608): the open address, and the one-time reinstall for the new signing key.
    return ("Hello,\n\nSiansa's F-Droid repository is now open to everyone, at a new address. In the F-Droid app, remove "
            "the old Siansa beta repository (Settings > Repositories) and add this one:\n\n   " + PUBLIC_URL +
            "/fdroid/repo\n\nSiansa is also signed with a new key now. F-Droid can't update across a key change, so "
            "please uninstall Siansa once and install it again from the new repository. You'll need to enter your "
            "servers again afterwards.\n\n" + welcome_tail())


DENY_TEXT = ("Hello,\n\nThank you for asking to join the Siansa beta. We can't offer you a place right now. "
             "We'll write if that changes.\n\nSiansa\n")


def approve_effects(rec, now=None, auto=False):
    """After an approval is recorded: email the tester (and the owner, for automatic ones, with the count)."""
    if rec["route"] == "fdroid" and not repo_url():
        line = "Approved, but there is no beta link yet, so no email was sent. Run scripts/repo-link on the server, then `signups link`."
    else:
        sent = send_mail(rec["email"], *welcome_mail(rec))
        line = "Approved. " + ("The welcome email was sent." if sent else "The welcome email could NOT be sent: check the mail settings.")
    if rec["route"] == "play":
        line += f" Now add {rec['email']} to the closed test's tester list in Play Console."
    line += " " + places_line()
    if auto:
        to = mail_settings()["NOTIFY_TO"]
        if to:
            send_mail(to, f"Siansa beta: {rec['email']} came off the waiting list", f"{rec['email']} ({rec['route']}) was approved automatically.\n{line}\n")
    return line


def promote(now=None):
    """While there is a free place, approve the oldest waiting sign-up. Returns one line per person."""
    lines = []
    while True:
        with locked():
            if counts()["taken"] >= LIMIT:
                break
            st = statuses()
            nxt = next((r for r in read_lines(DATA_FILE) if st.get(r["id"]) == "waiting"), None)
            if not nxt:
                break
            append_event(nxt["id"], "approved", now)
        lines.append(f"{nxt['email']}: " + approve_effects(nxt, now, auto=True))
    return lines


def decide(rec, action, now=None):
    """Record the owner's decision, then do what follows. Returns a line for the owner, or None if already decided."""
    with locked():
        if status_of(rec["id"]) in ("approved", "denied", "revoked"):
            return None
        append_event(rec["id"], "approved" if action == "approve" else "denied", now)
    if action == "deny":
        sent = send_mail(rec["email"], *template("deny", "Your Siansa beta request", DENY_TEXT, email=rec["email"], beta_repo_url=BETA_REPO_URL, issues_url=ISSUES_URL))
        line = "Denied. " + ("A short no was emailed." if sent else "The email could not be sent.")
        promoted = promote(now)  # a place may have freed up
        return line + (f" Waiting list: {'; '.join(promoted)}" if promoted else "")
    return approve_effects(rec, now)


def revoke(email, now=None):
    """Take a tester's place; the next one on the waiting list moves up. The link is shared, so to cut off
    access for good, rotate it."""
    email = email.strip().lower()
    n = 0
    with locked():
        st = statuses()
        for r in read_lines(DATA_FILE):
            if r["email"] == email and st.get(r["id"]) == "approved":
                append_event(r["id"], "revoked", now)
                n += 1
    return n, promote(now)


def _rewrite(path, lines):
    tmp = path + ".tmp"
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        for line in lines:
            f.write(json.dumps(line) + "\n")
    os.replace(tmp, path)


def forget(email, now=None):
    """Removes everyone's sign-ups for [email] completely (a test, or someone who asked to be removed), and their
    decisions; no email is sent. If they had a place, the next one on the waiting list moves up."""
    email = email.strip().lower()
    with locked():
        records = read_lines(DATA_FILE)
        gone = {r["id"] for r in records if r["email"] == email}
        if gone:
            _rewrite(DATA_FILE, [r for r in records if r["id"] not in gone])
            _rewrite(DECISIONS_FILE, [e for e in read_lines(DECISIONS_FILE) if e.get("id") not in gone])
    return len(gone), promote(now) if gone else []


def approved_fdroid_emails():
    st = statuses()
    return sorted({r["email"] for r in read_lines(DATA_FILE) if st.get(r["id"]) == "approved" and r["route"] == "fdroid"})


class Handler(BaseHTTPRequestHandler):
    server_version = "siansa-signup"
    sys_version = ""

    def _send(self, status, body, extra=(), ctype="text/html; charset=utf-8"):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in extra:
            if k == "Cache-Control":
                continue
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _client(self):
        who = self.headers.get("CF-Connecting-IP") or self.headers.get("X-Real-IP") or self.client_address[0]
        return hashlib.sha256(who.encode()).hexdigest()

    def _decide_params(self, form):
        one = lambda k: (form.get(k, [""])[0] or "")[:80]
        return one("id"), one("a"), one("t")

    def _decide_page(self, rec_id, action, token, confirm):
        """Check the link; show the button (GET) or act (POST). Returns (status, body)."""
        if not verify(rec_id, action, token):
            return page("Link not valid", "<p>This link isn't valid.</p>", 403)
        rec = find_record(rec_id)
        if not rec:
            return page("Not found", "<p>That sign-up doesn't exist any more.</p>", 404)
        already = status_of(rec_id)
        if already in ("approved", "denied", "revoked"):
            return page("Already decided", f"<p>{html.escape(rec['email'])} was already {already}.</p>")
        if already == "waiting":
            return page("On the waiting list", f"<p>{html.escape(rec['email'])} is on the waiting list and will be approved automatically when a place is free.</p>")
        if expired(rec):
            return page("Link expired", f"<p>This link is older than {LINK_DAYS} days.</p>", 410)
        if not confirm:
            word = "Approve" if action == "approve" else "Deny"
            details = (f"<p>{html.escape(rec['email'])}<br>Install: {'Google Play' if rec['route'] == 'play' else 'F-Droid'}"
                       f"<br>Server: {html.escape(rec['server'])}<br>Phone: {html.escape(rec['device'] or '-')}</p>")
            form = (f'<form method="post" action="/signup/decide"><input type="hidden" name="id" value="{html.escape(rec_id)}">'
                    f'<input type="hidden" name="a" value="{html.escape(action)}"><input type="hidden" name="t" value="{html.escape(token)}">'
                    f'<p><button type="submit">{word}</button></p></form>')
            return page(f"{word} this person?", details + form)
        result = decide(rec, action)
        if result is None:
            return page("Already decided", "<p>Someone just decided this one.</p>")
        return page("Done", f"<p>{html.escape(result)}</p>")

    def _report(self):
        now = time.time()
        if report_limited(self._client(), now):
            return self._send(429, b'{"error":"slow down"}', ctype="application/json")
        fields = report_fields(self.headers.get("X-Report", ""))
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = -1
        if fields is None:
            return self._send(400, b'{"error":"bad report"}', ctype="application/json")
        if length > REPORT_MAX_ZIP:
            return self._send(413, b'{"error":"too big"}', ctype="application/json")
        if reports_total() + max(length, 0) > REPORTS_MAX_TOTAL:
            return self._send(507, b'{"error":"full"}', ctype="application/json")
        rec_id = save_report(self.rfile, length, fields, now)
        if rec_id is None:
            return self._send(400, b'{"error":"bad log"}', ctype="application/json")
        title, body = issue_text(rec_id, fields, now)
        number, url = open_issue(title, body)
        threading.Thread(target=store_report_privately, args=(rec_id, number, now), daemon=True).start()
        print("report", rec_id, "issue", number, flush=True)  # no words, no address
        self._send(200, json.dumps({"id": rec_id, "issue": number, "url": url}).encode(), ctype="application/json")

    def do_POST(self):
        path = urlsplit(self.path).path
        if path == "/report":
            return self._report()
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = -1
        if length < 0 or length > MAX_BODY:
            return self._send(*page("Too much", "<p>That form was too big.</p>", 413))
        form = parse_qs(self.rfile.read(length).decode("utf-8", "replace"), keep_blank_values=True)
        now = time.time()
        if path == "/signup/decide":
            if too_many_fails(self._client(), now):
                return self._send(*page("Slow down", "<p>Too many tries.</p>", 429))
            return self._send(*self._decide_page(*self._decide_params(form), confirm=True))
        if path != "/signup":
            return self._send(*page("Not found", "", 404))
        if form.get("website", [""])[0]:  # hidden field only bots fill in: pretend it worked
            return self._send(303, b"", [("Location", "/signup-thanks")])
        client = self._client()  # only a hash is kept in memory, never written anywhere
        if rate_limited(client, now):
            return self._send(*page("Slow down", "<p>Too many tries. Please try again in an hour.</p>", 429))
        record, problems = check(form, now)
        if problems:
            return self._send(*problems_page(problems))
        rec, state = accept(record, now)
        _accepted.append(now)
        print("signup", state, record["route"], record["server"], flush=True)  # no email in the log
        if state == "new":
            notify_owner(rec)
        where = "/signup-waiting" if state == "waiting" else ("/signup-thanks-play" if rec["route"] == "play" else "/signup-thanks")
        self._send(303, b"", [("Location", where)])

    def do_GET(self):
        parts = urlsplit(self.path)
        if parts.path == "/auth":  # Caddy's forward_auth for /fdroid/t/<link>/repo
            uri = self.headers.get("X-Forwarded-Uri")
            if valid_link(uri):
                try:
                    record_fetch(uri, self.headers.get("CF-Connecting-IP") or self.headers.get("X-Real-IP") or self.client_address[0])
                except Exception as e:  # counting must never block a download
                    print("stats failed:", type(e).__name__, flush=True)
                return self._send(200, b"ok")
            if too_many_fails(self._client(), time.time()):
                return self._send(429, b"slow down")
            return self._send(404, b"Not found")
        if parts.path == "/signup/count":
            return self._send(200, json.dumps(counts()).encode(), ctype="application/json")
        if parts.path == "/signup/decide":
            if too_many_fails(self._client(), time.time()):
                return self._send(*page("Slow down", "<p>Too many tries.</p>", 429))
            return self._send(*self._decide_page(*self._decide_params(parse_qs(parts.query)), confirm=False))
        self._send(*page("Not found", "", 404))

    def log_message(self, *args):  # no per-request log lines (they would carry addresses)
        pass


def cli(argv):
    """Owner tools, run in the container: server.py revoke EMAIL, resend (the approval emails again), approve EMAIL,
    link EMAIL, accept EMAIL (approve and email them), deny EMAIL, notify-new-link, notify-open-repo, stats (today's summary),
    digest (send today's summary now)."""
    if len(argv) == 2 and argv[1] == "stats":
        print(digest_text(utc_day()), end="")
        return True
    if len(argv) == 2 and argv[1] == "digest":
        print("sent" if send_digest() else "not sent (NOTIFY_TO or mail not set up)")
        return True
    if len(argv) == 2 and argv[1] == "resend":
        st = statuses()
        pending = [r for r in read_lines(DATA_FILE) if st.get(r["id"]) is None and not expired(r)]
        for r in pending:
            print(("sent   " if notify_owner(r, wait=True) else "FAILED ") + r["email"])
        print(f"{len(pending)} undecided sign-up(s)")
        return True
    if len(argv) == 2 and argv[1] == "notify-new-link":
        for email in approved_fdroid_emails():
            print(("sent   " if send_mail(email, *new_link_mail(email)) else "FAILED ") + email)
        return True
    if len(argv) == 2 and argv[1] == "notify-open-repo":
        for email in approved_fdroid_emails():
            print(("sent   " if send_mail(email, *open_repo_mail(email)) else "FAILED ") + email)
        return True
    if len(argv) == 3 and argv[1] in ("approve", "link"):
        # For when email isn't working: approve (or just show the link for someone already approved) from the server.
        email = argv[2].strip().lower()
        rec, st = live_record_for(email)
        if not rec:
            print(f"No sign-up for {email}. They need to use the form on the website first.")
            return True
        if argv[1] == "approve" and st != "approved":
            with locked():
                if counts()["taken"] - (1 if st == "pending" else 0) >= LIMIT:
                    print(f"The beta is full ({LIMIT} places). Revoke someone first.")
                    return True
                append_event(rec["id"], "approved")
        elif st != "approved":
            print(f"{email} is {st}; use `approve` first.")
            return True
        if rec["route"] == "fdroid":
            print(repo_url() or "No beta link yet: run scripts/repo-link on the server first.")
        else:
            print(f"Google Play tester: add {email} to the closed test's tester list in Play Console.")
        print(places_line())
        return True
    if len(argv) == 3 and argv[1] in ("accept", "deny"):
        # Like the Approve and Deny buttons in the owner's email: the tester gets the welcome email, or a short no.
        email = argv[2].strip().lower()
        rec, st = live_record_for(email)
        if not rec:
            st_all = statuses()
            last = next((r for r in reversed(read_lines(DATA_FILE)) if r["email"] == email), None)
            print(f"{email} is already {st_all.get(last['id'])}." if last else f"No sign-up for {email}.")
            return True
        if st in ("approved", "denied", "revoked"):
            print(f"{email} is already {st}.")
            return True
        if argv[1] == "accept" and counts()["taken"] - (1 if st == "pending" else 0) >= LIMIT:
            print(f"The beta is full ({LIMIT} places). Revoke someone first, or leave them on the waiting list.")
            return True
        print(decide(rec, "approve" if argv[1] == "accept" else "deny") or "Nothing to do.")
        return True
    if len(argv) == 3 and argv[1] == "forget":
        n, promoted = forget(argv[2])
        print(f"removed {argv[2].strip().lower()} completely ({n} sign-up(s))" if n else f"No sign-up for {argv[2]}.")
        for line in promoted:
            print("from the waiting list:", line)
        return True
    if len(argv) == 3 and argv[1] == "revoke":
        n, promoted = revoke(argv[2])
        print(f"revoked {n} place(s) for {argv[2]}")
        for line in promoted:
            print("from the waiting list:", line)
        print("The beta link is shared. To cut off access, run: scripts/repo-link rotate")
        return True
    return False


if __name__ == "__main__":
    if cli(sys.argv):
        sys.exit(0)
    if not SECRET:
        print("SIGNUP_SECRET is not set: approval links will not work", flush=True)
    for _ in promote():  # a raised BETA_LIMIT lets the waiting list move up
        print("moved someone up from the waiting list", flush=True)
    threading.Thread(target=digest_loop, daemon=True).start()  # the owner's daily summary
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
