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

**Settings › Servers and services › Services › AudioMuse-AI**:

1. **Server address**, for example `http://192.168.1.10:8000`.
2. If AudioMuse-AI has a login: either an **API token**, or its **user name and password**.
3. **Test** runs a text search ("calm piano") to check it all works, then **Save**.

Only if AudioMuse-AI serves more than one music server: **Server name in AudioMuse**, in the card of your music
server under **Servers** (see below).

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
beat-driven and how steady the end is), for [beat-matched blends](playback.md#beat-matched-blends-experimental);
0.4.0 finds the phrases too (which bar of every 4 or 8 starts one, from where the parts of the song come in or
drop out, read over the song's last minute and its first 40 s), so blends go phrase on phrase. A song whose bars
don't change from one to the next gets no phrase, and blends bar on bar; so does one whose last minute changes
tempo. The downbeat is found more surely too: by the kick and by what changes over a whole bar. Updating to 0.4.0 measures every
song's beat grid again in the background; until a song's turn comes, its blends go bar on bar as before.
Songs measured by an older version keep their loudness and fades and are measured again for their beats. The
plugin's settings page (0.3.1) has a **Beat grids** section for that: how many songs have their grids and how many
are waiting, its own **Songs per run** (any number, since a round stops when its time is up; 0 to measure them
only when you ask), **Measure a batch now**, and **Measure all** with **Stop**. Each round measures the volume and
fade profiles first and the beat grids in the time left; songs Siansa is about to play come first. A song that
can't be read is tried again later (after an hour, 6 hours, a day, then 3 days); the page says why songs failed and
has **Try them all again now** (0.3.2). Songs on none of AudioMuse's music servers (removed, or not matched yet)
are counted apart.

From 0.4.1, **Measure all** goes on **without a break** until every song is measured. Meanwhile AudioMuse-AI waits
with its own jobs (analysis of new songs, clustering, scheduled tasks), as it does while any plugin job runs. If you
want them to get a turn, set **Measure all runs** to rounds of 12, 10 or 5 minutes every 15 (the way it ran before
0.4.1). **Stop now** shows while songs are being measured, whatever started it (Measure all, a batch now, or the
15-minute task): the run ends after the song it's on, Measure all goes off for both parts, and the songs measured so
far are kept.

From 0.4.2 the tempo of drum and bass and other breakbeats is read right. It often came out at two thirds (113 BPM for
170), with the beats off the music, so those songs were never beat-matched. Updating measures every song's beat
grids again in the background, songs Siansa is about to play first; the loudness and fades stay in use meanwhile.

While **Measure all** is on, the page says where it is and updates by itself (never while you're typing in it): how
many it has measured so far, with rounds **Between rounds** and when the next one starts, waiting for one of
AudioMuse-AI's own jobs to finish, or that nothing is starting because the plugin's scheduled task is switched off
(Administration › Scheduled Tasks). Under it, **Last round** says what the last round did.

## Several music servers in one AudioMuse-AI

AudioMuse-AI 3 can serve several music servers (for example Navidrome and Emby). Each one has a **display
name** in AudioMuse-AI (Setup › Music Servers). Siansa must name the right server in every request, or AudioMuse
answers with the other server's songs or says it doesn't know the song.

In **Settings › Servers and services**, in the card of your music server, **Server name in AudioMuse** is its name,
as AudioMuse-AI shows it (capitals don't matter). Leave it empty for AudioMuse-AI's default server. Each user keeps
their own: enter the Emby server's name in your Emby user's card, and Navidrome's in your Navidrome user's.

**Users shown together** (for example your kids' login on the same Navidrome, with a library of its own): if
AudioMuse-AI reads that login as a server of its own, enter that server's name in the kids' card too. Their songs
then go to AudioMuse-AI under their name, and yours under yours, so radios and "similar" work for both.

**AudioMuse-AI must know each server's songs.** It links songs on a second server to its analysis either by
analysing that server too, or by **Align** in its setup, which matches songs by path and tags. Songs it couldn't
match can't be used for radios and similar music. If Siansa says "AudioMuse doesn't know this song", see
[Troubleshooting](troubleshooting.md#audiomuse-doesnt-know-this-song).

The Siansa plugin measures songs on every server: it fetches each song from whichever server has it (0.3.3).
AudioMuse-AI runs the plugin's 15-minute task once per server; the first run does the work and the others stop
straight away, so more servers don't make it take longer.
