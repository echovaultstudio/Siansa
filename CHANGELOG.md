# Changelog

Every version of Musix, newest first. Each section is also shown as the notes on its GitHub release.
Changes add a note in `changelog.d/` instead of editing this file; the release adds the section here.

## 0.6.0beta — 2026-10-17

### Added
- **Source filter on Home, like Symfonium's.** The new filter button at the top right of Home (also a Sources
  tab next to Arrange) decides whose music shows, everywhere in the app:
  - **Only downloaded music**: everything that isn't on your phone is hidden in Home, Your Library and Search,
    and only downloaded songs play. Handy on a flight. The AI tools say they're not available meanwhile. When
    you're offline, Home offers to turn it on.
  - **Users**: every user on the phone has a card with an eye. Show several at once and their music plays
    together; each song streams, downloads and scrobbles with its own user's login, even from another server.
    Hiding a user hides all their songs, albums, artists and playlists. Hiding the user in use switches to
    another shown user (Musix restarts with their history and settings).
  - **Libraries**: tick each user's libraries on or off.
  - Small chips under the greeting show what's filtered; tap one to change it.

## 0.5.0beta — 2026-10-17

### Fixed
- **Pause and Resume in Downloads looked squashed.** They were squeezed in next to Add music and Shuffle all. They're now a
  small button next to the number of songs and their size, right above the download progress line.
- **AI tools no longer make empty playlists when AudioMuse can't be reached.** Offline, or while AudioMuse doesn't
  answer, the mood box, sound search, mixes, mood radios and song radio say "AI backend is currently not available :("
  and why (for example "You're offline."), instead of letting you type a prompt that ends in a network error.
- **Songs that aren't downloaded no longer freeze the play button offline.** When the phone is offline (or the
  server doesn't answer), songs that aren't on the phone are dimmed. Tapping one says it isn't downloaded, and
  playing a list plays only its downloaded songs.

### Added
- **Lyrics in every common format, including word by word.** Musix now reads LRC (also with several times on one
  line and an offset), enhanced LRC with word or syllable times, TTML (Apple Music style), SRT and WebVTT, and plain
  text: from the song's tags or a .lrc / .txt file next to it, as Navidrome provides them. When there are several,
  the most detailed one is used (word by word, then line by line, then plain), in your phone's language first.
  Lyrics timed word by word fill in as they're sung in the full-screen lyrics.

### Changed
- **"Artists you might like" moved from Home to Your Library → Your listening**, as **Other artists you might like**:
  a page with a map of how each suggested artist connects to the artists you play most (thicker, brighter lines for
  the ones you play more), and a card per artist saying why: who they're like, how often you've played that artist
  lately, how many people listen to them, and how many albums there are to discover. Tap one for more and a Deezer link.
- **ReplayGain is now on the Equaliser page** (Settings → Playback → Equaliser and ReplayGain), and there's one
  pre-amp instead of two: the equaliser's. If you had set the old ReplayGain pre-amp, its level moves to the
  equaliser's pre-amp.

## 0.4.2beta — 2026-10-17

### Changed
- **Swipe to queue vibrates sooner and stronger.** You feel a clear click the moment the amber "Add to queue" strip
  shows, and a firmer one when the song is added. It follows the phone's touch vibration setting.

## 0.4.1beta — 2026-10-17

### Changed
- **Swipe to queue vibrates.** Swiping a song from right to left gives a light tick as it passes the point where
  letting go adds it, and a short buzz when it's added to the queue.
- **No format label under the seek bar.** Now playing no longer shows FLAC, OPUS 160 and so on between the times;
  the format is still under About this song, further down.

## 0.4.0beta — 2026-10-17

### Fixed
- **Music stopped when you switched from the TV back to the phone.** It now comes back to the phone at the same song and position, paused.
- **The equaliser's pre-amp crackled on loud songs.** Peaks that would go past full volume are now turned
  down smoothly instead of being cut off.
- **The keyboard covered the "How are you feeling?" box on Home.** The box now moves up above the keyboard when you tap it. The same goes for the text boxes in Settings, Scrobbling and Vibes.

### Added
- **Add to playlist.** From the ⋮ menu in now playing, add the song to one of your own playlists on Navidrome.
- **Swipe a song to the left to queue it.** Drag any song in a list from right to left and let go: it's added to
  the queue, with an amber "Add to queue" strip behind it while you drag and a little buzz when it's added.
- **Download music for listening offline.** Tap the download button on an album, playlist or Liked songs, or pick
  albums, playlists and artists in the new Downloads menu (Settings → Downloads, or the arrow at the top of
  Your Library). Downloaded songs play from the phone with no connection, show a small arrow next to them, and
  stay until you remove them from the same menu. Downloads wait for Wi-Fi unless you change that, and you can
  set a storage limit. Downloads can be the original file or a smaller Opus version (Downloads → Download quality). Playlists, artists and liked songs pick up songs added later.
- **Download everything at once.** In Downloads → Add music, "Download all" downloads every album, playlist or
  artist in the list you're looking at (or the ones you searched for), and "Remove all" takes them off again.
- **Downloaded songs**, under Settings → Downloads (and Settings → Storage): every song on the phone with its size
  and quality. Tick songs, or Select all, and remove them together. Songs you remove aren't downloaded again
  when a playlist gets new songs; download the album or playlist again to bring them back.
- **Sleep timer.** In the player's ⋮ menu, choose Sleep timer to stop the music after 15, 30, 45 or 60 minutes, at the end of this song, or after a time you choose. While it runs, a moon with the time left shows at the top of the player; tap it to change the timer or turn it off.
- When the time is up, the song playing finishes and fades out over its last seconds before the music pauses, instead of stopping mid-song. Your volume and ReplayGain are back to normal when you press play again.
- **Wind down** (needs AudioMuse): near the end of the timer, the songs coming up are swapped for calm, slow ones, so the music gets quieter as you fall asleep.
- The sleep timer also works while you're playing on a TV or speaker: the music there pauses at the end of the song.
- **Equaliser.** Settings › Playback › Equaliser has ten bands, presets (Bass boost, Vocal, Treble boost,
  Rock and more), your own Custom setting and a pre-amp. It works the same on every phone and is remembered
  between restarts. It isn't used while casting.

### Changed
- **New now playing, like Spotify.**
  - Where the music plays (this phone, headphones, a TV or speaker) sits at the bottom left, and the queue
    button at the bottom right: tap it, or swipe up from it, to see the queue.
  - Scroll down for cards: a lyrics preview in the cover's colours, about the artist (photo, how much of
    them is in your library, their story) and about the song (format, sample rate, bit rate, plays).
  - Once you scroll, a small bar at the top keeps the song, the heart and play/pause at hand.
- **⋮ menu in now playing**, top right: like or unlike, add to a playlist, add to the queue, go to the
  album or artists, start song radio, song path, lyrics, song details, sleep timer.
- **Lyrics open full screen** on the cover's colour: the line playing now is white, the ones to come
  darker, and the seek bar and controls stay at the bottom. Tap a line to jump there.
- Song radio moved from the player into the ⋮ menu.
- **Add to queue works like Spotify.** A queued song now plays right after the current one, and after any songs you
  queued before it (in the order you added them), instead of at the very end of the album or playlist.

## 0.3.0beta — 2026-10-17

### Fixed
- **Artists with "&" or "and" in their name were split in two**, so Chase & Status could show as just "Status"
  or as two artists. Names that are one artist in your library now stay whole under a song and when choosing
  an artist.
- **Tapping the artist in now playing didn't offer every artist** on songs whose shown name is only the main
  artist (for example "Fred again.." on a song with Skrillex). Musix now asks Navidrome for all of the song's
  artists, so you can choose between them.
- **Songs from libraries you unchecked still played.** Popular songs on artist pages, song and artist radios,
  Liked songs, Rediscover and most played came straight from Navidrome, which includes every library. When
  some libraries are unchecked in Settings → Library sync, songs and albums from them are now left out
  everywhere, so for example a Dolby Atmos copy no longer plays after a song from your main library.
- **"Copy errors" said there was nothing to copy** when the log only had warnings. It's now "Copy problems"
  and copies the errors and the warnings, the same ones listed under Problems.

### Added
- **Cast to Chromecast and Google TV.** Tap the device button at the bottom left of the player (or the cast button on the song bar) and pick your TV or speaker: the queue, song and spot move there, and you keep controlling it from the phone, the notification or the lock screen. Pick "This phone" to bring the music back.
- **Play on TVs and speakers without Chromecast (DLNA).** Most smart TVs, Sonos, AV receivers and many
  network speakers show up in the same list, marked "DLNA". Play, pause, skip, seek and the phone's volume
  buttons all work, and songs follow on one after another. No Google Play services needed.
- **See where the music plays.** Like Spotify, the bottom left of the player shows the TV, speaker or Bluetooth headphones playing, for example "Sony Headphones".

### Changed
- **Calmer format label in now playing.** The label under the seek bar (FLAC, OPUS 160…) is now a quiet grey
  with a faint outline instead of bright amber or violet, so it fits in with the times next to it.
- **Now playing is spaced more evenly.** The seek bar sits a little higher, closer to the song title, and "Up
  next" has room above the phone's gesture bar instead of touching the bottom edge.

## 0.2.0beta — 2026-10-17

### Fixed
- **Release type chips on artist pages were squeezed against Popular.** The chips now have the same space
  above them as below them, so they sit evenly between the popular songs and the albums.
- **Changing the quality in Settings didn't change the song that was playing.** When you change the Wi-Fi or
  mobile data quality in Settings → Playback, the song that's playing now loads again at the new quality and
  carries on from the same spot, instead of staying at the old quality (or going silent).

### Added
- **Copy errors.** On Settings → Logs, "Copy errors" puts every error in the log, with its details, on the
  clipboard, ready to paste into a message or an issue. The app version and phone model are at the top.
- **Several Navidrome users.** Add more logins (on the same server or different ones) in Settings → Users and switch between them with one tap. Each user has their own library, history, mixes and settings, including AudioMuse, which can be left empty for a user without it. Musix restarts when you switch.

### Changed
- **The format under the seek bar is just a label now.** Tapping it no longer opens a quality menu (switching
  there didn't work); set the quality in Settings → Playback. The label is smaller, sits evenly between the
  two times and no longer makes the row taller.

## 0.1.0beta — 2026-10-17

### Fixed
- **Popular songs on an artist's page were slow to appear.** They're now kept on the phone and show straight
  away when you open an artist again. They're updated in the background every few days, and the first time a
  placeholder keeps their space so the page doesn't jump.

### Added
- **Song details.** "Show details" in a song's menu (the three dots) shows its format (FLAC, MP3, Opus…), bit rate, sample rate, file size, how often it was played, how often you skipped it on this phone, and the file's path on the server.
- **What's new in Settings.** A new page lists what changed in each version of Musix, newest first, with the version you have marked. Tap your version at the top (or any version's number) to open its release on GitHub.
- **Release type chips on artist pages.** Tap Albums, Singles, DJ-mixes and so on to see only that kind of
  release. Tap it again, or ✕, to see everything. Only the types the artist has are shown.
- **Hide parts of Home.** In Arrange Home, tap the eye next to a row to hide it from Home, and tap it again to bring
  it back. Reset shows everything again.
- **Song format in now playing.** Under the seek bar you now see what's playing: FLAC, ALAC, MP3, Opus and so on,
  in amber for the original file and in violet with its bitrate (for example "OPUS 160") when Navidrome converts
  the song. Tap it to pick another quality: the song carries on from where it was, and the next songs on Wi-Fi
  (or mobile data) use the same choice.

### Changed
- **Your playlists now show as a list.** Opening "Your playlists" from Home shows each playlist as a row with a
  small cover, its name, who made it and how many songs it has, like Spotify, instead of big album tiles.
  Playlists in Your Library also show how many songs they have.
- **Version numbers now show how big each release is.** A release with only fixes and small changes raises the last number (0.1.3 → 0.1.4); one with new features raises the middle number (0.1.3 → 0.2.0). 1.0 is kept for the first full release. The next release with new features will be 0.1.0beta.
- **Now playing moves as one card.** When you swipe it down, the blurred cover no longer shows through above it:
  the whole screen slides down as a single card with rounded top corners.

## 0.0.29beta — 2026-10-17

### Added
- **Backup.** Settings → Backup saves your settings, vibes, mixes, play history and Home and library layout to a
  file, and restores them, for example on a new phone. Automatic backup saves one a day to a folder you choose
  (also a NAS folder through an SMB, Nextcloud or Syncthing app) and keeps the last 7. Passwords, tokens and API
  keys are never included, so after restoring on a new phone you enter them again.

## 0.0.28beta — 2026-10-17

### Fixed
- **Mixes, mood playlists, sound search and radios played songs from libraries you had unchecked.** When you
  sync only some Navidrome libraries (Settings → Library sync), these now only use songs from those libraries.
- **Your listening showed songs you don't have.** Top songs and top albums now only list music that's in your library, so plays from other apps and services (counted by Koito) no longer appear there. The list still shows up to 10 by filling in from further down.
- **The Liked songs tile at the top of Home was blank.** It now shows the heart, like in Your library.

### Added
- **Song path.** Choose "Song path…" in any song's menu, pick a second song and a length (10, 20 or 30 songs), and Musix builds a queue that moves smoothly from the sound of one song to the other. Swap to make the chosen song the end instead of the start. Play it straight away or save it as a playlist on Navidrome. Needs AudioMuse.

### Changed
- **Disc names are easier to spot.** On albums with several discs, each disc's name now sits on a band in the album's colour (darker in dark mode, a soft tint in light mode), so it stands out in long track lists.

## 0.0.27beta — 2026-10-17

### Fixed
- **App icon looked off centre.** The sound bars now sit in the middle of the icon instead of leaning right and low.

### Added
- **Artists you might like** on Home: artists you don't have yet that are similar to the ones you play most, each with a photo. Tap one to see how many albums and fans it has, which of your artists it's like, and open it in Deezer to listen. The list is made once a day. Similar artists come from your Navidrome server (through Last.fm) and from Deezer; to find them and their photos, Musix sends Deezer only artist names. Like every Home row, you can move it under Arrange Home.

### Changed
- **App icon has more depth.** The bars have a soft shine and a gentle glow behind them, so the icon looks less flat. The themed (one-colour) icon stays plain.

## 0.0.26beta — 2026-10-17

### Fixed
- **The bar on now playing jumped around.** After you let go of it, it flew back to the old spot for a moment before moving to where you dropped it, and on a new song it could slide off in either direction. It now stays where you let go, starts each song from that song's own time and length, and moves smoothly instead of in half-second steps.
- **Grey square before the cover in now playing**, when opening it right after playing a song from a list. Two things changed:
  - the cover of the song that's playing is now loaded at full size as soon as it starts, so it's ready before you open now playing;
  - any cover that isn't ready at the size it's shown at now shows the same cover at a size that is (the one from the list or the mini player) until the sharper one is ready, instead of an empty grey square. This also applies to the blurred background in now playing, album pages and everywhere else covers are shown.

### Added
- **A to Z bar in Your Library.** When the library is sorted Alphabetical or by Creator and the list is long, a bar of letters runs down the right edge. Tap a letter, or drag along the bar, to jump there; a big letter shows next to your finger while you drag. Letters with nothing in them are dimmed, and names starting with a number or symbol are under #.
- **Arrange Home**: put Home's sections in the order you like. Tap the sliders button next to the greeting, then hold a row (or grab its handle) and drag it. The order is kept, and Reset puts it back.
- **This time last year** in Your listening: the same month a year ago, with your top artists, songs and albums from then. It has its own tile in Your Library and a button on the Your listening page.
- **Albums with several discs are split by disc**: each disc gets its own heading above its songs. Discs with a name (the "disc title" tag, as Navidrome shows it, e.g. for box sets) show that name; others show "Disc 1", "Disc 2"… Track numbers no longer carry the disc in front ("2.5" is now "5" under "Disc 2").
- **Appears on** on artist pages: a row of albums by other artists that this artist is on, such as features ("feat."), compilations and splits. Tap the "Appears on" heading to see all of them on one page. It uses your synced library, so it shows after a sync.

### Changed
- Alphabetical and Creator sorting now put names with accents with their letter (Ångström with the A's), and names starting with a number or symbol first.
- **Lyrics blend into now playing** instead of sitting in a dark box with visible edges. The box is gone; the lyrics fade out softly at the top and bottom into the blurred cover, and a light shadow keeps them readable on bright covers.
- **Now playing sits a little lower**: "Playing from album" was pressed against the top of the screen. It now has a gap above it that suits the phone's height (more on tall phones, less on small ones), so the whole screen looks more even.
- **Album pages no longer repeat the album's artist and name under every song.** A song only shows a second line when other artists are on it (a "feat." or a duet), and then only those artists. Musix reads this from the song's artist tags as Navidrome reports them.
- **Vibes moved from the bottom bar into Settings** (#3). The bottom bar now has four tabs: Home, Search, Your Library and Settings. Vibes is a row in Settings that shows how many vibes you have, and its page now has a back arrow.

## 0.0.25beta — 2026-10-16

### Added
- **See everything in a Home row.** Rows with more than four items (Rediscover, Radios, Your playlists, Recently played, Artist radios, Recently added, Your genres, Random picks) now have an arrow next to their title. Tap the title to open a page with all of them in a grid, including ones Home has no room for: every radio, every playlist and genre, and longer lists of recently played and recently added albums.

## 0.0.18beta — 2026-10-16

### Fixed
- **Placeholders flashing in lists** even for covers already on the phone:
  - covers already in memory are now drawn in the same frame, with no placeholder (the image library only looked at its memory after laying out the row, so even cached covers showed a placeholder for a frame);
  - list rows decode covers at the size they're shown (about 192 px instead of 300), a quarter of the memory each, so several hundred stay in memory and scrolling back is instant;
  - Your Library, album, playlist and mix pages load the next two dozen covers ahead of where you're scrolling, so they're ready before they come into view;
  - covers start loading straight away instead of waiting for the row to be laid out.

## 0.0.17beta — 2026-10-16

### Fixed
- **Albums with an animated GIF cover showed no cover** in lists, the mini player and now playing (found by the app tour, explained by the new image log). Navidrome converts GIFs to animated WebP, and Android's still-image decoder rejects those files. Wherever a still is shown, Musix now draws the first frame through Android's animation decoder instead. This also fixes such covers already in the image cache, without downloading them again.
- Making the still of a kept animated cover read the whole file into memory (they can be over 100 MB); it's now decoded straight from the file.

## 0.0.16beta — 2026-10-16

### Added
- **Quality checks on every change**, in one pipeline: the app must compile, pass Android Lint (likely crashes, wrong API use, security issues), pass new unit tests of its core logic, pass a secret scan of the whole history, and pass the emulator tests against a real Navidrome, including a tour of every page that plays songs and checks they start within 5 seconds. **A release is only published when all checks pass.** Dependabot now proposes library updates weekly and warns about known vulnerabilities.
- The log now starts each session with the app version, phone and the settings that change behaviour (never addresses, logins or keys), the library size, and **why the app last closed**: crashed, froze (with what it was doing), killed by Android for memory, swiped away… (Android 11 and newer). Crashes that happened too fast for the app to log itself now leave a trace.
- Errors in background work (syncing, mixes, covers) are logged with where they happened instead of being able to close the app.
- Settings → Logs → **Problems** shows only warnings and errors, with their counts, so issues stand out.

### Fixed
- Found by the new app tour: opening an artist from a song's menu, or opening a freshly made vibe mix, could navigate from a background thread, which can close the app. Both now always navigate on the main thread.
- Two issues Android Lint reports as errors (screen state that might never be set). Lint errors now fail the build.
- The start check added in 0.0.14 judged a song "stuck" by how much the player had buffered, which can stay at zero for a while on a big FLAC even as data arrives. On a slow connection it kept restarting the download, so the song never started. It now only restarts when no data at all has arrived for 2.5 seconds, and at most once per song.
- Artist pages without a photo: the name was white on a pale header in light mode. A soft dark band behind the name keeps it readable on any photo.
- Your listening pages in light mode: section titles were dark on the page's dark background. The page now always uses dark colours.
- Your Library: the Your listening tiles wrapped long month names ("Septemb / er"); they show "Sep", "Oct" or the year.

## 0.0.15beta — 2026-10-16

### Fixed
- Covers already on the phone faded in over a moment when scrolling, as if they were loading. Only covers that just came over the network fade in now; ones from the phone's own cache appear straight away.
- More decoded covers are kept in memory (30% of the app's memory instead of 20%), so scrolling back through a list doesn't read them again.
- Now playing: swiping to the next or previous song showed a small cover first and then swapped it for the big one (or the animation). The covers either side of the current song, and their animations, are now loaded ahead of time, and whether an album's cover is animated is remembered from the start, so the right cover is there when it slides in.
- The **Your Library** tab label was cut off on narrower phones; tab labels are a little smaller so it fits.

## 0.0.14beta — 2026-10-16

### Fixed
- **A chosen song sometimes loaded but didn't start** until play was pressed (the log showed songs "heard 0s" while the player said it was ready). Musix now checks 2.5 seconds after a song is chosen that it's actually playing, and fixes it if not: if Android held the sound back for another app it asks for the audio again, and if the song is stuck waiting with no data arriving it restarts the download from where it is. It keeps checking for a few rounds and logs what it found and did.
- **Missing covers** on some pages (artist Popular songs, the mini player, now playing):
  - a song without its own picture now falls back to its album's cover;
  - a song cover that is really the album's big animated cover is recognised by its first bytes and uses the copy already on the phone, instead of downloading 20–160 MB again (the server doesn't send a size to compare);
  - if an animated cover's still is missing, it's made on the spot instead of sending the whole original to the list.
- **Your listening**: songs with a common title (like "Ghost") weren't matched to your library, so they had no cover and couldn't be played. Title and artist are now searched together.
- Downloading all covers no longer stops on a single brief Wi-Fi or name lookup hiccup; it looks again after 5 seconds.

### Added
- The log records why playback starts and stops: play and pause with their reason (pressed, another app took the audio, headphones disconnected, remote control), when Android holds playback back, when sound actually starts, and how long each chosen song took to become audible.
- The log records every picture that fails to show, and why.
- **App tour test**: on every push, the app is walked through on an emulator against a real Navidrome: every tab, Your Library's filters, sort, grid and search, album and artist pages, liked songs, your listening, search, vibes, now playing and every settings page. Songs are played from the album, artist and search pages and must start within 5 seconds. A screenshot of every page is saved for review.

## 0.0.13beta — 2026-10-16

### Changed
- **Your Library**, rebuilt in the style of Spotify's library in Musix's colours, and now the one place to find everything:
  - **Filters** at the top: Playlists, Mixes, Albums, Artists and Your listening. Choosing one shows ✕ to go back and its own sub-filters: playlists by you or by others; mixes from today, made for you, vibes or earlier; albums you've played, not played yet, and by release type (Albums, EPs, Singles, Live…); artists you've played or not.
  - **Sort** by Recents, Recently added, Alphabetical, Creator or Most played. Recents follows your listening: Musix's own play history plus Navidrome's recently played albums (from other apps too).
  - A **list or grid** view, and **search** inside your library.
  - Each row says what it is: "Playlist · owner", "Album · artist", "EP · artist", "Mix · Made for you · 30 songs", "Artist · 4 albums".
  - **Pins**: Liked songs and this month's listening are pinned at the top; long-press anything to pin or unpin it.
  - The **+** button makes a mix from a sound description or syncs the library; the round initial opens Settings.
- **Your listening** (this month, last month, this year) moved from Home into Your Library.
- The bottom tab is called **Your Library**.

## 0.0.12beta — 2026-10-16

### Fixed
- **Download all covers stopped after a few seconds** ("Job was cancelled" in the log). Android stopped the background worker, and switching the option on also started the daily run at the same moment. The download now runs inside the app, once at a time; the background worker only starts it and waits, so if Android stops the worker the download carries on while Musix is running. The daily run no longer starts straight away, and the log says why a run stopped.
- The Storage page stuttered while covers downloaded: its progress now updates a few times a second instead of after every cover.
- Animated covers over 100 MB (motion covers can be 160 MB) were sent on as they were instead of being kept; the limit is now 400 MB.

### Changed
- Logs: the separate **Detailed logging** switch is gone. With **Keep a log** on, everything is recorded: frames, freezes, buffering, downloads, cover downloads, phone state, errors. The log keeps up to 10 MB (two 5 MB files).
- The log notes cover download progress every 250 covers, each cover that fails and why, whether the server sent a cover's size, and how much space animated covers take on the phone.

## 0.0.11beta — 2026-10-16

### Fixed
- **The app crashing (out of memory) and covers taking half a minute on albums with an animated WebP cover.** Navidrome only shrinks animated GIFs; an animated WebP comes back as the full original (here 1080×1080, several MB) whatever size is asked for. Musix's "300 px" cover was really the whole file, and the album page, now playing, the blurred background, the colour sampling, list tiles and the lock screen each downloaded it and decoded it as an animation of its own, which filled the 256 MB the app may use. Now:
  - an animated cover that Navidrome sends full size is downloaded **once**, kept on the phone, and a still of its first frame is made from it. Every other request for that cover (any size, the song's cover too) is answered from the phone without the network;
  - requests for the same cover that arrive together wait for that one download instead of each starting their own;
  - lists, backgrounds, colours and the lock screen always get a still picture. Like Symfonium, covers only move on the album page and in now playing, and the animation is decoded at screen size (720 px) rather than the file's full size;
  - only the last few animations are kept in memory.
- The lock screen and notification cover now come from Musix's image cache instead of being downloaded again in full on every song change.

### Added
- Settings → Storage → **Download all covers**: fetches every album, artist and playlist cover in the background, at the sizes the app shows, after each library sync and once a day. Only on Wi-Fi, and by default only when the phone is on the same local network as Navidrome (switch on "On any Wi-Fi" if you reach Navidrome by a domain name). Covers already on the phone are skipped, and it stops by itself when you leave home. Progress and a **Download covers now** button are on the same page.
- Image cache size **No limit**. With no limit, images are kept in the app's own storage, so Android doesn't clear them when the phone runs low on space. Switching keeps what's already downloaded.
- Device tests now include an album with a 1080 px animated WebP cover: it must be downloaded exactly once while the album page, now playing, background, colour and a list ask for it at the same time, lists must get a still, and the album page must still animate.

## 0.0.10beta — 2026-10-16

### Fixed
- **Playback stopping and starting, and blank covers, on albums with animated covers.** Found by running a real Navidrome 0.64.2 behind a link limited to 4.3 Mbit/s (what the phone measured):
  - Musix downloaded the album's original animated cover.gif (often over 10 MB), sometimes several copies at once (album page, now playing, the animation check). That took most of the connection: the song got about 1,100 kbit/s while FLAC needs about 1,370, so it kept stopping.
  - For every cover size requested, Navidrome converts an animated GIF with ffmpeg. 300 px worked (about 5 s), but 600 and 1000 px failed and returned no image, which is why big covers were blank, and each new request started the conversion again.
- Now Musix reads only the first bytes of an album's cover to see if it's animated, then shows the original only when it's small, otherwise Navidrome's 300 px animated version, which is converted once and kept. Big sizes are never asked for on animated albums, the result is remembered across app starts, and lists use one cover size so each album is converted only once.
- Animated covers now move on the album page and in now playing.

### Changed
- Songs, covers and API calls each use their own connections, so cover downloads can't hold up a song.
- If a song waits 8 seconds for data that's arriving slower than it needs, Musix switches to Opus straight away instead of after three stops.
- The log shows the Navidrome version, large cover downloads, how long Navidrome took to prepare an animated cover, and what each animated cover was shown as.

### Added
- Automated device tests: on every push, the app runs on an Android emulator against a real Navidrome with a test library (a 13 MB animated cover, a small one, a still one, and 5-minute FLACs) behind a 4.3 Mbit/s link. They check that animated covers are detected and animate on screen, and that a song starts within seconds and plays without stopping while an animated album is open. A control test confirms the old behaviour does stall.

## 0.0.9beta — 2026-10-16

### Fixed
- Songs started slowly and then played a few seconds, stopped, and started again. The log showed the connection to Navidrome delivering only about 250–335 kbit/s while the FLAC files need roughly 900–1,400 kbit/s, and Musix made it worse by downloading the next song at the same time. Now:
  - the next song is only pre-cached once the current one has fully downloaded, so they never share the connection;
  - after running dry mid-song, playback waits for 5 seconds of audio instead of 2, so there are fewer, longer stretches;
  - if the original still stops three times in two minutes, Musix carries on in Opus at a bitrate the connection can manage, from the same spot, and tries the original again after 15 minutes. This can be switched off under Settings → Playback.
- Personal and vibe mixes were cancelled half-way when you left Home while they were being made.

### Added
- The log now shows each stream's size and the bitrate it needs, how fast data is actually arriving (every few seconds with detailed logging), the format being streamed, and when the next song starts pre-caching. Every "Waited … for data" line includes arriving vs needed speed.
- **Settings** is now a tab on the far right of the bottom bar (and no longer a cog on Home).

### Changed
- The bottom bar is see-through: pages scroll on underneath it and fade out behind the tabs.
- The mini player is a floating card tinted with the playing song's cover colour, with a white progress line, matching now playing.

## 0.0.8beta — 2026-10-16

### Added
- **Your listening**: Wrapped-style pages for this month, last month and this year. They show minutes and plays, top artists, songs (playable) and albums, your listening clock by hour (early bird, night owl…), and how your taste moved: artists new in your rotation, ones that dropped out, and genres you played more or less. Built from Koito when you scrobble there, otherwise from Musix's own history.
- **Rediscover** on Home: albums you played a lot but not for at least five months, from Navidrome's play counts and your Koito favourites from 6 to 24 months ago. Each shows when you last played it. The old random shelf is now called **Random picks**.

### Fixed
- Animated covers were found (the log said so) but still didn't move. They're now drawn by Android's own image view with the animation started explicitly, and the log notes whether each animated cover arrived animated or as a still frame.

## 0.0.7beta — 2026-10-16

### Fixed
- Animated covers didn't move. Now playing asked Navidrome for the song's own picture, which is always the still image embedded in the file; the animated cover.gif / cover.webp in the album folder is only used for the album. Musix now checks whether the album has an animated cover and, if so, shows it in now playing and on the album page, at full size so older Navidrome versions don't flatten it. Albums without one keep each song's own cover.

### Added
- Settings → Storage → **Animated covers** switch (on by default).

## 0.0.6beta — 2026-10-16

### Added
- **Personal mixes** under Made for you, made fresh each day:
  - **Your sound**: AudioMuse's sonic fingerprint of what you play most in Navidrome.
  - **On repeat**: your most-played songs lately, from Musix's history and your Koito / ListenBrainz / Last.fm top tracks.
  - **Discover for you**: songs you haven't played that sound like your favourites, leaning away from artists you already play a lot.
- **Taste profile** built on the phone from Musix's own history, Koito or ListenBrainz and Last.fm top artists this month, and Navidrome's most-played albums. Only a few artist names ever go to the AI, never your history.
- Musix now notices skips (under 30 seconds). Songs skipped twice in a month, or once in the last few days, are left out of mixes.

### Changed
- Vibe mixes lean towards artists you play, and those you usually play at this time of day. The AI also gets your favourite artists and time-of-day habits as hints.
- Home only shows what fits now: the vibe for this hour and your personal mixes. This morning's mix no longer shows up in the evening, and the page moves on to the next vibe by itself when the hour changes.
- Background mix preparation also makes the personal mixes, so they're ready when you open the app.

## 0.0.5beta — 2026-10-16

### Changed
- Snappier everywhere: now playing opens and closes faster, page changes are quick fades, the queue panel, lyrics switch, cover swipe and seek thumb respond faster, and covers fade in quicker.
- The app is now optimised when it's built (unused code removed, the rest optimised), and ships a startup profile so Android compiles it ahead of time. Starting, scrolling and opening pages should be smoother.
- The log's format line also names the file type (container), e.g. MP4 with ALAC inside.

### Fixed
- Text and buttons in now playing were dark and hard to read.

## 0.0.4beta — 2026-10-16

### Added
- **ALAC playback**: Apple Lossless songs now play, through FFmpeg decoders built into the app, for formats the phone can't decode itself. They still play lossless and untouched.
- If a song's format still can't be decoded, it's played as Opus 320 kbit/s converted by Navidrome instead of skipping silently, and the log says which format it was.
- Swipe the cover in now playing left for the next song and right for the previous one.
- Press and hold play (in now playing or the mini player) to stop and clear the queue.
- Songs with several artists ("feat.", "&", …) ask which artist to open, with each artist's photo in a small circle. Works from now playing and from "Go to artist" in song menus.
- The log records each song's format and which decoder plays it.

### Changed
- Now playing slides up over the page you were on and slides back down to it. Dragging down moves the player with your finger; let go part-way or flick to close it.
- The queue closes by swiping down anywhere on it: the list scrolls back to the top first, then the panel follows your finger down.
- Faster, smoother cross-fades between pages.

### Fixed
- Some songs (ALAC) didn't play: the player found no decoder for them and loaded the next songs in the queue one after another in the background.

## 0.0.3beta — 2026-10-16

### Added
- **Song radio** from now playing and from any song's menu: AudioMuse finds songs that sound like the current one and share its mood, replaces Up next without interrupting the song, and keeps adding more as the queue runs low. Tap **Radio on** to stop it. Falls back to Navidrome's similar songs when AudioMuse isn't set up.

### Changed
- Now playing has a simpler seek bar: a thin line with a round thumb that grows while dragging, and the remaining time on the right. The mini player shows a thin progress line.
- Playback always asks Navidrome for the original, untouched file unless Opus is chosen under Playback, even if Navidrome has a default transcoding set for new players.
- Large covers and artist photos are fetched at 1000 px, so Navidrome sends them resized and compressed. Animated covers still move.

### Fixed
- Song starts were slowed down by the next song downloading at the same moment. Pre-caching the next song now waits 20 seconds.

## 0.0.2beta — 2026-10-16

### Added
- **Detailed logging** switch under Settings → Logs, for tracking down stutter: buffer waits and audio gaps, download speeds, song cache hits, slow server responses, a frozen-screen watchdog with what the app was doing, dropped frames, memory and phone temperature.

## 0.0.1beta — 2026-10-16

The first beta. Everything built so far:

### Added
- Library synced from Navidrome into a database on the phone, with a choice of Navidrome libraries, Symfonium-style progress, and quick incremental syncs at every start.
- Home in a streaming-app style with tiles for daily mixes, vibes, mood and genre radios, and your Navidrome playlists.
- Daily mixes and time-of-day **vibes** made on the phone with AudioMuse, never saved as Navidrome playlists. OpenRouter (optional) only gets a short prompt to name each mix and pick AudioMuse searches, never the library.
- **Describe your mood** to get a playlist on the spot.
- Artist pages with Navidrome's photo, biography, popular songs, similar artists and a discography grouped by release type (Albums, EPs, Singles, Live, Compilations, DJ-mixes and more, from the `releasetype` / album types tags).
- Album and playlist pages with a full-width image that blends away as you scroll.
- Now playing with a blurred cover background, lyrics, tap the cover to open the album, and swipe up for the queue.
- Radio and genre tiles open a preview first, with play and shuffle.
- Artist radios that start at once and keep filling.
- Hearts synced to Navidrome and loved on Last.fm.
- Scrobbling to Navidrome, Last.fm and ListenBrainz-compatible servers such as Koito.
- Optional Opus transcoding with separate Wi-Fi and mobile quality.
- Song cache and image cache with sizes you choose under Settings → Storage.
- ReplayGain, background playback and lock-screen controls.
- Optional logs that can be saved to a file and shared.
- Musix's own amber-and-plum colours.
- Versions come from `version.txt`; each version gets one release.

### Fixed
- Crash "Comparison method violates its general contract" when making mixes.
- Albums shown twice when they exist in more than one library.
- Proxy login (HTTP auth) failing with an invalid port error.
