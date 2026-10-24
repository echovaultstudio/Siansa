#!/bin/sh
# Basic hardening for the Siansa beta server (issue #410), Debian or Ubuntu, as root. Safe to run again.
#   sudo sh scripts/harden.sh
# SSH password logins are switched off only when NO_PASSWORD_SSH=1 is given, after you've checked that your own
# key login works (otherwise you could lock yourself out).
# Firewall: set SKIP_FIREWALL=1 if the hosting provider's firewall already does this (ufw is then left alone).
set -eu

SSH_PORT="${SSH_PORT:-22}"

if [ "${SKIP_FIREWALL:-0}" = 1 ]; then
	echo "== Firewall: skipped (SKIP_FIREWALL=1), the provider's firewall is used"
else
	echo "== Firewall: SSH, HTTP, HTTPS (and HTTP/3) only"
	ufw default deny incoming
	ufw default allow outgoing
	ufw limit "$SSH_PORT"/tcp comment 'SSH, rate limited'
	ufw allow 80/tcp comment 'HTTP (redirects to HTTPS, certificates)'
	ufw allow 443/tcp comment 'HTTPS'
	ufw allow 443/udp comment 'HTTP/3'
	ufw --force enable
	# Docker publishes ports past ufw's rules; only Caddy publishes any (80/443), Navidrome has none of its own.
fi

echo "== fail2ban for SSH"
cat > /etc/fail2ban/jail.d/siansa.local <<EOF
[sshd]
enabled = true
port = $SSH_PORT
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl enable --now fail2ban
systemctl restart fail2ban

echo "== Automatic security updates"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<EOF
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
systemctl enable --now unattended-upgrades

echo "== SSH"
mkdir -p /etc/ssh/sshd_config.d
{
	echo "PermitRootLogin prohibit-password"
	echo "MaxAuthTries 4"
	echo "X11Forwarding no"
	echo "AllowAgentForwarding no"
	if [ "${NO_PASSWORD_SSH:-0}" = 1 ]; then
		echo "PasswordAuthentication no"
		echo "KbdInteractiveAuthentication no"
	fi
} > /etc/ssh/sshd_config.d/50-siansa.conf
sshd -t
systemctl reload ssh 2>/dev/null || systemctl reload sshd
[ "${NO_PASSWORD_SSH:-0}" = 1 ] || echo "Password logins still allowed: run again with NO_PASSWORD_SSH=1 once your key works."

echo "== Docker logs kept small"
if [ ! -f /etc/docker/daemon.json ]; then
	echo '{ "log-driver": "local", "log-opts": { "max-size": "10m", "max-file": "3" } }' > /etc/docker/daemon.json
	systemctl restart docker
fi

echo "Done. Check: fail2ban-client status sshd (and ufw status verbose, unless SKIP_FIREWALL=1)"
