# Self-hosted CI runner (issue #218)

Musix's CI runs on GitHub's own runners by default. A server of your own can take over the **build** job
(build, Lint, unit tests) and, if it has KVM, the **emulator tests**. Two repository variables switch a job
over; delete a variable and that job goes back to GitHub. Nothing changes until you set them.

| Job | Minutes (last 30 days) | Moves with |
|---|---|---|
| Build, lint and unit tests | ~2,900 | `BUILD_RUNNER` |
| Emulator tests against Navidrome | ~1,800 | `EMULATOR_RUNNER` (needs KVM) |
| Secret scan, release | ~400 | stay on GitHub (small; the scan uses Docker) |

## 1. The server

- **x86-64 only.** Android's build tools (aapt2) and emulator have no Linux ARM builds.
- **8 GB RAM, 4 vCPU, 40 GB+ disk.** Gradle alone is set to 4 GB (`gradle.properties`); the emulator adds ~2 GB.
- **Ubuntu 24.04**, freshly installed.
- **KVM for the emulator tests.** Check before you buy or right after: `ls -l /dev/kvm` (or `kvm-ok`). Most
  cloud VMs, including shared cloud plans, don't pass virtualisation through. Without it, use the server for
  the build only; a dedicated / bare-metal server has it.
- Only outgoing connections are needed (to GitHub, Google's SDK download, Maven). No ports to open.

## 2. Install

1. On GitHub: **Settings → Actions → Runners → New self-hosted runner**. Copy only the **token** from the
   `./config.sh --url … --token XXXX` line (valid for one hour).
2. On the server:
   ```bash
   git clone https://github.com/Musix-Industreez/musix.git   # or copy ci/self-hosted/setup.sh over
   cd musix/ci/self-hosted
   sudo ./setup.sh <token> musix-runner-1
   ```
   It checks the server (x86-64, memory, disk, KVM), installs Java 17, ffmpeg and the Android SDK
   (platform 36, and with KVM the emulator and the API 33 image the tests use), adds 4 GB of swap, creates a
   `runner` user (no sudo) and registers the runner as a service with these labels:
   - always: `self-hosted`, `Linux`, `X64`, `musix`, `build`
   - with KVM: also `emulator`

   It also adds a weekly clean-up of old Gradle caches and turns on the firewall (SSH in only).
3. The runner shows as **Idle** under Settings → Actions → Runners.

## 3. Switch CI over

GitHub: **Settings → Secrets and variables → Actions → Variables → New repository variable**

| Name | Value |
|---|---|
| `BUILD_RUNNER` | `["self-hosted","musix","build"]` |
| `EMULATOR_RUNNER` | `["self-hosted","musix","emulator"]` (only with KVM) |

The next run uses it. **To go back:** delete the variable.

What changes for a job on the server (`.github/workflows/ci.yml`):
- GitHub-only steps (freeing disk space, enabling KVM, installing ffmpeg with sudo) are skipped.
- The test Navidrome and its slow-link proxy are stopped after each emulator run, and any left over from a
  cancelled run are stopped before the next (`scripts/test-server.sh`).
- The emulator image and Gradle caches stay on the server, so later runs start faster than on GitHub.

## 4. Looking after it

- **Updates:** `sudo apt update && sudo apt upgrade` now and then. The runner updates itself.
- **Disk:** `df -h /`. Gradle caches older than 30 days go weekly; `rm -rf ~runner/.gradle/caches` is
  always safe (the next build downloads again).
- **Logs:** `journalctl -u 'actions.runner.*' -f`
- **Re-register** (new server name, lost registration): run `setup.sh` again with a new token.
- **Remove:** `cd /home/runner/actions-runner && sudo ./svc.sh uninstall && sudo -u runner ./config.sh remove --token <removal token>`,
  then delete the variables.

## Security

The runner runs whatever the workflow and the code pushed to this repository tell it to. That's fine for a
private repository that only you (and Claude, on your behalf) push to. Keep it that way:
- Don't make the repository public while a self-hosted runner is attached, and don't run it for forks.
- Keep the server for CI only: no other services, no secrets on it besides the runner's own registration.
- Jobs run as the `runner` user without sudo.
