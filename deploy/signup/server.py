#!/usr/bin/env python3
"""Beta sign-up handler for the Siansa website (issue #410).

Standard library only.
- POST /signup: checks the form from site/index.html against the beta's minimum requirements, saves accepted
  sign-ups (signups.jsonl) and emails the owner two signed links, Approve and Deny.
- /signup/decide: the page behind those links. A link only shows a page; the decision is made by a button
  (POST), so mail scanners that open links can't approve anyone.
- Approve: F-Droid testers get their own login (testers.json, passwords stored hashed) and a welcome email;
  Play testers get a welcome email and the owner adds them to the Play Console tester list. Deny sends a polite no.
- GET /auth: Caddy asks this before serving /fdroid/repo (forward_auth); 200 for a tester's login, else 401.
No IP addresses are stored, no cookies, no third parties. Caddy passes /signup*, and /auth for its own checks.
"""
import base64
import calendar
import fcntl
import hashlib
import hmac
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
from email.message import EmailMessage
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

DATA_FILE = os.environ.get("SIGNUPS_FILE", "/data/signups.jsonl")
DATA_DIR = os.path.dirname(DATA_FILE)
DECISIONS_FILE = os.path.join(DATA_DIR, "decisions.jsonl")
TESTERS_FILE = os.path.join(DATA_DIR, "testers.json")
SECRET = os.environ.get("SIGNUP_SECRET", "")
PUBLIC_URL = os.environ.get("PUBLIC_URL", "https://siansa.app").rstrip("/")
LINK_DAYS = 14
FAILS_PER_HOUR = 30
MAX_BODY = 4096
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


def save(record):
    line = json.dumps(record, ensure_ascii=False) + "\n"
    fd = os.open(DATA_FILE, os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        os.write(fd, line.encode())
    finally:
        os.close(fd)


# ---------- decisions: signed links ----------

def sign(rec_id, action):
    return hmac.new(SECRET.encode(), f"{rec_id}:{action}".encode(), hashlib.sha256).hexdigest()[:32]


def verify(rec_id, action, token):
    return bool(SECRET) and action in ("approve", "deny") and hmac.compare_digest(sign(rec_id, action), token or "")


def link(rec_id, action):
    return f"{PUBLIC_URL}/signup/decide?id={rec_id}&a={action}&t={sign(rec_id, action)}"


def read_lines(path):
    try:
        with open(path) as f:
            return [json.loads(x) for x in f if x.strip()]
    except FileNotFoundError:
        return []


def find_record(rec_id):
    for r in read_lines(DATA_FILE):
        if r.get("id") == rec_id:
            return r
    return None


def decision_of(rec_id):
    for d in read_lines(DECISIONS_FILE):
        if d["id"] == rec_id:
            return d["status"]
    return None


def expired(rec, now=None):
    t = calendar.timegm(time.strptime(rec["time"], "%Y-%m-%dT%H:%M:%SZ"))
    return (time.time() if now is None else now) - t > LINK_DAYS * 86400


# ---------- testers: the F-Droid logins ----------

def hash_password(password, salt):
    return hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 100_000).hex()


def load_testers():
    try:
        with open(TESTERS_FILE) as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def add_tester(email, now=None):
    """Make a login for this person. Returns (username, password); only the hash is kept."""
    username = "tester-" + secrets.token_hex(3)
    password = secrets.token_urlsafe(18)
    salt = secrets.token_bytes(16)
    fd = os.open(TESTERS_FILE + ".lock", os.O_WRONLY | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        testers = load_testers()
        testers = {u: t for u, t in testers.items() if t["email"] != email}  # a new login replaces the old one
        testers[username] = {"email": email, "salt": salt.hex(), "hash": hash_password(password, salt),
                             "created": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now or time.time()))}
        tmp = TESTERS_FILE + ".tmp"
        with open(tmp, "w") as f:
            os.fchmod(f.fileno(), 0o600)
            json.dump(testers, f)
        os.replace(tmp, TESTERS_FILE)
    finally:
        os.close(fd)
    return username, password


def remove_tester(email):
    fd = os.open(TESTERS_FILE + ".lock", os.O_WRONLY | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        testers = load_testers()
        kept = {u: t for u, t in testers.items() if t["email"] != email.lower()}
        with open(TESTERS_FILE + ".tmp", "w") as f:
            os.fchmod(f.fileno(), 0o600)
            json.dump(kept, f)
        os.replace(TESTERS_FILE + ".tmp", TESTERS_FILE)
        return len(testers) - len(kept)
    finally:
        os.close(fd)


_ok_cache = {}  # sha256 of an accepted Authorization header -> expiry; saves hashing on every file of a download
_fails = {}


def check_login(header):
    if not header or not header.startswith("Basic "):
        return False
    key = hashlib.sha256(header.encode()).hexdigest()
    now = time.time()
    if _ok_cache.get(key, 0) > now:
        return True
    try:
        user, _, password = base64.b64decode(header[6:], validate=True).decode().partition(":")
    except Exception:
        return False
    t = load_testers().get(user)
    if t and hmac.compare_digest(hash_password(password, bytes.fromhex(t["salt"])), t["hash"]):
        _ok_cache[key] = now + 300
        return True
    return False


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
    except Exception as e:  # never log the message: it can hold a password
        print("mail failed:", type(e).__name__, flush=True)
        return False


def notify_owner(rec, wait=False):
    cfg = mail_settings()
    to = cfg["NOTIFY_TO"]
    if not to:
        return
    body = (f"New beta sign-up\n\nEmail: {rec['email']}\nInstall: {'Google Play' if rec['route'] == 'play' else 'F-Droid'}\n"
            f"Server: {rec['server']}\nPhone: {rec['device'] or '-'}\n\n"
            f"Approve: {link(rec['id'], 'approve')}\n\nDeny: {link(rec['id'], 'deny')}\n\n"
            f"The links work for {LINK_DAYS} days. Each opens a page with a button; nothing happens until you press it.\n")
    args = (to, f"Siansa beta sign-up: {rec['email']}", body)
    if wait:
        return send_mail(*args)
    threading.Thread(target=send_mail, args=args, daemon=True).start()


def welcome_text(rec, creds):
    if rec["route"] == "fdroid":
        user, password = creds
        return (f"Hello,\n\nYou're in the Siansa beta. To install it:\n\n1. In the F-Droid app, go to Settings > Repositories > +.\n"
                f"2. Address: {PUBLIC_URL}/fdroid/repo\n3. When it asks for a login, use:\n   User name: {user}\n   Password: {password}\n"
                f"4. Search for Siansa and install it.\n\nThe login is yours only; please don't share it. "
                f"Setting up your servers: https://github.com/echovaultstudio/siansa-beta-testers\n"
                f"Found a problem? Report it there or write to help@siansa.app.\n\nThank you for testing Siansa.\n")
    return ("Hello,\n\nYou're in the Siansa beta. We're adding your Google account email to the test on Google Play. "
            "You'll get a link in a separate message; open it, tap Become a tester, then install Siansa from Play. "
            "Please stay in the test for 14 days.\n\nSetting up your servers: https://github.com/echovaultstudio/siansa-beta-testers\n"
            "Found a problem? Report it there or write to help@siansa.app.\n\nThank you for testing Siansa.\n")


DENY_TEXT = ("Hello,\n\nThank you for asking to join the Siansa beta. We can't offer you a place right now. "
             "We'll write if that changes.\n\nSiansa\n")


def decide(rec, action, now=None):
    """Record the decision, make the login, send the email. Returns a line for the owner."""
    fd = os.open(DECISIONS_FILE, os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX)
        if decision_of(rec["id"]):
            return None
        status = "approved" if action == "approve" else "denied"
        os.write(fd, (json.dumps({"id": rec["id"], "status": status, "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now or time.time()))}) + "\n").encode())
    finally:
        os.close(fd)
    if action == "deny":
        sent = send_mail(rec["email"], "Your Siansa beta request", DENY_TEXT)
        return "Denied. " + ("A short no was emailed." if sent else "The email could not be sent.")
    creds = add_tester(rec["email"], now) if rec["route"] == "fdroid" else None
    sent = send_mail(rec["email"], "Welcome to the Siansa beta", welcome_text(rec, creds))
    line = "Approved. " + ("The welcome email was sent." if sent else "The welcome email could NOT be sent: check the mail settings.")
    if rec["route"] == "play":
        line += f" Now add {rec['email']} to the closed test's tester list in Play Console."
    return line


class Handler(BaseHTTPRequestHandler):
    server_version = "siansa-signup"
    sys_version = ""

    def _send(self, status, body, extra=()):
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in extra:
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
        already = decision_of(rec_id)
        if already:
            return page("Already decided", f"<p>{html.escape(rec['email'])} was already {already}.</p>")
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

    def do_POST(self):
        path = urlsplit(self.path).path
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
        save(record)
        _accepted.append(now)
        print("signup accepted", record["route"], record["server"], flush=True)  # no email in the log
        notify_owner(record)
        self._send(303, b"", [("Location", "/signup-thanks")])

    def do_GET(self):
        parts = urlsplit(self.path)
        if parts.path == "/auth":  # Caddy's forward_auth for /fdroid/repo
            now = time.time()
            if check_login(self.headers.get("Authorization")):
                return self._send(200, b"ok")
            if too_many_fails(self._client(), now):
                return self._send(429, b"slow down")
            return self._send(401, b"Login needed", [("WWW-Authenticate", 'Basic realm="Siansa beta", charset="UTF-8"')])
        if parts.path == "/signup/decide":
            if too_many_fails(self._client(), time.time()):
                return self._send(*page("Slow down", "<p>Too many tries.</p>", 429))
            return self._send(*self._decide_page(*self._decide_params(parse_qs(parts.query)), confirm=False))
        self._send(*page("Not found", "", 404))

    def log_message(self, *args):  # no per-request log lines (they would carry addresses)
        pass


def cli(argv):
    """Owner tools, run on the server: server.py revoke EMAIL, or server.py resend (the approval emails again)."""
    if len(argv) == 2 and argv[1] == "resend":
        pending = [r for r in read_lines(DATA_FILE) if not decision_of(r["id"]) and not expired(r)]
        for r in pending:
            print(("sent   " if notify_owner(r, wait=True) else "FAILED ") + r["email"])
        print(f"{len(pending)} undecided sign-up(s)")
        return True
    if len(argv) == 3 and argv[1] == "revoke":
        print(f"removed {remove_tester(argv[2].strip().lower())} login(s) for {argv[2]}")
        return True
    return False


if __name__ == "__main__":
    if cli(sys.argv):
        sys.exit(0)
    if not SECRET:
        print("SIGNUP_SECRET is not set: approval links will not work", flush=True)
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
