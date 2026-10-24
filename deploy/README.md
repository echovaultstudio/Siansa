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
