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
3. **Add a test TV.** While the app is unpublished, only registered devices can load it: **Add new device**, with
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

An id that isn't 8 hex digits is ignored (the log says so), and casting goes on with Google's screen.

## Remote control

On Google TV the remote's play/pause, next and previous go through the TV's media controls, which the Cast
library turns into the same commands the phone sends; the page allows queue next and previous for them. When the
TV hands key presses to the page itself, left and right move between the three buttons and OK presses one.
Previous on the screen goes back to the start of the song after its first 5 seconds, else to the song before.

## Original quality

The app sends each song as MP3 320 plus the original file's address. The page plays the original (FLAC and so on)
and, if the TV can't, reloads the queue as MP3 and keeps to MP3 for the rest of the session.

## Trying changes

Edit `index.html`, copy it to the site, and cast again: the TV loads the page fresh each time a cast starts.
