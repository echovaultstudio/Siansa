#!/bin/sh
# One-time setup of the Siansa beta server (issue #410), on Debian or Ubuntu, as root, from the deploy/ folder:
#   sudo DOMAIN=siansa.app sh scripts/setup.sh
# Safe to run again. It doesn't touch SSH logins for you: see harden.sh for that.
set -eu

: "${DOMAIN:?Set DOMAIN, e.g. DOMAIN=siansa.app}"
FDROID_DIR="${FDROID_DIR:-/srv/fdroid}"
DEMO_DIR="${DEMO_DIR:-/srv/siansa-demo}"
SITE_DIR="${SITE_DIR:-/srv/siansa-site}"
SIGNUPS_DIR="${SIGNUPS_DIR:-/srv/siansa-signups}"
HERE=$(cd "$(dirname "$0")/.." && pwd)

echo "== Packages"
apt-get update -q
apt-get install -y -q fdroidserver apksigner aapt unzip ufw fail2ban unattended-upgrades ca-certificates curl
if ! command -v docker >/dev/null; then
	echo "Docker is missing: install Docker Engine (https://docs.docker.com/engine/install/) and run this again." >&2
	exit 1
fi

echo "== Deploy user (CI publishes APKs as this user, nothing else)"
id siansa-deploy >/dev/null 2>&1 || useradd --system --create-home --home-dir /var/lib/siansa-deploy --shell /bin/sh siansa-deploy
install -m 755 "$HERE/scripts/siansa-publish" /usr/local/bin/siansa-publish
install -m 755 "$HERE/scripts/siansa-ci" /usr/local/bin/siansa-ci
install -m 755 "$HERE/scripts/siansa-site-update" /usr/local/bin/siansa-site-update
mkdir -p /etc/siansa && printf '%s\n' "$DOMAIN" > /etc/siansa/domain

echo "== F-Droid repo in $FDROID_DIR"
mkdir -p "$FDROID_DIR"
chown siansa-deploy:siansa-deploy "$FDROID_DIR"
as_deploy() { (cd "$FDROID_DIR" && runuser -u siansa-deploy -- "$@"); }
if [ ! -f "$FDROID_DIR/keystore.p12" ]; then
	# Makes config.yml and the repo signing key (keystore.p12), which never leave this server.
	as_deploy fdroid init --repo-keyalias siansa-beta
fi
# Our settings over fdroid's defaults (same keys removed first, then ours added).
keys=$(sed -n 's/^\([a-z_]*\):.*/\1/p' "$HERE/fdroid/config.yml" | grep -Ev '^(keystore|repo_keyalias)$' | paste -sd'|')
OLD_REPO_URL=$(sed -n 's/^repo_url: //p' "$FDROID_DIR/config.yml" 2>/dev/null | head -n 1)
grep -Ev "^($keys):" "$FDROID_DIR/config.yml" > "$FDROID_DIR/config.yml.new" || true
sed "s#DOMAIN#$DOMAIN#g" "$HERE/fdroid/config.yml" | grep -Ev '^(keystore|repo_keyalias):' >> "$FDROID_DIR/config.yml.new"
mv "$FDROID_DIR/config.yml.new" "$FDROID_DIR/config.yml"
# Running setup again must not undo the private beta address (scripts/repo-link).
case "$OLD_REPO_URL" in */fdroid/t/*) sed -i "s#^repo_url:.*#repo_url: $OLD_REPO_URL#" "$FDROID_DIR/config.yml" ;; esac
mkdir -p "$FDROID_DIR/repo" "$FDROID_DIR/metadata" "$FDROID_DIR/tmp"
sed "s#DOMAIN#$DOMAIN#g" "$HERE/fdroid/metadata/app.siansa.yml" > "$FDROID_DIR/metadata/app.siansa.yml"
# The app's icon for F-Droid (the APK's own icon is a drawn one that fdroidserver can't turn into a picture).
mkdir -p "$FDROID_DIR/metadata/app.siansa/en-US"
cp "$HERE/site/icon.png" "$FDROID_DIR/metadata/app.siansa/en-US/icon.png"
chown -R siansa-deploy:siansa-deploy "$FDROID_DIR"
chmod 750 "$FDROID_DIR"
chmod 600 "$FDROID_DIR/config.yml" "$FDROID_DIR/keystore.p12"
as_deploy fdroid update --create-metadata >/dev/null 2>&1 || true

echo "== Website pages for $DOMAIN in $SITE_DIR"
mkdir -p "$SITE_DIR"
cp "$HERE"/site/* "$SITE_DIR"/
for f in "$SITE_DIR"/*.html; do sed -i "s#{{DOMAIN}}#$DOMAIN#g" "$f"; done
chown -R siansa-deploy:siansa-deploy "$SITE_DIR"  # so CI can update the pages (scripts/siansa-site-update)

echo "== Cloudflare's certificate for Authenticated Origin Pulls (TLS_MODE=cloudflare)"
mkdir -p "$HERE/certs"
curl -fsSL -o "$HERE/certs/cloudflare-origin-pull-ca.pem" \
	https://developers.cloudflare.com/ssl/static/authenticated_origin_pull_ca.pem ||
	echo "Couldn't download it: get it from Cloudflare's docs (Authenticated Origin Pulls) into certs/" >&2

EMAILS_DIR="${EMAILS_DIR:-/srv/siansa-emails}"
echo "== Beta emails in $EMAILS_DIR"
mkdir -p "$EMAILS_DIR"
[ -n "$(ls -A "$EMAILS_DIR")" ] || cp "$HERE"/emails/*.txt "$EMAILS_DIR"/
chown -R siansa-deploy:siansa-deploy "$EMAILS_DIR"
chmod 755 "$EMAILS_DIR"
chmod 644 "$EMAILS_DIR"/*.txt

echo "== Beta sign-ups folder $SIGNUPS_DIR"
mkdir -p "$SIGNUPS_DIR"
chown 1000:1000 "$SIGNUPS_DIR"
chmod 700 "$SIGNUPS_DIR"

echo "== Demo Navidrome folders in $DEMO_DIR"
mkdir -p "$DEMO_DIR/music" "$DEMO_DIR/data"
chown -R 1000:1000 "$DEMO_DIR"

[ -f "$HERE/.env" ] || { cp "$HERE/.env.example" "$HERE/.env"; sed -i "s#^DOMAIN=.*#DOMAIN=$DOMAIN#; s#^DEMO_DOMAIN=.*#DEMO_DOMAIN=demo.$DOMAIN#" "$HERE/.env"; }
grep -q '^SIGNUP_SECRET=.' "$HERE/.env" || {
	if grep -q '^SIGNUP_SECRET=' "$HERE/.env"; then sed -i "s#^SIGNUP_SECRET=.*#SIGNUP_SECRET=$(openssl rand -hex 32)#" "$HERE/.env"; else echo "SIGNUP_SECRET=$(openssl rand -hex 32)" >> "$HERE/.env"; fi
}
chmod 600 "$HERE/.env"

cat <<EOF

Done. Next:
  1. Edit $HERE/.env (ACME_EMAIL), check DNS for $DOMAIN and demo.$DOMAIN, then: docker compose up -d
  2. Run scripts/harden.sh for the firewall, fail2ban and automatic security updates.
  3. Add CI's SSH key for publishing: scripts/add-ci-key.sh "<public key>"
  4. Put a few freely licensed albums in $DEMO_DIR/music and create the demo user in Navidrome (README).
The F-Droid repo's address and fingerprint (for testers) are on https://$DOMAIN/fdroid/repo/ once Caddy runs.
Back up $FDROID_DIR/keystore.p12 and config.yml somewhere offline.
EOF
