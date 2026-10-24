#!/usr/bin/env python3
"""Beta sign-up form handler for the Siansa website (issue #410).

Standard library only. Takes the form posted by site/index.html, checks the answers against the beta's minimum
requirements, and appends accepted sign-ups to a JSON-lines file. No IP addresses, no cookies, no third parties.
Caddy passes /signup here; nothing else reaches it.
"""
import fcntl
import hashlib
import html
import json
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

DATA_FILE = os.environ.get("SIGNUPS_FILE", "/data/signups.jsonl")
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

    def do_POST(self):
        if self.path != "/signup":
            return self._send(*page("Not found", "", 404))
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = -1
        if length < 0 or length > MAX_BODY:
            return self._send(*page("Too much", "<p>That form was too big.</p>", 413))
        form = parse_qs(self.rfile.read(length).decode("utf-8", "replace"), keep_blank_values=True)
        if form.get("website", [""])[0]:  # hidden field only bots fill in: pretend it worked
            return self._send(303, b"", [("Location", "/signup-thanks")])
        # Cloudflare (or Caddy) tells us the visitor; only a hash is kept in memory, never written anywhere.
        who = self.headers.get("CF-Connecting-IP") or self.headers.get("X-Real-IP") or self.client_address[0]
        client = hashlib.sha256(who.encode()).hexdigest()
        now = time.time()
        if rate_limited(client, now):
            return self._send(*page("Slow down", "<p>Too many tries. Please try again in an hour.</p>", 429))
        record, problems = check(form, now)
        if problems:
            return self._send(*problems_page(problems))
        save(record)
        _accepted.append(now)
        print("signup accepted", record["route"], record["server"], flush=True)  # no email in the log
        self._send(303, b"", [("Location", "/signup-thanks")])

    def do_GET(self):
        self._send(*page("Not found", "", 404))

    def log_message(self, *args):  # no per-request log lines (they would carry addresses)
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
