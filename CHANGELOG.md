# Changelog

Every version of Siansa, newest first. Each section is also shown as the notes on its GitHub release.
Changes add a note in `changelog.d/` instead of editing this file; the release adds the section here.

## 1.1.0 — 2026-11-07

### Added
- Siansa for Android TV and Google TV: install Siansa from the F-Droid repo on your TV (or from Google Play once it's
  there) and you get the TV app, made for the remote. Your phone keeps getting the phone app.

## 1.0.0 — 2026-11-07

### Fixed
- **Play on showed what the TV was doing instead of its name** ("Casting: Run Away", "SHIELD Android TV"). Each
  Chromecast now shows its own name with "Chromecast" under it, or "Chromecast · speaker" and "· speaker group".
- **Siansa's TV screen took a while to appear after picking a TV with nothing playing.** It now shows as soon as the
  TV opens it and says "Connected. Pick a song on your phone." A song without a cover shows Siansa's icon on the TV
  instead of an empty square.
- **Ending a cast from the phone was logged as a Cast error ("status code 2161").** It now says the cast was ended from
  the phone.
- **Casting an Apple Lossless (ALAC) album ended the cast.** Songs in formats Chromecast and Google TV can't play,
  such as ALAC and other .m4a files, now go to the TV as MP3 320; FLAC, MP3, Ogg, WAV and AAC still go as they are.
- **After one failed start, the TV kept Google's plain screen until Siansa was restarted.** Only that one retry uses
  it now; the next time you cast, Siansa's own TV screen is tried again.
- **Songs in the queue showed blank while casting a long queue, and could stop the music.** Songs the TV doesn't
  describe are filled in from your queue on the phone, and a song nobody can name is left out instead of being
  played as an empty song.
- **A short network drop moved the music to the phone and straight back to the TV, losing most of the queue.** The
  music now stays on the TV while Cast reconnects, and only comes back to the phone if the TV doesn't return.
- **Casting was unsteady: songs from an old queue played, "Now playing" went blank, the player vanished from the
  phone while the TV played, and a long queue never reached the TV.** The queue now stays on the phone, and the TV
  gets the song playing with a few around it, kept topped up as it plays. A new list replaces what the TV plays in
  one go, the phone always shows its own queue, and the music comes back to the phone as it was.
- **Apple Lossless albums still went to the TV as MP3 in "Lossless".** Songs played from your library now get their
  file type from your server before they go to the TV, so ALAC is sent as FLAC and FLAC as it is.
- **The player vanished from the phone while the TV kept playing**, after starting new music during a cast. The phone
  now asks the TV for its queue again when it loses sight of it.
- **Closing Siansa on the TV with its Back button brought an empty queue back to the phone.** The queue comes back as
  the phone last saw it on the TV.
- **Some songs' artists stayed joined in one name** ("ID, Camo & Krooked") when Navidrome had given the joined name the MusicBrainz ID of one of the artists in it. Such a name is now split into its artists like other joined names.
- **Artists joined in one name were only split when they were album artists.** A name like "ID, Camo & Krooked" that's only ever a song's artist now offers its artists too. A part your library doesn't list is found on your server (Navidrome, Emby or Jellyfin), so "ID" on DJ mixes goes to the server's ID artist with its other songs; "ID" or "Unknown" with no artist anywhere isn't offered.
- **DJ blends no longer start half a minute early on a song that's still going.** A song without a steady beat but loud to its end, or two such songs one after the other, now get a short blend instead of the whole crossfade length, so the song isn't cut short and the next song's beat doesn't play over it. A quiet, beatless intro still slides in under the old song for the whole length.
- On Emby, albums whose cover is a cover.jpg or folder.jpg in their folder (and not inside the song files) now show it,
  on the album page, in lists and in the player. Emby keeps that picture on the folder, not on the album.
- On Emby, synced lyrics from .lrc files next to your songs now show. Emby sends them back empty when asked for
  them as they are, so Siansa now asks for them in a form Emby does send, with the same lines and times.
- **The app could crash in the background while playing.** When Android had taken playback out of the foreground (after a pause or a Bluetooth disconnect) and the notification then changed in the background, Siansa crashed. It now plays on, and the notification catches up on the next change.
- **DJ blends came in at the wrong time when the plugin couldn't hear a song's bars.** A song made on a grid (a whole number of BPM, steady) now has its downbeats and 8-bar phrases counted from its first beat, so the blend starts on a phrase and the next song comes in on the one.
- **Adding a music server stopped the music and left an empty server behind**, even when you never saved it. Now it's filled in on the Servers and services page and only added when you tap Save, without restarting Siansa; switch to it from its card when you want to use it.
- **Siansa said it was offline after Android restarted it in the background**, until you force-stopped it. It now looks again as soon as you open it, or as soon as Android lets it use the network.
- Text on the TV that was too dark to read, and buttons cut off at the bottom of album pages: an album's songs now
  come straight after its buttons.
- The TV's Home and Library jumping and losing the remote's place while the library synced.
- 5.1 and 7.1 songs (Dolby or DTS decoded on the device, or multichannel FLAC) played without their centre and
  surround channels, often missing the vocals. They're now folded into stereo, on the phone too.
- Gaps between Dolby Atmos songs sent to the receiver (a DJ mix, a live album): the output now stays open from one
  song to the next.
- Dolby Atmos songs in `.m4a` or `.mp4` files on the TV: they now always stream as the original file while passthrough
  is on, and a song the receiver can't take after all plays decoded on the TV instead of failing.
- Dolby Atmos songs one after another on the TV (a DJ mix) now keep the output to the receiver open from one to the
  next, so there's no gap while the receiver finds the signal again, and a song that stalls there is started again
  instead of switching to a converted stream without Atmos.
- Songs whose file says it's far longer than it is (some Dolby Atmos DJ mixes in `.m4a`) stopped at their end and
  didn't load the next song in time, so a gapless mix had gaps, and the end of a song could switch to a converted
  stream without Dolby Atmos. Siansa now goes by the song's length from your server, on the phone too.
- The TV's Now playing: the cover is blurred much more, on TVs without Android 12 too (an NVIDIA SHIELD showed it
  sharp), the page is calmer, with the other buttons round under the controls and their names shown as the remote
  reaches them, and it runs more smoothly.
- Buttons on the TV that looked joined to the one beside them when the remote was on them.
- The TV's tabs along the top go back to that tab's own page: Library from an album opened in it shows the Library
  again, not the album.
- The TV's Library and Home rows show six covers across instead of four or five, and the lyrics in Now playing can be
  scrolled with the remote.
- Pausing for most of a second when making a mix (Today's mix, a daily mix), on the phone too; and the TV uses less memory for covers,
  giving them back when the TV runs low (an NVIDIA SHIELD kept warning of low memory). The sleep timer is gone from
  the TV app.
- Liked songs on the TV showing nothing: they now come from your server, as on the phone.
- Typing on the TV: text fields open Android's own text box with the keyboard up (Search as soon as you reach it), and
  take what's typed or pasted on a phone with the Google TV app.
- Siansa's settings showing the Siansa plugin as 0.3.3 whatever version was installed (update the plugin to 0.5.6).
- On the TV (and phones low on memory), covers no longer all load again every minute or so, which made the app lag
  and fetch the same covers over and over. While Siansa is on screen it keeps half of them when memory runs low.
- While casting, skipping a song no longer jumps back to the song before for a moment (and no longer tells
  ListenBrainz or Last.fm about both songs).
- While casting in Opus or MP3, moving the seek bar now plays from that point instead of starting the song over:
  the server starts the converted song where you moved to.
- On Emby and Jellyfin, downloaded songs now play from the phone. Before, they still streamed from the server, so
  with no connection a downloaded album skipped from song to song without playing.
- Home's new albums notice took a while to appear after opening the app, and only showed after going to another page
  and back. It now shows as soon as your library has loaded.
- TV app: Dolby Atmos songs sent to the receiver now run into each other without a gap. Most Atmos files start with a
  short priming frame, and the output to the receiver closed and reopened before each of those songs.
- TV app: buttons on the settings pages (Test, Save, Remove, Show the log and others) now get the accent ring when the
  remote is on them. Before, they worked but nothing showed you were on them.
- The Library (on the TV especially) no longer stalls on albums with big animated covers. Lists used to download each
  animation whole, some of them 20 to 60 MB, just to show its first picture; now they read only the start of the file,
  and the animation downloads when you open the album or play it.
- TV app: Dolby Atmos songs that follow straight on from the one before no longer stop for a moment about 3 seconds
  in. Siansa took the silence in its own mix (the song goes to the receiver instead) for lost sound and started the
  song's stream again.
- TV app: songs no longer drop out for a moment in the middle (FLAC and other songs Siansa mixes) when the TV is busy
  or low on memory. The TV now keeps half a second of sound ready instead of an eighth of a second.
- TV: the remote moves through a login's boxes like through buttons, and down from the last one (such as the
  AudioMuse password) reaches the Test button under it and stays there. Press OK on a box to type in it; up or down
  while typing goes on to the next control.
- TV app: the remote now moves down from a text box to the button under it, so Test under the AudioMuse login (and
  the other Test and Save buttons under a login) can be pressed.
- TV app: API keys, tokens and passwords show as dots while you type them, not as plain text. Last.fm's API key is
  hidden too, on the phone and the TV.
- TV: Similar albums on an album's page found nothing ("AudioMuse doesn't know this song"). It now asks AudioMuse
  about one of the album's songs, as the phone does.
- Casting to a Chromecast or Google TV: the phone no longer sends the TV songs it already has at every song change,
  which made the TV's list longer and longer. If the TV's Siansa screen closes by itself, the phone joins the TV
  again and the music carries on there, instead of coming back to the phone paused.
- TV: the remote no longer skips the Test buttons under the AudioMuse and Koito logins: down from the last box goes to Test.
- **Smart crossfade: a loud outro without a beat no longer plays half a minute under the next song.** Only an outro that winds down gets the long blend after its last beat; one that stays loud is blended over its last few seconds, so two songs don't play loud together.
- On Emby and Jellyfin, renaming a playlist right after adding or removing songs no longer goes back to the old name
  a moment later.
- **TV: Dolby Atmos songs that run into each other no longer stall at the start of the next one.** A file that says it's longer than it is now plays to its real last frame, so the next song starts in step.
- **TV: no more freeze as a song starts.** Small things Siansa saves while music plays (covers compared, a mix's place, notices tapped) no longer hold up the screen.
- **TV: typing and pasting from your phone.** Text fields are the TV's own, in the page: OK brings up just the keyboard, with no box popping up, and the Google TV app's keyboard and pasting work as in other apps.
- **TV: Up next marks the songs Siansa added on its own** with a star, as on the phone.
- **TV: Now playing's buttons line up.** The round buttons under the controls (Like, Lyrics, Up next, Song radio, Song path) each sit right under a control.

### Added
- **Smart crossfades blend after the last beat.** When a song ends in a beatless outro, the next song now comes in right after the last beat, and the outro plays out underneath it with its own fade, the way a DJ would mix it. There's no beat to clash with, so it works whatever the two tempos are. Needs version 0.5.0 of the Siansa plugin for AudioMuse-AI. It doesn't measure your library again: songs are checked as they come up in what you play.
- **Sound quality for Chromecast.** Tap the cog beside a Chromecast in **Play on** to choose **Lossless** (the
  default) or **MP3 320**. In Lossless, Apple Lossless (ALAC) and other lossless files Chromecast can't play are now
  turned into FLAC by your server instead of going as MP3. Navidrome needs its "flac" transcoding for this; recent
  versions have it, and the docs say how to add it.
- **Play on a TV or speaker from Home**, with **Play on…** in the menu at the top of Home.
- **Put Home's menu in your order** in **Settings › Style › Home Menu**.
- **A Siansa screen on the TV when casting.** A Chromecast or Google TV now shows Siansa while it connects, then the
  song's cover, title, artist and album with a progress bar, instead of Google's black "Default Media Receiver"
  screen. The TV remote's play/pause, next and previous buttons work, and TVs that can play your original files (FLAC
  and so on) get them instead of MP3. Every Chromecast and Google TV is listed in **Play on**: one that can't show the Siansa
  screen gets Google's plain screen instead, and if the Siansa screen fails to start, the cast tries again with
  Google's by itself.
- **Your Stations: internet radio and podcasts.** A new Stations chip in Your Library. It shows your server's radio stations (Navidrome keeps a list), and you can add your own with + › Add station: a stream link or a .m3u or .pls playlist. Podcasts you add by their RSS feed (+ › Add podcast) are under the Podcasts chip. Stations play live, with what's on when the station says; podcasts open their episodes. What you add stays on this phone.
- **Sign in with an API key.** On an OpenSubsonic server that gives you an API key (such as newer Navidrome), enter it instead of your username and password under Settings › Servers and services. Siansa uses it once your server says it takes API keys, and Test and Save tell you plainly when it doesn't.
- **Android Auto.** Siansa now shows up on the car's screen: browse Home (your mixes, mood radios and recently
  played), Playlists, Albums and Artists, search by typing or by voice, and play. Picking a song plays its whole list
  from there. Installed from F-Droid, GitHub or Siansa Dev? Turn on "Unknown sources" in Android Auto's developer
  settings once, as the Playback page of the docs shows.
- **More of Siansa on the TV.** The TV app's Home now has Today's mix, your mixes, radios, Rediscover, Jump back in and
  the other rows of your phone's Home, in the same order. The Library has liked songs and genres. Songs have Play next,
  Add to queue, Like and song radio. Now playing has Like and Up next.
- **Favourites on the TV**: favourite albums and artists in the Library, and a heart on album and artist pages.
- **Artist pages on the TV**: top songs, albums and similar artists, with Play, Shuffle and Artist radio; artists,
  genres and albums on other pages open them.
- **Several servers and users on the TV**: switch between them, add one, and pick whose music and which libraries
  show, under Settings.
- **Lyrics and song radio in Now playing on the TV**, over the cover blurred as on the phone, and today's daily mixes
  on Home.
- **AudioMuse's song path and song alchemy on the TV**.
- **Animated covers on the TV**, in Now playing and on album pages, and playlists' own covers from the server.
- **The TV opens Now playing when a song starts** (it can be switched off under Settings).
- **Settings on the TV**: servers, library sync, playback, streaming quality, mixes, scrobbling, colours and help,
  the same pages as on the phone.
- **AudioMuse on the TV**: Your sound and Discover for you among your mixes, sound radios, similar songs, and songs
  that sound like a few words you type or say in Search.
- **Skipping within a song on the TV**: rewind and fast forward on the remote work anywhere, and the progress bar in
  Now playing takes left and right for remotes without them.
- **Report a problem from the TV.** Debug logs are on in the TV app from the start, and a report sends the log the
  way the phone does.
- **Set up the TV with your phone's services too.** When you sign in the TV with its QR code, a tick box on the phone
  also sends AudioMuse, the AI service, Audiobookshelf, Last.fm and ListenBrainz (whichever are set up), so the TV
  has them without typing.
- **Siansa for Android TV and Google TV.** A TV app of its own that plays from your server without a phone casting
  to it, built for the remote: Home rows, your Library, Search (by voice too) and a big now playing. It installs next
  to the phone app and never replaces it.
- **Sign in the TV from your phone.** Scan the code on the TV, tap Open in Siansa, and your phone sends the TV your
  server and login over your home network. Typing it in on the TV still works.
- **Audio passthrough on the TV.** Dolby Atmos and other Dolby and DTS music can go unchanged to your AV receiver or
  soundbar: Settings › Audio passthrough on the TV.
- **Add to playlist, Song details and Stop the mix on the TV**, in Now playing (under More) and in a song's actions, as
  on the phone. A new playlist's name is typed in Android's own text box, so a phone's keyboard works there too.
- **Slide from the play button** in now playing to start something from the song playing: up for a DJ mix, down for a
  Steady radio, left for a song path and right for songs that sound like it. Only the way you slide shows its choice,
  and a tap and a press-and-hold do what they did. Change what each way does, or switch the slides off, under
  Settings › Style › Now playing.
- While a DJ mix plays, the play button has record grooves in its own colour.
- TV app: Dolby Atmos and Dolby Digital Plus songs sent to your receiver can play louder, so they match your other
  music at the same volume. Pick 3 to 12 dB under Settings › Playback › Dolby songs on your receiver. The sound itself
  isn't changed: the receiver just turns them down less.
- Liked songs can be a notice at the top of Home, a tap away. It's off to begin with: turn it on under Settings ›
  Notices and Cards › Notices, as Automatic or Pinned.
- Hide the title of any section of Home: in Arrange Home, tap a section's cog and turn off Show its title. The section
  keeps its place and spacing, just without the heading above it.
- Slide right from an album's Shuffle all the way onto Play for a DJ mix of the album: all its songs, in an order that
  blends well, with DJ blends. It doesn't always start from the same song. Play turns into a record while the mix
  plays. Needs AudioMuse-AI.
- Settings › Style › Now playing › Dynamic colours: the seek bar, the Play button and where it plays take the colour
  of the cover of what's playing, fading from song to song. They're always easy to see, whether the cover is dark or
  light; a black and white cover keeps your theme's colour. Off at first.
- TV: hold left or right on the remote in Now playing to wind back or forward through the song, faster the longer you hold. A press still skips.
- TV: Search shows your recent searches, radios and your genres before you type, as on the phone.
- **TV: notices on Home**, as on the phone.

### Changed
- **Artist radios use AudioMuse-AI.** With AudioMuse-AI set up, an artist radio (from Home or an artist's page) plays songs that sound like the artist, with one of their own every fourth song. Before, it only used your music server's similar songs, and played just the artist's own songs when the server had none. Without AudioMuse-AI it still uses the server's similar songs. In the queue, the songs AudioMuse-AI picked carry its mark, as a song radio's do.
- **More DJ blends are beat-matched.** House, techno and drum and bass made on a fixed tempo were often judged "not steady enough" and got only a short join. With the new audio engine they're now beat-matched over your blend length.
- **Opus for Chromecast and DLNA.** Songs converted for a Chromecast now go as Opus 320 instead of MP3 320: better
  sound for the same Wi-Fi. **Sound quality** (the cog beside a Chromecast in **Play on**) now has **Lossless** (the
  default), **Opus** and **MP3 320** (for older TVs and speakers that can't play Opus). A TV that can't play Opus
  switches to MP3 320 by itself. DLNA TVs and speakers that list Ogg among their formats get Opus too, and MP3 320 if
  they don't start an Opus song.
- **Smart crossfade: cold ends.** When a song ends loud and the next one starts loud, they no longer overlap for a moment (which sounded like a mistake). The first song plays to its last sound, then a short pause, then the next song from its start, as a radio DJ would. When both songs keep a beat (dance music), there's no pause: the next one starts right as the first ends. Only when both are hard: every other blend is as before.
- **The crossfade length says what it does with Smart on.** A line under the slider explains that it's the longest a fade can be: most fades are shorter, because the songs decide, and the full length is only used for songs not measured yet and for quiet intros without a beat in DJ blends.
- **A new song radio starts as Steady vibes with no surprises.** The shape and surprises you pick still apply while it plays, but each new radio starts calm and close to the song again. No surprises is also the default now.
- **A fresh install uses Siansa's own colours**, with grey pages and cards and the accent matching the theme (Siansa's amber). A theme you already picked stays.
- **DJ blends with a beatless intro or outro run your whole crossfade length.** When one of the two songs has no beat at all, there's nothing to clash with, so the next song now comes in under the old one over the full length you set, instead of a join of a few seconds.
- **Echo at the end and Keep the key are off for now** and no longer in Settings › Playback. DJ blends play plain, and the next song's pitch follows its speed, by at most a fraction of a semitone.
- **One menu at the top of Home.** The button beside the dice now opens a short column of round buttons under it: **Play on…**, **Sources** and **Arrange Home**, each with its name beside it. Each opens its usual menu. Press the button and slide down to one to open it in one go. While something plays on a TV or speaker, Play on… is amber and the button shows a small dot.
- **The dice rolls.** Tap it and it tumbles, keeps turning while it finds a song, and lands on a new face when the song starts.
- **A cleaner top of Home.** The greeting is gone: the notices are now the top of Home, with the menu as a round button at the end of their row, the chips' height and in line with them. Its lines are always amber, and it has a thin amber ring while the source filter hides something. The menu now opens a little further from the screen's edge.
- **The dice is in Home's menu.** **Roll the dice** is the first option under the round button; the dice tumbles in the button while it finds a song.
- The notices are no longer in Arrange Home, since they're always at the top.
- In the queue, swipe a song to the left to take it out (it was to the right), and to the right to play it next. The
  icon on the strip moves a little once it's done, and so does the queue icon when you swipe a song into the queue
  from any list.
- Starting a song radio, Steady and DJ each have an icon (a wave and a turntable) and no longer a description under
  them, so the choice is clearer.
- Settings › Playback › Crossfade and levelling has a link to the docs on how mixing works.
- **Start song radio** is now called **Start a mix**, and Stop song radio **Stop the mix**.
- The new albums notice counts the albums added in the last 30 days (it was the last three).
- Sliding from the play button now shows the choice's name in the middle of the cover, which blurs behind it, so your
  thumb no longer hides it. It's faint with "Slide a little further" until you're far enough, then lights up with
  "Let go to start". The icon beside the play button stays.
- Home's shortcuts are now just what you played last, newest first: Liked songs is no longer pinned first (it shows
  when you've played it). Choose how many shortcuts Home shows, from 2 to 8, under Settings › Style › Home.
- TV app: an artist's page shows their top three songs, with Show all for the rest, so their albums are only a few
  presses down.
- TV app: animated covers are off from the start, which makes the TV quicker. Turn them on under Settings › Animated
  covers.
- TV app: pages switch at once, without fading, so moving around feels quicker.
- TV app: Time machine is no longer in the Library for now. It took the TV several seconds to show and could make the
  music drop out. It's still on the phone.
- TV app: covers take half the memory, load a few at a time and appear without fading in, so pages fill and move more
  smoothly.
- TV app: Home and the Library stay still while your library syncs, and are read again once it's done. Rows that
  didn't change aren't drawn again, and Random picks keep their albums until you open Home again.
- Home's menu is easier to read: while it's open, the page behind it blurs a little and darkens, so the names of
  Play on…, Sources, Arrange Home and Roll the dice stand out. Phones before Android 12 darken it a bit more instead.
- Sliding from the play button now dims the seek bar and the bottom row (lyrics, Play on and the queue) too, and
  sliding down hides Play on completely so it doesn't peek out behind the choice. Sliding up puts the choice right on
  the seek bar.
- An album's buttons: add to a playlist and Download on the left, Similar albums, Shuffle and Play on the right, with the
  first song close under them. The add to queue button is gone from the album's page, and so is the separate "Continue
  from" button: Play itself carries on where you stopped, with a ring for how far you got, and the details line says
  "Continue from 8". Hold Play to start from the first song.
- TV: Now playing is as simple as the Chromecast screen: the cover, the song, its artist and album, and a thin line for
  the time, with Siansa's logo in the corner. OK plays and pauses, left and right skip, and down opens a menu with
  everything else (like, shuffle, repeat, lyrics, up next, start a mix, going to the artist or album, and more).
- TV: the Now playing chip at the top of the pages has a wave that moves while the music plays.
- TV: Similar albums and the other pages with many albums show six covers across, as the Library does.
- TV: an artist's page shows their albums, EPs, singles and DJ-mixes each in their own row, as the phone does.
- **A new audio engine.** Every song now plays through one output that Siansa mixes itself, so crossfades and DJ blends stay exactly in step, beat-matched blends hold their beat, and cold ends and blends after the last beat sound the way a DJ would mix them.
- Dynamic colours on now playing look more like the cover: the colour is the one that fills most of it, so a sepia cover with a red light in it gets tan instead of red and muted colours stay muted, and a black and white or grey cover gets silver instead of your theme's colour.
- TV: rows with only a few covers fill the screen as wide cards, Home's Today's mix, Shuffle, Liked songs and notices are one bar of pills (the Library's shelves too), and an album's artists, genre and label are plain words, with Play and the other buttons in line under them.
- With Dynamic colours on, the filled heart of a liked song in now playing takes the colour of the cover too, like the seek bar and the Play button.
- **Your Library remembers list or grid for each view.** Playlists, Albums, Artists and the rest each keep the look you picked, until you change it, also after Siansa restarts.
- **The log notes how songs blend.** It now says what the crossfade, blend length, beat-matching and bass swap are set to when Siansa starts, and each time you change one, so a report shows which settings a blend was made with.
- **The mini player starts with Play or pause, Next song and Add to playlist.** Cast moved to Home. A layout you already set up stays as it is.
- **Settings, tidied for v1.0.** **Servers and services** is now **Connections**. **Playback** now has the streaming quality and the book player's skip buttons; the separate Audiobooks and podcasts page is gone (your Audiobookshelf server is under Connections). **Streaming and data** is now **Data** and has everything that was under Downloads: your downloads, how they download, the storage limit, and the covers kept on the phone. The **Home** group's pages are now **Notices and Cards** and **Auto Generated Mixes**.
- **Beat-matched blends** are no longer marked experimental.
- **Radio stations show their logo.** Siansa looks up each station's picture once (on Radio Browser, a free list of radio stations, or on the station's own website) and keeps it, so stations no longer all show the same radio icon. It shows in Your Library, in the player, in the notification and on the lock screen.
- **Stations can be edited.** Tap ⋮ › Edit next to a station to change its name or link, or to give it a picture link of your own. For your server's stations you can set the picture.
- **Stations and podcasts sit right in Your Library.** The Stations chip lists your radio stations straight away, without a separate Your Stations card, and podcasts you add by their RSS link are under the Podcasts chip with your other podcasts. Add them with + › Add station or Add podcast at the top of Your Library.
- **Siansa 1.0.** Out of beta, with a new audio engine that mixes every song itself, so crossfades and DJ blends stay exactly in step; slides from the play button for a DJ mix, a radio, a song path or songs that sound like the one playing; a DJ mix of any album; Android Auto; Siansa's own screen on a Chromecast or Google TV; internet radio and podcasts in Your Stations; and Siansa for Android TV and Google TV, in beta.

## 0.51.1beta — 2026-11-02

### Fixed
- **Stopping during a crossfade left the old song playing**, with nothing on screen to stop it. A fade now ends with the player, when the queue is cleared or another song is chosen.

### Changed
- **Beat-matched blends are off by default.** On many phones they still drift out of step; turn them on in Settings › Crossfade and levelling › Beat-matched blends. If you already chose DJ radio or Everywhere, that stays.

## 0.51.0beta — 2026-11-02

### Fixed
- **DJ blends stuttered or came in late.** The next song is now loaded a minute ahead, so it no longer starts a blend cold (it began up to 400 ms late and off the beat). A song sped up or slowed down for a blend is resampled, like a DJ's pitch fader, instead of stretched in time, which could stutter on drums.

### Added
- **Keep the key** (Settings › Crossfade and levelling › Beat-matched blends, off): keeps the next song's key through a blend by stretching the sound instead.

## 0.50.1beta — 2026-11-02

### Fixed
- **Swipe to remove from the queue goes the other way now.** Swipe a queued song to the right to take it out: the
  reverse of the swipe that adds a song. The red strip shows behind the song as it slides, like the amber one when
  adding, instead of filling the whole row.
- **Emby and Jellyfin: album pictures were missing from lists.** An album whose art is in its song files rather
  than in the album folder showed its picture on its page and in the player, but not in Home, Your Library, search
  or an artist's albums. Those lists now show the first song's picture too.
- **Beat-matched blends still began a touch out of step.** The phone reports a wrong position for a few seconds
  after a jump; the silent copy of the song going out, and the next song after it comes in, are now judged only
  once the position is real. The copy starts 14 seconds ahead instead of 8 and is nudged into step in one measured
  move rather than chased.
- **The log said a song started playing minutes after it was chosen** when it had run straight on from the last
  one; it no longer counts from the wrong moment.

## 0.50.0beta — 2026-11-02

### Fixed
- **Beat-matched blends often came in with a hitch and stayed a touch out of step.** The silent copy of the song
  going out that takes over at the blend used to chase the one you hear and overshoot, and the next song was judged
  (and jumped) on a position that wasn't real yet, so many blends began off and were heard as two kick drums. It
  now gets in step with one or two measured jumps and a slight nudge over a longer run-up, the next song is jumped
  into step once at most (after a stall once more), and a blend may start a bar late rather than out of step.
- **Drum and bass and other tracks read at half the tempo got blends twice as long as set.** Tempos are now
  counted the way a DJ counts, so 16 bars are 16 bars.
- **Tracks of a continuous mix ("(Mixed)", "DJ Mix", "Mixed by") were beat-matched over the mix already in them.**
  They're no longer beat-matched; into and out of one the blend is short, and within the mix the tracks run on.
- **A big change of tempo (more than 4 %) was heard for half a minute.** Such blends are kept to 8 bars.

### Added
- **Bass swap** in Settings › Playback › Crossfade and levelling › Beat-matched blends (on by default): over the
  first half of a blend the old song's bass is taken away as the new song's comes in, as a DJ does with the EQ.
- **Take songs out of the queue.** Swipe a song in the queue to the left (the swipe that adds a song, in
  reverse, behind a red strip) or pick **Remove from queue** from its ⋮ menu. **Remove songs up to here** in that
  menu clears everything before that song, so it comes next.

## 0.49.0beta — 2026-11-01

### Fixed
- When the Siansa Analysis plugin isn't installed on AudioMuse, Similar albums now says so, instead of "AudioMuse
  doesn't know this song". Home no longer asks that server for similar artists on every visit while it's missing.
- On Emby and Jellyfin, an album with no cover of its own now shows its first song's cover on its page, as the
  player already did, instead of an empty square.
- **Beats clashed in some DJ blends.** A song queued while another played (a radio just started, a reshaped mix)
  had no measured start yet when its blend was planned, so it got a long fixed blend that ignored its beat; its
  measurements are now fetched as soon as it's queued. A song that ends on a beat no longer blends at length into a
  song that hasn't been measured, nor into one that starts on a beat it can't be matched to (often drum and bass,
  whose breaks rarely read as steady): the blend is short. A beat-matched blend into a short song is laid over
  fewer bars instead of being blended out of step.
- **A flutter after a DJ blend.** As the next song eased back to its own speed it changed speed every 200 ms, and
  each change can be heard. It now eases back in about 20 steps.
- **Cover rows: no arrow beside the card's title**, as on Home's other titles; tapping the title still opens it.
- **Cover rows: the dots under the card follow your swipe.** They stayed on the first column.
- **Drum and bass was never beat-matched in DJ blends.** The Siansa plugin read its tempo at two thirds (113 BPM for
  170), with the beats off the music. Plugin 0.4.2 reads it right and measures every song's beats again in the
  background; update it in AudioMuse-AI.

### Added
- **Cover rows for Home's own sections.** The look picker in Arrange Home (the cog on a section) now offers Cover rows,
  the look custom cards got in 0.48: covers with their details beside them, three to a column. Albums show their song
  count and year, and their cover plays them; books and podcasts show how far you are; a radio or genre shows its colours.

### Changed
- **Home's notice chips look like Your Library's chips**: the same round pills, without an ×. A notice kind you set to
  **Pinned** in Settings › Home › Notices now always shows and is never pushed out by other notices; the automatic
  ones come and go as needed and fill the places left.
- **Changing a DJ mix's shape moves the very next song.** A build or a wind down now takes about eight songs and a
  euphoric climb three, however many songs the radio has queued. Spread over all of them (up to 30), the next song
  hardly changed and the shape took hours.
- **Settings › Home has just Notices and Cards.** The source filter and Arrange Home are on the button at the top of
  Home.

## 0.48.0beta — 2026-11-01

### Fixed
- The Shuffle row in now playing's menu no longer shows a stray purple "MENUSHUFFLE" label.
- With large text, a menu item whose name wraps to two lines is no longer cut off.
- **Shaping a DJ mix now changes the very next song.** Picking a shape or surprise looks again at the radio's songs
  and a fresh lot like the one playing, so the next song follows the new shape (calmer for a wind down, brighter for
  euphoric), with songs that don't fit taken out and new ones put in. Before, it only reordered the songs already
  queued, so the next one often stayed the same.

### Added
- **Cover rows, a new look for your own cards.** Each row is a cover with who it's by, what it is and how long it is
  beside it, three to a column; swipe sideways for more. Tap the cover to play or carry on, tap the row to open it,
  and ⋮ (or press and hold) for more.
- **Cards of audiobooks and podcast episodes.** A custom card can now hold Audiobooks, Podcast episodes (the newest
  of your podcasts) or Keep listening (books and episodes you're partway through) from Audiobookshelf. They show as
  Cover rows, with how far you've got along the bottom of each cover.
- **Notices on Home.** A row of chips at the top of Home when there's something to say: the library sync failed, a
  book or episode to carry on with, new albums in your library, last month's report in the first week of a month,
  and new artists for you. Tap one to open it, or its × to hide it. They're ordered for you by default; in
  Settings › Home › Notices you can hide the row, or pin, keep automatic or turn off each kind.

### Changed
- **Settings are grouped and reordered**, so each setting is where you'd look for it. The start page has six groups:
  Sources, Listening, Home and mixes, Look, Your listening and App.
  - **Streaming and data** (new) has the streaming quality and loading songs ahead, both moved from Playback, and the
    song cache.
  - **Audiobooks and podcasts** (new) has the book player's skip buttons.
  - **Home** (new) opens the source filter, Arrange Home and Cards.
  - The image cache moved to Downloads, next to Download all covers. Animated covers moved to Style. Storage is gone.
  - What's new is in About. The Time Machine's "songs not in your library" switch is also in Scrobbling and history.
  - Connections is now **Servers and services**, Daily Vibe settings **Mixes and vibes**, Scrobbling **Scrobbling
    and history**, and Backup **Backup and restore**.
- **Settings sections start open.** Tap a heading to fold it; it stays folded the next time you come back.

## 0.47.0beta — 2026-10-31

### Fixed
- **DJ blends no longer cut or flutter at the start.** The next song is sent ahead by what this phone really needs
  (about 60 ms, not 150), a near miss is corrected by speed instead of a jump, and the speed is only nudged by up to
  1 % and only when it matters, so the beats come together without a pitch wobble.

### Added
- **Today's mix has its own settings**, at the top of Settings › Daily Vibe settings. **Auto update** on keeps it
  following the time of day; off keeps the mix you last made with ↻ until you press it again. **Songs**, **Surprise**,
  **Leave out most played** and **Days before a repeat** can be set for Today's mix on their own, or left to the vibe
  that makes it.

### Changed
- **Repeat and Add to playlist in now playing give a short buzz** when pressed, like swiping the mini player. It
  follows your phone's touch vibration setting.
- **Settings pages are in sections that fold.** Each shows its heading and what's set, and opens with the arrow on the
  right, as Connections does: Playback, Crossfade and levelling, Scrobbling and Storage.

## 0.46.0beta — 2026-10-31

### Fixed
- A DJ blend no longer gives up when the end of the old song is still loading at the moment it should start: it waits a moment for it, so the next song blends in instead of coming in with a cut.

### Added
- **Choose the colour behind everything.** Settings › Style › Themes › Pages and cards: Grey, Warm grey, Blue grey,
  or Match theme for the colour scheme's own tinted backgrounds (Siansa's purple among them).

### Changed
- **Pages, cards, menus and sheets are grey now**, like Spotify, instead of tinted purple. That's the now playing ⋮
  menu, Home's tiles and cards, and the Your listening pages too.
- **Swiping the mini player to change the song gives a short buzz**, so you feel the song change. It follows your
  phone's touch vibration setting.

## 0.45.0beta — 2026-10-31

### Added
- **DJ radio mixes phrase on phrase, like a DJ.** With the Siansa plugin 0.4.0, which finds the phrases a song is built from (its 4 or 8 bars), a beat-matched blend starts where a phrase of the song going out starts, the next song comes in on its first phrase, it lasts whole phrases, and the song going out is let go at the end of a phrase instead of wherever it happens to stop. Songs the plugin hasn't measured again yet blend bar on bar as before.
- **Echo at the end of a DJ blend.** Over the last bar the old song rings out in a hall, as a DJ echoes a track out, so the join is softer. On by default under Beat-matched blends; off if you'd rather not.
- **Blend length for DJ blends**: 4, 8 or 16 bars (8 by default), apart from the crossfade length, so ordinary fades can stay short while DJ blends run long.

### Changed
- **Smoother beat-matched blends.** The next song is no longer jumped into step once it can be heard (a jump is a cut in the sound); its speed brings it in step instead, and one odd position reading no longer sets it off. The old song is handed to the fade only when its two copies are in step, so the handover doesn't swish.
- **Crossfades up to 30 s** (was 12), for long, slow fades.
- A DJ blend now starts on the bar before its length rather than after, so it doesn't lose a bar when a song's end isn't on a bar.
- A quiet build-up that keeps a steady beat is laid on the old song's beats too, so when its drums come in they're in step.

## 0.44.2beta — 2026-10-31

### Fixed
- **DJ blends no longer stop a moment after they start.** When the next song came in a little off the beat, Siansa
  nudged it into step, then took that for you skipping and ended the blend at once.
- **The next song comes in on the beat.** Siansa now learns how long your phone takes to start it at a point, and
  starts it that much earlier.
- **Crossfades no longer stumble as they begin.** The fading song is now exactly in step when the fade takes over,
  instead of repeating a sliver of it.

## 0.44.1beta — 2026-10-31

### Fixed
- Similar songs, similar albums and radios now work for the songs of another user shown with yours (for example your kids' library): their songs go to AudioMuse under that user's own "Server name in AudioMuse".
- **"Your Library" was cut off in the tabs with a larger font.** The tab names now get a little smaller to fit.
- **Short albums left an empty gap at the top when scrolled.** A single's page now scrolls its cover away like a long
  album's: the title comes up under the title bar, and the spare room is below the songs.
- **A big animated cover could stay still for good.** When your server took too long to make its small version the
  first time, Siansa showed the cover still and kept it that way. It now asks again the next time it starts.
- **Home's source filter:** after a sync, the libraries sit just under the sync line, with no extra gap.
- **Custom card rules:** the ways of comparing (contains, is…) sit evenly between the rule's name and its value.

### Changed
- **Now playing scrolls as one page.** The small bar that appeared at the top when you scrolled down is gone: it
  showed the heart and pause button twice and half-covered the song's title. Scroll back up for the controls.
  About the artist and About this song now take a deep shade of the cover's colour instead of the same purple for
  every song.
- **"Speaker plays the queue" is gone.** On the JBL Bar it still paused a few seconds between songs, the same as
  without it, so it brought nothing. TVs and speakers that take the next song ahead still play without gaps.
- **A new app icon.** Siansa's sound bars in frost and amber, with a reflection under them, between its double half
  circles, on black.
- **Siansa starts in Mono with an amber accent,** to match the new black icon. If you picked a theme yourself in
  Settings › Style › Themes, it stays as it is.
- **Play button cards on Home only play from their ▶.** Tapping anywhere else on the card opens it, so it no longer
  starts playing by accident, and the arrow at the end is gone.

## 0.44.0beta — 2026-10-31

### Fixed
- Shape the mix: "Surprises" no longer breaks over two lines next to its slider.
- Albums, playlists and mixes with only a few songs: the cover now fades away fully and the title bar turns solid when you scroll to the end.
- Now playing: when you scroll down, the page now fades in under the small header, instead of a cut-off line of text showing below it.
- Arrange Home: the spacing step's name ("Compact") no longer breaks over two lines.
- Tiles, banners and cards no longer break a long word in two ("Melanchol / y"): the title gets a little smaller instead.
- Light mode: the tabs at the bottom are easy to see again; the bar behind them is now light too.
- Rearranging the nav bar and mini player buttons no longer gets stuck after the first move: rows make room every time.
- Arrange Home: holding a row near the top or bottom scrolls the list, so a row can be moved anywhere.

### Added
- Custom cards: song cards can now use **Play count**, and song and album cards get **Played** (when it was last played, for example "not in the last 90 days") and, for songs, **Times skipped**. Most played puts a song's own plays first.
- Settings › Style › Phone's top bar: keep it see-through, make it black, or hide it for full screen (swipe down from the top to see it for a moment).
- Play on a speaker: LinkPlay speakers (JBL Bar, WiiM, Arylic and others) can play the next few songs by themselves, so songs follow without the usual few-second gap. Experimental: turn it on with the cog beside the speaker.
- Jellyfin (10.9 or later) as a music server, alongside Navidrome and Emby: browsing, playback, playlists, favourites, play history, "now playing" on Jellyfin and lyrics.

### Changed
- Settings › Connections: every server's card now shows its address and login, so you can change a server that isn't in use without switching to it first.
- Chromecast: the TV or speaker now loads the next song before the current one ends, so songs follow each other with little or no gap.
- Home › Source filter: a **Sync all** button, Audiobookshelf listed with your music servers, and each sync shown on one small line under the server, with a single tick when it's done.

## 0.43.0beta — 2026-10-30

### Fixed
- Time Machine finds more of your songs in your library: a remastered, mono, single or "feat." version of a song, a title with or without its apostrophe, and "Beatles" for "The Beatles" now count as the same song, on any album, instead of "Not in your library".
- Search finds names with an apostrophe when you type them without one: "Im looking" finds "I'm Looking Through You", "dont" finds "Don't".

### Added
- Shape the mix: while a DJ song radio plays, the queue has a choice of Steady vibes, Steady build, Euphoric or Wind down for what the energy does over the next songs, and a Surprises slider for how far the mix may stray. Each change reorders the songs not yet played. The same shape applies to Keep playing in DJ mode.
- Change the look of any section on Home: in Arrange Home, the cog on a section offers the custom cards' looks (Tiles, Banner, List or Play button) as well as its own. A banner or play button plays the section where it can, such as today's mix or a row of albums. Your cards keep their own looks; their cog opens Settings › Cards.
- Custom cards can now be built from rules that keep or leave things out: each rule compares a song title, album, artist, genre, record label, release type, year, length, plays, date added, favourite or compilation ("contains", "doesn't contain", "is", "isn't", "at least", "between"…), and a card keeps what matches every rule or any of them. Cards can now list whole albums too, for example every 2024 album on one record label, and be sorted (random each day, newest, recently added, most played, A to Z) with a limit on how many. Cards are made and changed on a page of their own in Settings › Cards; cards made before keep their rules.
- Mix titles and sound searches can now be written by OpenAI, Anthropic, Google Gemini, Mistral, Groq, Ollama on your own network, or another service with an OpenAI-style API, as well as OpenRouter. Pick one in Settings › Connections › AI.

### Changed
- Settings › Users and Settings › Connections are now one page, Settings › Connections. Under Servers, each music server login has its own card with its address, login, the name shown in Siansa, its name in AudioMuse-AI and its library sync, followed by Audiobookshelf and Add a music server. Under Services are AudioMuse-AI and AI. Each card folds open with its arrow and starts folded.
- The loudness target (−18 or −14 LUFS) now also applies when songs are levelled by their ReplayGain tags alone, not only with the Siansa plugin. At −14, tagged songs play 4 dB louder than their tags say, but never louder than the file itself or past their peak. The choice now shows whenever levelling is on.

## 0.40.0beta — 2026-10-29

### Added
- Beat-matched blends in DJ radio (experimental). When two songs both keep a beat, the next one now starts on its first downbeat, on a bar of the song going out, and plays a touch faster or slower so the beats keep time through the blend, like a DJ mix; afterwards it eases back to its own speed. Two beats that can't be matched get a short blend instead of a long clashing one. Needs the Siansa plugin 0.3.0, which measures each song's beats; Settings › Playback › Crossfade and levelling › Beat-matched blends turns it off or on everywhere.

## 0.39.1beta — 2026-10-29

### Fixed
- **A cover replaced on your server now shows in the mini player, now playing, the lock screen and the notification too**, after the next sync. Before, those kept the old picture while the album page showed the new one.
- Play buttons on album, playlist, artist, custom card, Time Machine, report and fingerprint pages now turn into Pause while their songs play, and pause them instead of starting over.

## 0.39.0beta — 2026-10-28

### Fixed
- **Music on a DLNA speaker or TV now comes back to the phone, paused, as soon as you leave its Wi-Fi.** Before, Siansa could keep showing the speaker for a minute or more after it was out of reach. A speaker that stops answering is also let go after about 15 seconds, and a short note says so.
- **A podcast's progress bar now shows the episode you're on and moves as it plays.** It showed full with "0 min", or "47 min of 0 min".
- **Episodes without a season or number no longer show "S E".**
- **Smart crossfades no longer play the silence or the long fade-out at the end of a song.** The next song comes in where the song really ends: dead air is left out, and a long fade-out is cut once it's quiet, so the next song blends in while it's still fading. A silence mid-song, a quiet passage or a soft ending that stays at one level is never cut.

### Added
- **The song cache can be switched off.** Settings › Storage › Song cache › Off: no song is kept on the phone once it has played, and the songs already cached are removed. Loading songs ahead still works, for 1, 2 or 3 songs (Settings › Playback); each is removed once it has played.
- **Custom cards on Home.** Settings › Cards › Custom cards: make cards of your own and pin them to Home's new Your cards section. Give each a name, pick its look (song tiles, an album row, a banner, a song list or a play button) and where its songs come from: your library, by genre, years, artist, album, words in the title or favourites, or a sound described to AudioMuse-AI. Change a card any time, or take it off Home with its switch.
- **Podcast episodes show what they're about.** On a podcast's page, the arrow beside an episode unfolds its own description from Audiobookshelf: the guests and a short summary.

### Changed
- **Settings › Made for you cards is now Settings › Cards**, with Made for you and Custom cards on one page.
- **Book and podcast pages look like album pages.** The cover sits in the middle with its colours behind the page, the title and author read the same way, Audiobookshelf's description is at the top (tap it for the rest), and the buttons are the album page's: download, a ✓ to mark it finished (✕ to take that back) and the big round Play, which pauses while it plays.

## 0.38.5beta — 2026-10-28

### Fixed
- **Moving a section to the top in Arrange Home made it vanish.** It now stays in view at the top while you drag it.

### Changed
- **The F-Droid repository is now called Siansa and shows the Siansa icon** on the Repositories page of the F-Droid app, instead of "Siansa beta" with a QR code. Siansa's page in F-Droid now shows its open-source licence (GPL 3) and links to the source code, issues and changelog on GitHub.

## 0.38.4beta — 2026-10-28

### Fixed
- **Music no longer ends with a crash when the connection gets slow in the background.** The song now waits for data and carries on when the network is back.

### Changed
- **Problems you report from the app now open on Siansa's GitHub page.** The docs and the full list of changes are there too, and the links in About and What's new go there.

## 0.38.3beta — 2026-10-28

### Fixed
- **Only one mini player at a time.** The bar shows whatever you started last, music or a book or podcast.
  Pausing keeps it there; stopping it leaves no mini player, instead of the other one appearing from behind.

### Changed
- **Now playing and the book player open and close more smoothly.** The mini player fades and recedes as the
  player rises over it, and comes back the same way as the player goes. Swipe up on the mini player to open it,
  and both follow your finger: a hard swipe is quick, a gentle one settles.

## 0.38.2beta — 2026-10-27

### Changed
- **Siansa is open source**, under the GNU GPL version 3 or later. The code is at github.com/echovaultstudio/Siansa.
  Releases now come from Google Play's beta and the F-Droid repository, not as a download from GitHub.

## 0.38.1beta — 2026-10-27

### Fixed
- **Podcasts wouldn't play from Home or their page.** The ▶ said the podcast had no audio files; it now plays the episode you were in the middle of, or the newest one.
- **A book's mini player came back over the music's.** When a book or podcast couldn't start, its mini player stayed and popped up in front of the music's whenever a song paused. It's gone now, and starting music pauses a book, like a book starting pauses music.
- **Siansa could close when Audiobookshelf was removed.** A book shown on Home or its page now shows a plain cover
  instead.

### Changed
- **The time machine and Your listening open much faster.** A finished month or year is kept on the phone and
  shows at once; the current one shows what it had and then updates with the latest plays. The listening clock now
  counts every play of a year, not just the last 5,000.

## 0.38.0beta — 2026-10-27

### Added
- **About in Settings.** It shows the version and links to the open-source licences of the libraries Siansa is
  built on, the docs and the privacy policy. The licences also have their own page on siansa.app.

### Changed
- **The time machine shows the whole picture of a month or year**, as Your month does: minutes, plays, artists and
  songs at the top, your top artists with their plays, your top songs (with Play, Create memory and the songs not in
  your library, as before), top albums, your listening clock and how your taste moved compared with the period before.
- **Audiobookshelf moved to Settings › Users**, next to your music logins, with a new **Scan for new books** that
  asks the server to look for new and changed books, as its own Scan button does. An admin login is needed for the
  scan; the login itself can be any user.

## 0.37.0beta — 2026-10-27

### Fixed
- **A book's mini player shows while the book plays**, also when a song was paused to start it. Before, the song's
  mini player stayed on top and the book's was hidden behind it. Now the mini player is whatever is playing, or
  what played last.
- **Playing from a book's page no longer opens the player.** Tap the mini player to open it, as with music.
- **The book's cover in the player is the size of an album's** in Now Playing, not larger.
- **Books start sooner**: they begin once a second is loaded instead of two and a half.
- **Home's rows are spaced alike.** Album rows (Rediscover, Recently played, Random picks…) had extra room around
  each cover, so they sat further in, further apart and lower than the genre and radio rows. Every row now starts
  at the page's edge with the same gaps, and the mix and book carousels no longer leave extra room under their dots.
- **Play buttons on Home's mix cards show when that mix is playing.** Today's mix and the mixes and vibes in the
  carousel show ⏸ while their songs play, and a tap pauses them or plays on, as the book cards already do.
- **Home no longer jumps at the books carousel.** It used to load again every time it scrolled into view, appearing
  a moment later and pushing the page down. It now keeps its books and shows straight away, and asks Audiobookshelf
  again at most once a minute. Without podcast libraries, nothing is asked for podcasts at all.

### Added
- **Genre and record label pages.** At the foot of an album's page, its genres and record labels are tags: tap one
  to see every album in your library with that genre or label. Labels are read during the library sync, so a
  label's page fills after the first sync on this version.

### Changed
- **A cleaner album header.** Under the artist, an album now shows just its type and year. Its version, number of
  songs, length, genres and labels are at the foot of the page.
- **"Users and library sync" in Settings is now just "Users"**, the same as its page.
- **Help in Settings.** Report a problem and Logs are now one entry, **Help**, at the bottom of Settings. It has the
  **Debug logs** switch, the log itself, and Report a problem, with the steps to a useful report.
- **A report always carries the log.** It can only be sent with debug logs on and something recorded, after you
  confirm you made the problem happen a few times, so we can see it more than once.

## 0.36.0beta — 2026-10-27

### Fixed
- **Press and hold play to stop a book.** As with music, holding the play button in the book player or its mini
  player stops the book and clears the mini player. Before, holding it did nothing.
- **The last book you played is in Home's shortcuts.** Playing a book or a podcast adds it to the shortcuts at the
  top of Home, like an album, playlist or mix; the shortcut opens the book's page.
- **▶ on a book card pauses the book that's playing** instead of starting it again from the server. Tapping it twice
  used to open a new session on Audiobookshelf each time and leave the old ones open. The card and the book's page
  show ⏸ while that book plays.

### Added
- **A spacing slider in Arrange Home.** One slider, from Tight to Airy, sets the gap between every section on Home.
  Home starts out a step tighter than before.

### Changed
- **Search finds books and podcasts too.** With an Audiobookshelf server set up, a search shows **Books** and
  **Podcasts** rows under the music matches, by title, author or narrator; a tile opens the book's page.
- **The book player looks like Now Playing.** It slides up over the page like the music player, with the cover's
  colours behind it, the chapter at the top, the chapter's bar, chapter and skip buttons around play, and the speed,
  where you are in the whole book and the sleep timer underneath. Bookmark is next to the title; everything else is
  in the ⋮ menu. Drag it down to close.
- **Home's books and podcasts carousels show five cards**, not eight: what you're in the middle of, then up next
  and new. The rest are under Books and Podcasts in Your Library.
- **A book's page looks like an album's.** The cover's colour fades down from the top behind the cover and title,
  and the title bar turns solid in that colour as you scroll. The cover now lines up with the title instead of
  sitting low under a long list of narrators.

## 0.35.1beta — 2026-10-26

### Fixed
- **The Audiobookshelf login wasn't kept.** Test worked, but Save cleared the address, token, username and
  password again. They're saved now.

## 0.35.0beta — 2026-10-26

### Fixed
- **Vibes on Emby when AudioMuse reads another server.** When only part of AudioMuse's music is on your Emby server,
  vibes and mood playlists are now picked from the songs that are, instead of coming out with 1 or 2 songs. If the
  library on your phone isn't synced yet, Siansa looks the songs up on your server by title and artist. A vibe that
  still finds nothing says so, instead of being saved with no songs.
- **Repeat looked on by default.** A repeat set on an earlier list stayed on in the player for hours after, so
  the button showed orange for a new list. Repeat now turns off whenever a new list starts playing, like shuffle.
- Artists found through a song's credits (featured on a song, or on a compilation) showed no picture in search.

### Added
- **Audiobooks and podcasts from your Audiobookshelf server.** Set it up in Settings › Connections. Home gets
  **Your books** and **Your podcasts** carousels, Your Library gets **Books** and **Podcasts** chips, each book
  has its own page with chapters, and books play in their own player with skip back and forward, chapters, speed
  and a sleep timer to the end of the chapter. Where you are is kept on the server, so Audiobookshelf's own apps
  show the same place.

### Changed
- Crashes in the audio decoder can now be read on Google Play: the app bundle carries the decoder's symbol names.
  Nothing changes in the app itself.
- **Your Library's chips are back at full size.** When more chips are hidden to the right, the last one in view
  blends out and a **›** button sits after it to scroll on, instead of the chips shrinking to fit.

## 0.34.1beta — 2026-10-26

### Changed
- Each release now also comes as an Android App Bundle (`.aab`) next to the APK, ready for Google Play. The APK for
  F-Droid and direct installs stays the same.

## 0.34.0beta — 2026-10-26

### Fixed
- **Siansa said "Navidrome" to people using Emby.** Playlists, settings, sync, downloads and favourites now say
  "your server", and playlists without an owner show "Emby" or "Navidrome" as fits. On Emby, Settings › Playback and
  Downloads now say that Emby sends the original file, so the quality choices don't change anything there.
- **Vibes and AudioMuse mixes came out empty when AudioMuse knows your music from another server** (for example
  AudioMuse reads Navidrome and Siansa plays from Emby). Siansa now finds those songs in your library by title and
  artist, for vibes, mood playlists, the sound and discover mixes, song radio, Keep playing and Similar songs.
- **The AudioMuse test in Settings now says whether its songs are in your library**, instead of only that it
  answers. If they aren't, it says what to change.
- **Search finds every artist, not only album artists.** Artists who are only featured on a song, or only on
  a compilation, now show up, and the closest name comes first.
- **An artist's releases are their own albums.** Albums they only take part in (a feature, a mixtape by someone
  else) are under "Appears on" instead.
- The next sync after updating fetches every song again, once, to learn who is credited on each.
- **Siansa couldn't be updated on Google Play.** Play only takes updates for apps made for Android 16 now; Siansa is
  made for Android 16 again. Nothing changes on F-Droid.
- **Time machine: the chosen month was out of sight.** The month row now scrolls so the month you're looking at is
  in view, for example October when the page opens in October.
- Albums by "Various Artists" count as compilations, also on Emby.
- Songs whose artists Navidrome joins with " • " are split into each artist again (for "Appears on" and the
  artists under a track).

### Added
- **Every song in Your Library.** Albums, artists and the new **Songs** now sit under one **Music** chip; tap a song
  to play it and the songs after it.
- **Song details shows what the song's tags say:** all its artists, album artist, composer, lyricist and other
  roles, genres, moods, release date, BPM, ISRC, explicit or clean, comment and MusicBrainz ID, as far as your
  server passes them on.
- **An album's page shows its version** (for example "Deluxe edition"), the year it first came out when this is
  a later release, all its genres and its label.
- **Home carousels for certain days.** In **Home carousel**, pick the days a carousel is for, or **All week**.
  Two carousels can't have the same hours on the same day: Siansa says which one already has them. Vibes get the
  **All week** chip too.

### Changed
- **Today's mix stays until you tap refresh.** It's made once a day; when the hour's vibe moves on, the mix you
  have stays, and refresh makes one for the vibe of the hour. Your mixes carousel still changes with the part of
  the day, as set in Vibes.
- **On repeat is the songs you played most in the last 30 days.** It asks your server how often you played the songs
  of the albums you played lately (also played on other apps), adds what you played on this phone and on Koito,
  ListenBrainz or Last.fm, and checks again whenever you open it, like a playlist. Until there's enough to go on,
  its tile isn't shown on Home.
- **Your Library's chips fit better.** They shrink to fit your screen, so all of them show on smaller phones too.
  They come in the order you last opened them, and if some still don't fit, the row fades with a **›** that
  scrolls on.
- **Each chip keeps its own sort** until you change it, also after a restart: sorting Music by name no longer sorts
  your playlists the same way.

## 0.33.1beta — 2026-10-26

### Fixed
- **Search › show all albums or artists started under the title bar**, cutting the top row off. The page now starts
  below it, and uses the same grid of three (or list, and the quick-scroll handle) as other album and artist pages.
- **Hold to stop while casting kept the TV or speaker connected**, so the next song you picked played there again.
  Pressing and holding play/pause now also ends casting: the queue is cleared and the next song plays on the phone.

### Changed
- **Daily mixes stay as they are when you change the source filter.** Showing or hiding a user's music no longer
  makes every mix again. They're made once a day, when a new vibe starts, or when you tap refresh.
- **Without AudioMuse-AI, the AI mixes are hidden.** A user without AudioMuse-AI set up no longer sees today's mix,
  the carousel, the vibe tiles, Your sound or Discover for you. On repeat, made from your plays, stays.

## 0.33.0beta — 2026-10-25

### Fixed
- **Songs on a DLNA TV or speaker had a gap between them.** Siansa now hands the device the next song ahead, so
  devices that support it play one after the other without a gap. Devices that don't still get the next song
  sooner than before.
- **Naming a server renamed it for every user on it.** The name you give a server in **Settings › Users** is now
  each user's own, so two logins on one server can be told apart ("max on Home", "kids on Kids"). Names you
  already gave stay as they are.

### Added
- **Original files on DLNA TVs and speakers.** Tap the cog beside one in **Play on** and turn on **Send the
  original file**: it then gets your music as it is (for example FLAC) instead of MP3 320, whenever it says it
  plays that format. Better sound, and no tiny gap between songs.
- **Scroll quickly through long lists.** Liked songs, playlists, albums, favourite albums and artists, and Home's
  full lists now have the handle on the right that Your Library has. Drag it to move through the whole list; a
  label shows where you are (a letter, a month, or the place in the list).
- **Daily mixes without AudioMuse.** On a server without AudioMuse-AI, or when only music it hasn't analysed is
  shown (a kids' library on its own), today's mix and your carousel mixes are now picked from the music shown, by
  the genres that fit the vibe. Showing or hiding music in the source filter makes them again.

### Today
Everything else that changed today, in 0.30.1beta to 0.32.0beta:
- **Emby:** songs you play reach Emby's play history and show under its now playing; your Emby login key no
  longer shows in the log; AudioMuse song radio, sound search, similar albums and vibes work with Emby.
- **Several users and servers:** showing users together is for users on the same server; a user on your server
  at another address is recognised by its songs; artists and albums open on the song's own server;
  "Server name in AudioMuse" is kept per user.
- **Name your servers** in Settings › Users, to tell logins apart.
- **The source filter is followed everywhere:** Roll the dice, recently played, Home's shortcuts, playlists and
  today's mixes only show what you chose to show.
- **Save a mix as a playlist**, from any mix, radio or card, or from the queue.
- **Playlists check your server for changes when you open them**, with a turning cog.
- **Search shows the top 5 of each kind**; tap a section to see every match.
- **Add to playlist** only offers playlists that can hold the songs.
- **Fixes:** Siansa closing at start in 0.30.4beta; .m4a Dolby songs showing a wrong length; Navidrome's log filling
  with "missing parameter" warnings; Play and Shuffle for "Sounds like" at the top; even rows at first setup.

## 0.32.0beta — 2026-10-25

### Added
- **Name your servers.** In Settings › Users, tap the pencil next to a user to give their server a name, like
  "Home Navidrome". Every user on that server then shows it instead of the address, here and in the source filter.

## 0.31.0beta — 2026-10-25

### Fixed
- **A user on your own server, reached at another address, couldn't be shown with you.** For example kids on music.example.com and you on 192.168.1.10, both on one Navidrome. Siansa now recognises your server by its songs, whatever address it's reached by. Emby users in the list also say "(Emby)", so two users on one address are told apart.

### Added
- **Save a mix as a playlist.** Today's mix, your carousel mixes, Made for you, radios and your cards have a new button next to "Add all to queue" (a filled circle with a plus) that saves the songs as a playlist on your server or on this phone, named like the mix to start with. The queue can be saved the same way, from the button next to "Up next".

## 0.30.6beta — 2026-10-25

### Fixed
- **Play and Shuffle for "Sounds like" sat halfway down the search results.** They're now at the top of the "Sounds like" page.
- **Today's mix kept the music you'd hidden.** When you change what the source filter shows (your own music, other users, libraries), today's mixes are made again for what's shown, or hidden if AudioMuse isn't set up.
- **Navidrome's log filled with "missing parameter: 'u'" warnings.** Siansa checks whether your server can be reached without signing in, and that check asked Navidrome's API. It now just knocks on the server's front page, which Navidrome answers without a warning.

### Changed
- **Playlists check for changes when you open them.** A playlist on your server opens straight away from the copy on your phone, while a turning cog at the top shows Siansa asking your server whether it changed. If it did (for example a playlist your server rewrites every hour), the new songs show at once, with no library sync needed.
- **Search shows the top 5 of each kind.** Artists, albums, songs and "Sounds like" each show their best five; tap a section's title to see every match on a page of its own, with Play and Shuffle at the top for songs.
- **Playlists follow the source filter.** With your own music hidden, your own playlists are hidden too, so you only see the playlists of the users shown.
- **Add to playlist** only lets you pick playlists that can hold the songs: a server playlist takes its own user's songs, a playlist on this phone takes anyone's. The others are greyed out ("Only for songs from kids"), and shown users' own playlists are listed for their songs.
- **Showing users together is now for users on the same server.** You can still add users on other servers (Navidrome or Emby) and switch between them in Settings › Users. In the source filter, only users on the same server as you can be shown next to your music, so everything plays, opens and finds similar music from one server. The AudioMuse server name is set once per user again.

## 0.30.5beta — 2026-10-25

### Fixed
- **Siansa closed straight away after updating to 0.30.4beta.** The change that made Dolby (E-AC3) songs gapless stopped the player from starting, so it's taken out again until it's fixed properly.

## 0.30.4beta — 2026-10-25

### Fixed
- **Similar albums, vibes and text search with Emby.** Similar albums now names the song's server to AudioMuse, and searches that aren't about one song (vibes, Flow, sound search, similar artists) use the server whose music you show. Before, with your own music hidden in the source filter, they asked your own server and every song they found was left out.
- **Home showed recently played albums from music you'd hidden**, and opening one said "Not found". Recently played, the shortcuts at the top and recent artists now only show what the source filter shows.
- **Songs played didn't reach Emby's play history.** Emby didn't accept the date Siansa sent with each play; it now gets the date in the form it reads, so your plays and play counts show up in Emby.
- **Dolby (E-AC3) songs weren't gapless.** Their files claim to be far longer than they are, so the next song wasn't loaded ahead and started after a short silence. Siansa now loads ahead by size for these songs, so the next one follows straight on.
- **The music sources at first setup were uneven**: rows with a longer description were taller. Every source now has the same height.

## 0.30.3beta — 2026-10-25

### Fixed
- **AudioMuse with more than one music server.** Song radio, mood radio, paths and song details now name the right server to AudioMuse; before, only searches did. Each server's songs use their own "Server name in AudioMuse", also when you show another user's music next to yours, and Settings › Connections lets you fill in the names of your other users' servers on the same page.
- **Opening an artist from another user's song showed "Not found".** With two users' music shown (for example Emby and Navidrome), a song's artist and album now open on the song's own server.

## 0.30.2beta — 2026-10-25

### Fixed
- **Emby didn't show what Siansa was playing.** Siansa now tells Emby which file and play it is, so the song appears under Emby's now playing and your progress is kept.
- **The log showed your Emby login key.** It's now hidden like every other password and key. If you've shared a log made with an Emby user, remove Siansa under Devices in Emby's dashboard and sign in again, so the old key stops working.

## 0.30.1beta — 2026-10-25

### Fixed
- **Roll the dice played songs from libraries you'd hidden.** It now only picks from what's shown: the libraries you chose, the users shown, and only downloads when that's on. Popular songs, radios and other lists from the server also leave out your own music while it's hidden.
- **Some .m4a songs with Dolby (E-AC3) sound showed a length of over 30 minutes**, so the seek bar didn't work. When a file's own length is far from what your server says, the server's length is used.
- **AudioMuse with Emby: song radio and sound search didn't work.** "Server name in AudioMuse" (Settings › Connections) is kept for each user again, so your Emby user can name its Emby server in AudioMuse while your Navidrome user keeps its own. Sound search, mood radios and alchemy also find their songs in your library by title and artist when AudioMuse knows them under another server, and a song AudioMuse can't find now says how to fix it.

## 0.30.0beta — 2026-10-24

### Fixed
- **Report a problem: the keyboard covered the text boxes.** The page now ends above the keyboard, so what you type stays in view.
- **Artist radios on Home showed a grey picture** although the artist's page had a photo. Home now gets each artist's photo the way their page does.
- **A stopped song came back in the mini player.** An artist radio's later songs (they arrive a few seconds after it starts) were added even after you stopped the music or started something else. Now they're only added while that radio is still playing.
- **Rearranging the nav bar (and the mini player's buttons) got stuck half-way.** A row now follows your finger past the others, and the rows it passes glide out of the way.

### Added
- **Show an Emby user's music next to your Navidrome music (and the other way round).** In Home's filter, switch on another user of any server type: their albums, artists and playlists join yours, and their songs play, download, show covers and lyrics, and count as played on their own server, with their login.
- **Hide the user in use's music without a restart.** In Home's filter, tap the eye on the user in use while another user's music shows: their music hides at once, and their settings, history and playlists stay as they are.

### Changed
- **Beta reports you sent are closed when they're fixed**, with a note saying which version has the fix and how to update.
- **No lyrics card under now playing when the lyrics button is on.** The button already opens the lyrics, so the cards below start with the artist. With the button off (Settings › Style › Now Playing), the lyrics card stays.
- **What's new shows what's new in your version.** Every version's changes are a tap away, in the beta testers repository on GitHub.

## 0.29.1beta — 2026-10-24

### Fixed
- **An Emby user shown next to another said Navidrome couldn't sync.** Their card now says plainly that Emby users can't be shown next to another user yet, and how to play their music instead.

### Changed
- **No "2 users" chip on Home.** When several users' music is shown, the filter button stays lit instead.
- **The Siansa Analysis plugin has its own public catalog.** Add its address in AudioMuse-AI (see the beta testers page) and install Siansa Analysis.
- **Report a problem says where your log goes.** It is kept privately on the Siansa server and in a private GitHub repository, and may be read with AI tools to find the problem.

## 0.29.0beta — 2026-10-24

### Fixed
- **Mixes on Emby said "Navidrome didn't recognise AudioMuse's tracks".** When AudioMuse knows your music under another server's ids, Siansa now finds each track in your library by its title and artist.

### Added
- **Report a problem from the app.** Under Settings › Report a problem, say what happened and what you expected, and send it with your log to the Siansa beta team. The log stays private; a short note with your words is posted in the beta testers repo.

### Changed
- **Logs always record everything.** The "Detail for" choices are gone: when "Keep a log" is on, all detail is kept. Passwords, tokens and API keys are still removed.
- **AudioMuse and OpenRouter are set up once.** They're the same for every account and every music server, so changing server or account no longer asks for them again.

## 0.28.1beta — 2026-10-24

### Added
- **Welcome to the Siansa beta.** A music player for your own Navidrome or Emby server, with mixes made for the moment. The smart parts (mixes made for you, Vibes, sounds-like, Song path, Your sound) come from AudioMuse-AI with the **Siansa Analysis** plugin installed.
