# Playback

## Streaming quality

**Settings › Streaming & storage › Streaming quality**, set separately **On Wi-Fi** and **On mobile data**:

- **Original**: the file as it is on your server. Best on Wi-Fi.
- **Opus 96–320 kbit/s**: converted by your server (Navidrome's "opus" transcoding profile), to save data.
- **On Emby** the original file is always played and downloaded: Siansa can only have Navidrome and other
  Subsonic servers convert songs, so the quality choices don't change anything there.

Siansa always plays the quality you chose; it never switches by itself. Changes apply from the next song.

## Song cache and loading ahead

- **Song cache** (**Settings › Streaming & storage**, 2 GB by default): songs are kept on the phone as they play, so replays
  and skips start at once and survive a bad connection. Only original-quality streams are cached. **Off** keeps
  no song once it has played (the songs already cached are removed); it applies from the next song.
- **Load songs ahead** (**Settings › Streaming & storage**): while a song plays, Siansa loads the next ones, one at a time, so
  they start at once and keep playing if the connection drops. 2 on Wi-Fi and 1 on mobile data by default, up to
  5. Only original files are loaded ahead. This works with the song cache off too, for up to 3 songs: each is held
  only until it has played.
- **Image cache** (500 MB by default): covers and artist photos, so they show at once and offline. **Download all
  covers** fetches every cover in the background (Wi-Fi only by default).

## Downloads

**Settings › Streaming & storage › Downloads** (or the download button on an album or playlist): music you download stays on the phone
and plays without a connection. It isn't part of the song cache and is never removed by itself.

- **Download on Wi-Fi only** (on by default).
- **Download quality**: the original file, or Opus to save space.
- **Storage limit**: downloads stop when it's reached.
- **Only downloaded music** (in the source filter on Home) hides everything that isn't on the phone. When you're
  offline, Siansa offers to switch it on.

## Gapless playback

Songs play back to back with no gap when they're meant to (live albums, DJ mixes). Some Dolby (E-AC3) files
claim to be far longer than they are; Siansa uses your server's length for those and loads the next song ahead by
size, so they stay gapless too.

## Levelling (even loudness)

**Settings › Sound**:

- **Levelling**: **Track** plays every song at the same level. **Album** keeps an album's loud and quiet songs
  apart. **Off** plays songs as they are.
- **Where loudness comes from**: song tags (ReplayGain), or the **Siansa plugin**, which measures the audio itself
  (useful when your files have no ReplayGain tags). Songs the plugin hasn't measured yet use their tags.
- **Target**: how loud songs are brought to, by the plugin's measurement or by ReplayGain tags alone. −18 LUFS is
  ReplayGain's own level and leaves room for loud songs. −14 is louder, as many streaming services play: tagged songs play 4 dB louder
  than their tags say, but never louder than the file itself or past their peak, so quiet songs can't be raised to it.

Details: [How it works › Levelling](how-it-works.md#levelling).

## Crossfade

Same page. **Off**, **Fixed**, or **Smart**:

- **Smart** decides every change by itself: there's nothing to set. Fades are up to 20 seconds, and in DJ radio and DJ
  mixes, songs that keep a beat blend beat on beat (below).
- **Fixed** fades every change over the length you choose (1–30 seconds, 6 by default). With the Siansa plugin, in DJ
  radio and DJ mixes songs that keep a beat blend beat on beat over the **Blend length in DJ radio and DJ mixes** (4, 8 or
  16 bars), with the bass swapped halfway.
- An album playing in order stays gapless with either.

How Smart decides:

- Smart crossfades follow how songs end and start, measured by the Siansa plugin. A long fade-out starts blending
  early, a hard ending doesn't blend at all. A slow intro fades in slowly, a hard hit comes in at once.
- **Cold ends**: when a song ends hard (loud to its last second) and the next one starts hard (loud straight away),
  they don't overlap at all: the old song plays to its very last sound, then a short pause (0.4 seconds, 0.6 when
  their tempos are far apart), then the next song from its start, as a radio DJ plays a cold end. Both songs stay
  whole. When both songs keep a beat (dance music, a DJ radio), there's no pause: the next song starts right as the
  old one's last sound ends, so the music doesn't stop. In DJ radio and DJ mixes there's never a pause. Only when both edges are hard: a soft ending or a soft start
  blends as above. A beat-matched DJ blend still
  comes first.
- Most fades are shorter than 20 seconds, as the songs decide. The full 20 seconds is used for songs the plugin hasn't
  measured yet, and in DJ blends for a quiet intro without a beat.
- Silence at the end of a song is left out: the next song comes straight in. A long fade-out is cut once it's quiet
  (at most the crossfade length of it), so the next song comes in while it's still fading. Only what runs to the very
  end counts: a silence or a quiet passage mid-song never starts the next song, a soft ending that stays at one low
  level is played, and nothing is left out before three quarters of the song. The log says what was left out.
- **After the last beat**: when a song's beat stops well before its end (a beatless outro of 6 seconds or more,
  measured by Siansa plugin 0.5.0), the next song comes in one beat after the last, and the outro plays out under it
  to the end of its sound, its own fade-out and all; only the silence after it is left out. There's no beat to
  clash with, so it needs no beat-matching and works however far apart the tempos are. It can be longer than the
  crossfade length: up to half a minute of outro (then the last half minute of it), and never more than a third of
  either song. Only an outro that winds down (quiet for its last 6 seconds or more) gets that long blend: a beatless
  outro that stays loud is blended only over its last 6 seconds, so two songs never play loud together for long.
- **After a quiet ending** (an old song quiet for its last 4 seconds or more, or a beatless outro that winds down), the
  next song builds up slowly rather than coming in at full level: it reaches its full level at its first change (its
  first phrase change, where its bass comes in, or its first drop, from Siansa Beats' readings), within the quiet
  ending and at most half a minute; without those readings, over the whole quiet ending.
- Songs that run straight on within one album aren't faded, so they stay gapless.
- Radio stations and podcast episodes ([Your Stations](stations.md)) are never faded into or out of.
- Without the plugin's measurements, Smart uses the fixed length. When the song going out keeps a beat but the
  next song hasn't been measured, the blend is short instead, so the two beats don't clash for the whole length.

### Beat-matched blends

A song that winds down at its end (its last five seconds or more well under its own loudness) fades into the next
one over that stretch, up to 12 seconds, where the two beats can't be laid on each other and the keys don't clash.
Whether two voices would clash isn't known: nothing measures where a song is sung.
Where the end isn't soft and the two beats can't be laid on each other, the old song's last bar echoes out on its
own beat and the next drops in on its first downbeat, as a DJ does it (it needs Siansa Beats' grid at the end).

A blend is planned early enough for the longest one (32 bars); when there's no time left for the one picked, because
you moved near the end of the song, a quick one is made instead, or a short blend of whatever is left.

DJ radio, Keep playing in DJ mode and the DJ mix of an album or playlist leave out songs longer than 15 minutes (a
whole DJ set or a live recording in one file): they're mixes themselves. You can still start one from such a song.

In DJ playback only: DJ radio, songs Keep playing adds in DJ mode, and the DJ mix of an album or playlist (slide
Shuffle onto Play). An album or playlist playing on its own never beat-matches. Needs Siansa Beats, or the Siansa plugin
on AudioMuse-AI. Two songs that both keep a beat can clash at the changeover when their beats land at different moments,
like a bad DJ set. The Siansa plugin for AudioMuse measures the beat grid at each end of every song, so Siansa can
blend them the way a DJ mixes:

- A song that keeps a beat blends into the next one the way a DJ mixes. With plugin 0.4.0, which also finds the phrases (the 4 or 8 bars a song is built from), the
  blend goes **phrase on phrase**: it starts where a phrase of the song going out starts, the next song comes in on
  its first phrase, it lasts whole phrases (as many as the **Blend length** below allows: 8 bars gives one 8-bar
  phrase, 16 bars two, 4 bars half of one), and the song going out is let go
  at the end of a phrase: a short last part-phrase (less than half a phrase) isn't played, so both songs' phrases
  end together. Without phrases, the blend starts on a bar of the song going out, the next song starts on its first
  downbeat, and the blend is the Blend length in whole bars. Either way the next song plays a touch faster or slower (up
  to 8 %) so its beats keep time with the old song's, then eases back to its own speed over about 25
  seconds, in small steps too slight to hear. A shift of more than 4 % is kept to 8 bars, however long the Blend
  length, so the song is back at its own speed sooner.
- **Smart picks how to blend (with Siansa Beats' readings at both ends, and the keys from AudioMuse-AI):**
  - **Long blend**, 32 bars with the bass swapped in the middle: tempos within 2 %, keys that mix, both ends sure of
    their beat, and 8 bars or more without bass at one end.
  - **Drop swap**: the next song comes in 16 (or 8) bars before its first drop, so the drop lands where the old song
    goes; the old song keeps its bass until then. Not when the energy goes down.
  - **Filter fade**, 16 bars: the old song is swept out with a rising high-pass filter, when the next song starts 3 dB
    or more quieter, or the keys only half mix.
  - **Quick swap**, 4 bars: when the keys clash, a beat is under 90 % sure, or you moved in the song in the last
    20 seconds.
  - **Echo out**: when the tempos are too far apart to match, the two never play together. The old song's last bar
    echoes out and it stops on the bar line; the next song drops in at full level on its first downbeat as it stops,
    under the last faint repeats. Only into a song whose beat starts right away: one with an intro without a beat
    slides in under the old song instead.
  - **Bass swap**, 16 bars: the usual. Never the same technique twice running when another fits as well.
  - Fixed always uses the bass swap over the bars set. The log says which was picked and why ("smart: …").
- **Siansa Beats**: each end of a song is read from where its beat locks onto its grid (the first and
  last 4 bars it all keeps to). A blend stays inside that, and an end needs 80 % of its beats on the grid there to be
  beat-matched, and 80 % of them sitting on the grid. A track cut from a mixed CD carries the DJ's own changeovers at
  its ends: its blends are laid inside its lock and kept to 8 bars, and without a lock it gets a short blend.
  - Two songs are beat-matched only when Siansa Beats has a grid for both (or, without Siansa Beats, the plugin's
    readings for both): one with a grid and one without gets a short blend, never the two readings laid on each other.
  - A song whose beat comes in more than 8 seconds into it (an ambient intro) isn't beat-matched from there, which
    would skip its intro: it slides in under the old song from its start, and the old song is gone a bar before
    its beat comes in.
  - Siansa asks for a song's readings again after half an hour when Siansa Beats hadn't analysed it yet, so a
    song analysed since is blended on its grid.

In DJ playback, a blend where one end has no beat at all (a beatless
intro, an ambient outro) runs the whole crossfade length: there's nothing to clash with, so the next song comes in
under the old one as a DJ would, fading in over the first half. A song going out without a beat only gets that
when it winds down (quiet for its last 6 seconds or more); one that's still loud to its end, and two beatless songs
that are both loud where they meet, get a short blend of about 6 seconds, so the song isn't cut short.

Whatever the setting, two songs that both keep a beat but can't be matched (tempos more than 8 % apart, counting
double and half time as the same; a beat that isn't steady; a stream that can't jump to a point) get a short blend,
so their beats clash only for a moment. A beatless ending or start keeps the smart blend as before.

**Blend length** (4, 8 or 16 bars; 8 by default): how long a beat-matched blend is, counted in bars the way a DJ
counts it. It's apart from the crossfade length, so ordinary fades can stay short while DJ blends run long: 8 bars
is about 15 seconds at 128 BPM, 16 bars about 30. A blend starts on a bar, so it runs to the song's end a part-bar
longer, and it never takes more than a third of a song: into a short song it's laid over fewer bars, still beat
on beat.

**Bass swap** (always on, with no setting): over the first half of a blend the old song's bass is taken away (a filter sweeping
up to about 200 Hz) while the new song's, held back at first, is let in, as a DJ does with the EQ: two kick drums
never play at once, so a blend a few ms out of step is heard as a mix, not a flam.

Echo at the end and Keep the key are off for now and not shown in Settings: the blends play plain, and the next
song's pitch moves with its speed (by at most two thirds of a semitone at 4 %, like a DJ's pitch fader), then glides
back as it eases to its own speed.

Tempos are counted the way a DJ counts: a beat the plugin read at half time (drum and bass at 87, house at 63)
is doubled, so a blend's bars are real bars. Tracks of a continuous mix ("(Mixed)", "[Mixed]", a "DJ Mix" or
"Mixed by" album) already hold the neighbouring tracks blended in, so they're never beat-matched: into and out of
one the blend is short, and within its album the tracks run straight on, gapless.

A quiet build-up that
keeps a steady beat with clear bars is laid on the old song's beats too, so when its drums come in they're in
step.

Downbeats are the hardest thing to hear in a recording. When the plugin couldn't hear a song's
bars or phrases but the song was made on a grid (a whole number of BPM, steady from start to end), its downbeats and
8-bar phrases are assumed to count from its first beat, as DJ software assumes, and the blend is laid phrase on phrase
all the same; the log says "assumed". When that doesn't hold either, the songs are laid beat on beat, and a mix can
land a beat off. The log says how each blend was planned and
how far the next song was out of step. Behind the scenes, a second copy of the song going out plays silently for
the last 14 seconds before a blend and is brought in step with the one you hear (a jump or two, then a short nudge
of speed, each judged only once the phone reports a real position, which takes a few seconds), so it can take the
song over without a hitch; a blend may start up to a bar late rather than out of
step. Songs the plugin measured before it measured beats keep their loudness and
fades and get their beats when the plugin measures them again: see the **Beat grids** section on the plugin's settings page
([AudioMuse-AI](audiomuse.md)).

Crossfades only happen on this phone: on a TV or speaker, songs play back to back. They're not used with Repeat
one, or while the sleep timer lets a song finish.

## Equaliser

**Settings › Sound › Equaliser**: ten bands, presets, and a pre-amp. Songs stay as loud as with the equaliser
off; the pre-amp makes everything louder or quieter on top, and peaks that would distort are smoothed. Moving a
band saves it as your Custom preset. Not used while casting.

## Now playing and the mini player

Tap the mini player, or swipe up on it, to open now playing; swipe down on now playing, or tap ⌄, to close it.
Both follow your finger, and let go wherever you like: a hard swipe finishes quickly, a gentle one settles, and
a short pull springs back. The mini player fades away as the player rises and comes back as it goes. The book
player opens and closes the same way.

A **DJ mix** (from the play button, the DJ mix bar, Start a mix, an album's or playlist's Shuffle, or Keep playing in DJ
mode) is only offered when **Siansa Beats** answers with everything it needs: the beat grids its blends follow, and
each song's tempo, key and energy to put the songs in order (Siansa Beats 0.25 or newer, with the songs' sound taken
over on its page). The tempo is always the one Siansa Beats finds from the song's beats; AudioMuse-AI's is never used
for a DJ mix, and AudioMuse-AI on its own (or with the old Siansa plugin, which isn't kept up any more) doesn't make
one possible. Siansa checks at the start, every half hour after that, and every two minutes while Siansa Beats
doesn't answer. Until then the DJ choices are greyed out with the reason ("Needs Siansa Beats", "Needs Siansa Beats
0.25"), an album's Shuffle is a plain shuffle, and Keep playing in DJ mode adds songs as Steady does. Steady radio,
shuffle and crossfades work without it, with AudioMuse-AI or your server's similar songs as before.

Slide a finger from the play button to start something from the song playing: **up** for a DJ mix, **down** for a
Steady radio, **left** for its song path and **right** for songs that sound like it. Only the way you slide shows its
choice: its icon beside the button, and its name in the middle of the cover, which blurs behind it so your thumb never
hides it. Until you're far enough the name is faint and says "Slide a little further"; then it lights up in your
colour, says "Let go to start" and the phone clicks, and letting go there starts it. Everything else on the page dims while you
slide, and going down, Play on makes way. Slide back to the middle to change your mind. A tap still plays and pauses, and pressing
and holding still stops. What each way does can be changed, or the slides switched off, under **Settings › Now playing ›
Play button**. While a DJ mix plays, the play button has record grooves in its colour, with a glint that turns as it
plays.

Sliding **up** from the play button opens the **DJ mix bar** (when up is set to DJ mix): the seek bar's line opens
into a slim, see-through bar with five symbols, **Steady vibes**, **Steady build**, the mix in the middle, **Euphoric**
and **Wind down**, and closes back into the line when you let go. With no DJ mix playing, the middle is **Start**: let go
there for a DJ mix from the song playing, or on a shape to start one in that shape. While a DJ mix plays, the middle is
**Stop** (the songs left in the queue play on, without blends), and a shape changes the mix as **Shape your mix** in the
queue does: the next songs change; the mix's shape now has a ring in your colour. Slide up onto the bar, then along it:
the place under your finger lights up (Stop in white, the rest in your colour) and the phone clicks at each one, while
the cover shows its name, what it does and "Let go to start", "Let go to stop" or "Let go to reshape". Slide back down
to change your mind. An album's or playlist's DJ mix plays its own songs, so its bar has only **Stop**. Put the places
in your own order under **Settings › Now playing › DJ mix bar**.

Swipe the mini player left for the next song or right for the one before; the phone gives a short buzz as the song
changes, and when you press **Repeat** or **Add to playlist** in now playing (if touch vibration is on in your
phone's settings). With no song after this one, a swipe left springs back and the song plays on; with none before
it, a swipe right starts it again.

Pull up from now playing, or tap the queue button, for the queue. Tapping a song in an album, a playlist or any list
starts a new queue from that song: the songs before it in the list aren't queued (Shuffle plays the whole list). Tap a
song in the queue to jump to it. Swipe a song to the
left to take it out of the queue, behind a red strip whose bin gives a little shake; swipe it to the right to play it
next, straight after the song playing, behind an amber strip. Each gives a buzz. Its ⋮ menu has **Remove from queue**
and **Play next** too, and **Remove songs up to here**,
which clears every song before that one so it comes next (if one of them was playing, that song starts). **Save
queue as playlist** (the filled circle) keeps the whole queue.

Scroll down in now playing for **About the artist** and **About this song**. The whole page scrolls as one, in the
colours of the cover, and the cards take a deep shade of the cover's colour too (a dark grey for a black-and-white
cover). Scroll back up for the controls. A small slip of the thumb doesn't leave the cards
peeking in: now playing glides back unless you swipe up or scroll a quarter of the way down.

## Lyrics

Lyrics come from your server (Navidrome's lyrics, or the lyrics file or tags it reads). Synced lyrics light up line
by line, and word by word when your server sends word timings (Navidrome 0.63 or newer).

Open them with the speech bubble bottom left in now playing. While they're open, the same button, lit, sits in the
same corner: tap it to close the lyrics again.

## Sleep timer and Wind down

From now playing. Pick a time, or let the current song finish. **Wind down** swaps everything after the current
song for a path from that song to calmer music, long enough to fill the timer, so the music gets calmer and quieter
as you fall asleep. Without a timer, Wind down queues a number of calm songs (**Settings › Radios & mixes › Wind down**:
10, 20 or 30) and the music ends with them.

## Casting (Chromecast and DLNA)

The device button in now playing shows TVs and speakers on the same Wi-Fi: Chromecast and Google TV, and DLNA
ones (most smart TVs, Sonos, AV receivers). They play straight from your server: a Chromecast gets your music
lossless (see **Chromecast sound quality** below), a DLNA one Opus 320 kbit/s if it lists Ogg among the formats it
plays, otherwise MP3 320, unless you ask for the original files.
Stopping the cast brings the music back to the phone, paused. If the TV's Siansa screen closes by itself, the phone
joins the TV again straight away and the music carries on there (at most once in two minutes; if it keeps closing,
the music comes back to the phone). In **Play on**, each Chromecast shows its own name with
"Chromecast" under it ("Chromecast · speaker" or "Chromecast · speaker group" for speakers and groups); DLNA ones show
"DLNA" and their model.

On an Android TV or Google TV, [Siansa's TV app](tv.md) can also play from your server on its own, and send Dolby
and DTS music unchanged to your AV receiver (audio passthrough).

**Out of reach.** When the phone leaves the Wi-Fi a DLNA TV or speaker is on (you leave the house), the music comes
back to the phone, paused, within a few seconds. A device that stops answering on the same Wi-Fi is let go after
about 15 seconds. A short note says which device was lost. The device itself plays from your server, so it may
finish the song it has (and the next one, if it was handed that ahead) before it falls silent.

**DLNA without gaps.** Siansa hands a DLNA TV or speaker the next song while the current one plays, so devices
that support it go straight on to the next song. Devices that don't get the next song when the current one ends,
with a short gap. How well a device does this is up to the device: some speakers (the JBL Bar 800, for example)
pause for a few seconds between songs whichever way they get them.

**The TV screen.** Casting to a Chromecast or Google TV shows Siansa on the TV: a Siansa screen while it connects
(also when you pick the TV before playing anything: it then says "Connected. Pick a song on your phone.", and the
music goes to the TV when you start it), then the song's cover (Siansa's icon for a song without one), title, artist and album with a progress bar, and whether it's paused. The TV remote's
play/pause, next and previous buttons work, and so does the phone. When the queue ends, the TV says so and waits for
the next song. A TV that can't show Siansa's screen still plays: it gets Google's plain screen instead (always as MP3
320), by itself, and the next time you cast, Siansa tries its own screen again.

**Chromecast sound quality.** Tap the cog beside a Chromecast in **Play on** and pick **Sound quality**, for every
Chromecast:

- **Lossless** (the default): FLAC, MP3, Ogg, WAV and AAC files go to the TV as they are. Lossless files the TV
  can't play, such as Apple Lossless (ALAC) and other .m4a files, Monkey's Audio, WavPack, AIFF and DSD, are
  converted to FLAC by your server on the way. Other files (WMA, for example) go as Opus 320. If the TV can't play a
  song this way, it switches to Opus by itself, then to MP3 320 if Opus doesn't play either, and so do the songs of
  the same kind after it.
- **Opus**: every song is converted to Opus 320 kbit/s by your server. Uses less Wi-Fi.
- **MP3 320**: every song is converted to MP3 320 kbit/s by your server, for older TVs and speakers that can't play
  Opus.

It applies to music you start playing after you change it. Siansa asks your server for each song's file type just
before the songs go to the TV (the library doesn't keep it), which can take a moment for a long queue. Converting to
FLAC needs Navidrome (or another Subsonic server) with a **flac** transcoding, and Opus its **opus** transcoding (both
come with Navidrome); with Jellyfin and Emby, those files go as MP3 320.

**Adding the FLAC conversion to Navidrome.** Recent Navidrome versions come with it (**Settings › Transcodings**
lists **flac audio**). If yours doesn't, ALAC songs fall back to MP3 320 on the TV. To add it, sign in to Navidrome as
an admin, open **Settings › Transcodings**, choose **Create**, and fill in:

- **Name**: `flac audio`
- **Target format**: `flac`
- **Default bit rate**: `0` (FLAC has none)
- **Command**: `ffmpeg -ss %t -i %s -map 0:a:0 -v 0 -c:a flac -f flac -`

Navidrome only lets you edit transcodings when it runs with `ND_ENABLETRANSCODINGCONFIG=true`; set it, restart
Navidrome, add the transcoding, then set it back. Navidrome needs `ffmpeg` (its Docker image has it).

**Chromecast without gaps.** The queue stays on the phone: a Chromecast, Google TV or speaker with Chromecast built in
gets the song playing, the one before it and the next four, with more sent as it plays, and it's asked to load the
next song about 20 seconds before the current one ends, so it can go straight on. You can change or jump anywhere in
the queue on the phone as usual. Songs that go as MP3 can leave a very short pause between songs that run into each
other; FLAC usually doesn't.

**Original files on DLNA.** Tap the cog beside a DLNA TV or speaker in **Play on** and turn on **Send the original
file**. It then gets your music as it is (for example FLAC) instead of converted, whenever it says it plays that
format; other songs are still converted. Better sound, and no tiny gap between songs, but it uses more Wi-Fi. The
setting is kept for each device, shows which formats the device says it plays, and isn't offered when it only
plays MP3. It applies from the next song.

**Opus on DLNA.** Songs converted for a DLNA TV or speaker go as Opus 320 kbit/s (in Ogg) when the device lists Ogg or
Opus among the formats it plays, and as MP3 320 when it doesn't. If it doesn't start an Opus song after all, Siansa
sends that song again as MP3 320, and that device gets MP3 until you restart the app.

## Android Auto

Siansa shows up in Android Auto as a music app. On the car's screen you can browse and play:

- **Home**: your newest mix of each vibe, the mood radios (when AudioMuse-AI is set up) and **Recently played**,
  the albums, playlists, mixes and artists you last played on the phone.
- **Playlists**, **Albums** and **Artists**: each opens to its songs. An artist's songs are grouped by album.
- **Search**, by typing (when parked) or by voice: "Play Radiohead on Siansa". A name that matches an artist or
  album exactly plays that artist or album; otherwise the songs found play. "Play music on Siansa" plays your newest
  mix.

Picking a song plays its whole list from that song, the same as tapping it on the phone. Mood radios play straight
away. Albums, playlists and mixes played from the car show up in **Recently played** on the phone's Home too.

With more than 200 albums or artists, **Albums** and **Artists** open to a folder for each letter (**123** for names
that start with a number), so every one of them can be reached; a letter with more than 200 is in parts. Other very
long lists, such as a playlist of many hundred songs, show only their first few hundred in the car, as much as a car
takes in one list. Picking a song still plays the whole list from it; search for anything further down. Covers are
loaded by the phone and handed to the car, so the car never needs to reach your server.

**Siansa not in Android Auto?** Android Auto only lists apps installed from Google Play, unless you allow others.
Siansa from F-Droid, GitHub or Siansa Dev needs this once:

1. On the phone, open Android Auto's settings (**Settings › Connected devices › Connection preferences › Android
   Auto**, or search the phone's settings for "Android Auto").
2. Scroll down and tap **Version** about ten times, until it asks to turn on developer settings. Say **OK**.
3. Open the **⋮** menu at the top, then **Developer settings**, and turn on **Unknown sources**.
4. Unplug and plug the phone in again (or restart the car's screen). Siansa now shows up among the music apps.

**Trying it without a car.** Google's Desktop Head Unit shows a car screen on a computer:

1. In Android Auto's **⋮** menu (with developer settings on, as above), choose **Start head unit server**.
2. On the computer, in Android Studio, open **SDK Manager › SDK Tools** and install **Android Auto Desktop Head Unit
   Emulator**.
3. Connect the phone by USB with USB debugging on, then run `adb forward tcp:5277 tcp:5277` and start
   `desktop-head-unit` from the SDK's `extras/google/auto` folder.
4. On Samsung phones, turn off **Auto Blocker** while testing (it blocks USB commands), and back on afterwards.

Google's guide: [Test with the Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu).
