# Settings

The Settings tab. Its start page puts you first: a search box, who's signed in on what, your services with a dot
each (green when it answers, red when it doesn't), then twelve pages in four groups:

| Group | Pages |
|---|---|
| Music | [Playback](#playback) · [Mixes](#mixes) · [Discovery](#discovery) · [Scrobbling](#scrobbling) |
| Phone | [Storage](#storage) · [Backup](#backup) |
| Look | [Home](#home) · [Theme](#theme) · [Player](#player) · [Navigation](#navigation) |
| Support | [Help](#help) · [About](#about) |

Above the groups: tap your name to open [Your server](#your-server) (your servers and logins), tap a service to go
straight to its own page, and **All services** beside the services to open [Services](#services), the list of them
all. The services shown are the ones that are on; one switched off isn't.

**Find a setting**: the box at the top. It matches a setting's name, its page and the words people use for it, so
"battery" finds Pure black and "loud" finds Levelling. Tap a result and its page opens with that section open and lit
for a moment.

Settings pages are in sections that fold: each shows its heading and, while folded, what's set. They start open;
tap a heading to fold it, and it stays folded the next time you come to the page.

Each setting is in one place, on the page for what it does: what a service does is set there, and the service's own
page links to it.

## Your server

The card at the top opens your music servers and logins. Each server login is a card: tap it, or its arrow, to fold
it open. **Save** keeps every change on the page, and **Test** uses what's typed, before you save.

- **Each music server login** (a "user": its own library, history and settings). The one in use has its address,
  user name, password or an API key (OpenSubsonic), an optional proxy login, its **Name shown in Siansa**, its **Server name in AudioMuse-AI** and
  its **Library sync**. Another one has **Switch to it** (Siansa restarts) and **Remove**. See
  [Servers, users and libraries](servers-and-users.md).
- **Add a music server**: another login on a server, or another server. It's filled in on the page and added when
  you save it, without restarting Siansa; switch to it from its card.

## Services

**All services** on the Services card opens the list: each service with its dot and what it's doing, and each opens
its own page. A service's page has its switch (off: left alone and shown nowhere, its login kept), how to reach it
(address, key, **Test**, **Save**) and **Used for**: what it does, each line opening the page where that's set.

- **Siansa Beats**: its address (port 8090) and, if it asks for one, its API key or password. Used for loudness and DJ
  blends (Playback), Home's mixes (Mixes), genres, similar music and moods (Discovery) and your
  listens (Scrobbling). See [Siansa Beats](siansa-beats.md).
- **AudioMuse-AI**: address, and an API token or user name and password. Not listed while Siansa Beats is on: Beats
  does all it did. See [AudioMuse-AI](audiomuse.md).
- **Audiobookshelf**: address, API token or user name and password, and **Scan for new books**. See
  [Audiobooks and podcasts](books.md).
- **Last.fm** and **ListenBrainz / Koito**: set on [Scrobbling](#scrobbling).
- **AI** (optional): which service writes Today's mix, Flow's words and the mood radios (OpenRouter, OpenAI,
  Anthropic, Google Gemini, Mistral, Groq, Ollama or another), its key and its model. Not listed while Siansa Beats'
  own AI does this (Siansa Beats 0.57 or newer, with AI set up on its page). See [AI features](ai.md).

**For the best Siansa**, under the list: a line per service, ✓ when it's on and answers, ✕ when it doesn't (with
what the check said), – when it's not needed, ○ when it's off or optional.

## Playback

How music sounds and plays.

| Setting | What it does |
|---|---|
| **Streaming quality** | Original or Opus 96–320 kbit/s, on Wi-Fi and on mobile data, each from a drop-down. |
| **Untouched playback** | Plays each song at its own sample rate and bit depth, straight to the phone's sound output. Crossfades, DJ blends and the equaliser are off while it's on. See [Playback](playback.md#untouched-playback). |
| **Equaliser** | Ten bands, presets and a pre-amp, on its own page. |
| **Where loudness and fades come from** | The songs' tags, or Siansa Beats (the Siansa plugin on AudioMuse-AI before it). |
| **Levelling** | Track, Album or Off, and the target (−18 or −14 LUFS). |
| **Crossfade & DJ blends** | Off, Fixed or Smart, and **DJ blends** on Siansa Beats' beat grids. Smart has nothing to set: the phone decides every change. Fixed has its length (1–30 s) and, with Siansa Beats, the blend length in bars for DJ radio and DJ mixes. At the bottom, **How mixing works** opens these docs on crossfades and blends. |
| **Book skips** | How far the book player's ↺ and ↻ skip, 10–90 s each. |
| **Dolby songs on your receiver** | On the TV app only, with audio passthrough on. |

See [Playback](playback.md).

## Mixes

| Setting | What it does |
|---|---|
| **Home's mixes** | Who makes For now, Made for you and More of you: **Siansa Beats** (from your listening) or **This phone** (your vibes and Today's mix). While Siansa Beats makes them, the phone makes none of its own. With Siansa Beats: **Mix settings** (what goes into them). With this phone: **Today's mix and vibes**. See [Home and your mixes](home-and-mixes.md#mixes-from-siansa-beats). |
| **Radio wandering** | How far a song radio may wander: Stay close, A little, Wander. |
| **When the queue ends** | Stop, Steady or DJ, when the queue is about to end. A DJ mix's shape and surprises are set in the queue while a DJ radio plays (see [Discovery](discovery.md#shape-the-mix)). |
| **Wind down** | How many calm songs Wind down plays without a sleep timer: 10, 20 or 30. |

## Discovery

With Siansa Beats set up: **Genres** by sound (off: your songs' tags), **Similar music & search by words**, and
**Moods in Flow** (vocals, a woman's or a man's voice, how songs feel). Without it, what AudioMuse-AI does.

## Storage

What you hear over the network, what it costs in data, and how well Siansa plays on without a connection.

| Setting | What it does |
|---|---|
| **Load songs ahead** | How many songs to load ahead, on Wi-Fi and on mobile data (0–5). |
| **Song cache** | Keeps songs as they play, up to the size chosen, with **Clear song cache**. **Off** keeps no song once it has played (from the next song); then up to 3 songs are loaded ahead, each held only until it plays. A new size applies after a restart. |
| **Downloads** | **Your downloads** (what's on the phone, opening the list to add or remove music), **Download on Wi-Fi only**, the download quality (a drop-down), the storage limit and **Remove all downloads**. See [Playback](playback.md#downloads). |
| **Covers on the phone** | **Download all covers** (every album, artist, playlist, song and disc cover, at the small size lists use and the big one now playing shows, and albums' animated covers whole while **Animated covers** is on, which can be 100 MB each; it looks each artist up first, also artists who are only credited on songs (a compilation, a featured singer), and at the end asks again every half minute, for up to ten minutes, for photos the server is still fetching (Navidrome gets them from Apple Music or Deezer a few at a time; those it has none for yet are counted apart, and the next run asks again), so their photo is downloaded even when Navidrome found it after your library was last read; with a limit on the image cache, give it room for them all), and the **Image cache** (its size on a slider, 100 MB to 5 GB or no limit, and **Clear image cache**; a new size applies after a restart). |
| **Library on the phone** | What the library database takes. The log is under [Help](#help). |

The downloads themselves are also in Your Library (the download button at the top), with **Add music**,
**Downloaded songs** and a link to these settings.

## Home

The source filter (whose music shows, and **Only downloaded music**) is in the menu behind the round button at the
top of Home.

- **Top of Home**: how many shortcuts the top of Home shows, 2 to 8 (8 from the start; they're what you played last,
  newest first); **Notices**, the row of chips at the top of Home, shown or not, and each kind **Pinned** (always
  first, in the order you pin them), **Automatic** (the default: ordered by what needs you and what you tap) or
  **Off**; and **Your own notices**. See [Notices](home-and-mixes.md#notices).
- **Arrange Home**: the order of Home's rows, which are hidden, their looks and titles, the cards' colours and the
  spacing; the same as Arrange Home in the menu behind Home's round button.
- **Home menu**: the order of the options along Home's menu, the pill the round button opens (Roll the dice, Play
  on…, Sources, Arrange Home): hold a row and drag it.
- **Your cards**: opens the cards' page: **Made for you** (cards of your own on Home's Made for you; each gets new songs
  every day; tap a name to change it) and **Custom cards** (cards pinned to Home's **Your cards** section, each with a
  name, a look and its songs, from your library by tags or by a sound described to AudioMuse-AI; the switch takes it
  off Home without removing it). See [Your cards](home-and-mixes.md#your-cards).

## Theme

- **Light or dark and colours**: Auto (follows your phone, including its dark-mode schedule), Light or Dark; **Pure black** when dark
  (saves battery on OLED screens); a colour scheme, or **Your colour** (pick one colour and Siansa makes the rest,
  in light and dark; white, black or a grey makes white, black or grey buttons, as near to it as can still be seen on
  each page), or colours from your wallpaper; and the accent colour for selected items and buttons. Siansa
  starts on its own **Siansa** colours, with the accent matching them (**Match theme**, Siansa's amber). **Pages and cards** sets the colour behind everything
  (pages, cards, menus and sheets): **Grey** to start with, **Warm grey**, **Blue grey**, or **Match
  theme** for the colour scheme's own tinted backgrounds. Album, artist and now playing pages keep their colours from
  the cover.
- **Dynamic colours** (off at first), one switch for the whole app: now playing's seek bar, Play button, the heart of
  a liked song and where it plays, and the Play button on every page of songs (albums, playlists, artists, radios, mixes,
  cards, books and podcasts), and the book player's seek bar and buttons, take a soft colour from
  the cover (an artist's picture; a radio's or mix's own drawn cover; a book's or podcast's) instead of your theme's
  (the same colour the page's top is painted in; each cover's colours are kept on the phone, so a page seen before, or
  shown in a list, opens in its colours and they don't change while it's open),
  fading from song to song. On those pages only Play takes it;
  Shuffle and the other buttons keep your theme's, and an artist's heart is red once they're a favourite. It's the colour that fills most of the
  cover, a little calmer than on the cover itself, so it sits with the cover rather than shouting over it, and on a page
  with a coloured header it stays in the header's family (a khaki header never gets a blue Play from the same picture): a sepia
  cover with a red light in it gets tan, not red, and a muted colour stays muted; a cover in black and white gets
  silver, and so does one with a few small spots of different colours. The colour is lightened or darkened as much as
  it needs to be to stand out from what's behind it, in the light theme too.
- **Status bar** (the clock and battery): **See-through** (the app shows behind it), **Black**, or **Hidden** for
  full screen. When it's hidden, swipe down from the top of the screen to see it for a moment; on phones with a camera hole at the top, pages stay just below it.
- **Animated covers**: when an album folder has an animated cover (cover.gif or cover.webp), it moves on the album
  page and in now playing. Lists and the Library show its first frame, read from the start of the file, so a big
  animation is only downloaded where it plays.

## Player

- **Buttons**: the lyrics, device and queue buttons, or the earlier look with the lyrics card peeking in.
- **About this song**: **How the song is playing** (on at first) shows the file and what really plays side by side in
  the lower part of now playing. See [Playback](playback.md#how-the-song-is-playing).
- **Star ratings** (off at first): rate songs from one to five stars. Pick where the stars show: **Stars under the
  title**, **Stars in About this song** (the lower part of now playing), or both; one of the two always stays on. Only
  with a server that keeps ratings (Navidrome and other Subsonic servers): on Emby and Jellyfin this section isn't
  there. See [Playback](playback.md#star-ratings).
- **Play button**: whether sliding from the play button does anything, what each way does (DJ mix, Steady radio,
  Song path, Sonic match, Start a mix… or Nothing), and **Record grooves** on the play button while a DJ mix plays.
- **DJ mix bar**: the order of the places on the bar that opens over the seek bar when you slide up from the play
  button (Steady vibes, Steady build, Start or stop the mix, Euphoric, Wind down to start with): hold a row and drag it;
  top to bottom is left to right on the bar. See [Playback](playback.md).
- **Mini player**: its buttons, in order: hold a row and drag it; tap the eye to show or hide it. It starts with
  **Play or pause**, **Next song** and **Add to playlist**.

## Navigation

The tabs, in their order on the bar: hold a row and drag it; tap the eye to show or hide it. Settings always stays.

## Scrobbling

When a song counts, your server, Siansa Beats (when it's set up), Last.fm, ListenBrainz or Koito, and which users are sent. See
[Your listening](your-listening.md#scrobbling). **Time Machine**: whether it also lists songs you played that aren't
in your library now (the same switch is on the Time Machine page).

## Backup

A backup holds your settings, vibes, mixes, play history, Home and library layout and more, **without passwords,
tokens or API keys**. **Save backup** writes a file you pick; **Restore** brings one back (Siansa then restarts).
**Automatic backup** saves one a day to a folder you choose and keeps the last 7.

## Help

**Report a problem**, with the steps to a useful report. Under **Debugging**: **Debug logs** on or off and **Show
the log** (with its size). See [Privacy, logs and reporting problems](privacy-and-logs.md).

**Bad blend button** shows or hides a **Bad blend** button on now playing. While on, it appears over the cover
during a DJ blend and for a minute after it; tap it when a change between two songs sounded wrong, and it's written
to the log and sent to Siansa Beats' list of bad blends. Off unless you turn it on (on in Siansa Dev).

## About

The version, **What's new** (the changes in the version you have; tap it to see every version's changes), and links
to the **open-source licences** of the libraries Siansa is built on (<https://siansa.app/licenses>), these docs and
the [privacy policy](https://siansa.app/privacy).
