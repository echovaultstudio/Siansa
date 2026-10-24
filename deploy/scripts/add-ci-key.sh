#!/bin/sh
# Lets CI publish APKs to the F-Droid repo (issue #410), and nothing else, with one SSH key:
#   sudo sh scripts/add-ci-key.sh "ssh-ed25519 AAAA… siansa-ci"
# Make the key pair on your own computer: ssh-keygen -t ed25519 -N "" -C siansa-ci -f siansa-ci
# The private half (siansa-ci) goes into the GitHub secret FDROID_SSH_KEY; the public half is given here.
set -eu

KEY="${1:?Give the public key in quotes}"
case "$KEY" in ssh-ed25519\ *|ssh-rsa\ *|ecdsa-sha2-*) ;; *) echo "That doesn't look like a public key" >&2; exit 1 ;; esac

HOME_DIR=$(getent passwd siansa-deploy | cut -d: -f6)
mkdir -p "$HOME_DIR/.ssh"
# restrict: no shell, port forwarding, agent or terminal; the only thing it can do is siansa-publish.
echo "command=\"/usr/local/bin/siansa-publish\",restrict $KEY" > "$HOME_DIR/.ssh/authorized_keys"
chown -R siansa-deploy:siansa-deploy "$HOME_DIR/.ssh"
chmod 700 "$HOME_DIR/.ssh"
chmod 600 "$HOME_DIR/.ssh/authorized_keys"

echo "Done. For GitHub, also add the secrets:"
echo "  FDROID_HOST        = this server's name (e.g. $(hostname -f))"
echo "  FDROID_KNOWN_HOSTS = the output of: ssh-keyscan -t ed25519 <that name>"
