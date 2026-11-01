# How it works

How Siansa works things out, for the curious. Everything here runs on your phone, except where it asks your own
servers.

## Your taste

Many features start from a picture of what you like, built **on the phone** from:

- Siansa's own history: what you played, at what time of day, and what you skipped;
- Koito or ListenBrainz top artists and songs this month (if you scrobble there);
- Last.fm top artists and songs this month (if you're signed in);
- your server's most played albums.

From that, each artist gets a weight from 0 to 1 (how much you play them lately), and songs you skipped more than
once lately, or in the last few days, are marked as skipped. Only small summaries ever leave the phone: a few
artist names in the AI prompt, never your history.

## Today's mix and vibes

1. **The search.** With AI on, the AI turns the vibe's guidance (and the moment, if allowed) into a title, a sound
   search and up to three sound concepts. Without AI, the vibe's fallback sound search is used, nudged by the
   moment.
2. **AudioMuse-AI** runs that search over your library by sound (text-to-audio search), and its "alchemy" finds a
   second pool: the vibe's sound, pulled away from your most played songs.
3. **Picking** the day's songs:
   - your most played songs and songs you skip never come in; songs played in the last few days only when nothing
     else is left;
   - the **Surprise** share comes from the second pool, from artists you don't play much;
   - artists you play a lot, and artists in yesterday's mix, weigh less;
   - each artist appears only a few times, and a little randomness makes sure two days never come out the same.
4. **Matching.** AudioMuse-AI's songs are found in your library by id, or by title and artist when they're known
   under another server. Songs not in the music the source filter shows are left out.
5. **From the library.** When fewer than half of AudioMuse-AI's songs are in the music shown, the rest of the mix
   comes from the music shown: songs whose genre is one of the words of the vibe's search,
   then genres you usually play at this hour, then the rest, with the same rules for recent, skipped and most
   played songs, and two or three songs per artist.

A mix is made once per vibe per day and kept. **Make a mix now** makes it again.

## Song radio

The first song always plays first. AudioMuse-AI finds songs that sound like it and share its mood. Whenever only a
few songs are left, more are found:

- **Stay close**: always like the first song.
- **A little**: every other batch looks like the song playing, but keeps only songs that are also like the first
  song; when too few are left, it goes back to the first song.
- **Wander**: each batch is like the song playing, so the radio drifts.

Songs already heard on the radio aren't added again.

## DJ order

DJ mode (Keep playing, song radio) orders songs for smooth changes. From the last song, each next song is the best
change from the one before:

- **how alike** it is to the starting song (AudioMuse-AI's ranking),
- **tempo**: the closer the better; half and double time count as the same (a 70 BPM song mixes into a 140 BPM
  one),
- **key**: on the Camelot wheel DJs use, the same key is best, then its neighbours,
- and a **new artist** is better than the same one again.

When AudioMuse-AI doesn't know a song's tempo or key, that part counts as neutral.

## Levelling

Each song gets one volume, from **one** source, never two at once:

- With **Track** levelling, the song's loudness is brought towards the target: −18 LUFS (close to what ReplayGain
  aims for) or −14 LUFS.
- The loudness is the Siansa plugin's measurement of the audio (EBU R128, in LUFS) when that's the chosen source
  and the song is measured; otherwise the song's ReplayGain tags. The tags bring a song to −18 LUFS, so with the −14
  target their gain is raised by 4 dB.
- **Album** levelling keeps the album's tags when the song has them, since the plugin measures songs one by one.
- Like ReplayGain, levelling works through the player's volume, so it can only **turn songs down**. That's why −14
  can't raise quiet songs to it.

## Smart crossfades

The Siansa plugin measures how each song starts and ends (the MixRamp idea from the MPD player): for a range of
levels, how long after the start the song first gets that loud, and how long before the end it's last that loud.

For each change of song, the **overlap** is the current song's quiet tail plus the time the next song takes to get
going, up to your crossfade length:

- a long fade-out starts blending early; a hard ending doesn't blend at all;
- a slow intro fades in slowly; a hard hit comes in at once;
- the next track of the same album (same or next disc) isn't faded, so live albums and DJ mixes stay gapless.

The current song fades out over the whole overlap and the next one fades in, both "equal power" so the loudness
doesn't dip in the middle. Without measurements, the fixed length is used, except after a song that ends on a steady
beat: then the blend is short, since the next song's beat can't be followed. Siansa fetches the measurements of the
next few songs whenever the queue changes, so a radio or a reshaped DJ mix has them before its first blend.

## When a song counts (scrobbling)

A play counts once you've heard the chosen share of the song (50% by default, from 10% to 100%) **or four
minutes**, whichever comes first. Songs of 30 seconds or less never count, and neither does anything heard for
under 10 seconds. Counted plays wait in a queue on the phone and are sent when there's a connection.

## Song length

Some files carry a wrong length in their header (some Dolby E-AC3 .m4a files claim over 30 minutes). When the
player's length is more than 1.5 times, or less than half, what your server says, the server's length is used for
the seek bar and the time left. While such a song plays, the next song is loaded ahead by size rather than by time,
so playback stays gapless.

## Roll the dice

A random song you've never played: your server has no play for it, and it isn't in Siansa's history. Siansa asks
your server for random songs, a batch at a time, until it finds one. With libraries or users hidden, or other
users shown, it rolls from the synced library instead, so it only picks from what's shown.

## Flow sliders

Moving a slider doesn't ask the AI again. It re-runs AudioMuse-AI's search and re-ranks the results:

- **Energy** and **tempo** move songs up or down by AudioMuse-AI's energy and tempo for each song, compared with
  the rest of the results.
- **Familiar** moves songs by how often you play their artist.
- **Sound dials** steer the search towards (+) or away from (−) a sound concept, with AudioMuse-AI's steering
  weights 1, 2, 3, 5 and 10.

## Time Machine counts

Plays from Koito and Last.fm are merged by artist and title. The same listen usually reached both, so a song's
count is the **higher** of the two, never the sum.

## Artists you might like

For each artist you play (most played first), similar artists come from your server (Last.fm through Navidrome),
and from Deezer when that's too few. An artist similar to several you like comes first; then the more you play the
artist it's like, and the more similar it is, the higher. Artists you already have are left out.
