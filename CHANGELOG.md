# Changelog

Every version of Musix, newest first. Each section is also shown as the notes on its GitHub release.
To publish a new version: add a section at the top, set the same version in `version.txt`, and push.

## 0.0.5beta — 2026-10-16

### Changed
- Snappier everywhere: now playing opens and closes faster, page changes are quick fades, the queue panel, lyrics switch, cover swipe and seek thumb respond faster, and covers fade in quicker.
- The app is now optimised when it's built (unused code removed, the rest optimised), and ships a startup profile so Android compiles it ahead of time. Starting, scrolling and opening pages should be smoother.
- The log's format line also names the file type (container), e.g. MP4 with ALAC inside.

### Fixed
- Text and buttons in now playing were dark and hard to read.

## 0.0.4beta — 2026-10-16

### Added
- **ALAC playback**: Apple Lossless songs now play, through FFmpeg decoders built into the app, for formats the phone can't decode itself. They still play lossless and untouched.
- If a song's format still can't be decoded, it's played as Opus 320 kbit/s converted by Navidrome instead of skipping silently, and the log says which format it was.
- Swipe the cover in now playing left for the next song and right for the previous one.
- Press and hold play (in now playing or the mini player) to stop and clear the queue.
- Songs with several artists ("feat.", "&", …) ask which artist to open, with each artist's photo in a small circle. Works from now playing and from "Go to artist" in song menus.
- The log records each song's format and which decoder plays it.

### Changed
- Now playing slides up over the page you were on and slides back down to it. Dragging down moves the player with your finger; let go part-way or flick to close it.
- The queue closes by swiping down anywhere on it: the list scrolls back to the top first, then the panel follows your finger down.
- Faster, smoother cross-fades between pages.

### Fixed
- Some songs (ALAC) didn't play: the player found no decoder for them and loaded the next songs in the queue one after another in the background.

## 0.0.3beta — 2026-10-16

### Added
- **Song radio** from now playing and from any song's menu: AudioMuse finds songs that sound like the current one and share its mood, replaces Up next without interrupting the song, and keeps adding more as the queue runs low. Tap **Radio on** to stop it. Falls back to Navidrome's similar songs when AudioMuse isn't set up.

### Changed
- Now playing has a simpler seek bar: a thin line with a round thumb that grows while dragging, and the remaining time on the right. The mini player shows a thin progress line.
- Playback always asks Navidrome for the original, untouched file unless Opus is chosen under Playback, even if Navidrome has a default transcoding set for new players.
- Large covers and artist photos are fetched at 1000 px, so Navidrome sends them resized and compressed. Animated covers still move.

### Fixed
- Song starts were slowed down by the next song downloading at the same moment. Pre-caching the next song now waits 20 seconds.

## 0.0.2beta — 2026-10-16

### Added
- **Detailed logging** switch under Settings → Logs, for tracking down stutter: buffer waits and audio gaps, download speeds, song cache hits, slow server responses, a frozen-screen watchdog with what the app was doing, dropped frames, memory and phone temperature.

## 0.0.1beta — 2026-10-16

The first beta. Everything built so far:

### Added
- Library synced from Navidrome into a database on the phone, with a choice of Navidrome libraries, Symfonium-style progress, and quick incremental syncs at every start.
- Home in a streaming-app style with tiles for daily mixes, vibes, mood and genre radios, and your Navidrome playlists.
- Daily mixes and time-of-day **vibes** made on the phone with AudioMuse, never saved as Navidrome playlists. OpenRouter (optional) only gets a short prompt to name each mix and pick AudioMuse searches, never the library.
- **Describe your mood** to get a playlist on the spot.
- Artist pages with Navidrome's photo, biography, popular songs, similar artists and a discography grouped by release type (Albums, EPs, Singles, Live, Compilations, DJ-mixes and more, from the `releasetype` / album types tags).
- Album and playlist pages with a full-width image that blends away as you scroll.
- Now playing with a blurred cover background, lyrics, tap the cover to open the album, and swipe up for the queue.
- Radio and genre tiles open a preview first, with play and shuffle.
- Artist radios that start at once and keep filling.
- Hearts synced to Navidrome and loved on Last.fm.
- Scrobbling to Navidrome, Last.fm and ListenBrainz-compatible servers such as Koito.
- Optional Opus transcoding with separate Wi-Fi and mobile quality.
- Song cache and image cache with sizes you choose under Settings → Storage.
- ReplayGain, background playback and lock-screen controls.
- Optional logs that can be saved to a file and shared.
- Musix's own amber-and-plum colours.
- Versions come from `version.txt`; each version gets one release.

### Fixed
- Crash "Comparison method violates its general contract" when making mixes.
- Albums shown twice when they exist in more than one library.
- Proxy login (HTTP auth) failing with an invalid port error.
