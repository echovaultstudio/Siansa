# Siansa Beats

[Siansa Beats](https://github.com/echovaultstudio/siansa-beats) is Siansa's companion service: a Docker container
next to your music server that listens to every song once and tells the app what it heard. Siansa works without it
as a plain player; with it, everything "smart" in the app comes from one place. Your music never leaves your
servers, and nothing is ever changed on them.

## What it gives the app

| In the app | From Siansa Beats |
|---|---|
| Even volume, smooth and smart crossfades | Each song's measured loudness and how it starts and ends |
| DJ mixes and DJ blends that keep to the beat | Every beat and bar of each song, its tempo and its key |
| Genre list, genre pages and genre radios | Each song's genres, heard from its sound |
| Song radio, Similar songs, Keep playing, Wind down | Songs that sound like a song |
| Similar albums and "Fans also like" | Albums and artists that sound alike |
| Song path and Flow | A run of songs from one sound to another, or to a mood |
| Mood radios, vibes, today's mix, cards made from a sound, "Songs that sound like…" in Search | Music found from a description in words |
| Your sound (the sonic fingerprint) and the mixes made from it | What your most played songs sound like |
| Your listening, Time Machine | Your listens, kept on your own server ("Scrobble to Siansa Beats") |

## Set it up

1. **Run Siansa Beats** on a Docker server that can reach your music server: one file and `docker compose up -d`.
   Its [quick start](https://github.com/echovaultstudio/siansa-beats#quick-start) has the three lines. It reads
   Navidrome, Emby and Jellyfin.
2. **On its page** (port 8090), the **Get started** card walks through adding your music server and choosing the
   libraries to analyse. A big library takes a while the first time; the app uses what's ready as it comes.
3. **In Siansa**, connect it: on first setup choose **Add Siansa Beats**, or later
   **Settings › Connections › Siansa Beats**. Enter its address (for example `http://192.168.1.10:8090`) and, if you
   set one on its page, its API key or password. **Test** says what it can do, then **Save**.

**Settings › Services** shows whether it's working, and has a switch to turn it off without removing it.

## Several users

Each login of your music server is its own entry in Siansa Beats (add it under its **Setup › Music servers** with
that user's name). Siansa asks Siansa Beats under the login the songs belong to, also when another user's music is
shown beside your own, so a child's album is answered with that child's library and not yours. This needs Siansa
Beats 0.44.1 or newer for similar albums and artists. A question with no song in it (an artist by name, a search by
words) is asked under the user in use.

## Siansa Beats and AudioMuse-AI

Siansa Beats needs no other service. If you also have [AudioMuse-AI](audiomuse.md) set up in Siansa, Siansa Beats is
asked first, and AudioMuse-AI when Siansa Beats can't answer (a song whose sound it doesn't have yet). With Siansa
Beats working, AudioMuse-AI can be switched off under Settings › Services.

Without Siansa Beats, the smart parts come from AudioMuse-AI and its Siansa plugin where those are set up, and
otherwise from your songs' tags: see [AudioMuse-AI and the Siansa plugin](audiomuse.md). DJ mixes are only offered
with Siansa Beats.

## When something's wrong

- **"Needs Siansa Beats"** on a button or slide: it isn't set up, or isn't answering. Check Settings › Services.
- **Test fails**: is the address its port (8090), and is the key the one from its page (Setup › Access)?
- **Similar music is thin or empty**: its page's Overview shows how far each kind of analysis is; songs not heard
  yet can't be matched.
- **The wrong user's music comes back**: the entry in Siansa Beats must use that user's name on the music server.
