# Siansa's TV screen (Cast receiver)

`index.html` is the page a Chromecast or Google TV shows while Siansa casts to it (issue #114): a Siansa splash
when the phone connects, then the song's cover, title, artist, album and progress, with previous, play/pause and
next. It uses Google's Cast receiver library (CAF v3) and nothing else from outside: covers and songs come from
the music server, as with Google's own TV screen.

Until it's registered and the app is built with its app id, Siansa casts to Google's **Default Media Receiver**
instead: the same songs, with Google's plain screen ("Default Media Receiver", black until something plays).

## Turning it on

1. **Host the page.** Copy `index.html` to the website in `echovaultstudio/siansa-dev`, as
   `deploy/site/cast.html` (the site takes only flat files), so it's served at **https://siansa.app/cast.html**; the
   Caddyfile there gives it its own Content-Security-Policy (Google's Cast library, covers and songs from any server). It must be HTTPS; Cast devices
   won't load a receiver over plain HTTP. Copy it again whenever it changes here.
2. **Register it.** In the [Google Cast SDK Developer Console](https://cast.google.com/publish) (one-time fee):
   **Add new application › Custom Receiver**, name **Siansa**, receiver URL `https://siansa.app/cast.html`. The console
   gives an 8-character app id (for example `A1B2C3D4`).
3. **Add a test TV.** While the app is unpublished, only registered devices can load it (other TVs get Google's
   screen, see below): **Add new device**, with
   the TV's serial number (Google TV: **Settings › System › About › Serial number**). Wait about 15 minutes, then
   restart the TV.
4. **Build the app with the id.** Either set the repository variable `CAST_RECEIVER_ID` (GitHub › Settings ›
   Secrets and variables › Actions › Variables; it isn't secret: every app that casts carries its id) so CI's
   builds (Siansa Dev and releases) use it, or put it in `gradle.properties` as `siansa.castReceiverId=A1B2C3D4`
   so builds from source (F-Droid) use it too. Doing both is fine; the variable wins.
5. **Test.** Cast from the new build to the test TV: the Siansa splash, then the song screen; the remote's
   play/pause, next and previous work. The app's log says which screen it uses (`Cast`: "Casting to Siansa's own TV
   screen (receiver …)").
6. **Publish** the app in the Cast console so every TV can show it.

## TVs that can't show it

Siansa lists every Cast device, whatever its id, and picks the screen per TV when you cast (`CastScreens` in
`app/src/main/java/app/siansa/playback/Casting.kt`):

- **Offered Siansa's screen** (Cast lists the app for that TV): Siansa's screen.
- **Not offered** (the app is unpublished and the TV isn't a registered test device): Google's Default Media
  Receiver, automatically. So before step 6, the registered test TV shows Siansa's screen and every other TV
  Google's; nothing disappears from **Play on**.
- **Siansa's screen fails to start** (the page can't load, a launch error): the cast is tried again on the same TV
  with Google's screen, and that TV keeps Google's screen until the app restarts. Cancelling a cast isn't retried.

The log says which screen each TV got and why (`Cast`: "…: Google's standard TV screen: this TV isn't offered
Siansa's screen", "Siansa's TV screen didn't start on … trying again with Google's standard TV screen", "Casting to …
with …").

An id that isn't 8 hex digits is ignored (the log says so), and casting goes on with Google's screen.

## Remote control

The screen has no buttons: the cover, the song, a thin line for where it is, and Siansa in the corner. On the
remote, a press of **left** is the previous song and **right** the next; **holding** either winds back or forward
through the song (5 s steps, 10 s after two seconds held, 20 s after five). **OK** plays or pauses, and the media
keys do what they say. A press acts when the key is let go (or 600 ms after it, on TVs that don't say). A sign over the cover shows what a press did, and pause stays on the cover while paused. Previous goes back to
the start of the song after its first 5 seconds, else to the song before.

Google TV also turns left and right into "seek a few seconds" commands of its own. The page takes a relative seek
the same way (one on its own is a press, several in a row a hold), and drops any seek that comes with a left or
right key, so a press never jumps around in the
song. Seeks from the phone (always to a point in the song) work as before.

## Original quality

The app sends each song as MP3 320 plus the original file's address. The page plays the original (FLAC and so on)
and, if the TV can't, reloads the queue as MP3 and keeps to MP3 for the rest of the session.

## Trying changes

Edit `index.html`, copy it to the site, and cast again: the TV loads the page fresh each time a cast starts.
