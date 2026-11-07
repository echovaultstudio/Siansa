# AudioMuse-AI and the Siansa plugin

[AudioMuse-AI](https://github.com/NeptuneHub/AudioMuse-AI) runs on your own server next to your music server. It
listens to every song once and can then answer "what sounds like this song?" or "find songs that sound like *rainy
jazz piano*". Siansa asks it those questions. Your music never leaves your servers.

The **Siansa Analysis plugin** adds what Siansa needs on top: each song's measured loudness, how it starts and ends,
its beats and phrases, and similar albums and artists.

## What needs what

| Feature | Needs |
|---|---|
| Today's mix, vibes, the carousel, mood radios, Wind down | AudioMuse-AI |
| Search by sound, Flow, Your sound | AudioMuse-AI |
| Song radio, similar songs, Song path | AudioMuse-AI (simpler versions from your server without it) |
| Keep playing in DJ mode | AudioMuse-AI (tempo and key) |
| Similar albums and artists | The Siansa plugin |
| Levelling from measured loudness, smart crossfades, DJ blends | The Siansa plugin (otherwise tags and fixed crossfades) |

## Set up AudioMuse-AI in Siansa

**Settings › Connections › Services › AudioMuse-AI**: its address (`http://192.168.1.10:8000`), an **API token** or
its user name and password if it has a login, then **Test** and **Save**. You need AudioMuse-AI 3.6.4 or newer with
text search (CLAP) on.

## Install the Siansa plugin

1. In AudioMuse-AI, open **Plugins › Repositories** and add:
   ```
   https://raw.githubusercontent.com/echovaultstudio/siansa-audiomuse-plugin/main/manifest.json
   ```
2. In **Catalog**, install **Siansa Analysis** and apply the restart.
3. Update from **Plugins** when a new version is offered. Measurements are kept.

New songs are measured right after AudioMuse-AI analyses them, and older ones a batch every 15 minutes. **Measure
all songs** (Plugins › Siansa Analysis › Settings) does the whole library at once. On a big library that takes hours
or days, and AudioMuse-AI waits with its own jobs meanwhile, so you can set it to run in rounds (10 minutes every 15,
say). **Stop now** ends it, keeping what's done. Songs Siansa is about to play are measured first.

Until a song is measured, Siansa uses its ReplayGain tags and fixed crossfades. New plugin versions sometimes measure
the beats again in the background (the page says how many are waiting); the loudness and fades stay in use meanwhile.

## Several music servers in one AudioMuse-AI

AudioMuse-AI can serve several music servers, each with a display name (Setup › Music Servers). Enter that name as
**Server name in AudioMuse**, in the card of the matching server under **Settings › Connections**. Leave it empty for
AudioMuse-AI's default server.

AudioMuse-AI must know each server's songs, by analysing it or with **Align** in its setup. If Siansa says
"AudioMuse doesn't know this song", see [Troubleshooting](troubleshooting.md#audiomuse-doesnt-know-this-song).
