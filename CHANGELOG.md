# Changelog

Every version of Musix, newest first. Each section is also shown as the notes on its GitHub release.
To publish a new version: add a section at the top, set the same version in `version.txt`, and push.

## 0.0.11beta — 2026-10-16

### Fixed
- **The app crashing (out of memory) and covers taking half a minute on albums with an animated WebP cover.** Navidrome only shrinks animated GIFs; an animated WebP comes back as the full original (here 1080×1080, several MB) whatever size is asked for. Musix's "300 px" cover was really the whole file, and the album page, now playing, the blurred background, the colour sampling, list tiles and the lock screen each downloaded it and decoded it as an animation of its own, which filled the 256 MB the app may use. Now:
  - an animated cover that Navidrome sends full size is downloaded **once**, kept on the phone, and a still of its first frame is made from it. Every other request for that cover (any size, the song's cover too) is answered from the phone without the network;
  - requests for the same cover that arrive together wait for that one download instead of each starting their own;
  - lists, backgrounds, colours and the lock screen always get a still picture. Like Symfonium, covers only move on the album page and in now playing, and the animation is decoded at screen size (720 px) rather than the file's full size;
  - only the last few animations are kept in memory.
- The lock screen and notification cover now come from Musix's image cache instead of being downloaded again in full on every song change.

### Added
- Settings → Storage → **Download all covers**: fetches every album, artist and playlist cover in the background, at the sizes the app shows, after each library sync and once a day. Only on Wi-Fi, and by default only when the phone is on the same local network as Navidrome (switch on "On any Wi-Fi" if you reach Navidrome by a domain name). Covers already on the phone are skipped, and it stops by itself when you leave home. Progress and a **Download covers now** button are on the same page.
- Image cache size **No limit**. With no limit, images are kept in the app's own storage, so Android doesn't clear them when the phone runs low on space. Switching keeps what's already downloaded.
- Device tests now include an album with a 1080 px animated WebP cover: it must be downloaded exactly once while the album page, now playing, background, colour and a list ask for it at the same time, lists must get a still, and the album page must still animate.

## 0.0.10beta — 2026-10-16

### Fixed
- **Playback stopping and starting, and blank covers, on albums with animated covers.** Found by running a real Navidrome 0.64.2 behind a link limited to 4.3 Mbit/s (what the phone measured):
  - Musix downloaded the album's original animated cover.gif (often over 10 MB), sometimes several copies at once (album page, now playing, the animation check). That took most of the connection: the song got about 1,100 kbit/s while FLAC needs about 1,370, so it kept stopping.
  - For every cover size requested, Navidrome converts an animated GIF with ffmpeg. 300 px worked (about 5 s), but 600 and 1000 px failed and returned no image, which is why big covers were blank, and each new request started the conversion again.
- Now Musix reads only the first bytes of an album's cover to see if it's animated, then shows the original only when it's small, otherwise Navidrome's 300 px animated version, which is converted once and kept. Big sizes are never asked for on animated albums, the result is remembered across app starts, and lists use one cover size so each album is converted only once.
- Animated covers now move on the album page and in now playing.

### Changed
- Songs, covers and API calls each use their own connections, so cover downloads can't hold up a song.
- If a song waits 8 seconds for data that's arriving slower than it needs, Musix switches to Opus straight away instead of after three stops.
- The log shows the Navidrome version, large cover downloads, how long Navidrome took to prepare an animated cover, and what each animated cover was shown as.

### Added
- Automated device tests: on every push, the app runs on an Android emulator against a real Navidrome with a test library (a 13 MB animated cover, a small one, a still one, and 5-minute FLACs) behind a 4.3 Mbit/s link. They check that animated covers are detected and animate on screen, and that a song starts within seconds and plays without stopping while an animated album is open. A control test confirms the old behaviour does stall.

## 0.0.9beta — 2026-10-16

### Fixed
- Songs started slowly and then played a few seconds, stopped, and started again. The log showed the connection to Navidrome delivering only about 250–335 kbit/s while the FLAC files need roughly 900–1,400 kbit/s, and Musix made it worse by downloading the next song at the same time. Now:
  - the next song is only pre-cached once the current one has fully downloaded, so they never share the connection;
  - after running dry mid-song, playback waits for 5 seconds of audio instead of 2, so there are fewer, longer stretches;
  - if the original still stops three times in two minutes, Musix carries on in Opus at a bitrate the connection can manage, from the same spot, and tries the original again after 15 minutes. This can be switched off under Settings → Playback.
- Personal and vibe mixes were cancelled half-way when you left Home while they were being made.

### Added
- The log now shows each stream's size and the bitrate it needs, how fast data is actually arriving (every few seconds with detailed logging), the format being streamed, and when the next song starts pre-caching. Every "Waited … for data" line includes arriving vs needed speed.
- **Settings** is now a tab on the far right of the bottom bar (and no longer a cog on Home).

### Changed
- The bottom bar is see-through: pages scroll on underneath it and fade out behind the tabs.
- The mini player is a floating card tinted with the playing song's cover colour, with a white progress line, matching now playing.

## 0.0.8beta — 2026-10-16

### Added
- **Your listening**: Wrapped-style pages for this month, last month and this year. They show minutes and plays, top artists, songs (playable) and albums, your listening clock by hour (early bird, night owl…), and how your taste moved: artists new in your rotation, ones that dropped out, and genres you played more or less. Built from Koito when you scrobble there, otherwise from Musix's own history.
- **Rediscover** on Home: albums you played a lot but not for at least five months, from Navidrome's play counts and your Koito favourites from 6 to 24 months ago. Each shows when you last played it. The old random shelf is now called **Random picks**.

### Fixed
- Animated covers were found (the log said so) but still didn't move. They're now drawn by Android's own image view with the animation started explicitly, and the log notes whether each animated cover arrived animated or as a still frame.

## 0.0.7beta — 2026-10-16

### Fixed
- Animated covers didn't move. Now playing asked Navidrome for the song's own picture, which is always the still image embedded in the file; the animated cover.gif / cover.webp in the album folder is only used for the album. Musix now checks whether the album has an animated cover and, if so, shows it in now playing and on the album page, at full size so older Navidrome versions don't flatten it. Albums without one keep each song's own cover.

### Added
- Settings → Storage → **Animated covers** switch (on by default).

## 0.0.6beta — 2026-10-16

### Added
- **Personal mixes** under Made for you, made fresh each day:
  - **Your sound**: AudioMuse's sonic fingerprint of what you play most in Navidrome.
  - **On repeat**: your most-played songs lately, from Musix's history and your Koito / ListenBrainz / Last.fm top tracks.
  - **Discover for you**: songs you haven't played that sound like your favourites, leaning away from artists you already play a lot.
- **Taste profile** built on the phone from Musix's own history, Koito or ListenBrainz and Last.fm top artists this month, and Navidrome's most-played albums. Only a few artist names ever go to the AI, never your history.
- Musix now notices skips (under 30 seconds). Songs skipped twice in a month, or once in the last few days, are left out of mixes.

### Changed
- Vibe mixes lean towards artists you play, and those you usually play at this time of day. The AI also gets your favourite artists and time-of-day habits as hints.
- Home only shows what fits now: the vibe for this hour and your personal mixes. This morning's mix no longer shows up in the evening, and the page moves on to the next vibe by itself when the hour changes.
- Background mix preparation also makes the personal mixes, so they're ready when you open the app.

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
