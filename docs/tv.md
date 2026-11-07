# Siansa on your TV

Siansa has its own app for Android TV and Google TV (a Google TV Streamer, a Chromecast with Google TV, an NVIDIA
SHIELD, or a TV with Google TV built in). It plays from your server by itself, made for the remote. It keeps its own
settings, library and sign-ins, apart from your phone's.

## Getting it

It's the same Siansa, in the same places: your TV gets the TV app, your phone the phone app.

- **Google Play**: once you're a tester ([join here](https://siansa.app/#join)), open [Siansa on Google Play](https://play.google.com/store/apps/details?id=app.siansa) on a computer or phone, signed in with your TV's Google account, tap **Install** and pick your TV. Searching on the TV may not find it while Siansa is in testing.
- **F-Droid**: install an F-Droid app on the TV, add `https://siansa.app/fdroid/repo`, and install Siansa.

## Signing in

The TV shows a QR code. Scan it with your phone's camera, tap **Open in Siansa**, then **Sign in**. Leave **Also set
up … on the TV** ticked to bring AudioMuse-AI, the AI service, Audiobookshelf and scrobbling across too.

The phone and TV must be on the same home network. What the phone sends is sealed with a key that's only in the QR
code. No phone at hand? **Type it in instead** with the remote.

## Finding your way

The remote's arrows and centre button do everything; what you're on is ringed in the accent colour. The tabs along the
top are **Home**, **Library**, **Search** and **Settings**.

- **Home**: Today's mix, Shuffle your library and Liked songs, your notices, then the rows of your phone's Home, in the
  same order.
- **Library**: albums, artists, playlists and genres as covers, Liked songs and your favourites.
- **Albums and artists**: Play, Shuffle and Add to favourites; an artist's top songs, each kind of release, similar
  artists and Artist radio.
- **Search**: type, or press the microphone and say it. "Play … on Siansa" from Google TV's own search works too.
- **Now playing**: the cover, the song and a thin line for the time. **OK** plays and pauses, **left** and **right**
  skip (hold them to wind through the song), and **down** opens the menu: Like, Shuffle, Repeat, **Lyrics**, **Up
  next**, Start a mix, Song path, Similar songs and more. Rewind and fast forward on the remote jump 10 seconds
  anywhere.

## With AudioMuse-AI

As on the phone: Your sound and Discover for you, sound radios, Similar songs, **Start a mix** with DJ blends,
**Songs that sound like…** in Search, Song path, and Song alchemy on a playlist. Crossfades and levelling use the
Siansa plugin's measurements.

## Settings

Servers and users, Show music from, Connections, Library sync, Auto Generated Mixes, Scrobbling and history,
Playback, **Audio passthrough**, Colours (always dark), Animated covers (off at first, as they slow a TV down), **Open
Now playing when a song starts**, Help and **Sign out**. Downloads, Android Auto, arranging Home and cards stay on the
phone.

**Report a problem** (under Help) sends what happened with the TV's log. Debug logs are on from the start in the TV
app; nothing leaves the TV unless you send a report.

## Audio passthrough

With an AV receiver or soundbar on HDMI (eARC or ARC), Siansa sends Dolby and DTS music to it unchanged: Dolby Atmos,
Dolby Digital Plus, Dolby Digital, TrueHD, DTS and DTS-HD. It's on from the start (**Settings › Audio passthrough**),
and works only for formats the TV says the receiver takes; the page lists them.

- Only those songs go as they are; the rest of your music plays as usual. They always stream as the original file.
- They play without crossfades, levelling, the equaliser or Siansa's volume: use the receiver's.
- Atmos often plays quieter than other music. **Settings › Playback › Dolby songs on your receiver** plays Atmos and
  Dolby Digital Plus 3 to 12 dB louder, without changing the sound.
- Songs in the same format play gapless, live albums and DJ mixes included.
- Files in `.m4a`, `.mp4`, `.mka`, `.mkv`, `.eac3` and `.ac3` work. Your server must send them untouched.

If a song can't go to the receiver, it plays decoded on the TV instead, and the passthrough page says why.

Casting to the TV from your phone still works too, with or without the TV app.
