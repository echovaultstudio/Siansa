# Musix

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

Outside your home network the phone needs to reach both servers, for example over a VPN such as WireGuard or Tailscale.

Passwords, tokens and the API key are stored encrypted with a key from the Android Keystore and are excluded from backups.

## Building

GitHub Actions builds a signed APK on every push to `main`. The version comes from `version.txt` (e.g. `0.0.3beta`). To publish a new release, add a section `## <version>` at the top of `CHANGELOG.md` with what was added, changed and fixed, set the same version in `version.txt`, and push: the release `v<version>` gets `musix-<version>.apk` and that changelog section as its notes. Edits to older changelog sections are copied to their releases too. The signing key is in `app/musix.keystore`, so each new build installs over the previous one. Keep this repository private.

To build locally: `gradle :app:assembleRelease` with Gradle 8.11+, JDK 17 and the Android SDK (platform 35).
