# Changelog

Every version of Siansa, newest first. Each section is also shown as the notes on its GitHub release.
Changes add a note in `changelog.d/` instead of editing this file; the release adds the section here.

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
