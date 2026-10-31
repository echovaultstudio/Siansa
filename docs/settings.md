# Settings

The Settings tab, page by page.

## Connections

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

| Setting | What it does |
|---|---|
| **Equaliser** | Ten bands, presets and a pre-amp. |
| **Crossfade and levelling** | Levelling (Track, Album, Off), where loudness comes from (tags or the Siansa plugin), the target (−18 or −14 LUFS), the crossfade (Off, fixed or Smart, 1–12 s), and beat-matched blends (experimental: Off, DJ radio or Everywhere; with Smart and the plugin). |
| **Streaming quality** | Original or Opus 96–320 kbit/s, on Wi-Fi and on mobile data. |
| **Song radio** | How far a radio may wander: Stay close, A little, Wander. |
| **Keep playing** | Off, Steady or DJ, when the queue is about to end. A DJ mix's shape and surprises are set in the queue while a DJ radio plays (see [Discovery](discovery.md#shape-the-mix)). |
| **Wind down** | How many calm songs Wind down plays without a sleep timer: 10, 20 or 30. |
| **Load songs ahead** | How many songs to load ahead, on Wi-Fi and on mobile data (0–5). |
| **Books** | Only with Audiobookshelf: how far the book player's ↺ and ↻ skip, 10–90 s each. |

See [Playback](playback.md) and [Discovery](discovery.md).

## Style

- **Themes**: Auto (follows your phone, including its dark-mode schedule), Light or Dark; **Pure black** when dark
  (saves battery on OLED screens); a colour scheme, or **Your colour** (pick one colour and Siansa makes the rest,
  in light and dark), or colours from your wallpaper; and the colour of selected items. Album, artist and now
  playing pages keep their colours from the cover.
- **Now playing**: the lyrics, device and queue buttons, or the earlier look with the lyrics card peeking in.
- **Mini player** and **bottom bar**: hold a row and drag it to reorder; tap the eye to show or hide it.
- **Phone's top bar** (the clock and battery): **See-through** (the app shows behind it), **Black**, or **Hidden** for
  full screen. When it's hidden, swipe down from the top of the screen to see it for a moment; on phones with a camera hole at the top, pages stay just below it.

## Scrobbling

When a song counts, your server, Last.fm, ListenBrainz or Koito, and which users are sent. See
[Your listening](your-listening.md#scrobbling).

## Daily Vibe settings

Your vibes for each part of the day, and the **Use the moment** panel. See
[Home and your mixes](home-and-mixes.md#vibes-and-todays-mix).

## Cards

**Made for you**: cards of your own on Home's Made for you. Each gets new songs every day; tap a name to change it.

**Custom cards**: cards pinned to Home's **Your cards** section, each with a name, a look (song tiles, album row,
banner, song list or play button) and its songs: from your library by tags (genre, years, artist, album, title
words, favourites) or by a sound described to AudioMuse-AI. Tap a card to change its name, look or rules; the switch
takes it off Home without removing it. See [Your cards](home-and-mixes.md#your-cards).

## Downloads

Your downloads, and their settings: Wi-Fi only, quality, storage limit. See [Playback](playback.md#downloads).

## Storage

How much space downloads, the song cache, the image cache, the library database and the logs use, with their
limits and **Clear** buttons. **Animated covers**: when an album folder has an animated cover (cover.gif or
cover.webp), it moves on the album page and in now playing. New cache sizes apply after a restart. The song cache
can also be switched **Off**, so no song is kept once it has played; that applies from the next song. Songs loaded
ahead (Settings › Playback, up to 3 with the cache off) are held only until they play.

## Backup

A backup holds your settings, vibes, mixes, play history, Home and library layout and more, **without passwords,
tokens or API keys**. **Save backup** writes a file you pick; **Restore** brings one back (Siansa then restarts).
**Automatic backup** saves one a day to a folder you choose and keeps the last 7.

## About

The version, and links to the **open-source licences** of the libraries Siansa is built on
(<https://siansa.app/licenses>), these docs and the [privacy policy](https://siansa.app/privacy).

## Help

At the bottom of Settings: **Debug logs** on or off, **Show the log**, and **Report a problem**, with the steps to
a useful report. See [Privacy, logs and reporting problems](privacy-and-logs.md).

## What's new

The changes in the version you have. Tap it to see every version's changes.
