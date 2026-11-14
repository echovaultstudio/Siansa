# Settings

The Settings tab. Its start page puts you first: a search box, who's signed in on what, your services with a dot
each (green when it answers, red when it doesn't), then twelve pages in three groups of four:

| Group | Pages |
|---|---|
| Playing | [Sound](#sound) · [Radios & mixes](#radios--mixes) · [Streaming & storage](#streaming--storage) · [Audiobooks & podcasts](#audiobooks--podcasts) |
| Home & look | [Home page](#home-page) · [Theme](#theme) · [Now playing](#now-playing) · [Bottom bar](#bottom-bar) |
| Listening & app | [Scrobbling & history](#scrobbling--history) · [Backup & restore](#backup--restore) · [Help & feedback](#help--feedback) · [About](#about) |

Above the groups: tap your name to open [Connections](#connections) (your servers and logins), and **Manage** beside
the services to open [Services](#services). The services shown are the ones that are on; one switched off isn't.

**Find a setting**: the box at the top. It matches a setting's name, its page and the words people use for it, so
"battery" finds Pure black and "loud" finds Levelling. Tap a result and its page opens with that section open and lit
for a moment.

Settings pages are in sections that fold: each shows its heading and, while folded, what's set. They start open;
tap a heading to fold it, and it stays folded the next time you come to the page.

## Services

What Siansa talks to besides your music server, each with a switch. A service switched off is left alone and shown
nowhere: no card on Settings, no notice, no option that needs it; its login stays for when you switch it back on.

- **For the best Siansa**, at the top: a line per service, ✓ when it's on and answers, ✕ when it doesn't (with what
  the check said), – when it's not needed (with Siansa Beats on, AudioMuse-AI covers nothing Beats doesn't, and the
  line offers **Turn AudioMuse-AI off**), ○ when it's off or optional.
- **Siansa Beats** (Siansa Dev): beats and phrases for DJ blends, loudness and keys, genres by sound, similar albums
  and artists, and where your listening is kept.
- **AudioMuse-AI**: the sound of each song, similar music and the search by words, when there's no Siansa Beats.
- **Audiobookshelf**: audiobooks and podcasts.
- **Last.fm** and **ListenBrainz / Koito**: your listening, scrobbled as you go (their switches are the same as on
  [Scrobbling & history](#scrobbling--history)).
- **AI**: mix titles and sound searches in your own words.

Each card shows whether the service answers (checked when the page opens, with the same calls as Connections' Test
buttons, at most once a minute), what it gives Siansa, and **Set up**, which opens its card on Connections.

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

## Sound

How music sounds.

| Setting | What it does |
|---|---|
| **Equaliser** | Ten bands, presets and a pre-amp, on its own page. |
| **Where loudness and fades come from** | The songs' tags, or Siansa Beats (the Siansa plugin on AudioMuse-AI before it). |
| **Levelling** | Track, Album or Off, and the target (−18 or −14 LUFS). |
| **Crossfade** | Off, Fixed or Smart. Smart has nothing to set: the phone decides every change. Fixed has its length (1–30 s) and, with Siansa Beats, the blend length in bars for DJ radio and DJ mixes. At the bottom, **How mixing works** opens these docs on crossfades and blends. |
| **Dolby songs on your receiver** | On the TV app only, with audio passthrough on. |

See [Playback](playback.md).

## Radios & mixes

| Setting | What it does |
|---|---|
| **Radio wandering** | How far a song radio may wander: Stay close, A little, Wander. |
| **When the queue ends** | Stop, Steady or DJ, when the queue is about to end. A DJ mix's shape and surprises are set in the queue while a DJ radio plays (see [Discovery](discovery.md#shape-the-mix)). |
| **Wind down** | How many calm songs Wind down plays without a sleep timer: 10, 20 or 30. |
| **Today's mix and vibes** | Opens the daily mixes' page: **Today's mix** (Auto update, and its own tuning over the vibe's), your vibes for each part of the day, and the **Use the moment** panel. See [Home and your mixes](home-and-mixes.md#vibes-and-todays-mix). |

## Streaming & storage

What you hear over the network, what it costs in data, and how well Siansa plays on without a connection.

| Setting | What it does |
|---|---|
| **Streaming quality** | Original or Opus 96–320 kbit/s, on Wi-Fi and on mobile data, each from a drop-down. |
| **Load songs ahead** | How many songs to load ahead, on Wi-Fi and on mobile data (0–5). |
| **Song cache** | Keeps songs as they play, up to the size chosen, with **Clear song cache**. **Off** keeps no song once it has played (from the next song); then up to 3 songs are loaded ahead, each held only until it plays. A new size applies after a restart. |
| **Downloads** | **Your downloads** (what's on the phone, opening the list to add or remove music), **Download on Wi-Fi only**, the download quality (a drop-down), the storage limit and **Remove all downloads**. See [Playback](playback.md#downloads). |
| **Covers on the phone** | **Download all covers** (every album, artist, playlist, song and disc cover, at the small size lists use and the big one now playing shows; it looks each artist up first, so their photo is downloaded even when Navidrome found it after your library was last read; with a limit on the image cache, give it room for them all), and the **Image cache** (its size on a slider, 100 MB to 5 GB or no limit, and **Clear image cache**; a new size applies after a restart). |
| **Other data on this phone** | What the library database and the logs take. |

The downloads themselves are also in Your Library (the download button at the top), with **Add music**,
**Downloaded songs** and a link to these settings.

## Audiobooks & podcasts

How far the book player's ↺ and ↻ skip, 10–90 s each. The Audiobookshelf server itself is under
[Services](#services), and **Scan for new books** is on Home's Books section. See [Audiobooks and podcasts](books.md).

## Home page

The source filter (whose music shows, and **Only downloaded music**) and **Arrange Home** (the order of Home's
sections, which are hidden, their looks and the spacing) are in the menu behind the round button at the top of Home,
not here.

- **Top of Home**: how many shortcuts the top of Home shows, 2 to 8 (8 from the start; they're what you played last,
  newest first); **Notices**, the row of chips at the top of Home, shown or not, and each kind **Pinned** (always
  first, in the order you pin them), **Automatic** (the default: ordered by what needs you and what you tap) or
  **Off**. See [Notices](home-and-mixes.md#notices).
- **Home menu**: the order of the options along Home's menu, the pill the round button opens (Roll the dice, Play
  on…, Sources, Arrange Home): hold a row and drag it.
- **Cards**: opens the cards' page: **Made for you** (cards of your own on Home's Made for you; each gets new songs
  every day; tap a name to change it) and **Custom cards** (cards pinned to Home's **Your cards** section, each with a
  name, a look and its songs, from your library by tags or by a sound described to AudioMuse-AI; the switch takes it
  off Home without removing it). See [Your cards](home-and-mixes.md#your-cards).

## Theme

- **Light or dark and colours**: Auto (follows your phone, including its dark-mode schedule), Light or Dark; **Pure black** when dark
  (saves battery on OLED screens); a colour scheme, or **Your colour** (pick one colour and Siansa makes the rest,
  in light and dark), or colours from your wallpaper; and the accent colour for selected items and buttons. Siansa
  starts on its own **Siansa** colours, with the accent matching them (**Match theme**, Siansa's amber). **Pages and cards** sets the colour behind everything
  (pages, cards, menus and sheets): **Grey** to start with, **Warm grey**, **Blue grey**, or **Match
  theme** for the colour scheme's own tinted backgrounds. Album, artist and now playing pages keep their colours from
  the cover.
- **Dynamic colours** (off at first), one switch for the whole app: now playing's seek bar, Play button, the heart of
  a liked song and where it plays, and the Play button on every page of songs (albums, playlists, artists, radios, mixes,
  cards, books and podcasts) and in its title bar, and the book player's seek bar and buttons, take a soft colour from
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

## Now playing

- **Buttons**: the lyrics, device and queue buttons, or the earlier look with the lyrics card peeking in.
- **Play button**: whether sliding from the play button does anything, what each way does (DJ mix, Steady radio,
  Song path, Sonic match, Start a mix… or Nothing), and **Record grooves** on the play button while a DJ mix plays.
- **DJ mix bar**: the order of the places on the bar that opens over the seek bar when you slide up from the play
  button (Steady vibes, Steady build, Start or stop the mix, Euphoric, Wind down to start with): hold a row and drag it;
  top to bottom is left to right on the bar. See [Playback](playback.md).
- **Mini player**: its buttons, in order: hold a row and drag it; tap the eye to show or hide it. It starts with
  **Play or pause**, **Next song** and **Add to playlist**.

## Bottom bar

The tabs, in their order on the bar: hold a row and drag it; tap the eye to show or hide it. Settings always stays.

## Scrobbling & history

When a song counts, your server, Siansa Beats (when it's set up), Last.fm, ListenBrainz or Koito, and which users are sent. See
[Your listening](your-listening.md#scrobbling). **Time Machine**: whether it also lists songs you played that aren't
in your library now (the same switch is on the Time Machine page).

## Backup & restore

A backup holds your settings, vibes, mixes, play history, Home and library layout and more, **without passwords,
tokens or API keys**. **Save backup** writes a file you pick; **Restore** brings one back (Siansa then restarts).
**Automatic backup** saves one a day to a folder you choose and keeps the last 7.

## Help & feedback

**Debug logs** on or off, **Show the log**, and **Report a problem**, with the steps to a useful report. See
[Privacy, logs and reporting problems](privacy-and-logs.md).

**Bad blend button** shows or hides a **Bad blend** button on now playing. While on, it appears over the cover
during a DJ blend and for a minute after it; tap it when a change between two songs sounded wrong, and it's written
to the log and sent to Siansa Beats' list of bad blends. Off unless you turn it on (on in Siansa Dev).

## About

The version, **What's new** (the changes in the version you have; tap it to see every version's changes), and links
to the **open-source licences** of the libraries Siansa is built on (<https://siansa.app/licenses>), these docs and
the [privacy policy](https://siansa.app/privacy).
