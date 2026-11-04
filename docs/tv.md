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

To sign out, go to **Settings › Server › Sign out** on the TV. Your phone stays signed in.

## Finding your way

Everything works with the remote's arrows and the centre button.

- **Home**: Shuffle your library, Liked songs, and rows of recently added albums, your most played, your playlists,
  something different, and artists.
- **Library**: every album, artist and playlist, as covers.
- **Search**: type with the on-screen keyboard or press the microphone button and say it. "Play … on Siansa" from
  Google TV's own search opens it too.
- **Now playing**: the cover, the song and the controls, large enough to read from the sofa. Rewind and fast forward
  on the remote skip back and forward 10 seconds.

Crossfades, DJ blends, levelling, the equaliser and the other playback settings work as on the phone.

## Audio passthrough

With an AV receiver or soundbar on the TV's HDMI (eARC or ARC), Siansa can send Dolby and DTS music to it unchanged,
so the receiver decodes it: Dolby Atmos (E-AC-3 JOC), Dolby Digital Plus, Dolby Digital, Dolby TrueHD, DTS and DTS-HD.

Switch it on in **Settings › Audio passthrough › Dolby and DTS formats**. The page lists the formats your receiver
takes; if it lists none, check the receiver is on and connected over HDMI.

- Only those songs go to the receiver as they are. All other music plays as usual.
- Those songs play without crossfades, DJ blends, levelling, the equaliser or Siansa's volume: the receiver decides
  how they sound. Use the receiver's volume.
- Songs in the same format play one after another without a gap.
- They always stream as the original file, whatever **Streaming quality** says, since a converted song can't go to the
  receiver.

Your server must send these files untouched. Navidrome does, unless a transcoding rule of its own applies to them.
Siansa recognises Dolby and DTS songs in `.mka` (Matroska), `.eac3`, `.ac3` and `.m4a` files. An `.m4a` with Dolby
audio is recognised once it has played.

Passthrough is only for the TV app. Phones and casting to a Chromecast decode as before.

## Casting from your phone

Casting to the TV from the phone still works as before, with or without the TV app installed.
