# Changelog

Every version of Siansa, newest first. Each section is also shown as the notes on its GitHub release.
Changes add a note in `changelog.d/` instead of editing this file; the release adds the section here.

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
