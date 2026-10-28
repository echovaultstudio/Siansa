# Playback

## Streaming quality

**Settings › Playback › Streaming quality**, set separately **On Wi-Fi** and **On mobile data**:

- **Original**: the file as it is on your server. Best on Wi-Fi.
- **Opus 96–320 kbit/s**: converted by your server (Navidrome's "opus" transcoding profile), to save data.
- **On Emby** the original file is always played and downloaded: Siansa can only have Navidrome and other
  Subsonic servers convert songs, so the quality choices don't change anything there.

Siansa always plays the quality you chose; it never switches by itself. Changes apply from the next song.

## Song cache and loading ahead

- **Song cache** (**Settings › Storage**, 2 GB by default): songs are kept on the phone as they play, so replays
  and skips start at once and survive a bad connection. Only original-quality streams are cached. **Off** keeps
  no song once it has played (the songs already cached are removed); it applies from the next song.
- **Load songs ahead** (**Settings › Playback**): while a song plays, Siansa loads the next ones, one at a time, so
  they start at once and keep playing if the connection drops. 2 on Wi-Fi and 1 on mobile data by default, up to
  5. Only original files are loaded ahead. This works with the song cache off too, for up to 3 songs: each is held
  only until it has played.
- **Image cache** (500 MB by default): covers and artist photos, so they show at once and offline. **Download all
  covers** fetches every cover in the background (Wi-Fi only by default).

## Downloads

**Settings › Downloads** (or the download button on an album or playlist): music you download stays on the phone
and plays without a connection. It isn't part of the song cache and is never removed by itself.

- **Download on Wi-Fi only** (on by default).
- **Download quality**: the original file, or Opus to save space.
- **Storage limit**: downloads stop when it's reached.
- **Only downloaded music** (in the source filter on Home) hides everything that isn't on the phone. When you're
  offline, Siansa offers to switch it on.

## Gapless playback

Songs play back to back with no gap when they're meant to (live albums, DJ mixes). Some Dolby (E-AC3) files
claim to be far longer than they are; Siansa uses your server's length for those and loads the next song ahead by
size, so they stay gapless too.

## Levelling (even loudness)

**Settings › Playback › Crossfade and levelling**:

- **Levelling**: **Track** plays every song at the same level. **Album** keeps an album's loud and quiet songs
  apart. **Off** plays songs as they are.
- **Where loudness comes from**: song tags (ReplayGain), or the **Siansa plugin**, which measures the audio itself
  (useful when your files have no ReplayGain tags). Songs the plugin hasn't measured yet use their tags.
- **Target**: −18 LUFS matches ReplayGain and leaves room for loud songs. −14 is louder, like Spotify, but quiet
  songs can't be raised to it.

Details: [How it works › Levelling](how-it-works.md#levelling).

## Crossfade

Same page. **Off**, a **fixed** length (1–12 seconds, 6 by default), or **Smart**:

- Smart crossfades follow how songs end and start, measured by the Siansa plugin. A long fade-out starts blending
  early, a hard ending doesn't blend at all. A slow intro fades in slowly, a hard hit comes in at once.
- Songs that run straight on within one album aren't faded, so they stay gapless.
- Without the plugin's measurements, Smart uses the fixed length.

Crossfades only happen on this phone: on a TV or speaker, songs play back to back. They're not used with Repeat
one, or while the sleep timer lets a song finish.

## Equaliser

**Settings › Playback › Equaliser**: ten bands, presets, and a pre-amp. Songs stay as loud as with the equaliser
off; the pre-amp makes everything louder or quieter on top, and peaks that would distort are smoothed. Moving a
band saves it as your Custom preset. Not used while casting.

## Now playing and the mini player

Tap the mini player, or swipe up on it, to open now playing; swipe down on now playing, or tap ⌄, to close it.
Both follow your finger, and let go wherever you like: a hard swipe finishes quickly, a gentle one settles, and
a short pull springs back. The mini player fades away as the player rises and comes back as it goes. The book
player opens and closes the same way.

## Lyrics

Lyrics come from your server (Navidrome's lyrics, or the lyrics file or tags it reads). Synced lyrics light up line
by line, and word by word when your server sends word timings (Navidrome 0.63 or newer).

## Sleep timer and Wind down

From now playing. Pick a time, or let the current song finish. **Wind down** swaps everything after the current
song for a path from that song to calmer music, long enough to fill the timer, so the music gets calmer and quieter
as you fall asleep. Without a timer, Wind down queues a number of calm songs (**Settings › Playback › Wind down**:
10, 20 or 30) and the music ends with them.

## Casting (Chromecast and DLNA)

The device button in now playing shows TVs and speakers on the same Wi-Fi: Chromecast and Google TV, and DLNA
ones (most smart TVs, Sonos, AV receivers). They play straight from your server, as MP3 320 kbit/s, so every device
can play them. Stopping the cast brings the music back to the phone, paused.

**DLNA without gaps.** Siansa hands a DLNA TV or speaker the next song while the current one plays, so devices
that support it go straight on to the next song. Devices that don't get the next song when the current one ends,
with a short gap. How well a device does this is up to the device.

**Original files on DLNA.** Tap the cog beside a DLNA TV or speaker in **Play on** and turn on **Send the original
file**. It then gets your music as it is (for example FLAC) instead of MP3 320, whenever it says it plays that
format; other songs still go as MP3 320. Better sound, and no tiny gap between songs, but it uses more Wi-Fi. The
setting is kept for each device, shows which formats the device says it plays, and isn't offered when it only
plays MP3. It applies from the next song.
