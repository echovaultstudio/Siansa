# Releases

Every release of Siansa and the pull requests in it, newest first. CI updates this page after each
release (`scripts/release_prs.py`); what changed for the app's user is in `CHANGELOG.md`.

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

- 4 commits pushed straight to main
