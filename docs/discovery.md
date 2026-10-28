# Discovery

## Song radio

Long-press a song (or use its menu) and pick **Start song radio**. The radio always starts with its song, then
adds songs that sound like it and share its mood. When only a few are left, it finds more, so it keeps going.
Playing anything else ends the radio.

**Settings › Playback › Song radio** sets how far it may wander:

| Choice | What happens |
|---|---|
| **Stay close** | Every song is like the one the radio started from. |
| **A little** (default) | Mostly like the first song, with a few steps away from it. |
| **Wander** | Each new batch follows the song playing, so the radio drifts. |

When you start a radio you can also pick **Steady** or **DJ** order (see Keep playing below).

Without AudioMuse-AI, song radio uses Navidrome's similar songs, or Emby's Instant Mix.

## Keep playing

**Settings › Playback › Keep playing**: what happens when the queue is about to end.

| Choice | What happens |
|---|---|
| **Off** | The music stops when the queue ends. |
| **Steady** | Adds songs like the last one in the queue, so the music carries on in the same style. |
| **DJ** | Adds songs like the last one and orders them for smooth changes: close tempo and keys that go together. Needs AudioMuse-AI. |

Songs already in the queue or played lately aren't added again. Songs Siansa added by itself are marked in the
queue.

## Similar songs, albums and artists

From a song's menu: **Similar songs** and **Similar albums**. On an artist's page: **Fans also like**.

- **Similar songs** come from the best source your setup has: Navidrome's sonic similarity (if the AudioMuse-AI
  Navidrome plugin is installed), else AudioMuse-AI directly, else Navidrome's similar songs (or Emby's Instant
  Mix).
- **Similar albums and artists** come from the Siansa Analysis plugin: albums and artists that *sound* alike.
  "Fans also like" lists those first, then the artists your server suggests.

## Song path

From a song's menu: **Song path…**. Pick a second song (or a mood) and Siansa builds a queue that moves smoothly
from the sound of one to the other. Choose how many songs. **Follow the lyrics** moves by what the songs are
about, not only how they sound (needs lyrics analysed in AudioMuse-AI). Play it, or save it as a playlist.

## Flow

The **Flow** tab is for vibes on the spot. **Describe what you want to hear** ("tired but hopeful", "rainy
Sunday", "need to focus") and Siansa makes a mix for it. Then **shape your sound** with sliders:

- **Energy** and **Tempo**: calmer and slower, or more energy and faster.
- **Familiar**: from artists you rarely play to artists you play most.
- **Sound dials**: up to a few of AudioMuse-AI's sound concepts (piano, acoustic, electronic…), each turned
  towards or away from that sound.

Moving a slider re-runs the search and re-ranks the songs without asking the AI again. Vibes you make are kept,
and can be renamed or deleted.

## Search

**Search** looks in two ways at once: by name (artists, albums, songs) as you type, and by sound once you pause
("dreamy synth pop", "energetic electronic"), where AudioMuse-AI finds songs that sound like your words. Each kind
shows its top five; tap a section's title (**Artists**, **Albums**, **Songs**, **Sounds like …**) to see every
match on a page of its own, with Play and Shuffle at the top for songs.

## Roll the dice

One song from your library you've never played, at random. "Never played" means your server has no play for it
and it isn't in Siansa's own history. With some music hidden or other users shown, it rolls only from what the
source filter shows.

## Artists you might like

Artists you don't have yet, similar to the ones you play most. They come from your server's artist info (Last.fm
via Navidrome), and from Deezer when that's too few. Deezer also gives each one a photo, its number of albums and
fans; only the artist's name is sent to Deezer. Made once a day, after a library sync. Lines join each artist to
the ones you play that it's like; the thicker the line, the more you play that artist.

## Rediscover

Albums you used to play a lot but not lately. Built from your server's play counts, Siansa's own history and
Koito (if you scrobble there).
