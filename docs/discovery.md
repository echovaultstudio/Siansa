# Discovery

## Song radio

Long-press a song (or use its menu) and pick **Start a mix**, or slide from the play button in now playing (see
[Playback](playback.md#now-playing-and-the-mini-player)). The radio always starts with its song, then
adds songs that sound like it and share its mood. When only a few are left, it finds more, so it keeps going.
Playing anything else ends the radio.

**Settings › Radios & mixes › Radio wandering** sets how far it may wander:

| Choice | What happens |
|---|---|
| **Stay close** | Every song is like the one the radio started from. |
| **A little** (default) | Mostly like the first song, with a few steps away from it. |
| **Wander** | Each new batch follows the song playing, so the radio drifts. |

When you start a radio you pick **Steady** (the wave) or **DJ** (the turntable) order; Keep playing below says what each does.

### Shape the mix

While a DJ radio plays, the queue (pull up from now playing) has **Shape your mix**, folded under its heading with
what's set beside it ("Steady build · some surprises"). Tap it to open two bars like the DJ mix bar on now playing:
slide or tap along the first for what the energy does over the songs not yet played, and along **Surprises** for how
far the mix may stray. Each change picks the radio's next songs again, starting
with the very next one: from the ones queued and a fresh lot that sound like the song playing, so a wind down finds
calmer songs and a euphoric mix brighter ones at a tempo that still mixes. The queue keeps as many songs as before;
ones that don't fit the new shape go, and may come back later. The song playing and anything you queued yourself
stay where they are.

| Shape | What happens |
|---|---|
| **Steady vibes** (default) | Stays where the mix started: the same tempo and energy, song after song. See [Steady stays steady](#steady-stays-steady). |
| **Steady build** | The energy climbs song by song, from where it is now to the top in about eight songs. |
| **Euphoric** | Climbs to a high in three songs and stays there, with the brightest (happy, party) songs first. |
| **Wind down** | The energy falls song by song, to the calmest in about eight songs. |

The next song only changes when another song fits the new shape better and still mixes: a build's next song is only
a step livelier, and a radio with few songs at a tempo that mixes may have just the one. **Surprises** lets the mix
stray further.

#### Steady stays steady

A Steady vibes mix is held to the song it **started** from, not only to the last song, so it can't walk step by step
from a mellow house song into drum and bass, or jump to techno at the same tempo. With Surprises at None, a song may
play only when all of these hold; Surprises loosens them a place at a time:

| | None | A few | Some | Lots |
|---|---|---|---|---|
| **Tempo**, of the first song's | within 6 % | within 10 % | within 15 %, or half or double time | anywhere |
| **Energy**, of the first song's | within a quarter | within about half | further still | free |
| **How alike it sounds** | among the most alike half | a little less | less still | any |
| **Kind of music** | the first song's | shares one with it | any | any |
| **Held to the first song** | strongly | loosely | a little | not at all: the mix can travel |
| **Keys** | count most | a lift up a tone counts too | as before | count least |

- **Tempo:** half and double time don't count as the same tempo below Some (a song at 87 doesn't follow one at 174).
- **Kind of music** comes from Siansa Beats' genres (0.32 or newer): house, techno, trance, drum and bass, breaks and
  bass, hard dance, calm electronic and dance pop within electronic music, and each other genre (rock, hip hop,
  jazz…) as its own. A song that is clearly techno doesn't follow a house song. A song Siansa Beats isn't sure
  about, or has no genres for, is never held back for it.
- **Energy** is how energetic a song is to a listener (Siansa Beats 0.35 works it out for every song, on a scale of
  about 1 to 9), compared song with song: with no surprises a steady mix stays within half a point of where it
  started. It isn't loudness: a loud but slow, heavy song counts as calmer than a hard one mastered quieter. With an
  older Siansa Beats, or AudioMuse-AI alone, it's AudioMuse-AI's energy (a song's loudness), compared only between
  songs the same one measured.

A steady mix picks from four times as many similar songs as it plays, to find enough that fit, and prefers a shorter
mix to one that drifts. When hardly any fit, the nearest ones are let in so the music carries on (another kind of
music last), and the radio may queue fewer songs at a time. Halftime drum and bass (drums that feel like 87, played
in a 174 set) counts as 174 in a mix when Siansa Beats hears it clearly as drum and bass or halftime; a slow song
with only a trace of it stays slow. The other shapes (Steady build, Euphoric, Wind down) are as they were.

**Surprises** (None, A few, Some, Lots): at None every song is a close neighbour of the last in tempo and how
alike it is; the further right, the further a song may stray, though its key still has to go with the last
song's, so a surprise still mixes in. It starts at None. The shape and surprises also apply to Keep playing in DJ
mode, and are kept in Settings; each new song radio starts again from Steady vibes with no surprises. Energy and brightness come from AudioMuse-AI's analysis; a song it hasn't analysed counts as
middling.

Without AudioMuse-AI, song radio uses Navidrome's similar songs, or Emby's Instant Mix.

## Keep playing

**Settings › Radios & mixes › When the queue ends**: what happens when the queue is about to end.

| Choice | What happens |
|---|---|
| **Off** | The music stops when the queue ends. |
| **Steady** | Adds songs like the last one in the queue, so the music carries on in the same style. |
| **DJ** | Adds songs like the last one and orders them for smooth changes: close tempo and keys that go together, shaped as the mix is (see above). Needs Siansa Beats. |

Songs already in the queue or played lately aren't added again. Songs Siansa added by itself are marked in the
queue.

## Similar songs, albums and artists

From a song's menu: **Similar songs** and **Similar albums**. On an artist's page: **Fans also like**.

On an album's page, the buttons under its details: **add to a playlist** (the plus) and **Download** on the left,
**Similar albums** (with AudioMuse-AI), **Shuffle** and **Play** on the right.

- **Play** carries on where you stopped, when you left the album part way: a ring round it shows how far you got,
  and the details line says **Continue from 8**. Press and hold Play to start from the first song instead.
- The **plus** adds the whole album to a playlist you pick.
- **Shuffle** plays the album shuffled. With AudioMuse-AI and Siansa Beats answering, **slide right** from it all the way onto Play for a **DJ mix
  of the album**: its own songs only, in an order that blends well (tempo and key, as a DJ mix orders them), played
  with DJ blends. It picks among the orders that blend best, so it doesn't always start from the same song. Nothing
  else is added, so the mix ends with the album.

While you slide, the cover says **DJ mix**, as in now playing, and the rest of the page dims. Once your finger is on
Play, it turns into a record and the phone clicks; letting go starts the mix. Play stays a turning record while the
album's mix plays.

The same slide works on every list of songs: a playlist, a radio, a mix, a card, Liked songs and the others (not
search results). A list of more than 300 songs mixes a random 300 of them.

On an artist's page, the buttons under the release count: the **heart** on the left makes the artist a favourite
(in your server too); **Radio**, **Shuffle** and **Play** on the right.

Under the buttons, the artist's release types are tabs: **All**, then each type they have (**Albums**, **EPs**,
**Singles**, **Live**, **DJ-mixes**…). Pick one to see only those releases; **All** (or tapping the chosen tab again)
shows the whole page: popular songs, every release, **Appears on**, **About** and **Fans also like**. An artist with
many types scrolls the tabs sideways. As you scroll down, the page slides softly over the artist's picture and the tabs
stay pinned under the title bar. The artist's name, large at the foot of the picture, moves up with the page and
then glides into the title bar, shrinking as it goes, to become the page's title; scroll back and it glides out again.

- **Similar songs** come from the best source your setup has: Navidrome's sonic similarity (if the AudioMuse-AI
  Navidrome plugin is installed), else AudioMuse-AI directly, else Navidrome's similar songs (or Emby's Instant
  Mix).
- **Similar albums and artists** come from the Siansa Analysis plugin: albums and artists that *sound* alike.
  "Fans also like" lists those first, then the artists your server suggests.
  An album you've only just added has none until its sound has been analysed: Siansa says so, and it works later.

## Song path

From a song's menu: **Song path…**. Pick a second song (or a mood) and Siansa builds a queue that moves smoothly
from the sound of one to the other. Choose how many songs. Play it, or save it as a playlist.

## Flow

The **Flow** tab is for vibes on the spot. **Describe what you want to hear** ("tired but hopeful", "rainy
Sunday", "need to focus") and Siansa makes a mix for it. Then **shape your sound**, on Flow before you make a
vibe or on the vibe's own page (the sliders button next to Pin to Home):

- **The sound pad**: one dot sets two things at once. Drag it up for more energy or down for calmer, right for
  faster or left for slower. On a vibe's page each small dot is one of its songs, so you see where the mix is now;
  the dot can go anywhere, also where the mix has no songs yet (an ambient mix drifting into something faster).
- **Niche ↔ Familiar**: from artists you rarely play to artists you play most.
- **Sounds**: a dial for each sound, turned towards or away from it. On a vibe's page they're the sounds the AI
  picked for it and **the mix's own sounds**: genres some of its songs are in and others aren't, as Siansa Beats
  hears them (an ambient mix isn't offered "more rock"). **Also in this mix** lists its other sounds: tap one to
  add a dial for it. Without Siansa Beats the dials are the AI's alone. On Flow, before there's a mix, they're a
  few of AudioMuse-AI's sound concepts (piano, acoustic, electronic…).

Letting go re-runs the search and re-ranks the songs without asking the AI again; **Reset** puts everything back
in the middle. Vibes you make are kept, and can be renamed or deleted.

## Search

**Search** looks in two ways at once: by name (artists, albums, songs) as you type, and by sound once you pause
("dreamy synth pop", "energetic electronic"), where AudioMuse-AI finds songs that sound like your words. Each kind
shows its top five; tap a section's title (**Artists**, **Albums**, **Songs**, **Sounds like …**) to see every
match on a page of its own, with Play and Shuffle at the top for songs.

Names are listed best first: the one called exactly what you typed, then names that start with it, then names with
a word that starts with it, and last the ones that only have the letters inside a word. Typing the artist too
("moderat gita") narrows it down.

Before you type, Search shows your genres to browse, the 30 with the most songs, then **All genres** for every one. In
Siansa with Siansa Beats, they're the genres Siansa Beats hears in your music, most songs first: a song is in every genre
it sounds like (its tags play no part there), and a genre's page lists all its songs. The list is saved on your phone, so it shows at once, and it's brought up to date in the background every
half hour. When Siansa Beats is slow or away, the saved list stays. A genre whose songs are all in libraries you hide
(Home's filter) isn't listed, and each genre counts only its songs in the libraries you show. A genre's page shows the
first three lines of what it is, with **Show more** for the rest. Home's **Your genres** shows the same list. Without
AudioMuse or Siansa Beats, or until Siansa Beats has heard enough songs, Search and Home show your library's genre
tags as always: Siansa Beats is never needed. The genre on an album's or an artist's page is still the one in its tags.

## Roll the dice

One song from your library you've never played, at random. "Never played" means your server has no play for it
and it isn't in Siansa's own history. With some music hidden or other users shown, it rolls only from what the
source filter shows.

Roll the dice is in Home's menu (the round button at the top of Home): tap it there, or slide along to it from the
button. While the song is found, the dice tumbles in the menu button and lands on a new face when the song starts.
With animations turned off on the phone, it only fades.

## Artists you might like

Artists you don't have yet, similar to the ones you play most. They come from your server's artist info (Last.fm
via Navidrome), and from Deezer when that's too few. Deezer also gives each one a photo, its number of albums and
fans; only the artist's name is sent to Deezer. Made once a day, after a library sync. Lines join each artist to
the ones you play that it's like; the thicker the line, the more you play that artist.

## Rediscover

Albums you used to play a lot but not lately. Built from your server's play counts, Siansa's own history and
Koito (if you scrobble there).
