# Playback

## Quality, cache and downloads

All under **Settings › Data**.

- **Streaming quality**, set apart **On Wi-Fi** and **On mobile data**: **Original** (the file as it is) or **Opus
  96–320 kbit/s**, converted by your server to save data. Siansa never switches by itself. On Emby the original file
  is always played.
- **Song cache** (2 GB by default): songs are kept as they play, so replays and skips start at once and survive a bad
  connection. Only original-quality streams are cached.
- **Load songs ahead**: the next 1–5 songs are loaded while one plays (2 on Wi-Fi, 1 on mobile data by default), so
  they keep playing if the connection drops.
- **Image cache** (500 MB by default), and **Download all covers** for every cover at once.
- **Downloads** (or the download button on an album or playlist): music that stays on the phone and plays offline.
  Choose Wi-Fi only, the quality and a storage limit. **Only downloaded music**, in Home's source filter, hides the
  rest; Siansa offers it when you're offline.

Songs meant to run on (live albums, DJ mixes) play **gapless**.

## Levelling

**Settings › Playback › Crossfade and levelling**. **Track** plays every song at the same loudness, **Album** keeps an
album's loud and quiet songs apart, **Off** plays them as they are. Loudness comes from the songs' ReplayGain tags, or
from the **Siansa plugin**, which measures the audio itself (for files without tags). **Target** sets how loud: −18
LUFS (ReplayGain's level) or louder, up to −14 like Spotify.

## Crossfade

Same page. **Off**, a **fixed** length (1–30 seconds), or **Smart**, which needs the Siansa plugin:

- **Smart** follows how each song ends and starts: a long fade-out blends early, a hard ending meets a hard start
  like a radio DJ's cold end, silence at the end is skipped, and an album that runs straight on stays gapless.
  **Up to** is the longest a fade can be; most are shorter.
- **Beat-matched blends** (Off, **DJ radio** or **Everywhere**): two songs with a beat are mixed as a DJ would, phrase
  on phrase, the next song's speed nudged (up to 8 %) to keep time, then eased back. **Blend length** is 4, 8 or 16
  bars. **Bass swap** (on by default) trades the two songs' bass halfway, so two kick drums never play at once. It's off
  by default, as on some phones a blend still drifts out of step.

Radio stations and podcasts are never faded. Crossfades happen only on the phone, not when casting.
The details: [How it works › Crossfades](how-it-works.md#crossfades-and-dj-blends).

## Equaliser

**Settings › Playback › Equaliser**: ten bands, presets and a pre-amp. Songs stay as loud as with it off. Not used
while casting.

## Now playing

- **Open and close** by tapping or swiping the mini player up, and swiping down. Swipe the mini player left or right
  for the next or last song.
- **Slide from the play button** to start something from the song playing: **up** a DJ mix, **down** a Steady radio,
  **left** its song path, **right** songs that sound like it. Its name lights up when you've gone far enough; let go
  to start it. Change what each way does under **Settings › Style › Now playing**.
- **The queue**: pull up from now playing. Swipe a song left to remove it, right to play it next. **Save queue as
  playlist** keeps it.
- Scroll down for **About the artist** and **About this song**.
- **Lyrics** come from your server. Synced lyrics follow the song line by line, and word by word on Navidrome 0.63 or
  newer.
- **Sleep timer**: pick a time, or let the song finish. **Wind down** fills the time with a path to calmer music, so
  it gets quieter as you fall asleep.

## Casting

The device button in now playing lists TVs and speakers on your Wi-Fi: **Chromecast** and Google TV, and **DLNA**
(most smart TVs, Sonos, AV receivers). They play straight from your server, without gaps where the device allows.
Stopping brings the music back to the phone, paused, and so does leaving home.

- **Chromecast** shows Siansa's own screen on the TV (cover, title, progress) and takes the TV remote's buttons. Tap the
  cog beside it for **Sound quality**: **Lossless** (the default; files the TV can't play are converted to FLAC by your
  server), **Opus** or **MP3 320**.
- **DLNA** gets Opus 320 or MP3 320. Turn on **Send the original file** under its cog for lossless.

On Navidrome, lossless casting of Apple Lossless and similar files needs the **flac audio** transcoding (**Settings ›
Transcodings**). Recent versions have it. To add it, run Navidrome with `ND_ENABLETRANSCODINGCONFIG=true`, create
one with target format `flac`, bit rate `0` and command `ffmpeg -ss %t -i %s -map 0:a:0 -v 0 -c:a flac -f flac -`.

On an Android TV, [Siansa's TV app](tv.md) plays on its own instead, and can pass Dolby and DTS to your receiver.

## Android Auto

On the car's screen: **Home** (your newest mixes, the mood radios and Recently played), **Playlists**, **Albums**,
**Artists** and **Search**, by voice too ("Play Glass Harbour on Siansa"). Covers come through the phone, so the car
never needs your server.

**Not listed in Android Auto?** It only lists apps from Google Play unless you allow others. For Siansa from F-Droid
or Siansa Dev: open Android Auto's settings on the phone, tap **Version** about ten times, then **⋮ › Developer
settings › Unknown sources**, and plug the phone in again.
