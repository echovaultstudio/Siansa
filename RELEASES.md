# Releases

Every release of Siansa and the pull requests in it, newest first. CI updates this page after each
release (`scripts/release_prs.py`); what changed for the app's user is in `CHANGELOG.md`.

## [1.1.0](https://github.com/echovaultstudio/Siansa/releases/tag/v1.1.0) — 2026-11-07

- [#989](https://github.com/echovaultstudio/Siansa/pull/989) TV app on Google Play and F-Droid, in the same listing as the phone app (@Th3K1ngP1n)
- [#1005](https://github.com/echovaultstudio/Siansa/pull/1005) Google Play: a short note of its own for each version (@Th3K1ngP1n)
- [#1008](https://github.com/echovaultstudio/Siansa/pull/1008) Release 1.0.0: the changelog and version the release couldn't write (@Th3K1ngP1n)
- [#1009](https://github.com/echovaultstudio/Siansa/pull/1009) Branch flow after v1.0: features on dev, fixes on main; the release pushes with its own key (@Th3K1ngP1n)
- [#1010](https://github.com/echovaultstudio/Siansa/pull/1010) Google Play: releases go to Google Play again (.play-hold removed) (@Th3K1ngP1n)
- [#1012](https://github.com/echovaultstudio/Siansa/pull/1012) Dependabot: the app's library updates go to dev (@Th3K1ngP1n)

## [1.0.0](https://github.com/echovaultstudio/Siansa/releases/tag/v1.0.0) — 2026-11-07

- [#815](https://github.com/echovaultstudio/Siansa/pull/815) Getting started: mention Last.fm and Koito (@Th3K1ngP1n)
- [#827](https://github.com/echovaultstudio/Siansa/pull/827) Siansa 1.0: mixer into main (@Th3K1ngP1n)
- [#829](https://github.com/echovaultstudio/Siansa/pull/829) Emby test: requests recorded safely when they come in at once (@Th3K1ngP1n)
- [#831](https://github.com/echovaultstudio/Siansa/pull/831) Not stuck offline after Android starts Siansa in the background (@Th3K1ngP1n)
- [#833](https://github.com/echovaultstudio/Siansa/pull/833) Artist radios use AudioMuse-AI, the server's similar songs without it (@Th3K1ngP1n)
- [#834](https://github.com/echovaultstudio/Siansa/pull/834) Smart crossfades blend after the last beat (plugin 0.5.0) (@Th3K1ngP1n)
- [#835](https://github.com/echovaultstudio/Siansa/pull/835) New audio engine: the echo at the end of a blend, on the beat (@Th3K1ngP1n)
- [#836](https://github.com/echovaultstudio/Siansa/pull/836) New audio engine: Keep the key, by its own time-stretch (@Th3K1ngP1n)
- [#837](https://github.com/echovaultstudio/Siansa/pull/837) No crash when Android refuses the foreground to a late notification (@Th3K1ngP1n)
- [#838](https://github.com/echovaultstudio/Siansa/pull/838) Artist radios: AudioMuse's picks carry its mark in the queue (@Th3K1ngP1n)
- [#840](https://github.com/echovaultstudio/Siansa/pull/840) Plugin 0.5.1: Measure all fills in where every song's beat stops (@Th3K1ngP1n)
- [#841](https://github.com/echovaultstudio/Siansa/pull/841) Split a joined artist name that carries one of its artists' MusicBrainz IDs (@Th3K1ngP1n)
- [#842](https://github.com/echovaultstudio/Siansa/pull/842) Plugin 0.5.2: shorter texts on its settings page (@Th3K1ngP1n)
- [#843](https://github.com/echovaultstudio/Siansa/pull/843) Plugin 0.5.3: the beat grids bar follows the check for where the beat stops (@Th3K1ngP1n)
- [#844](https://github.com/echovaultstudio/Siansa/pull/844) Split joined names that are only a song's artist too (@Th3K1ngP1n)
- [#845](https://github.com/echovaultstudio/Siansa/pull/845) Plugin 0.5.4: a run stopped by a restart counts as stopped after 5 minutes (@Th3K1ngP1n)
- [#846](https://github.com/echovaultstudio/Siansa/pull/846) Joined artist names: "ID" and "Unknown" aren't artists (@Th3K1ngP1n)
- [#847](https://github.com/echovaultstudio/Siansa/pull/847) Joined artist names: parts the library lacks are looked up on the server (@Th3K1ngP1n)
- [#848](https://github.com/echovaultstudio/Siansa/pull/848) [Wait for logs] Beat-match grid tracks the plugin read a little unsteady (@Th3K1ngP1n)
- [#849](https://github.com/echovaultstudio/Siansa/pull/849) [Wait for logs] DJ blends where at most one end has a beat run the whole crossfade length (@Th3K1ngP1n)
- [#850](https://github.com/echovaultstudio/Siansa/pull/850) [Wait for logs] Log the blend settings, and each change to them (@Th3K1ngP1n)
- [#851](https://github.com/echovaultstudio/Siansa/pull/851) [Wait for logs] Hide Echo at the end and Keep the key for now (@Th3K1ngP1n)
- [#852](https://github.com/echovaultstudio/Siansa/pull/852) DJ blends: the whole length only when one end has no beat at all (@Th3K1ngP1n)
- [#853](https://github.com/echovaultstudio/Siansa/pull/853) Casting: a Siansa screen on the TV, once the receiver is registered (#114) (@Th3K1ngP1n)
- [#854](https://github.com/echovaultstudio/Siansa/pull/854) Sign in with an API key (OpenSubsonic, #493) (@Th3K1ngP1n)
- [#855](https://github.com/echovaultstudio/Siansa/pull/855) Android Auto: browse and play from the car (#62) (@Th3K1ngP1n)
- [#857](https://github.com/echovaultstudio/Siansa/pull/857) Your Stations: internet radio and podcasts (#353) (@Th3K1ngP1n)
- [#858](https://github.com/echovaultstudio/Siansa/pull/858) Station pictures, editing stations, stations and podcasts right in Your Library (#353) (@Th3K1ngP1n)
- [#859](https://github.com/echovaultstudio/Siansa/pull/859) Cast TV screen in Siansa's main colours and icon (#114) (@Th3K1ngP1n)
- [#861](https://github.com/echovaultstudio/Siansa/pull/861) Casting: every Cast device listed, Google's screen where Siansa's can't start (#114) (@Th3K1ngP1n)
- [#862](https://github.com/echovaultstudio/Siansa/pull/862) Cast TV screen: simpler player, remote left/right as previous/next (#114) (@Th3K1ngP1n)
- [#863](https://github.com/echovaultstudio/Siansa/pull/863) Cast TV screen: hold left or right to wind through the song (#114) (@Th3K1ngP1n)
- [#864](https://github.com/echovaultstudio/Siansa/pull/864) Cast: the TV screen reports remote presses to the phone's log (#114) (@Th3K1ngP1n)
- [#865](https://github.com/echovaultstudio/Siansa/pull/865) DJ blends: a loud beatless ending gets a short blend, not the whole length (@Th3K1ngP1n)
- [#866](https://github.com/echovaultstudio/Siansa/pull/866) Crossfade: say that with Smart the length is the longest a fade can be (@Th3K1ngP1n)
- [#867](https://github.com/echovaultstudio/Siansa/pull/867) New song radios start as Steady vibes, no surprises; fresh installs use Siansa's colours (@Th3K1ngP1n)
- [#868](https://github.com/echovaultstudio/Siansa/pull/868) Cast TV screen: hold to wind works on Google TV, OK no longer pauses and plays again (#114) (@Th3K1ngP1n)
- [#869](https://github.com/echovaultstudio/Siansa/pull/869) Casting: ALAC goes as MP3, Siansa's screen retried, no blank queue songs (#114) (@Th3K1ngP1n)
- [#870](https://github.com/echovaultstudio/Siansa/pull/870) Cast quality: Lossless or MP3 320 under the Chromecast cog, ALAC as FLAC (#114) (@Th3K1ngP1n)
- [#871](https://github.com/echovaultstudio/Siansa/pull/871) Gapless casting through the phone: the mix as one live FLAC stream to the TV (#114) (@Th3K1ngP1n)
- [#872](https://github.com/echovaultstudio/Siansa/pull/872) Home: a cast button between the dice and the filter, when there's something to play on (@Th3K1ngP1n)
- [#873](https://github.com/echovaultstudio/Siansa/pull/873) Casting: ALAC as FLAC for library songs, the phone keeps its player, How it plays switches during a cast (#114) (@Th3K1ngP1n)
- [#874](https://github.com/echovaultstudio/Siansa/pull/874) Gapless casting: the TV screen plays the phone's stream in its own audio element, WAV fallback, no endless retries (#114) (@Th3K1ngP1n)
- [#875](https://github.com/echovaultstudio/Siansa/pull/875) Plugin 0.5.5: only the tick in Scheduled Tasks, with a description (@Th3K1ngP1n)
- [#876](https://github.com/echovaultstudio/Siansa/pull/876) Gapless casting: the TV's stream starts (no early pause, enough data before start), clearer cast-end log (#114) (@Th3K1ngP1n)
- [#877](https://github.com/echovaultstudio/Siansa/pull/877) Siansa for Android TV, sign-in from the phone, audio passthrough (#860) (@Th3K1ngP1n)
- [#878](https://github.com/echovaultstudio/Siansa/pull/878) Casting: Siansa's TV screen shows straight away with nothing playing; Play on shows each TV's own name (#114) (@Th3K1ngP1n)
- [#880](https://github.com/echovaultstudio/Siansa/pull/880) Casting stability: the phone's queue is the only one, an own Cast player keeps the TV a few songs (#114) (@Th3K1ngP1n)
- [#881](https://github.com/echovaultstudio/Siansa/pull/881) Cast and DLNA: Opus 320 for converted songs (@Th3K1ngP1n)
- [#882](https://github.com/echovaultstudio/Siansa/pull/882) Casting: remove "Gapless through this phone" for now (@Th3K1ngP1n)
- [#883](https://github.com/echovaultstudio/Siansa/pull/883) Mini player: a fresh install shows play/pause, next song and add to playlist (@Th3K1ngP1n)
- [#884](https://github.com/echovaultstudio/Siansa/pull/884) Your Library: list or grid kept for each view (@Th3K1ngP1n)
- [#885](https://github.com/echovaultstudio/Siansa/pull/885) Home: one menu (Play on, Sources, Arrange Home) with glide, and a rolling dice (@Th3K1ngP1n)
- [#887](https://github.com/echovaultstudio/Siansa/pull/887) TV app: the core of Siansa, Report a problem, readable text, Atmos fixes (#860) (@Th3K1ngP1n)
- [#888](https://github.com/echovaultstudio/Siansa/pull/888) Home menu: bubbles in one straight line under the button (@Th3K1ngP1n)
- [#889](https://github.com/echovaultstudio/Siansa/pull/889) Home: notices at the top with the menu as a round button in line; the dice in the menu; Home Menu order in Style (@Th3K1ngP1n)
- [#890](https://github.com/echovaultstudio/Siansa/pull/890) Smart crossfade: cold ends, a short pause between a hard ending and a hard start (@Th3K1ngP1n)
- [#894](https://github.com/echovaultstudio/Siansa/pull/894) TV sign-in: also set up the phone's services, with a tick box (#860) (@Th3K1ngP1n)
- [#898](https://github.com/echovaultstudio/Siansa/pull/898) Settings for v1.0: Connections, Playback with streaming quality and book skips, Data with downloads, Notices and Cards, Auto Generated Mixes (@Th3K1ngP1n)
- [#899](https://github.com/echovaultstudio/Siansa/pull/899) Smart crossfade: a loud beatless outro gets a short blend; no pause in a cold end between two beats (@Th3K1ngP1n)
- [#902](https://github.com/echovaultstudio/Siansa/pull/902) TV: Atmos no longer stalls at the next song, no freeze at a song's start, typing in the page, notices, Up next stars, Now playing buttons in line (@Th3K1ngP1n)
- [#904](https://github.com/echovaultstudio/Siansa/pull/904) Downloaded songs play from the phone on Emby and Jellyfin (#903) (@Th3K1ngP1n)
- [#905](https://github.com/echovaultstudio/Siansa/pull/905) Test on real Navidrome, Emby and Jellyfin servers in CI; fix Emby lyrics and folder covers (@Th3K1ngP1n)
- [#907](https://github.com/echovaultstudio/Siansa/pull/907) Casting: skips no longer flicker, seeks in Opus and MP3 no longer start over (#901) (@Th3K1ngP1n)
- [#908](https://github.com/echovaultstudio/Siansa/pull/908) TV: covers no longer all reload each time the SHIELD says memory is critical (#892) (@Th3K1ngP1n)
- [#909](https://github.com/echovaultstudio/Siansa/pull/909) CodeQL advanced setup, so Kotlin is scanned too (@Th3K1ngP1n)
- [#910](https://github.com/echovaultstudio/Siansa/pull/910) CodeQL advanced setup on main, so Kotlin is scanned too (@Th3K1ngP1n)
- [#911](https://github.com/echovaultstudio/Siansa/pull/911) CLAUDE.md: releases never publish the APK on GitHub, only F-Droid and Google Play (@Th3K1ngP1n)
- [#912](https://github.com/echovaultstudio/Siansa/pull/912) CLAUDE.md: releases never publish the APK on GitHub, only F-Droid and Google Play (@Th3K1ngP1n)
- [#914](https://github.com/echovaultstudio/Siansa/pull/914) Dev builds: their GitHub page warns they're test builds, for helping find bugs only (@Th3K1ngP1n)
- [#917](https://github.com/echovaultstudio/Siansa/pull/917) Queue swipes: right to left removes, left to right plays next, small animations (#915) (@Th3K1ngP1n)
- [#918](https://github.com/echovaultstudio/Siansa/pull/918) Song radio: icons for Steady and DJ, no descriptions; mixing docs in Settings (#916) (@Th3K1ngP1n)
- [#920](https://github.com/echovaultstudio/Siansa/pull/920) Slide from the play button to start a mix; grooves during a DJ mix; Start a mix (#919) (@Th3K1ngP1n)
- [#921](https://github.com/echovaultstudio/Siansa/pull/921) TV: Add to playlist, Song details and Stop the mix (#895) (@Th3K1ngP1n)
- [#923](https://github.com/echovaultstudio/Siansa/pull/923) Home: new albums notice shows once the library has loaded, and covers 30 days (#922) (@Th3K1ngP1n)
- [#925](https://github.com/echovaultstudio/Siansa/pull/925) TV: join Atmos songs without a gap (priming frame) (#924) (@Th3K1ngP1n)
- [#927](https://github.com/echovaultstudio/Siansa/pull/927) TV: show focus on buttons in settings pages (Test and others) (#926) (@Th3K1ngP1n)
- [#929](https://github.com/echovaultstudio/Siansa/pull/929) Library: make animated covers' stills from the start of the file (#928) (@Th3K1ngP1n)
- [#931](https://github.com/echovaultstudio/Siansa/pull/931) TV: play Dolby songs louder on the receiver (Settings › Playback) (#930) (@Th3K1ngP1n)
- [#936](https://github.com/echovaultstudio/Siansa/pull/936) Play button slides: the choice's name in the blurred cover (#933) (@Th3K1ngP1n)
- [#937](https://github.com/echovaultstudio/Siansa/pull/937) Home shortcuts: only what was played last, 2 to 8 of them (#934) (@Th3K1ngP1n)
- [#938](https://github.com/echovaultstudio/Siansa/pull/938) Home notices: Liked songs as a notice you can add (#935) (@Th3K1ngP1n)
- [#940](https://github.com/echovaultstudio/Siansa/pull/940) TV: no gap 3 s into Atmos songs that follow straight on (#939) (@Th3K1ngP1n)
- [#942](https://github.com/echovaultstudio/Siansa/pull/942) TV: no silences mid-song when the TV is busy (mixer buffer 480 ms) (#941) (@Th3K1ngP1n)
- [#944](https://github.com/echovaultstudio/Siansa/pull/944) TV artist page: top songs folded to three (#943) (@Th3K1ngP1n)
- [#946](https://github.com/echovaultstudio/Siansa/pull/946) TV: animated covers off by default (#945) (@Th3K1ngP1n)
- [#948](https://github.com/echovaultstudio/Siansa/pull/948) TV: pages switch at once, no fade (#947) (@Th3K1ngP1n)
- [#950](https://github.com/echovaultstudio/Siansa/pull/950) TV: Time machine out of the Library for now (#949) (@Th3K1ngP1n)
- [#952](https://github.com/echovaultstudio/Siansa/pull/952) TV: lighter covers (#951) (@Th3K1ngP1n)
- [#954](https://github.com/echovaultstudio/Siansa/pull/954) TV: rows stay still during a sync (#953) (@Th3K1ngP1n)
- [#956](https://github.com/echovaultstudio/Siansa/pull/956) TV: the remote reaches Test under a login (up and down leave a text box) (#955) (@Th3K1ngP1n)
- [#958](https://github.com/echovaultstudio/Siansa/pull/958) TV: keys and passwords show as dots (#957) (@Th3K1ngP1n)
- [#960](https://github.com/echovaultstudio/Siansa/pull/960) Home menu: blur and dim the page behind it (#959) (@Th3K1ngP1n)
- [#962](https://github.com/echovaultstudio/Siansa/pull/962) Arrange Home: hide a section's title (#961) (@Th3K1ngP1n)
- [#964](https://github.com/echovaultstudio/Siansa/pull/964) Play button slides: dim everything around, hide Play on going down, up choice on the seek bar (#963) (@Th3K1ngP1n)
- [#966](https://github.com/echovaultstudio/Siansa/pull/966) Album bar: Download, Similar, Shuffle, Play; Play continues; slide shuffle onto Play for a DJ mix (#965) (@Th3K1ngP1n)
- [#968](https://github.com/echovaultstudio/Siansa/pull/968) TV: similar albums asks AudioMuse through one of the album's songs (#967) (@Th3K1ngP1n)
- [#969](https://github.com/echovaultstudio/Siansa/pull/969) TV text boxes: a control of the page that types on OK, so the remote reaches Test (#955) (@Th3K1ngP1n)
- [#971](https://github.com/echovaultstudio/Siansa/pull/971) Now playing: dynamic colours from the cover, a Style switch (#970) (@Th3K1ngP1n)
- [#973](https://github.com/echovaultstudio/Siansa/pull/973) Casting: no songs sent twice at a song change; join the TV again when its screen closes (#972) (@Th3K1ngP1n)
- [#975](https://github.com/echovaultstudio/Siansa/pull/975) TV now playing: as simple as the Chromecast screen; down opens the menu; Siansa logo; the chip's wave moves (#974) (@Th3K1ngP1n)
- [#976](https://github.com/echovaultstudio/Siansa/pull/976) Album DJ mix: start the music on the main thread (#965) (@Th3K1ngP1n)
- [#977](https://github.com/echovaultstudio/Siansa/pull/977) Album bar: plus and download left, similar, shuffle and Play right; Continue from 8 in the details (#965) (@Th3K1ngP1n)
- [#979](https://github.com/echovaultstudio/Siansa/pull/979) TV: the remote reaches the Test buttons under a login (@Th3K1ngP1n)
- [#981](https://github.com/echovaultstudio/Siansa/pull/981) TV: album grids six across (@Th3K1ngP1n)
- [#983](https://github.com/echovaultstudio/Siansa/pull/983) TV: an artist's releases in a row per type (@Th3K1ngP1n)
- [#985](https://github.com/echovaultstudio/Siansa/pull/985) TV: hold left or right in Now playing to wind through the song (@Th3K1ngP1n)
- [#987](https://github.com/echovaultstudio/Siansa/pull/987) Showcase: the play button's slides and an album's DJ mix (@Th3K1ngP1n)
- [#991](https://github.com/echovaultstudio/Siansa/pull/991) Docs ready for v1 (@Th3K1ngP1n)
- [#993](https://github.com/echovaultstudio/Siansa/pull/993) v1: the new audio engine for everyone (@Th3K1ngP1n)
- [#995](https://github.com/echovaultstudio/Siansa/pull/995) Dynamic colours: the colour that fills the cover, silver for black and white (@Th3K1ngP1n)
- [#997](https://github.com/echovaultstudio/Siansa/pull/997) TV: cards fill the screen, one bar of pills, plain album values, search history (@Th3K1ngP1n)
- [#999](https://github.com/echovaultstudio/Siansa/pull/999) Dynamic colours: the liked heart in the cover's colour too (@Th3K1ngP1n)
- [#1001](https://github.com/echovaultstudio/Siansa/pull/1001) v1.0: release from main to F-Droid, Google Play held (@Th3K1ngP1n)
- [#1003](https://github.com/echovaultstudio/Siansa/pull/1003) Plugin: a failed health check no longer sends the error's text (@Th3K1ngP1n)
- 1 commit pushed straight to main

## [0.51.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.51.1beta) — 2026-11-02

- [#823](https://github.com/echovaultstudio/Siansa/pull/823) Crossfade: a fade ends when the player stops or the queue changes; beat-matched blends off by default (@Th3K1ngP1n)
- [#825](https://github.com/echovaultstudio/Siansa/pull/825) Siansa Dev: a test build of the mixer branch, installed next to Siansa (@Th3K1ngP1n)

## [0.51.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.51.0beta) — 2026-11-02

- [#817](https://github.com/echovaultstudio/Siansa/pull/817) DJ blends: load the next song ahead; resample instead of time-stretch (@Th3K1ngP1n)

## [0.50.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.50.1beta) — 2026-11-02

- [#810](https://github.com/echovaultstudio/Siansa/pull/810) Swipe to remove from the queue goes left to right, behind a strip (@Th3K1ngP1n)
- [#812](https://github.com/echovaultstudio/Siansa/pull/812) Emby and Jellyfin: album lists show a song's picture when the album has none of its own (@Th3K1ngP1n)
- [#814](https://github.com/echovaultstudio/Siansa/pull/814) Beat-matched blends: judge the tail and the next song once positions are real; open-loop nudge (@Th3K1ngP1n)

## [0.50.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.50.0beta) — 2026-11-02

- [#806](https://github.com/echovaultstudio/Siansa/pull/806) Take songs out of the queue: swipe left, Remove from queue, Remove songs up to here (@Th3K1ngP1n)
- [#808](https://github.com/echovaultstudio/Siansa/pull/808) Beat-matched blends: measured tail sync, one jump, DJ tempos, bass swap (@Th3K1ngP1n)

## [0.49.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.49.0beta) — 2026-11-01

- [#790](https://github.com/echovaultstudio/Siansa/pull/790) Home: Cover rows as a look for every section in Arrange Home (@Th3K1ngP1n)
- [#791](https://github.com/echovaultstudio/Siansa/pull/791) AudioMuse: a missing Siansa plugin is told from an unknown song (@Th3K1ngP1n)
- [#793](https://github.com/echovaultstudio/Siansa/pull/793) Emby, Jellyfin: an album without a cover of its own shows its first song's on its page (@Th3K1ngP1n)
- [#795](https://github.com/echovaultstudio/Siansa/pull/795) Home's notice chips as Your Library's pills; an × beside a pin unpins it (@Th3K1ngP1n)
- [#797](https://github.com/echovaultstudio/Siansa/pull/797) DJ blends: no long blend over an unknown beat, fewer bars into short songs (#796) (@Th3K1ngP1n)
- [#799](https://github.com/echovaultstudio/Siansa/pull/799) DJ mix shapes: a fixed number of songs, so the next song follows a reshape (#798) (@Th3K1ngP1n)
- [#801](https://github.com/echovaultstudio/Siansa/pull/801) Cover rows: no arrow, dots follow the swipe; Settings › Home: Notices and Cards (#800) (@Th3K1ngP1n)
- [#803](https://github.com/echovaultstudio/Siansa/pull/803) Plugin 0.4.2: breakbeats read at their tempo (#802) (@Th3K1ngP1n)
- [#805](https://github.com/echovaultstudio/Siansa/pull/805) Review fixes and Cover rows screenshots for the merged pull requests (@Th3K1ngP1n)

## [0.48.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.48.0beta) — 2026-11-01

- [#779](https://github.com/echovaultstudio/Siansa/pull/779) Now playing menu: no MENUSHUFFLE badge on the shuffle row (@Th3K1ngP1n)
- [#781](https://github.com/echovaultstudio/Siansa/pull/781) Settings: grouped and reordered, sections open by default (@Th3K1ngP1n)
- [#787](https://github.com/echovaultstudio/Siansa/pull/787) DJ radio: shaping the mix changes the very next song (@Th3K1ngP1n)

## [0.47.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.47.0beta) — 2026-10-31

- [#769](https://github.com/echovaultstudio/Siansa/pull/769) Brand: the banner's tagline as the website's, and an F-Droid sized banner (@Th3K1ngP1n)
- [#770](https://github.com/echovaultstudio/Siansa/pull/770) Docs: Getting started links the plugin and says it does the DJ blends (@Th3K1ngP1n)
- [#772](https://github.com/echovaultstudio/Siansa/pull/772) Now playing: a short buzz when repeat or add to playlist is pressed (@Th3K1ngP1n)
- [#774](https://github.com/echovaultstudio/Siansa/pull/774) DJ blends: no cut at the start, no pitch flutter during the blend (@Th3K1ngP1n)
- [#775](https://github.com/echovaultstudio/Siansa/pull/775) Screenshots: the crossfade settings pictures on the grey pages (@Th3K1ngP1n)
- [#777](https://github.com/echovaultstudio/Siansa/pull/777) Today's mix: Auto update and its own tuning; settings sections fold (@Th3K1ngP1n)

## [0.46.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.46.0beta) — 2026-10-31

- [#762](https://github.com/echovaultstudio/Siansa/pull/762) Grey pages and cards instead of the purple tint, with a choice in Themes (@Th3K1ngP1n)
- [#764](https://github.com/echovaultstudio/Siansa/pull/764) Mini player: a short buzz when a swipe changes the song (@Th3K1ngP1n)
- [#768](https://github.com/echovaultstudio/Siansa/pull/768) DJ blends: wait for the old song's copy while it loads, instead of calling the blend off (@Th3K1ngP1n)

## [0.45.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.45.0beta) — 2026-10-31

- [#754](https://github.com/echovaultstudio/Siansa/pull/754) Showcase: audiobooks and podcasts, four years of listening, and the website's animations (@Th3K1ngP1n)
- [#758](https://github.com/echovaultstudio/Siansa/pull/758) DJ radio: blends phrase on phrase, like a DJ (plugin 0.4.0) (@Th3K1ngP1n)
- [#760](https://github.com/echovaultstudio/Siansa/pull/760) DJ blends: no jumps once heard, the handover in step, an echo at the end, crossfades up to 30 s (@Th3K1ngP1n)
- [#766](https://github.com/echovaultstudio/Siansa/pull/766) Plugin 0.4.1: the settings page says where the rounds are and keeps itself current (@Th3K1ngP1n)

## [0.44.2beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.44.2beta) — 2026-10-31

- [#743](https://github.com/echovaultstudio/Siansa/pull/743) Showcase screenshots: the app on the emulator with a made-up library (@Th3K1ngP1n)
- [#746](https://github.com/echovaultstudio/Siansa/pull/746) Crossfade log: why a blend was or wasn't beat-matched, and how well it kept in step (@Th3K1ngP1n)
- [#748](https://github.com/echovaultstudio/Siansa/pull/748) Blends: a jump no longer stops the blend, the next song lands on the beat, no stumble at the start (@Th3K1ngP1n)
- [#752](https://github.com/echovaultstudio/Siansa/pull/752) Licences page: the website's current copy (wordmark header, versioned links) (@Th3K1ngP1n)
- [#755](https://github.com/echovaultstudio/Siansa/pull/755) Revert the blend fixes (#748): back to a pull request, not a release yet (@Th3K1ngP1n)
- [#756](https://github.com/echovaultstudio/Siansa/pull/756) Blends: our own jump no longer stops the blend, the next song lands on the beat, and the tail keeps in step (@Th3K1ngP1n)

## [0.44.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.44.1beta) — 2026-10-31

- [#671](https://github.com/echovaultstudio/Siansa/pull/671) Bump androidx.palette:palette-ktx from 1.0.0 to 1.1.0 in the androidx group (@dependabot[bot])
- [#672](https://github.com/echovaultstudio/Siansa/pull/672) Bump actions/setup-python from 6 to 7 in the actions group (@dependabot[bot])
- [#709](https://github.com/echovaultstudio/Siansa/pull/709) AudioMuse: a shown user's songs are asked on their own AudioMuse server (@Th3K1ngP1n)
- [#711](https://github.com/echovaultstudio/Siansa/pull/711) Screenshot tests on every pull request (@Th3K1ngP1n)
- [#715](https://github.com/echovaultstudio/Siansa/pull/715) Emulator tests: SpacingTest split by area of the app (@Th3K1ngP1n)
- [#716](https://github.com/echovaultstudio/Siansa/pull/716) CI: compile the emulator tests on every pull request (@Th3K1ngP1n)
- [#718](https://github.com/echovaultstudio/Siansa/pull/718) Now playing: one scrolling page, cards in the cover's colour (@Th3K1ngP1n)
- [#719](https://github.com/echovaultstudio/Siansa/pull/719) CI: emulator tests on pull requests labelled ui (@Th3K1ngP1n)
- [#722](https://github.com/echovaultstudio/Siansa/pull/722) Casting: remove "Speaker plays the queue" (@Th3K1ngP1n)
- [#724](https://github.com/echovaultstudio/Siansa/pull/724) Album pages: a short album scrolls its cover away instead of leaving a gap (@Th3K1ngP1n)
- [#725](https://github.com/echovaultstudio/Siansa/pull/725) Bottom tabs: "Your Library" fits with a larger font (@Th3K1ngP1n)
- [#727](https://github.com/echovaultstudio/Siansa/pull/727) New app icon: nordic bars with a reflection between Siansa's half circles (@Th3K1ngP1n)
- [#729](https://github.com/echovaultstudio/Siansa/pull/729) Default theme: Mono colours with the amber accent (@Th3K1ngP1n)
- [#731](https://github.com/echovaultstudio/Siansa/pull/731) Play button cards: only the play button plays, no arrow (@Th3K1ngP1n)
- [#733](https://github.com/echovaultstudio/Siansa/pull/733) Licences page: the website's new look (@Th3K1ngP1n)
- [#735](https://github.com/echovaultstudio/Siansa/pull/735) Hold releases while a batch of pull requests is merged (@Th3K1ngP1n)
- [#737](https://github.com/echovaultstudio/Siansa/pull/737) Animated covers: a slow first answer no longer makes a cover still for good (@Th3K1ngP1n)
- [#739](https://github.com/echovaultstudio/Siansa/pull/739) Emulator tests: no crash, the tour and cover checks pass, and even gaps they found (@Th3K1ngP1n)
- [#742](https://github.com/echovaultstudio/Siansa/pull/742) Publish the held release (v0.44.1beta) (@Th3K1ngP1n)
- [#750](https://github.com/echovaultstudio/Siansa/pull/750) Build: Robolectric from Google's copy of Maven Central, so the release build gets its jar (@Th3K1ngP1n)

## [0.44.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.44.0beta) — 2026-10-31

- [#676](https://github.com/echovaultstudio/Siansa/pull/676) Google Play: say why an upload is refused, on one line, and at which step (@Th3K1ngP1n)
- [#679](https://github.com/echovaultstudio/Siansa/pull/679) Shape the mix: keep "Surprises" on one line (@Th3K1ngP1n)
- [#680](https://github.com/echovaultstudio/Siansa/pull/680) Custom cards: Play count for songs, Played and Times skipped rules (@Th3K1ngP1n)
- [#684](https://github.com/echovaultstudio/Siansa/pull/684) Style: the phone's top bar see-through, black or hidden (@Th3K1ngP1n)
- [#685](https://github.com/echovaultstudio/Siansa/pull/685) Album pages: the cover fades fully on a short album (@Th3K1ngP1n)
- [#686](https://github.com/echovaultstudio/Siansa/pull/686) Now playing: the page fades in under the small header (@Th3K1ngP1n)
- [#688](https://github.com/echovaultstudio/Siansa/pull/688) Connections: change another login's server without switching to it (@Th3K1ngP1n)
- [#690](https://github.com/echovaultstudio/Siansa/pull/690) Chromecast: load the next song before the current one ends (@Th3K1ngP1n)
- [#692](https://github.com/echovaultstudio/Siansa/pull/692) DLNA: let LinkPlay speakers (JBL Bar, WiiM…) play the queue themselves (@Th3K1ngP1n)
- [#695](https://github.com/echovaultstudio/Siansa/pull/695) Source filter: Sync all, Audiobookshelf listed, the sync on one line with one tick (@Th3K1ngP1n)
- [#696](https://github.com/echovaultstudio/Siansa/pull/696) Arrange Home: the spacing step's name fits on one line (@Th3K1ngP1n)
- [#698](https://github.com/echovaultstudio/Siansa/pull/698) Tiles and cards keep whole words in their titles (@Th3K1ngP1n)
- [#700](https://github.com/echovaultstudio/Siansa/pull/700) Light mode: the bar behind the tabs follows the theme (@Th3K1ngP1n)
- [#702](https://github.com/echovaultstudio/Siansa/pull/702) Rearranging: rows move on every drag; Arrange Home scrolls while a row is held (@Th3K1ngP1n)
- [#704](https://github.com/echovaultstudio/Siansa/pull/704) Jellyfin as a music source (@Th3K1ngP1n)
- [#706](https://github.com/echovaultstudio/Siansa/pull/706) CI: name the release APK whether or not it's signed (@Th3K1ngP1n)
- [#707](https://github.com/echovaultstudio/Siansa/pull/707) Emulator tests: SpacingTest put back together after merging (@Th3K1ngP1n)

## [0.43.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.43.0beta) — 2026-10-30

- [#650](https://github.com/echovaultstudio/Siansa/pull/650) Shape the mix: steady, build, euphoric, wind down and surprises in DJ radio (@Th3K1ngP1n)
- [#652](https://github.com/echovaultstudio/Siansa/pull/652) Pinned card views draw without the player again (emulator tests) (@Th3K1ngP1n)
- [#654](https://github.com/echovaultstudio/Siansa/pull/654) Plugin 0.3.1: beat grids get their own section on the settings page (@Th3K1ngP1n)
- [#656](https://github.com/echovaultstudio/Siansa/pull/656) Change the look of any Home section from Arrange Home (@Th3K1ngP1n)
- [#658](https://github.com/echovaultstudio/Siansa/pull/658) Plugin 0.3.2: beat grids that failed are tried again, any songs per run (@Th3K1ngP1n)
- [#660](https://github.com/echovaultstudio/Siansa/pull/660) Plugin 0.3.3: songs on every music server get measured (@Th3K1ngP1n)
- [#662](https://github.com/echovaultstudio/Siansa/pull/662) Time Machine finds other versions of a song; search finds names typed without an apostrophe (@Th3K1ngP1n)
- [#664](https://github.com/echovaultstudio/Siansa/pull/664) Custom cards: rules to include and exclude, album cards, order and size (@Th3K1ngP1n)
- [#666](https://github.com/echovaultstudio/Siansa/pull/666) One Connections page: servers and services, folded; more AI services (@Th3K1ngP1n)
- [#668](https://github.com/echovaultstudio/Siansa/pull/668) The loudness target applies to ReplayGain tags too (@Th3K1ngP1n)
- [#670](https://github.com/echovaultstudio/Siansa/pull/670) Next release is 0.43.0beta (@Th3K1ngP1n)

## [0.40.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.40.0beta) — 2026-10-29

- [#647](https://github.com/echovaultstudio/Siansa/pull/647) Plugin 0.3.0: the beat grid at each end of a song, for beat-matched blends (@Th3K1ngP1n)
- [#648](https://github.com/echovaultstudio/Siansa/pull/648) Beat-matched blends in DJ radio (experimental) (@Th3K1ngP1n)

## [0.39.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.39.1beta) — 2026-10-29

- [#637](https://github.com/echovaultstudio/Siansa/pull/637) Going-public runbook removed (now in siansa-dev) (@Th3K1ngP1n)
- [#641](https://github.com/echovaultstudio/Siansa/pull/641) Disc covers follow their album's cover version: a replaced cover shows in the mini player too (@Th3K1ngP1n)
- [#643](https://github.com/echovaultstudio/Siansa/pull/643) Plugin 0.2.4: songs PyAV can't decode are measured with the ffmpeg tool (@Th3K1ngP1n)
- [#645](https://github.com/echovaultstudio/Siansa/pull/645) Play buttons turn into Pause while their songs play (@Th3K1ngP1n)

## [0.39.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.39.0beta) — 2026-10-28

- [#619](https://github.com/echovaultstudio/Siansa/pull/619) README: F-Droid and Google Play buttons (@Th3K1ngP1n)
- [#621](https://github.com/echovaultstudio/Siansa/pull/621) Song cache: an Off choice (@Th3K1ngP1n)
- [#623](https://github.com/echovaultstudio/Siansa/pull/623) Custom cards: cards of your own pinned to Home (@Th3K1ngP1n)
- [#625](https://github.com/echovaultstudio/Siansa/pull/625) DLNA: let go of the speaker when the phone leaves its Wi-Fi (@Th3K1ngP1n)
- [#627](https://github.com/echovaultstudio/Siansa/pull/627) Book and podcast pages like album pages; podcast progress follows the episode (@Th3K1ngP1n)
- [#629](https://github.com/echovaultstudio/Siansa/pull/629) Smart fades leave out silence and the end of long fade-outs (@Th3K1ngP1n)
- [#631](https://github.com/echovaultstudio/Siansa/pull/631) Podcast episodes: their own description unfolds under them (@Th3K1ngP1n)
- [#633](https://github.com/echovaultstudio/Siansa/pull/633) Daily summary email: the app version and its F-Droid downloads only (@Th3K1ngP1n)
- [#635](https://github.com/echovaultstudio/Siansa/pull/635) Server kit and emails move to the private siansa-dev repository (@Th3K1ngP1n)

## [0.38.5beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.38.5beta) — 2026-10-28

- [#612](https://github.com/echovaultstudio/Siansa/pull/612) F-Droid repo and listing for the public project: name, icon, licence, links (@Th3K1ngP1n)
- [#616](https://github.com/echovaultstudio/Siansa/pull/616) Website: "Get Siansa" in the menu leads to F-Droid first (@Th3K1ngP1n)
- [#617](https://github.com/echovaultstudio/Siansa/pull/617) Arrange Home: a section moved to the top stays in view (@Th3K1ngP1n)

## [0.38.4beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.38.4beta) — 2026-10-28

- [#603](https://github.com/echovaultstudio/Siansa/pull/603) Don't crash when a song waits for data in the background (@Th3K1ngP1n)
- [#605](https://github.com/echovaultstudio/Siansa/pull/605) Public repo test and runbook after the history rewrite (@Th3K1ngP1n)
- [#607](https://github.com/echovaultstudio/Siansa/pull/607) Close the beta testers repository: reports, docs and changelog move here (@Th3K1ngP1n)
- [#609](https://github.com/echovaultstudio/Siansa/pull/609) Website: F-Droid repository open to everyone, Google Play beta button (@Th3K1ngP1n)
- [#610](https://github.com/echovaultstudio/Siansa/pull/610) CI: pull requests and quick checks on GitHub's runners (@Th3K1ngP1n)
- [#611](https://github.com/echovaultstudio/Siansa/pull/611) Website deploy on GitHub's runner (@Th3K1ngP1n)
- 4 commits pushed straight to main

## [0.38.3beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.38.3beta) — 2026-10-28

- 6 commits pushed straight to main

## [0.38.2beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.38.2beta) — 2026-10-27

- 19 commits pushed straight to main

## [0.38.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.38.1beta) — 2026-10-27

- 15 commits pushed straight to main

## [0.38.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.38.0beta) — 2026-10-27

- 11 commits pushed straight to main

## [0.37.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.37.0beta) — 2026-10-27

- 21 commits pushed straight to main

## [0.36.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.36.0beta) — 2026-10-27

- 23 commits pushed straight to main

## [0.35.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.35.1beta) — 2026-10-26

- 2 commits pushed straight to main

## [0.35.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.35.0beta) — 2026-10-26

- 29 commits pushed straight to main

## [0.34.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.34.1beta) — 2026-10-26

- 2 commits pushed straight to main

## [0.34.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.34.0beta) — 2026-10-26

- 25 commits pushed straight to main

## [0.33.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.33.1beta) — 2026-10-26

- 5 commits pushed straight to main

## [0.33.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.33.0beta) — 2026-10-25

- 8 commits pushed straight to main

## [0.32.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.32.0beta) — 2026-10-25

- 1 commit pushed straight to main

## [0.31.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.31.0beta) — 2026-10-25

- 2 commits pushed straight to main

## [0.30.6beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.30.6beta) — 2026-10-25

- 8 commits pushed straight to main

## [0.30.5beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.30.5beta) — 2026-10-25

- 1 commit pushed straight to main

## [0.30.4beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.30.4beta) — 2026-10-25

- 5 commits pushed straight to main

## [0.30.3beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.30.3beta) — 2026-10-25

- 2 commits pushed straight to main

## [0.30.2beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.30.2beta) — 2026-10-25

- 2 commits pushed straight to main

## [0.30.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.30.1beta) — 2026-10-25

- 5 commits pushed straight to main

## [0.30.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.30.0beta) — 2026-10-24

- 12 commits pushed straight to main

## [0.29.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.29.1beta) — 2026-10-24

- 5 commits pushed straight to main

## [0.29.0beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.29.0beta) — 2026-10-24

- 3 commits pushed straight to main

## [0.28.1beta](https://github.com/echovaultstudio/Siansa/releases/tag/v0.28.1beta) — 2026-10-24

- 99 commits pushed straight to main
