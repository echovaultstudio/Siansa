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
import ssl
import sys
import threading
import time
import urllib.request
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
ISSUES_URL = "https://github.com/echovaultstudio/siansa-beta-testers/issues/new/choose"
BETA_REPO_URL = "https://github.com/echovaultstudio/siansa-beta-testers"
FAILS_PER_HOUR = 30
LIMIT = int(os.environ.get("BETA_LIMIT", "12") or 12)
MAX_BODY = 4096
REPORT_MAX_ZIP = 40 * 1024 * 1024
REPORTS_MAX_TOTAL = 2 * 1024 * 1024 * 1024
REPORTS_DIR = os.environ.get("REPORTS_DIR", os.path.join(DATA_DIR, "reports"))
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
BETA_REPO = os.environ.get("BETA_REPO", "echovaultstudio/siansa-beta-testers")
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


def issue_text(rec_id, f):
    title = "Report " + rec_id + ": " + " ".join(f["happened"].split())[:70]
    body = ("**What happened**\n" + quote(f["happened"]) + "\n\n**What was expected**\n" + quote(f["expected"] or "-") +
            "\n\n| | |\n|---|---|\n| Report id | `" + rec_id + "` |\n| App | " + f["app"].replace("|", "/") +
            " |\n| Android | " + f["android"].replace("|", "/") + " |\n| Phone | " + f["device"].replace("|", "/") +
            " |\n| Server type | " + f["source"].replace("|", "/") +
            " |\n\nThe log was sent privately to the maintainer; quote the report id if you add details.")
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


def valid_link(uri):
    m = LINK_PATH.match((uri or "").split("?", 1)[0])
    code = current_code()
    return bool(m and code) and hmac.compare_digest(m.group(1), code)


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


def welcome_tail():
    return ("Setting up your servers: " + BETA_REPO_URL + "\n"
            "Found a problem or have an idea? Report it here: " + ISSUES_URL + "\n"
            "(Don't post passwords or your server's address. You can also write to help@siansa.app.)\n\n"
            "Thank you for testing Siansa.\n")


def welcome_text(rec):
    if rec["route"] == "fdroid":
        return ("Hello,\n\nYou're in the Siansa beta. To install it:\n\n1. In the F-Droid app, go to Settings > Repositories > +.\n"
                f"2. Paste the beta address:\n   {repo_url()}\n3. Search for Siansa and install it.\n\n"
                "The address is private to the beta: please don't share it.\n\n" + welcome_tail())
    return ("Hello,\n\nYou're in the Siansa beta. We're adding your Google account email to the test on Google Play. "
            "You'll get a link in a separate message; open it, tap Become a tester, then install Siansa from Play. "
            "Please stay in the test for 14 days.\n\n" + welcome_tail())


def new_link_text():
    return ("Hello,\n\nThe Siansa beta address has changed. In the F-Droid app, remove the old Siansa beta repository "
            "(Settings > Repositories) and add this one:\n\n   " + repo_url() + "\n\nThe old address no longer works. "
            "Please don't share the new one.\n\n" + welcome_tail())


DENY_TEXT = ("Hello,\n\nThank you for asking to join the Siansa beta. We can't offer you a place right now. "
             "We'll write if that changes.\n\nSiansa\n")


def approve_effects(rec, now=None, auto=False):
    """After an approval is recorded: email the tester (and the owner, for automatic ones, with the count)."""
    if rec["route"] == "fdroid" and not repo_url():
        line = "Approved, but there is no beta link yet, so no email was sent. Run scripts/repo-link on the server, then `signups link`."
    else:
        sent = send_mail(rec["email"], "Welcome to the Siansa beta", welcome_text(rec))
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
        sent = send_mail(rec["email"], "Your Siansa beta request", DENY_TEXT)
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
        title, body = issue_text(rec_id, fields)
        number, url = open_issue(title, body)
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
            if valid_link(self.headers.get("X-Forwarded-Uri")):
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
    """Owner tools, run in the container: server.py revoke EMAIL, resend (the approval emails again), approve EMAIL, link EMAIL, notify-new-link."""
    if len(argv) == 2 and argv[1] == "resend":
        st = statuses()
        pending = [r for r in read_lines(DATA_FILE) if st.get(r["id"]) is None and not expired(r)]
        for r in pending:
            print(("sent   " if notify_owner(r, wait=True) else "FAILED ") + r["email"])
        print(f"{len(pending)} undecided sign-up(s)")
        return True
    if len(argv) == 2 and argv[1] == "notify-new-link":
        for email in approved_fdroid_emails():
            print(("sent   " if send_mail(email, "The Siansa beta address has changed", new_link_text()) else "FAILED ") + email)
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
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
