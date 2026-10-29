# AudioMuse-AI and the Siansa plugin

[AudioMuse-AI](https://github.com/NeptuneHub/AudioMuse-AI) runs on your own server next to your music server. It
listens to every song once (its "analysis") and can then answer questions like "what sounds like this song?" or
"find songs that sound like *rainy jazz piano*". Siansa asks it those questions. Your music never leaves your
servers.

The **Siansa Analysis plugin** adds two things to AudioMuse-AI that Siansa needs: each song's measured loudness and
how it starts and ends, and similar albums and artists.

## What needs what

| Feature | Needs |
|---|---|
| Today's mix, vibes, Home carousel | AudioMuse-AI (text search) |
| Search by sound, Flow | AudioMuse-AI (text search) |
| Mood radios (Chill, Focus…), Wind down | AudioMuse-AI (text search) |
| Song radio, similar songs | AudioMuse-AI, or Navidrome's sonic similarity, or (simpler) Navidrome's similar songs / Emby's Instant Mix |
| Keep playing in DJ mode | AudioMuse-AI (tempo and key) |
| Song path | AudioMuse-AI, or the AudioMuse-AI Navidrome plugin |
| Your sound, sonic fingerprint | AudioMuse-AI |
| Similar albums, similar artists ("Fans also like") | Siansa Analysis plugin |
| Levelling from measured loudness, smart crossfades, beat-matched blends | Siansa Analysis plugin (otherwise ReplayGain tags and fixed crossfades) |

## Set up AudioMuse-AI in Siansa

**Settings › Connections › AudioMuse-AI**:

1. **Server address**, for example `http://192.168.1.10:8000`.
2. If AudioMuse-AI has a login: either an **API token**, or its **user name and password**.
3. **Server name in AudioMuse**: only if AudioMuse-AI serves more than one music server (see below).
4. **Test** runs a text search ("calm piano") to check it all works, then **Save**.

The AudioMuse-AI connection is the same for every user. The server name is kept per user (see below).

## Install the Siansa Analysis plugin

1. In AudioMuse-AI, open **Plugins › Repositories** and add:
   ```
   https://raw.githubusercontent.com/echovaultstudio/siansa-audiomuse-plugin/main/manifest.json
   ```
2. Open the **Catalog** tab, install **Siansa Analysis**, and apply the restart.
3. AudioMuse-AI checks for new versions by itself. Update from **Plugins** when one is offered. Your measurements
   are kept across updates.

It needs AudioMuse-AI 3.6.4 or later and works on the standalone builds too.

**What it does after installing:**

- **Volume and fade profiles.** Each song is measured right after AudioMuse-AI analyses it. Older songs are
  measured a batch at a time every 15 minutes (the plugin's scheduled task). To measure the whole library at
  once, use **Measure all songs** on the plugin's settings page (Plugins › Siansa Analysis › Settings). On a large
  library that takes hours or days, and AudioMuse-AI pauses its own jobs while a round runs.
- **Similar albums and artists.** Built from AudioMuse-AI's analysis, and built again by itself when your library
  or the analysis changes.

Until a song is measured, Siansa uses its ReplayGain tags, and crossfades use the fixed length.

Plugin 0.3.0 also measures the beat grid at each end of a song (tempo, where the beats and bars fall, how
beat-driven and how steady the end is), for [beat-matched blends](playback.md#beat-matched-blends-experimental).
Songs measured by an older version keep their loudness and fades and are measured again for their beats. The
plugin's settings page (0.3.1) has a **Beat grids** section for that: how many songs have their grids and how many
are waiting, its own **Songs per run** (any number, since a round stops when its time is up; 0 to measure them
only when you ask), **Measure a batch now**, and **Measure all** with **Stop**. Each round measures the volume and
fade profiles first and the beat grids in the time left; songs Siansa is about to play come first. A song that
can't be read is tried again later (after an hour, 6 hours, a day, then 3 days); the page says why songs failed and
has **Try them all again now** (0.3.2). Songs on none of AudioMuse's music servers (removed, or not matched yet)
are counted apart.

## Several music servers in one AudioMuse-AI

AudioMuse-AI 3 can serve several music servers (for example Navidrome and Emby). Each one has a **display
name** in AudioMuse-AI (Setup › Music Servers). Siansa must name the right server in every request, or AudioMuse
answers with the other server's songs or says it doesn't know the song.

In **Settings › Connections › AudioMuse-AI**, **Server name in AudioMuse** is the name of this user's server,
as AudioMuse-AI shows it (capitals don't matter). Leave it empty for AudioMuse-AI's default server. Each user keeps
their own: switch to your Emby user and enter the Emby server's name there, and to your Navidrome user for
Navidrome's. Users shown together are always on one server, so one name covers them.

**AudioMuse-AI must know each server's songs.** It links songs on a second server to its analysis either by
analysing that server too, or by **Align** in its setup, which matches songs by path and tags. Songs it couldn't
match can't be used for radios and similar music. If Siansa says "AudioMuse doesn't know this song", see
[Troubleshooting](troubleshooting.md#audiomuse-doesnt-know-this-song).

The Siansa plugin measures songs on every server: it fetches each song from whichever server has it (0.3.3).
AudioMuse-AI runs the plugin's 15-minute task once per server; the first run does the work and the others stop
straight away, so more servers don't make it take longer.
