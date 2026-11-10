# Settings

The Settings tab, page by page. The start page has six groups, so each setting is where you'd look for it:

| Group | Pages |
|---|---|
| Sources | [Connections](#connections) |
| Listening | [Playback](#playback) · [Data](#data) |
| Home | [Notices and Cards](#notices-and-cards) · [Auto Generated Mixes](#auto-generated-mixes) |
| Look | [Style](#style) |
| Your listening | [Scrobbling and history](#scrobbling-and-history) |
| App | [Backup and restore](#backup-and-restore) · [Help](#help) · [About](#about) |

Settings pages are in sections that fold: each shows its heading and, while folded, what's set. They start open;
tap a heading to fold it, and it stays folded the next time you come to the page.

## Connections

Everything Siansa connects to, on one page, in two parts. Each server and service is a card: tap it, or its arrow,
to fold it open; they start folded. **Save** (at the top, or in any open card) keeps every change on the page, and
**Test** in each card uses what's typed, before you save.

**Servers**

- **Each music server login** (a "user": its own library, history and settings). The one in use has its address,
  user name, password or an API key (OpenSubsonic), an optional proxy login, its **Name shown in Siansa**, its **Server name in AudioMuse** and
  its **Library sync**. Another one has **Switch to it** (Siansa restarts) and **Remove**. See
  [Servers, users and libraries](servers-and-users.md).
- **Audiobookshelf**: address, API token or user name and password, and **Scan for new books**. See
  [Audiobooks and podcasts](books.md).
- **Add a music server**: another login on a server, or another server. It's filled in on the page and added when
  you save it, without restarting Siansa; switch to it from its card.

**Services**

- **AudioMuse-AI**: address, and an API token or user name and password. See [AudioMuse-AI](audiomuse.md).
- **Siansa Beats** (Siansa Dev): its address (port 8090) and, if it asks for one, its API key or password. With it
  set, each song's beats, loudness, fades and key, and the genres, come straight from Siansa Beats instead of the
  Siansa plugin on AudioMuse-AI; leave the address empty to keep using the plugin, or the songs' tags. **Test** says
  how many songs it has analysed. It reads Navidrome, so it's for a Navidrome server. See
  [AudioMuse-AI](audiomuse.md#siansa-beats-siansa-dev).
- **AI** (optional): which service writes mix titles and sound searches (OpenRouter, OpenAI, Anthropic, Google
  Gemini, Mistral, Groq, Ollama or another), its key, its model, and **AI titles and sound searches**. See
  [AI features](ai.md).

## Playback

How music plays.

| Setting | What it does |
|---|---|
| **Equaliser** | Ten bands, presets and a pre-amp. |
| **Crossfade and levelling** | Levelling (Track, Album, Off), where loudness comes from (tags or the Siansa plugin), the target (−18 or −14 LUFS), the crossfade (Off, Fixed or Smart). Smart has nothing to set: the phone decides every change. Fixed has its length (1–30 s) and, with the plugin, the blend length in bars for DJ radio and DJ mixes. At the bottom, **How mixing works** opens these docs on crossfades and blends. |
| **Streaming quality** | Original or Opus 96–320 kbit/s, on Wi-Fi and on mobile data. |
| **Song radio** | How far a radio may wander: Stay close, A little, Wander. |
| **Keep playing** | Off, Steady or DJ, when the queue is about to end. A DJ mix's shape and surprises are set in the queue while a DJ radio plays (see [Discovery](discovery.md#shape-the-mix)). |
| **Wind down** | How many calm songs Wind down plays without a sleep timer: 10, 20 or 30. |
| **Audiobooks and podcasts** | How far the book player's ↺ and ↻ skip, 10–90 s each. The Audiobookshelf server itself is under [Connections](#connections). |

See [Playback](playback.md), [Discovery](discovery.md) and [Audiobooks and podcasts](books.md).

## Data

What's kept on the phone, what it costs in data, and how well Siansa plays on without a connection.

| Setting | What it does |
|---|---|
| **Load songs ahead** | How many songs to load ahead, on Wi-Fi and on mobile data (0–5). |
| **Song cache** | Keeps songs as they play, up to the size chosen, with **Clear song cache**. **Off** keeps no song once it has played (from the next song); then up to 3 songs are loaded ahead, each held only until it plays. A new size applies after a restart. |
| **Downloads** | **Your downloads** (what's on the phone, opening the list to add or remove music), **Download on Wi-Fi only**, the download quality, the storage limit and **Remove all downloads**. See [Playback](playback.md#downloads). |
| **Covers on the phone** | **Download all covers** (every album, artist and playlist cover; it looks each artist up first, so their photo is downloaded even when Navidrome found it after your library was last read), and the **Image cache** (its size, a limit or none, and **Clear image cache**; a new size applies after a restart). |
| **Other data on this phone** | What the library database and the logs take. |

The downloads themselves are also in Your Library (the download button at the top), with **Add music**,
**Downloaded songs** and a link to these settings.

## Notices and Cards

The source filter (whose music shows, and **Only downloaded music**) and **Arrange Home** (the order of Home's
sections, which are hidden, their looks and the spacing) are in the menu behind the round button at the top of Home,
not here.

- **Notices**: the row of chips at the top of Home: show it or not, and each kind **Pinned** (always first, in the
  order you pin them), **Automatic** (the default: ordered by what needs you and what you tap) or **Off**. See
  [Notices](home-and-mixes.md#notices).
- **Cards**:
  - **Made for you**: cards of your own on Home's Made for you. Each gets new songs every day; tap a name to change it.
  - **Custom cards**: cards pinned to Home's **Your cards** section, each with a name, a look (song tiles, album row, banner, song list or play button) and its songs, from your library by tags (genre, years, artist, album, title words, favourites) or by a sound described to AudioMuse-AI. Tap a card to change its name, look or rules; the switch takes it off Home without removing it. See [Your cards](home-and-mixes.md#your-cards).

## Auto Generated Mixes

**Today's mix** (Auto update, and its own tuning over the vibe's), your vibes for each part of the day, and the
**Use the moment** panel. See [Home and your mixes](home-and-mixes.md#vibes-and-todays-mix).

## Style

- **Themes**: Auto (follows your phone, including its dark-mode schedule), Light or Dark; **Pure black** when dark
  (saves battery on OLED screens); a colour scheme, or **Your colour** (pick one colour and Siansa makes the rest,
  in light and dark), or colours from your wallpaper; and the accent colour for selected items and buttons. Siansa
  starts on its own **Siansa** colours, with the accent matching them (**Match theme**, Siansa's amber). **Pages and cards** sets the colour behind everything
  (pages, cards, menus and sheets): **Grey** to start with, **Warm grey**, **Blue grey**, or **Match
  theme** for the colour scheme's own tinted backgrounds. Album, artist and now playing pages keep their colours from
  the cover.
- **Dynamic colours** (off at first), one switch for the whole app: now playing's seek bar, Play button, the heart of
  a liked song and where it plays, and the Play button on every page of songs (albums, playlists, artists, radios, mixes,
  cards) and in its title bar, take a soft colour from the cover (an artist's picture; a radio's or mix's own drawn
  cover) instead of your theme's, fading from song to song. On those pages only Play takes it;
  Shuffle and the other buttons keep your theme's, and an artist's heart is red once they're a favourite. It's the colour that fills most of the
  cover, a little calmer than on the cover itself, so it sits with the cover rather than shouting over it: a sepia
  cover with a red light in it gets tan, not red, and a muted colour stays muted; a cover in black and white gets
  silver, and so does one with a few small spots of different colours. The colour is lightened or darkened as much as
  it needs to be to stand out from what's behind it, in the light theme too.
- **Now playing**: the lyrics, device and queue buttons, or the earlier look with the lyrics card peeking in. **Play
  button slides**: whether sliding from the play button does anything, what each way does (DJ mix, Steady radio,
  Song path, Sonic match, Start a mix… or Nothing), and **Record grooves** on the play button while a DJ mix plays.
- **Mini player** and **bottom bar**: hold a row and drag it to reorder; tap the eye to show or hide it.
- **Home**: how many shortcuts the top of Home shows, 2 to 8 (8 from the start). They're what you played last, newest
  first.
- **DJ Mix Bar**: the order of the places on the bar that opens over the seek bar when you slide up from the play
  button (Steady vibes, Steady build, Start or stop the mix, Euphoric, Wind down to start with): hold a row and drag it;
  top to bottom is left to right on the bar. See [Playback](playback.md).
- **Home Menu**: the order of the options under the round button at the top of Home (Roll the dice, Play on…,
  Sources, Arrange Home): hold a row and drag it. The mini player
  starts with **Play or pause**, **Next song** and **Add to playlist**.
- **Phone's top bar** (the clock and battery): **See-through** (the app shows behind it), **Black**, or **Hidden** for
  full screen. When it's hidden, swipe down from the top of the screen to see it for a moment; on phones with a camera hole at the top, pages stay just below it.
- **Animated covers**: when an album folder has an animated cover (cover.gif or cover.webp), it moves on the album
  page and in now playing. Lists and the Library show its first frame, read from the start of the file, so a big
  animation is only downloaded where it plays.

## Scrobbling and history

When a song counts, your server, Last.fm, ListenBrainz or Koito, and which users are sent. See
[Your listening](your-listening.md#scrobbling). **Time Machine**: whether it also lists songs you played that aren't
in your library now (the same switch is on the Time Machine page).

## Backup and restore

A backup holds your settings, vibes, mixes, play history, Home and library layout and more, **without passwords,
tokens or API keys**. **Save backup** writes a file you pick; **Restore** brings one back (Siansa then restarts).
**Automatic backup** saves one a day to a folder you choose and keeps the last 7.

## Help

**Debug logs** on or off, **Show the log**, and **Report a problem**, with the steps to a useful report. See
[Privacy, logs and reporting problems](privacy-and-logs.md).

## About

The version, **What's new** (the changes in the version you have; tap it to see every version's changes), and links
to the **open-source licences** of the libraries Siansa is built on (<https://siansa.app/licenses>), these docs and
the [privacy policy](https://siansa.app/privacy).
