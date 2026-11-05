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

Everything works with the remote's arrows and the centre button. The tabs along the top (Home, Library, Search,
Settings) always open that tab's own page, wherever you are.

- **Home**: Today's mix, Shuffle your library and Liked songs, then the rows of your phone's Home, in the same order
  and with the same rows hidden: Today's mixes (the daily mixes made today), Jump back in, Your mixes, Rediscover, Radios, Your playlists, Recently played, Artist
  radios, Recently added, Your genres and Random picks. A radio starts playing straight away.
- **Library**: albums, artists, playlists and genres as covers, six across, your liked songs, favourite albums and
  favourite artists, and Time machine.
- **Albums, playlists and mixes**: the cover (a playlist's own cover from the server) and what it is at the top, with the artists, genre and label to open,
  then Play, Shuffle and Add to favourites; down from them goes straight to the songs. The button at the end of each
  song has Play next, Add to queue, Add to playlist, Like, Start (or Stop) song radio, Similar songs, Go to album or
  artist, and Song details.
- **Artists**: Play, Shuffle, Artist radio and Add to favourites, then their top songs, albums and similar artists.
- **Search**: type with the on-screen keyboard or press the microphone button and say it. "Play … on Siansa" from
  Google TV's own search opens it too.
- **Now playing**: the cover, the song and the controls, large enough to read from the sofa, over the cover blurred as
  on the phone. Under the controls are round buttons for Like, Lyrics, Up next, Song radio (or Stop song radio while
  one plays), Add to playlist, Song path (with AudioMuse) and Song details; each says its name as the remote reaches it. Synced lyrics follow the song; right from the buttons
  reaches the lyrics, and up and down scroll through them. Animated covers move here and on album pages, as on the
  phone. The artists and the album open their pages. The TV app has no sleep timer: the TV's own sleep setting
  turns everything off.
- **Typing**: OK on a text field opens a box over the page with the keyboard up (in Search it opens as soon as you get
  there). Type with the remote, say it with the microphone button, or type or paste on your phone with the Google TV
  app's keyboard. What you type shows straight away; OK or Done closes the box, and Cancel puts back what was there.
- **Skipping within a song**: rewind and fast forward on the remote go back and forward 10 seconds anywhere in the
  app. On a remote without them, press up from the controls in Now playing to reach the progress bar, then left and
  right.

## AudioMuse

With AudioMuse set up (on the phone or under **Settings › Servers and services** on the TV), the TV has what it adds on
the phone:

- **Your sound** and **Discover for you** among Your mixes on Home, made when you pick them.
- **Radios** on Home: sound radios that play straight away.
- **Similar songs** in a song's actions, and **Start song radio** with DJ blends.
- **Songs that sound like…** in Search: type or say a few words ("rainy sunday morning").
- **Your sound** in the Library: the sound of your library.
- **Song path** in Now playing and in a song's actions: from that song to another, through songs that sound in between.
- **Song alchemy** on a playlist's page: songs that blend the sound of its songs.
- Loudness and fade shapes from the Siansa plugin for crossfades and levelling, and the vibes of your daily mixes, as
  on the phone.

Scrobbling to Last.fm or ListenBrainz works on the TV as on the phone once it's set up under **Settings › Scrobbling
and history** (each server or user on the TV has its own).

## Settings

The TV has the settings that matter on a TV, each the same page as on the phone:

- **Servers and users**: every server and user on the TV; pick one to switch (Siansa starts again as them). **Add a
  server or user** signs in with the code on the TV, like the first time, and keeps its own library and settings.
- **Show music from**: whose music shows on the TV and which of their libraries, as in the phone's Sources.
- **Servers and services** (AudioMuse, Last.fm and more), **Library sync**, **Mixes and vibes** and **Scrobbling and history**.
- **Audio passthrough** (below), **Playback** (crossfades, DJ blends, levelling, the equaliser, keep playing) and
  **Streaming quality**.
- **Colours**: the TV is always dark.
- **Help**: debug logs, **Report a problem**, the log, What's new and About.
- **Open Now playing when a song starts**: on from the start; picking a song, an album or a radio goes straight to the
  player.
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
- Songs in the same format play one after another without a gap, a DJ mix or a live album included: the output to the
  receiver stays open from one song to the next. A file that says it's longer than it is (some DJ mixes do) plays as
  long as your server says the song is, so the next one still follows straight on.
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
