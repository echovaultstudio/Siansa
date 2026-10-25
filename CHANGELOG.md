# Changelog

Every version of Siansa, newest first. Each section is also shown as the notes on its GitHub release.
Changes add a note in `changelog.d/` instead of editing this file; the release adds the section here.

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
