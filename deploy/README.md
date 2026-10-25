# Siansa beta server

One server for the closed beta (issue #410):

| Address | What |
|---|---|
| `https://siansa.app` | Home page for testers |
| `https://siansa.app/privacy` | Privacy policy (for the Play listing) |
| `https://siansa.app/fdroid/repo` | F-Droid repo with every release, for testers outside Play |
| `https://demo.siansa.app` | Demo Navidrome with a small free library, for Google's reviewers and testers |

Caddy serves all of it over HTTPS. The domain sits behind Cloudflare's proxy (orange cloud): visitors get
Cloudflare's certificate, and Caddy uses a free Cloudflare **Origin Certificate** (valid for 15 years) so the
hop from Cloudflare to the server is encrypted too, and only accepts connections that come from Cloudflare.
Without Cloudflare, set `TLS_MODE=auto` in `.env` and Caddy gets Let's Encrypt certificates itself. The F-Droid repo is signed on
the server; its key never leaves it. CI sends each new release's APK over SSH with a key that can only do that.

## What's in here

| File | For |
|---|---|
| `docker-compose.yml` | Caddy and the demo Navidrome, locked down: read-only containers, no extra capabilities, Navidrome reachable only through Caddy |
| `Caddyfile` | The sites, security headers (HSTS, CSP, no framing, no referrer), compression, logs without IP addresses |
| `.env.example` | Your domain, e-mail, folders and the demo admin password: copy to `.env` |
| `site/` | Home page, privacy policy, styles and the icon |
| `fdroid/` | The F-Droid repo settings and the app's listing |
| `scripts/setup.sh` | One-time setup: packages, the F-Droid repo and its key, the publish user, folders |
| `scripts/harden.sh` | Firewall, fail2ban, automatic security updates, SSH settings, small Docker logs |
| `scripts/add-ci-key.sh` | Lets CI publish to the F-Droid repo, and nothing else |
| `scripts/siansa-publish` | What CI's key runs: checks the APK (size, package, signature) and adds it to the repo |
| `scripts/siansa-source` | Keeps each version's source (GPL) next to its APK, as `app.siansa_<code>_src.tar.gz` |

## Step by step

You need a Debian or Ubuntu server with Docker Engine, and SSH access with sudo.

### 1. Cloudflare
In the Cloudflare dashboard for `siansa.app`:
1. **DNS**: add `A` records (and `AAAA` for IPv6) for `siansa.app` and `demo` pointing at the server, proxied
   (orange cloud).
2. **SSL/TLS › Origin Server › Origin Certificates › Create Certificate**: let Cloudflare generate the key
   (RSA), hostnames `siansa.app` and `*.siansa.app`, 15 years, **PEM**. Keep the page open: the private key is
   shown only once. Save the certificate as `origin.pem` and the key as `origin.key`.
3. **SSL/TLS › Overview**: set the encryption mode to **Full (strict)**.
4. **SSL/TLS › Origin Server › Authenticated Origin Pulls**: switch it on. Caddy then refuses anything that
   didn't come through Cloudflare.

### 2. Copy this folder to the server
```sh
scp -r deploy you@server:~/siansa-deploy
ssh you@server
cd ~/siansa-deploy
```
Then put the two files from step 1 in `certs/` (`origin.pem`, `origin.key`), e.g. from your computer:
`scp origin.pem origin.key you@server:~/siansa-deploy/certs/`. They are git-ignored; keep them off GitHub.

### 3. Set up
```sh
sudo DOMAIN=siansa.app sh scripts/setup.sh
```
This installs fdroidserver, apksigner, aapt, ufw, fail2ban and unattended-upgrades. It creates:
- the F-Droid repo in `/srv/fdroid` with its signing key
- the `siansa-deploy` user
- the website in `/srv/siansa-site`
- the demo folders in `/srv/siansa-demo`
- `.env`

**Back up `/srv/fdroid/keystore.p12` and `/srv/fdroid/config.yml` somewhere offline.** If the key is lost,
every tester has to remove and add the repo again.

### 4. Fill in `.env`
```sh
nano .env    # ACME_EMAIL, and DEMO_ADMIN_PASSWORD (make one: openssl rand -base64 24)
```

### 5. Harden the server
```sh
sudo sh scripts/harden.sh
```
This sets up:
- a firewall that lets in only SSH (rate limited), 80 and 443 (skip it with `SKIP_FIREWALL=1` if your hosting
  provider's firewall already does this)
- fail2ban for SSH
- automatic security updates
- stricter SSH settings
- small Docker logs

Once you've checked that **your own SSH key login works**, switch off password logins:
`sudo NO_PASSWORD_SSH=1 sh scripts/harden.sh`.

### 6. Start
```sh
docker compose up -d
docker compose logs -f caddy     # should show no errors; with TLS_MODE=auto wait for "certificate obtained"
```
Open `https://siansa.app` and `https://siansa.app/privacy`. Check the headers at
[securityheaders.com](https://securityheaders.com) and the certificate at
[ssllabs.com](https://www.ssllabs.com/ssltest/).

### Beta sign-ups and who may download
The home page has a sign-up form. `signup/server.py` checks the answers (email, install route, Android 8.0+, own
Navidrome or Emby server reachable from the phone, 14 days for Play, feedback, privacy) and saves accepted ones in
`/srv/siansa-signups` (no IP addresses). Each new one is emailed to `NOTIFY_TO` with **Approve** and **Deny** links:
- A link opens a page with a button; nothing happens until you press it (so mail scanners can't approve anyone).
  Links are signed and work for 14 days, once.
- **Approve**, F-Droid: the tester gets an email with the beta's one private address,
  `https://siansa.app/fdroid/t/<code>/repo`, and links to the beta testers GitHub repo (issues) for reports.
  It's one address for everybody because F-Droid only accepts the address the repo's own index names; a different
  address per person makes it treat the address as a mirror and the app list stays empty. Caddy asks the sign-up
  service whether the code is valid before serving anything (`forward_auth`); `/fdroid/repo` is closed.
  Make the address once, after `setup.sh`: `sudo sh scripts/repo-link` (it prints the address and writes it into
  the F-Droid index). `sudo sh scripts/repo-link rotate` makes a new one, kills the old one at once, rebuilds the
  index and emails everyone approved for F-Droid. Rotate when someone leaves or the address leaks; testers then
  remove the old repo in F-Droid and add the new one.
- **Approve**, Google Play: the tester gets a welcome email, and the page tells you to add their email to the
  closed test's tester list in Play Console (Google gives no way to do that from here).
- **Deny** sends a short no.
- **Places:** `BETA_LIMIT` (12) testers at a time, Google Play and F-Droid together. When it's full, new sign-ups
  are accepted and put on a waiting list (the home page shows "N of 12 beta places taken"). When a place frees up
  (a deny, or `revoke`), the oldest waiting person is approved automatically and emailed; you get a note.
  Raise `BETA_LIMIT` in `.env` and restart to let more in.

Mail goes out through a mailbox on your mail server: fill in `SMTP_*`, `SMTP_FROM` and `NOTIFY_TO` in `.env`
(`SIGNUP_SECRET` is made by `setup.sh`). Then `docker compose up -d`.

```sh
sudo sh scripts/signups          # table with status (pending / approved / denied)
sudo sh scripts/signups play     # approved Google emails, comma separated, to paste into Play Console
sudo sh scripts/signups fdroid
sudo sh scripts/signups csv
sudo sh scripts/signups revoke EMAIL   # free a place; the next one waiting moves up (the link is shared: rotate to cut access)
sudo sh scripts/signups approve EMAIL  # approve without email and print the address
sudo sh scripts/repo-link rotate       # new private address, emailed to everyone approved for F-Droid
```
The approval emails didn't arrive (mail settings were wrong at the time)? After fixing `.env` and `docker compose up -d`:
`sudo docker compose exec signup python -I /app/server.py resend` sends them again for every undecided sign-up.

A tester who loses the address: `sudo sh scripts/repo-link` shows it again. The address is in Caddy's request log
on the server, which rotates after a few megabytes; don't share those logs.

Your emails (new sign-up, automatic approval) end with "Beta places: N of 12 have the link, …".

Unit tests: `python3 -I signup/test_server.py`. Check the Caddyfile after changing it:
`docker compose exec caddy caddy validate --config /etc/caddy/Caddyfile`.

### Changing the website and the beta emails from GitHub

The pages are in `deploy/site/` and the emails testers get are in `deploy/emails/` (see its README for what
each file is and the `$placeholders`). When a change to either is merged to `main`, the *Website* workflow
sends them to the server with the same restricted SSH key that publishes APKs; the next email uses the new
text, with no restart. The server accepts only a tar.gz of plain files for this and can run nothing else.

One-time on the server, from the kit folder (after unpacking the update, and after `add-ci-key.sh` has been run):

```
sudo sh scripts/enable-site-deploy.sh
sudo docker compose up -d
```

The first command installs the update scripts and makes the key run `siansa-ci`, which picks "publish an APK",
"update the site" or "keep a version's source". Run it again after an update that adds a script (the source one
came with issue #473); until then the server refuses the source and only the APK goes out. The second mounts the emails folder (`/srv/siansa-emails`) into the sign-up service. Changes
to `docker-compose.yml`, `Caddyfile` and `.env` still need you to copy them and restart.

### Short commands on the server

`scripts/beta.sh` adds `beta-…` commands to your shell (bash). Load them once:

```
echo '. ~/siansa-deploy/scripts/beta.sh' >> ~/.bashrc && . ~/.bashrc
```

Then `beta-help` lists them: `beta` (everyone and their status), `beta-accept EMAIL` and `beta-deny EMAIL` (like
the buttons in your email), `beta-remove EMAIL`, `beta-today`, `beta-new-link` and more.

### Daily summary and F-Droid stats

Every evening (`DIGEST_UTC`, 21:55 UTC by default) the sign-up service emails `NOTIFY_TO` a summary of the day: new
sign-ups and decisions, places taken, how many devices checked the F-Droid beta repo, downloads per build, and
problem reports from the app. `sudo sh scripts/signups stats` shows it now; `sudo sh scripts/signups digest` sends it
now. Devices are told apart by address with a key that only lives for the day; only totals are stored
(`/srv/siansa-signups/stats.json`).

### Problem reports from the app

Settings › Report a problem sends the tester's two answers and the whole log to `POST /report`. The log is kept in
`/srv/siansa-signups/reports/<id>.zip` (owner-only, 2 GB cap, 5 reports per hour per tester) and a public issue with
the words and the report id is opened in `siansa-beta-testers`. To turn issues on, create a fine-grained token at
GitHub › Settings › Developer settings › Personal access tokens › Fine-grained: only the `siansa-beta-testers`
repo, permission **Issues: Read and write**, nothing else. Put it in `.env` as `GITHUB_TOKEN=` and run
`docker compose up -d`. Without it the log is still kept. To also keep a copy of each log in a private repository (so it can be read next to its issue),
create a private repo such as `siansa-logs` with a README (it needs a `main` branch), make a second fine-grained token
for that repo only with **Contents: Read and write**, and set `REPORTS_REPO=echovaultstudio/siansa-logs` and
`REPORTS_TOKEN=` in `.env`. Delete old reports with `sudo rm /srv/siansa-signups/reports/<id>.*`.

### 7. The demo server for Google (and testers)
Google reviews the app before testers get it, and needs a server to log in to: without one, the review fails
with "we couldn't access your app".
1. Put a few albums you're allowed to share in `/srv/siansa-demo/music`. Public-domain or CC0 recordings are
   the easiest: no credits to show. Musopen and the Internet Archive have public-domain classical, for
   example. Keep the files tagged (artist, album, track, cover) so the app looks its best.
2. Open `https://demo.siansa.app` and log in as `admin` with `DEMO_ADMIN_PASSWORD`. Navidrome makes this
   account at first start, so nobody else can claim the empty server first.
3. Under **Users**, add a user `reviewer` with a long password, **not** an admin. Users can't change their own
   details on this server (`ND_ENABLEUSEREDITING=false`), so the login stays as you gave it.
4. Navidrome scans the music at start and then daily. To scan now: **Settings › Scan library**.
5. In Play Console › **App content › App access**, choose "All or some functionality is restricted" and add:
   - name "Demo server"
   - server address `https://demo.siansa.app`
   - user `reviewer` and its password
   - instructions: "On the first screen pick OpenSubsonic, then Not now, then enter the address, user and
     password above and tap Save. Music appears after the library sync."

### 8. Let CI publish to the F-Droid repo
On your own computer:
```sh
ssh-keygen -t ed25519 -N "" -C siansa-ci -f siansa-ci
```
On the server:
```sh
sudo sh scripts/add-ci-key.sh "$(cat siansa-ci.pub)"   # the .pub file copied over, or paste its text
ssh-keyscan -t ed25519 siansa.app                     # for FDROID_KNOWN_HOSTS
```
In GitHub › the repository › **Settings › Secrets and variables › Actions**, add:

| Secret | Value |
|---|---|
| `FDROID_HOST` | `siansa.app` |
| `FDROID_SSH_KEY` | the whole private key file `siansa-ci` |
| `FDROID_KNOWN_HOSTS` | the `ssh-keyscan` line |

Then delete `siansa-ci` from your computer. From the next release on, CI sends the APK to the repo, and the
release job's log says "published". The key can't open a shell; all it can do is hand over an APK.

To add the current release by hand: `ssh siansa-deploy@siansa.app < siansa-0.28.0beta.apk`, or as root
`sudo -u siansa-deploy siansa-publish < file.apk`.

### 9. Testers on F-Droid
In F-Droid (or Droid-ify / Neo Store): **Settings › Repositories › +**, then the address
`https://siansa.app/fdroid/repo`. The page at that address shows its fingerprint and a QR code to scan. Send
testers the address with the fingerprint, so they can tell the repo is yours.

## Keeping it running
- `docker compose pull && docker compose up -d` once a month (Caddy, Navidrome). Pin a newer Navidrome
  version in `docker-compose.yml` when you update it.
- The repo keeps the newest 5 builds (`archive_older` in `/srv/fdroid/config.yml`).
- Logs: `docker compose logs caddy` (no IPs or query strings), `journalctl -u ssh`, `fail2ban-client status sshd`.

## Notes
- The F-Droid builds are the GitHub release APKs, signed with the repository's key. Play builds are signed by
  Google (Play App Signing), so a tester can't update from one to the other: each install sticks to where it
  came from.
- The privacy policy gives `privacy@siansa.app` as the contact: set up that address, or change it in
  `site/privacy.html` before running `setup.sh`.
