# Settings

The Settings tab, page by page. The start page has six groups, so each setting is where you'd look for it:

| Group | Pages |
|---|---|
| Sources | [Servers and services](#servers-and-services) |
| Listening | [Playback](#playback) · [Streaming and data](#streaming-and-data) · [Downloads](#downloads) · [Audiobooks and podcasts](#audiobooks-and-podcasts) |
| Home and mixes | [Home](#home) · [Mixes and vibes](#mixes-and-vibes) |
| Look | [Style](#style) |
| Your listening | [Scrobbling and history](#scrobbling-and-history) |
| App | [Backup and restore](#backup-and-restore) · [Help](#help) · [About](#about) |

Settings pages are in sections that fold: each shows its heading and, while folded, what's set. They start open;
tap a heading to fold it, and it stays folded the next time you come to the page.

## Servers and services

Everything Siansa connects to, on one page, in two parts. Each server and service is a card: tap it, or its arrow,
to fold it open; they start folded. **Save** (at the top, or in any open card) keeps every change on the page, and
**Test** in each card uses what's typed, before you save.

**Servers**

- **Each music server login** (a "user": its own library, history and settings). The one in use has its address,
  user name, password, an optional proxy login, its **Name shown in Siansa**, its **Server name in AudioMuse** and
  its **Library sync**. Another one has **Switch to it** (Siansa restarts) and **Remove**. See
  [Servers, users and libraries](servers-and-users.md).
- **Audiobookshelf**: address, API token or user name and password, and **Scan for new books**. See
  [Audiobooks and podcasts](books.md).
- **Add a music server**: another login on a server, or another server.

**Services**

- **AudioMuse-AI**: address, and an API token or user name and password. See [AudioMuse-AI](audiomuse.md).
- **AI** (optional): which service writes mix titles and sound searches (OpenRouter, OpenAI, Anthropic, Google
  Gemini, Mistral, Groq, Ollama or another), its key, its model, and **AI titles and sound searches**. See
  [AI features](ai.md).

## Playback

How music plays.

| Setting | What it does |
|---|---|
| **Equaliser** | Ten bands, presets and a pre-amp. |
| **Crossfade and levelling** | Levelling (Track, Album, Off), where loudness comes from (tags or the Siansa plugin), the target (−18 or −14 LUFS), the crossfade (Off, fixed or Smart, 1–30 s), and beat-matched blends (experimental: Off, DJ radio or Everywhere, their length in bars, and the echo at the end; with Smart and the plugin). |
| **Song radio** | How far a radio may wander: Stay close, A little, Wander. |
| **Keep playing** | Off, Steady or DJ, when the queue is about to end. A DJ mix's shape and surprises are set in the queue while a DJ radio plays (see [Discovery](discovery.md#shape-the-mix)). |
| **Wind down** | How many calm songs Wind down plays without a sleep timer: 10, 20 or 30. |

See [Playback](playback.md) and [Discovery](discovery.md).

## Streaming and data

What decides how much data Siansa uses and how well it plays on without a connection.

| Setting | What it does |
|---|---|
| **Streaming quality** | Original or Opus 96–320 kbit/s, on Wi-Fi and on mobile data. |
| **Load songs ahead** | How many songs to load ahead, on Wi-Fi and on mobile data (0–5). |
| **Song cache** | Keeps songs as they play, up to the size chosen, with **Clear song cache**. **Off** keeps no song once it has played (from the next song); then up to 3 songs are loaded ahead, each held only until it plays. A new size applies after a restart. |
| **Other data on this phone** | What the library database and the logs take. |

## Downloads

Your downloads (**Add music**, **Downloaded songs**, **Remove all downloads**) and how songs download: Wi-Fi only,
quality, storage limit. Under them, the covers kept on the phone: **Download all covers**, and the **Image cache**
(its size, a limit or none, and **Clear image cache**; a new size applies after a restart). See
[Playback](playback.md#downloads).

## Audiobooks and podcasts

**Skip buttons**: how far the book player's ↺ and ↻ skip, 10–90 s each. **Audiobookshelf** opens the server's card
in Servers and services. See [Audiobooks and podcasts](books.md).

## Home

- **Source filter**: whose music shows, everywhere in the app, and **Only downloaded music**.
- **Arrange Home**: the order of Home's sections, which are hidden, each section's look, and the spacing.

  Both open the same panel as the button at the top of Home.
- **Notices**: the row of chips at the top of Home: show it or not, and each kind **Pinned** (always first, in the
  order you pin them), **Automatic** (the default: ordered by what needs you and what you tap) or **Off**. See
  [Notices](home-and-mixes.md#notices).
- **Cards**:
  - **Made for you**: cards of your own on Home's Made for you. Each gets new songs every day; tap a name to change it.
  - **Custom cards**: cards pinned to Home's **Your cards** section, each with a name, a look (song tiles, album row, banner, song list or play button) and its songs, from your library by tags (genre, years, artist, album, title words, favourites) or by a sound described to AudioMuse-AI. Tap a card to change its name, look or rules; the switch takes it off Home without removing it. See [Your cards](home-and-mixes.md#your-cards).

## Mixes and vibes

**Today's mix** (Auto update, and its own tuning over the vibe's), your vibes for each part of the day, and the
**Use the moment** panel. See [Home and your mixes](home-and-mixes.md#vibes-and-todays-mix).

## Style

- **Themes**: Auto (follows your phone, including its dark-mode schedule), Light or Dark; **Pure black** when dark
  (saves battery on OLED screens); a colour scheme, or **Your colour** (pick one colour and Siansa makes the rest,
  in light and dark), or colours from your wallpaper; and the accent colour for selected items and buttons. Siansa
  starts on **Mono** colours with the **Amber** accent. **Pages and cards** sets the colour behind everything
  (pages, cards, menus and sheets): **Grey** to start with, like Spotify, **Warm grey**, **Blue grey**, or **Match
  theme** for the colour scheme's own tinted backgrounds. Album, artist and now playing pages keep their colours from
  the cover.
- **Now playing**: the lyrics, device and queue buttons, or the earlier look with the lyrics card peeking in.
- **Mini player** and **bottom bar**: hold a row and drag it to reorder; tap the eye to show or hide it.
- **Phone's top bar** (the clock and battery): **See-through** (the app shows behind it), **Black**, or **Hidden** for
  full screen. When it's hidden, swipe down from the top of the screen to see it for a moment; on phones with a camera hole at the top, pages stay just below it.
- **Animated covers**: when an album folder has an animated cover (cover.gif or cover.webp), it moves on the album
  page and in now playing.

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
