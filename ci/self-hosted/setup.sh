#!/usr/bin/env bash
# Sets up a self-hosted GitHub Actions runner for Musix (issue #218) on a fresh Ubuntu 24.04 x86-64 server.
#
#   sudo ./setup.sh <registration token> [runner name]
#
# The token comes from GitHub: the repository's Settings → Actions → Runners → New self-hosted runner (it's
# valid for an hour). Run it again with a new token to re-register; everything else is skipped when present.
# See README.md next to this file for the whole procedure and how to switch CI over to the runner.
set -euo pipefail

TOKEN="${1:?Usage: sudo ./setup.sh <registration token> [runner name]}"
NAME="${2:-musix-$(hostname -s)}"
REPO_URL="https://github.com/Musix-Industreez/musix"
RUNNER_USER="runner"
RUNNER_HOME="/home/${RUNNER_USER}"
RUNNER_DIR="${RUNNER_HOME}/actions-runner"
ANDROID_HOME="/opt/android-sdk"
# The emulator the tests use (app/build.gradle.kts: managed device "ciPhone", Pixel 6, API 33, AOSP image).
SYSTEM_IMAGE="system-images;android-33;default;x86_64"
COMPILE_SDK="platforms;android-36"
# Android command-line tools; the current number is on https://developer.android.com/studio#command-line-tools-only
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
SWAP_GB=4

say() { printf '\n== %s\n' "$*"; }
[ "$(id -u)" = 0 ] || { echo "Run as root (sudo)."; exit 1; }

say "Checking the server"
[ "$(uname -m)" = "x86_64" ] || { echo "Needs an x86-64 server: Android's build tools and emulator don't run on ARM Linux."; exit 1; }
MEM_GB=$(awk '/MemTotal/ {printf "%d", $2/1024/1024 + 0.5}' /proc/meminfo)
DISK_GB=$(df -BG --output=avail / | tail -1 | tr -dc 0-9)
echo "Memory: ${MEM_GB} GB · free disk: ${DISK_GB} GB · CPUs: $(nproc)"
[ "$MEM_GB" -ge 7 ] || echo "WARNING: under 8 GB of memory. The build alone wants 6-8 GB; expect swapping."
[ "$DISK_GB" -ge 35 ] || echo "WARNING: under 40 GB free. The SDK, emulator image and Gradle caches take ~25 GB."
if [ -e /dev/kvm ]; then
  KVM=yes
  echo "KVM: available, so this runner can also run the emulator tests."
else
  KVM=no
  echo "KVM: NOT available (no /dev/kvm). The Android emulator is far too slow without it: use this runner"
  echo "     for the build only (BUILD_RUNNER) and keep the emulator tests on GitHub. See README.md."
fi

say "Packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl git unzip zip jq python3 ffmpeg openjdk-17-jdk-headless ufw \
  libpulse0 libnss3 libxcomposite1 libxcursor1 libxi6 libxtst6 libxdamage1 libxrandr2 libasound2t64 libgl1 > /dev/null
[ "$KVM" = yes ] && apt-get install -y -qq qemu-kvm cpu-checker > /dev/null && (kvm-ok || true)

say "Swap (${SWAP_GB} GB), so a memory peak slows the build instead of killing it"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l "${SWAP_GB}G" /swapfile && chmod 600 /swapfile && mkswap /swapfile > /dev/null && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

say "Runner user '${RUNNER_USER}' (no sudo; jobs run as this user)"
id "$RUNNER_USER" >/dev/null 2>&1 || useradd -m -s /bin/bash "$RUNNER_USER"
if [ "$KVM" = yes ]; then
  getent group kvm >/dev/null && usermod -aG kvm "$RUNNER_USER"
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0660"' > /etc/udev/rules.d/99-kvm-runner.rules
  udevadm control --reload-rules && udevadm trigger --name-match=kvm || true
fi

say "Android SDK in ${ANDROID_HOME}"
if [ ! -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "${ANDROID_HOME}/cmdline-tools" && cd /tmp
  curl -sSLo cmdline-tools.zip "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
  unzip -q -o cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" && rm cmdline-tools.zip
  rm -rf "${ANDROID_HOME}/cmdline-tools/latest" && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
fi
SDKM="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --sdk_root="$ANDROID_HOME" --licenses > /dev/null || true
"$SDKM" --sdk_root="$ANDROID_HOME" "platform-tools" "$COMPILE_SDK" > /dev/null
# The emulator and its image are only worth the 6 GB with KVM.
[ "$KVM" = yes ] && "$SDKM" --sdk_root="$ANDROID_HOME" "emulator" "$SYSTEM_IMAGE" > /dev/null
chown -R "${RUNNER_USER}:${RUNNER_USER}" "$ANDROID_HOME"

say "GitHub Actions runner"
LABELS="musix,build"
[ "$KVM" = yes ] && LABELS="${LABELS},emulator"
sudo -u "$RUNNER_USER" mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"
if [ ! -x ./config.sh ]; then
  VERSION=$(curl -sSL https://api.github.com/repos/actions/runner/releases/latest | jq -r .tag_name | sed 's/^v//')
  echo "Runner version ${VERSION}"
  sudo -u "$RUNNER_USER" curl -sSLo runner.tgz "https://github.com/actions/runner/releases/download/v${VERSION}/actions-runner-linux-x64-${VERSION}.tar.gz"
  sudo -u "$RUNNER_USER" tar xzf runner.tgz && rm runner.tgz
  ./bin/installdependencies.sh > /dev/null
fi
# What every job sees: where the SDK is.
cat > .env <<ENV
ANDROID_HOME=${ANDROID_HOME}
ANDROID_SDK_ROOT=${ANDROID_HOME}
LANG=C.UTF-8
ENV
chown "${RUNNER_USER}:${RUNNER_USER}" .env
[ -f .runner ] && ./svc.sh stop >/dev/null 2>&1 || true
[ -f .runner ] && ./svc.sh uninstall >/dev/null 2>&1 || true
sudo -u "$RUNNER_USER" ./config.sh --unattended --replace --url "$REPO_URL" --token "$TOKEN" \
  --name "$NAME" --labels "$LABELS" --work _work
./svc.sh install "$RUNNER_USER" > /dev/null
./svc.sh start > /dev/null
echo "Runner '${NAME}' registered with labels: self-hosted, Linux, X64, ${LABELS}"

say "Weekly clean-up (Sunday 04:30): old Gradle caches and the runner's work folder"
cat > /etc/cron.d/musix-runner-cleanup <<CRON
30 4 * * 0 ${RUNNER_USER} find ${RUNNER_HOME}/.gradle/caches -mindepth 1 -maxdepth 1 -mtime +30 -exec rm -rf {} + ; rm -rf ${RUNNER_DIR}/_work/_temp/* 2>/dev/null
CRON

say "Firewall: SSH in, nothing else (the runner only connects out to GitHub)"
ufw allow OpenSSH > /dev/null && ufw --force enable > /dev/null

say "Done"
cat <<NEXT
Next, in GitHub (repository Settings → Secrets and variables → Actions → Variables):
  BUILD_RUNNER     = ["self-hosted","musix","build"]
$( [ "$KVM" = yes ] && echo '  EMULATOR_RUNNER  = ["self-hosted","musix","emulator"]' || echo '  (no EMULATOR_RUNNER: this server has no KVM, the emulator tests stay on GitHub)' )
Delete a variable to move that job back to GitHub's runners. The runner shows as Idle under Settings → Actions → Runners.
NEXT
