# Siansa on your TV

Siansa has its own app for Android TV and Google TV (a Google TV Streamer, a Chromecast with Google TV, an NVIDIA
SHIELD, or a TV with Google TV built in). It plays from your server on its own, without a phone casting to it.

It's an app of its own, **Siansa** on the TV's home screen. It doesn't replace the phone app or change anything on
your phone: install it on the TV, and both keep their own settings, library and sign-ins.

## Signing in

The first time you open it, the TV shows a QR code.

1. Scan the code with your phone's camera.
2. Tap **Open in Siansa**, then **Sign in** in Siansa on your phone.
3. The TV signs in to the same server as your phone and syncs its library.

The phone and the TV must be on the same home network. Your phone sends the TV only the server address and the login
it uses, sealed with a key that's only in the QR code, so nothing else on the network can read it. A code that
points anywhere other than your home network is ignored.

No Siansa on your phone, or the TV isn't on Wi-Fi or Ethernet? Pick **Type it in instead** and enter the server
address, username and password with the remote.

To sign out, go to **Settings › Sign out** on the TV. Your phone stays signed in.

## Finding your way

Everything works with the remote's arrows and the centre button.

- **Home**: Today's mix, Shuffle your library and Liked songs, then the rows of your phone's Home, in the same order
  and with the same rows hidden: Jump back in, Your mixes, Rediscover, Radios, Your playlists, Recently played, Artist
  radios, Recently added, Your genres and Random picks. A radio starts playing straight away.
- **Library**: albums, artists, playlists and genres as covers, and your liked songs.
- **Albums, playlists and mixes**: Play, Shuffle, or pick a song to play from it. The button at the end of each song
  has Play next, Add to queue, Like, Start song radio, and Go to album or artist.
- **Search**: type with the on-screen keyboard or press the microphone button and say it. "Play … on Siansa" from
  Google TV's own search opens it too.
- **Now playing**: the cover, the song and the controls, large enough to read from the sofa, with Like, Up next and a
  sleep timer. Rewind and fast forward on the remote skip back and forward 10 seconds.

## Settings

The TV has the settings that matter on a TV, each the same page as on the phone:

- **Servers and services**, **Library sync**, **Mixes and vibes** and **Scrobbling and history**.
- **Audio passthrough** (below), **Playback** (crossfades, DJ blends, levelling, the equaliser, keep playing) and
  **Streaming quality**.
- **Colours**: the TV is always dark.
- **Help**: debug logs, **Report a problem**, the log, What's new and About.
- **Sign out** of the server on the TV; your phone stays signed in.

Downloads, mobile data, Android Auto, the mini player, arranging Home, cards and backups are on the phone only.

## Reporting a problem

Debug logs are on in the TV app from the start, so when something goes wrong the log already has it. Make the problem
happen a few times, then go to **Settings › Report a problem**, say what happened and what you expected, and send it.
The log goes with the report, the same way as from the phone, and the report says it came from the TV app. Nothing
leaves the TV unless you send a report, and you can turn debug logs off under **Settings › Debug logs**.

## Audio passthrough

With an AV receiver or soundbar on the TV's HDMI (eARC or ARC), Siansa can send Dolby and DTS music to it unchanged,
so the receiver decodes it: Dolby Atmos (E-AC-3 JOC), Dolby Digital Plus, Dolby Digital, Dolby TrueHD, DTS and DTS-HD.

It's on from the start (**Settings › Audio passthrough › Dolby and DTS formats**), and only does something when the TV
says the receiver takes the format. The page lists the formats your receiver takes; if it lists none, check the
receiver is on and connected over HDMI. Without a receiver that takes them, Dolby and DTS songs are decoded on the TV
and play in stereo, with the centre and surround channels folded in.

- Only those songs go to the receiver as they are. All other music plays as usual.
- Those songs play without crossfades, DJ blends, levelling, the equaliser or Siansa's volume: the receiver decides
  how they sound. Use the receiver's volume.
- Songs in the same format play one after another without a gap.
- They always stream as the original file, whatever **Streaming quality** says, since a converted song can't go to the
  receiver.

Your server must send these files untouched. Navidrome does, unless a transcoding rule of its own applies to them.
Siansa plays Dolby and DTS songs in `.m4a`, `.mp4`, `.mka`, `.mkv`, `.eac3` and `.ac3` files, and always streams
those as the original file while passthrough is on. An `.m4a` or `.mp4` with Dolby audio is known as one once it has
played, and from then on it doesn't blend with the songs around it.

If sending a song to the receiver fails, Siansa plays it decoded on the TV instead, and keeps doing that until it's
opened again. The passthrough page then says so; a report sent then has the reason in its log.

Passthrough is only for the TV app. Phones and casting to a Chromecast decode as before.

## Casting from your phone

Casting to the TV from the phone still works as before, with or without the TV app installed.
