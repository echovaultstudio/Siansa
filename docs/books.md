# Audiobooks and podcasts

Siansa plays the audiobooks and podcasts on your [Audiobookshelf](https://www.audiobookshelf.org/) server, next
to your music. Books and podcasts stay apart from music: they are never in mixes, radios, vibes, scrobbling or
Your listening.

## Setting it up

**Settings › Servers and services › Servers › Audiobookshelf**: the server's web address (for example `192.168.1.10:13378`) and
either an **API token** (in Audiobookshelf, under your user) or your username and password. **Test** checks the
login and says how many book and podcast libraries the user can see.

**Scan for new books**, under it, asks Audiobookshelf to look for new and changed books in its libraries, as the
Scan button on its own page does, and shows them once the server is done. Only an **admin** login can ask for a
scan; with another login the row says so.

There is **one Audiobookshelf login for the whole app**, like AudioMuse: it stays when you switch between
Navidrome or Emby users.

## On Home

With Audiobookshelf set up, Home gets a **Your books** carousel: a started book says **Continue**, with the
chapter and the time left, and ▶ on its cover carries on where you were. Books you haven't started say
**Up next** (the next in a series you're reading) or **New**. **Your podcasts** is a second carousel, with
**Pick up** on an episode you haven't finished. Each carousel has at most five cards; the rest are in Your Library.
Both are panels in **Arrange Home**.

## In Your Library

**Books** and **Podcasts** chips. Books sorts by **Recents** (last listened), **Title**, **Author**, **Series**
and **Recently added**. Podcasts has **Listening**, **All** and **Subscriptions**.

## In Search

A search shows **Books** and **Podcasts** rows under the music matches, by title, author or narrator. Tap one to
open its page.

## A book's page

Laid out like an album's page: the cover's colours fade down behind the cover (a portrait for a book, a square for a
podcast), then the title, the author, and a line with the narrator, series, genres, year and length (or the number of
episodes for a podcast). Audiobookshelf's description follows, folded to a few lines: tap it for the rest. Under it,
how far you are, with a bar and how much is left; on a podcast that's the episode you're on.

The row of buttons is the album page's: **download** (tap again to remove it from the phone) and **✓ mark finished**
(✕ takes it back; on a podcast it's the episode you're on) on the left, the big round **Play** on the right. It
continues where you were, and pauses while the book plays. Chapters or episodes follow: chapters you've heard muted,
the current one highlighted; tap any to play from there. On a podcast, the arrow beside an episode unfolds what that
episode is about (its guests, a short summary), as Audiobookshelf has it.

## The book player

Books play in a player like Now Playing: it slides up over the page, with the cover's colours behind it, and drags
down to close. There's no shuffle, repeat, queue or crossfade.

- The **chapter** is at the top; tap it to open the book's page.
- **↺ and ↻** skip back and forward: 10 s back and 30 s forward to start with, each 10 to 90 s in
  **Settings › Audiobooks and podcasts**. **⏮ ⏭** at the ends go to the previous and next chapter.
- The bar is the chapter's. How far you are in the whole book, and how long is left, is under the controls.
- **Speed** (bottom left) steps from 0.5× to 3× and is remembered per book. The **moon** (bottom right) stops the
  book at the end of the chapter. **Bookmark** (next to the title) marks the current time, kept on the server.
- The **⋮ menu** has the same, plus **Mark finished** and the book's page.
- The **mini player** shows ↺ and ↻ instead of previous and next while a book plays; tap it to open the player.
  Playing from a book's page starts the book without opening the player. There's only ever one mini player: for
  whatever you started last, music or a book. Pausing keeps it; stopping it (press and hold) leaves none, and the
  other one doesn't come back until you play it again.

Music and a book never play at once: starting one pauses the other.

## Where you are is kept on the server

Siansa tells Audiobookshelf where you are every 15 seconds and when you pause, so its own apps and the web show
the same place. Opening a book you listened to elsewhere continues from there.
