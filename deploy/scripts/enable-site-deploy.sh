#!/bin/sh
# Lets CI update the website and the beta emails (issue #410), with the same SSH key that publishes APKs.
#   sudo sh scripts/enable-site-deploy.sh
# Run it from the kit folder (the one with scripts/ and site/), once; safe to run again.
set -eu
HERE=$(cd "$(dirname "$0")/.." && pwd)
[ -f "$HERE/.env" ] || { echo "Run this in the kit folder: $HERE/.env is missing" >&2; exit 1; }
DOMAIN=$(sed -n 's/^DOMAIN=//p' "$HERE/.env" | head -n 1)
[ -n "$DOMAIN" ] || { echo "DOMAIN isn't set in .env" >&2; exit 1; }
SITE_DIR="${SITE_DIR:-/srv/siansa-site}"
EMAILS_DIR="${EMAILS_DIR:-/srv/siansa-emails}"
HOME_DIR=$(getent passwd siansa-deploy | cut -d: -f6)
AUTH="$HOME_DIR/.ssh/authorized_keys"
[ -f "$AUTH" ] || { echo "No CI key yet: run scripts/add-ci-key.sh first" >&2; exit 1; }

install -m 755 "$HERE/scripts/siansa-ci" /usr/local/bin/siansa-ci
install -m 755 "$HERE/scripts/siansa-site-update" /usr/local/bin/siansa-site-update
install -m 755 "$HERE/scripts/siansa-publish" /usr/local/bin/siansa-publish
mkdir -p /etc/siansa && printf '%s\n' "$DOMAIN" > /etc/siansa/domain

mkdir -p "$SITE_DIR" "$EMAILS_DIR"
[ -n "$(ls -A "$EMAILS_DIR")" ] || cp "$HERE"/emails/*.txt "$EMAILS_DIR"/
chown -R siansa-deploy:siansa-deploy "$SITE_DIR" "$EMAILS_DIR"
chmod 755 "$SITE_DIR" "$EMAILS_DIR"
chmod 644 "$EMAILS_DIR"/* 2>/dev/null || true

# The key keeps its restrictions; it now runs siansa-ci, which picks "publish an APK" or "update the site".
sed -i 's#command="/usr/local/bin/siansa-publish"#command="/usr/local/bin/siansa-ci"#' "$AUTH"
grep -q 'siansa-ci"' "$AUTH" || { echo "Couldn't update $AUTH" >&2; exit 1; }

echo "Done. CI can now update $SITE_DIR and $EMAILS_DIR. Make sure docker-compose.yml mounts $EMAILS_DIR:"
echo "  cd $HERE && sudo docker compose up -d"
