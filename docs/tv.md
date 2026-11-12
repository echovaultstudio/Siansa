# Siansa on your TV

Siansa has its own app for Android TV and Google TV (a Google TV Streamer, a Chromecast with Google TV, an NVIDIA
SHIELD, or a TV with Google TV built in). It plays from your server on its own, without a phone casting to it.

It's an app of its own, **Siansa** on the TV's home screen. It doesn't replace the phone app or change anything on
your phone: install it on the TV, and both keep their own settings, library and sign-ins.

> The TV app is in beta: it does most of what the phone app does, and it's still growing. Problems and ideas are very
> welcome: **Settings › Help › Report a problem** on the TV sends one with its log.

## Getting the TV app

The TV app is the same Siansa as on your phone, in the same places: your TV gets the TV app, your phone the phone app.

- **F-Droid:** install an F-Droid app on the TV (F-Droid itself, or one made for TVs), add Siansa's repository
  `https://siansa.app/fdroid/repo` as for the phone, and install Siansa. On a TV it installs the TV app.
- **Google Play:** while Siansa is a closed test on Google Play, [ask for a place](https://siansa.app/#join), then open
  the Play Store on your TV and search for Siansa.

## Signing in

The first time you open it, the TV shows a QR code.

1. Scan the code with your phone's camera.
2. Tap **Open in Siansa**. Leave **Also set up … on the TV** ticked to bring your phone's other services too
   (AudioMuse, the AI service, Audiobookshelf, Last.fm and ListenBrainz, whichever are set up), then tap **Sign in**.
3. The TV signs in to the same server as your phone and syncs its library.

The phone and the TV must be on the same home network. Your phone sends the TV the server address and the login it
uses, and the services you ticked, sealed with a key that's only in the QR code, so nothing else on the network can
read them. A code that points anywhere other than your home network is ignored. Services the TV already has and the
phone doesn't send stay as they are.

No Siansa on your phone, or the TV isn't on Wi-Fi or Ethernet? Pick **Type it in instead** and enter the server
address, username and password with the remote.

To sign out, go to **Settings › Sign out** on the TV. Your phone stays signed in.

## Finding your way

Everything works with the remote's arrows and the centre button; what the remote is on is lit or ringed in the accent colour, buttons on the settings pages included. The tabs along the top (Home, Library, Search,
Settings) always open that tab's own page, wherever you are.

- **Home**: one bar of pills at the top: Today's mix, Shuffle your library and Liked songs, then your notices, as on
  the phone (what needs you, what you're partway through, what's new; shown or hidden in Settings › Notices and Cards ›
  Notices). Then the rows of your phone's Home, in the same order
  and with the same rows hidden: Today's mixes (the daily mixes made today), Jump back in, Your mixes, Rediscover, Radios, Your playlists, Recently played, Artist
  radios, Recently added, Your genres and Random picks. A radio starts playing straight away. Rows fill the screen: a
  row of only two or three is wide cards, a third of the screen each, with the cover beside the name. Coming back to
  Home, its rows are there at once, and brought up to date a moment later.
- **Library**: the shelves in a bar of pills at the top; albums, artists, playlists and genres as covers, six across, your liked songs, favourite albums and
  favourite artists. Time machine is on the phone only for now. Similar albums and the other pages with many albums
  (a genre, a label, Appears on) are six across too.
- **Albums, playlists and mixes**: the cover (a playlist's own cover from the server) and what it is at the top, with the artists, genre and label in
  plain words to open, then Play, Shuffle and Add to favourites in line under them; down from them goes straight to the songs. The button at the end of each
  song has Play next, Add to queue, Add to playlist, Like, Start (or Stop) a mix, Similar songs, Go to album or
  artist, and Song details.
- **Artists**: their photo (looked up again, so one Navidrome found since your library was read shows instead of a
  placeholder), Play, Shuffle, Artist radio and Add to favourites, then their top three songs (**Show all** for the rest), a row for each kind of release (Albums, EPs, Singles,
  DJ-mixes…) and similar artists.
- **Search**: type with the on-screen keyboard or press the microphone button and say it. "Play … on Siansa" from
  Google TV's own search opens it too. With nothing typed, it shows your recent searches (the phone's, kept when you
  search or go into the results; **Clear** forgets them), then radios (with AudioMuse) and your genres, as on the phone:
  in Siansa Dev with Siansa Beats, the genres it heard in your music (Home's Your genres and Library's genres too),
  otherwise your genre tags.
- **Now playing**: as simple as Siansa's Chromecast screen: the cover, the song, its artist and album, a thin line
  for the time, over the cover blurred as on the phone, and Siansa's logo in the corner. Under the cover, a short
  guide to the remote's keys. There are no buttons: **OK**
  plays and pauses (paused, the cover darkens under a pause sign), **left** and **right** go to the previous and next
  song (hold them to wind back or forward through the song, faster the longer you hold; it plays from there when you
  let go), and **down** opens the menu: Like, Shuffle and Repeat (each saying how it's set), Lyrics, Up next, Start a mix
  (Stop the mix while one plays), Go to the artist, Go to the album, Song path (with AudioMuse), Add to playlist,
  Similar songs and Song details. Lyrics and Up next open on the right: press right to move into them (up and down
  scroll the lyrics; OK on a song in Up next plays from it) and Back to close them. In Up next, songs Siansa added on its
  own (a radio, Keep playing) have a star, as in the phone's queue; synced lyrics follow the song. Animated covers can
  move here and on album pages, as on the phone: they're off from the start on the TV, and **Settings › Animated
  covers** turns them on. The **Now playing** chip at the top of the pages takes you here; its wave moves while the
  music plays. The TV app has no sleep timer: the TV's own sleep setting turns everything off.
- **Typing**: text fields are the TV's own, in the page: move to one (its border lights up) and press OK for the
  keyboard (in Search it comes up as soon as you open it). Type with the remote, say it with the microphone button, or
  type or paste on your phone with the Google TV app's keyboard. What you type shows straight away; Done (or Search)
  finishes.
- **Skipping within a song**: rewind and fast forward on the remote go back and forward 10 seconds anywhere in the
  app.

## AudioMuse

With AudioMuse set up (on the phone or under **Settings › Connections** on the TV), the TV has what it adds on
the phone:

- **Your sound** and **Discover for you** among Your mixes on Home, made when you pick them.
- **Radios** on Home: sound radios that play straight away.
- **Similar songs** in a song's actions, and **Start a mix** with DJ blends. A DJ mix is offered only when Siansa Beats
  answers with the songs' sound, as on the phone; otherwise its choice is greyed out with the reason, and Steady still plays.
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
- **Connections** (AudioMuse, Last.fm and more), **Library sync**, **Auto Generated Mixes** and **Scrobbling and history**.
- **Audio passthrough** (below), **Playback** (crossfades, DJ blends, levelling, the equaliser, keep playing) and
  **Streaming quality** (on the Playback page).
- **Colours**: the TV is always dark. **Animated covers** (off from the start: they make the TV slower) is beside it.
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
- Dolby Atmos songs often play quieter than FLACs or the TV's own sound at the same volume, because the receiver turns
  them down to the level they're marked with. **Settings › Playback › Dolby songs on your receiver** has them played
  3 to 12 dB louder: Siansa marks them nearer the receiver's full level on their way, without changing the sound. A
  song can't go past full level, so one already marked near it gets less. It works for Dolby Atmos and Dolby Digital
  Plus, not for Dolby TrueHD, DTS or plain Dolby Digital.
- Songs in the same format play one after another without a gap, a DJ mix or a live album included: the output to the
  receiver stays open from one song to the next. A file that says it's longer than it is (some DJ mixes do) plays to
  its real last frame, read from the file's own audio, so the next one still follows straight on. Most Atmos files
  start with a short priming frame; it goes to the receiver with the rest of the song, as Kodi sends it, so the output
  doesn't stop for it.
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
