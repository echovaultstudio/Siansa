# Siansa

<p align="center"><img src="docs/brand/siansa-banner.png" alt="Siansa" width="720"></p>

A personal Android music player for **Navidrome**, with daily mixes built on the phone from **AudioMuse-AI** text search and, optionally, titles and searches written by an **OpenRouter** model.

## What it does

- **Library**: albums (A–Z, recently added, most played, random), artists, album and artist pages, search.
- **Player**: background playback with notification and lock-screen controls, gapless playback, shuffle and repeat, a queue, ReplayGain (track or album, with pre-amp), and scrobbling back to Navidrome.
- **Lyrics**: synced lyrics from Navidrome's OpenSubsonic API that scroll and highlight along with the song (tap a line to jump), plain lyrics otherwise.
- **Animated covers**: animated cover.gif / cover.webp move on the album page and in now playing (lists show the first frame). An animated WebP, which Navidrome can't shrink, is downloaded once and kept on the phone.
- **Covers offline**: optionally download every album, artist and playlist cover in the background on your home Wi-Fi, with an image cache that can have no size limit.
- **Vibes and daily mixes**: each vibe covers part of the day (Morning, Daytime, Evening, Night by default). When you open the app, the vibe for the current hour builds today's mix:
  1. If AI is on, OpenRouter gets a small prompt: the vibe's guidance, its forbidden words, the day and time, the audio device (car Bluetooth, headphones…), your last 20 plays and the sound concepts AudioMuse understands. It answers with a title, a sound search and a one-line description. **Your library is never sent.**
  2. The answer is rejected if it uses a forbidden word, and the vibe's fallback search is used instead.
  3. AudioMuse's text search finds matching tracks by sound. The app picks a varied set, skips songs from recent days, limits tracks per artist and avoids the same artist twice in a row.
  4. The mix is saved **only on the phone**. Nothing is written to Navidrome's playlists.
- **Sound search**: the Search tab can also describe a sound ("rainy jazz piano") and play what AudioMuse finds.

## Setup

1. Install the latest APK from the repo's **Releases** page.
2. In Settings, enter:
   - **Navidrome**: address (e.g. `http://192.168.1.10:4533`), username, password.
   - **AudioMuse-AI**: address of its web/API port (e.g. `http://192.168.1.10:8000`) and its API token if authentication is on. Text search (CLAP) must be enabled.
   - **OpenRouter** (optional): API key and model id (default `google/gemini-2.5-flash`; any OpenRouter model id works).
3. Use **Test** next to each section, then **Save**.
4. Optional: install the **Siansa plugin** on AudioMuse-AI (see [`audiomuse-plugin/`](audiomuse-plugin/README.md)) and choose it
   under Settings › Crossfade and levelling. It measures each song's loudness and how it starts and ends, for
   levelling and smooth crossfades, and finds similar albums and artists.

Outside your home network the phone needs to reach both servers, for example over a VPN such as WireGuard or Tailscale.

Passwords, tokens and the API key are stored encrypted with a key from the Android Keystore and are excluded from backups.

## Quality checks

Every push to `main` and every pull request runs `.github/workflows/ci.yml`:

| Check | What it catches |
|---|---|
| **Build** (`assembleRelease`) | Syntax and type errors; the release APK must compile and shrink with R8. |
| **Unit tests** (`app/src/test`) | Mistakes in the app's own logic: server addresses, cover detection, ReplayGain, release types, name matching, the home-network check. Run in seconds, no phone needed. |
| **Plugin tests** (`audiomuse-plugin/tests`) | Mistakes in the AudioMuse-AI plugin: loudness against the BS.1770 reference, fade shapes, the ramp layout the app reads, similar lists and when they rebuild. |
| **Android Lint** | Likely crashes, wrong Android API use for older versions, and security issues. Errors appear as annotations on the commit. |
| **Secret scan** (gitleaks, `.gitleaks.toml`) | Passwords, tokens or API keys committed anywhere in the history. |
| **Emulator tests** (`app/src/androidTest`) | The app on an Android emulator against a real Navidrome behind a phone-speed link: animated covers downloaded once and shown as stills in lists, songs playing while covers load, and a tour of every page that plays songs from several of them. Screenshots of every page and the app's log go to the `ci-screens` branch. They run after each merge to main and nightly, not on pull requests, and report problems without holding back releases (song start times on the throttled emulator vary too much to fail on; slow starts are listed in the run). |
| **Dependabot** (`.github/dependabot.yml`) | Weekly pull requests for outdated libraries and alerts for known vulnerabilities in them. |

A release is published from main when the build, Lint, unit tests and secret scan pass. A failing check shows a red cross on the commit, and its details are in the run's annotations.

Inside the app, with **Settings → Logs → Keep a log** on, the log records each start (version, phone, settings that change behaviour), why the app last closed (crash, freeze, killed for memory, swiped away; Android 11+), every error in background work, playback decisions, downloads and covers. **Problems** on the Logs page shows only warnings and errors.

## Building

GitHub Actions builds a signed APK on every push to `main` and publishes it once all quality checks pass. The version comes from `version.txt` (e.g. `0.0.3beta`). Each change adds a note in `changelog.d/` (see its README). When changes are merged to `main` and the checks pass, CI raises the version by the size of the change (fixes 0.1.3 → 0.1.4, new features 0.1.3 → 0.2.0, 1.0 only when asked for; see `changelog.d/README.md`), publishes `v<version>` with `siansa-<version>.apk` and the notes, and records the section in `CHANGELOG.md` and the version in `version.txt`. Edits to older changelog sections are copied to their releases too. `RELEASES.md` lists every release with the pull requests in it and is updated after each release. The signing key is in `app/siansa.keystore`, so each new build installs over the previous one. Keep this repository private.

To build locally: `gradle :app:assembleRelease` with Gradle 8.14+, JDK 17 and the Android SDK (platform 36).
