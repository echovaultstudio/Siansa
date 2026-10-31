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
- **Target**: how loud songs are brought to, by the plugin's measurement or by ReplayGain tags alone. −18 LUFS is
  ReplayGain's own level and leaves room for loud songs. −14 is louder, like Spotify: tagged songs play 4 dB louder
  than their tags say, but never louder than the file itself or past their peak, so quiet songs can't be raised to it.

Details: [How it works › Levelling](how-it-works.md#levelling).

## Crossfade

Same page. **Off**, a **fixed** length (1–12 seconds, 6 by default), or **Smart**:

- Smart crossfades follow how songs end and start, measured by the Siansa plugin. A long fade-out starts blending
  early, a hard ending doesn't blend at all. A slow intro fades in slowly, a hard hit comes in at once.
- Silence at the end of a song is left out: the next song comes straight in. A long fade-out is cut once it's quiet
  (at most the crossfade length of it), so the next song comes in while it's still fading. Only what runs to the very
  end counts: a silence or a quiet passage mid-song never starts the next song, a soft ending that stays at one low
  level is played, and nothing is left out before three quarters of the song. The log says what was left out.
- Songs that run straight on within one album aren't faded, so they stay gapless.
- Without the plugin's measurements, Smart uses the fixed length.

### Beat-matched blends (experimental)

**Settings › Playback › Crossfade and levelling › Beat-matched blends**, shown with Smart crossfades and the Siansa
plugin. Two songs that both keep a beat can clash at the changeover when their beats land at different moments,
like a bad DJ set. With the plugin 0.3.0, which measures the beat grid at each end of every song, Siansa can blend
them the way a DJ mixes:

- **DJ radio** (the default): in DJ radio and DJ keep-playing, a song that keeps a beat blends into the next one bar
  for bar. The blend starts on a bar of the song going out, the next song starts on its first downbeat, and it
  plays a touch faster or slower (up to 8 %, pitch kept) so its beats keep time with the old song's. Afterwards it
  eases back to its own speed over about 25 seconds, too slowly to hear. The blend is as many whole bars as fit the
  crossfade length.
- **Everywhere**: the same in every smart crossfade.
- **Off**: fades don't follow the beat.

Whatever the setting, two songs that both keep a beat but can't be matched (tempos more than 8 % apart, counting
double and half time as the same; a beat that isn't steady; a stream that can't jump to a point) get a short blend,
so their beats clash only for a moment. A beatless ending or start keeps the smart blend as before.

It's experimental: downbeats are the hardest thing to hear in a recording, so when either song's bars aren't clear
the songs are laid beat on beat instead, and a mix can land a beat off. The log says how each blend was planned and
how far the next song was out of step. Songs measured before plugin 0.3.0 keep their loudness and fades and get
their beats when the plugin measures them again: see the **Beat grids** section on the plugin's settings page
([AudioMuse-AI](audiomuse.md)).

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

Scroll down in now playing for **About the artist** and **About this song**. The whole page scrolls as one, in the
colours of the cover, and the cards take a deep shade of the cover's colour too (a dark grey for a black-and-white
cover). Scroll back up for the controls.

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

**Out of reach.** When the phone leaves the Wi-Fi a DLNA TV or speaker is on (you leave the house), the music comes
back to the phone, paused, within a few seconds. A device that stops answering on the same Wi-Fi is let go after
about 15 seconds. A short note says which device was lost. The device itself plays from your server, so it may
finish the song it has (and the next one, if it was handed that ahead) before it falls silent.

**DLNA without gaps.** Siansa hands a DLNA TV or speaker the next song while the current one plays, so devices
that support it go straight on to the next song. Devices that don't get the next song when the current one ends,
with a short gap. How well a device does this is up to the device.

**Chromecast without gaps.** A Chromecast, Google TV or speaker with Chromecast built in gets the whole queue and is
asked to load the next song about 20 seconds before the current one ends, so it can go straight on. Songs go as MP3,
which can leave a very short pause between songs that run into each other.

**Speakers that play the queue themselves (experimental).** Some speakers can't take the next song ahead (for
example the JBL Bar 800), so songs change with a gap of a few seconds. Speakers built on LinkPlay (JBL Bar, WiiM,
Arylic, Audio Pro and others) can instead be handed the next few songs as a list of their own and play through it by
themselves. Tap the cog beside the speaker in **Play on** and turn on **Speaker plays the queue**; it's only offered
on speakers that have it. Siansa keeps the speaker's list in step as the queue changes. If the speaker refuses,
songs go one at a time as before.

**Original files on DLNA.** Tap the cog beside a DLNA TV or speaker in **Play on** and turn on **Send the original
file**. It then gets your music as it is (for example FLAC) instead of MP3 320, whenever it says it plays that
format; other songs still go as MP3 320. Better sound, and no tiny gap between songs, but it uses more Wi-Fi. The
setting is kept for each device, shows which formats the device says it plays, and isn't offered when it only
plays MP3. It applies from the next song.
