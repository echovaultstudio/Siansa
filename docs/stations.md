# Your Stations: radio and podcasts

**Your Stations** keeps internet radio and podcasts in one place, under **Your Library**. Open it from the
**Stations** chip, or from the **Your Stations** card in the list. Siansa works just the same if you never use it.

## Radio from your server

With Navidrome (or another OpenSubsonic server), the radio stations your server keeps come first, under **From your
server**. In Navidrome you add them in its web page (**Radios**). Siansa only shows and plays them: change them on
the server.

Jellyfin and Emby don't have a list Siansa reads, so there you add your own stations (below).

## Adding your own

Tap **+** at the top of Your Stations and paste a link:

- **A radio stream**: the address that plays (for example `https://stream.example.com/live.mp3`).
- **A playlist that points at one**: `.m3u`, `.m3u8` or `.pls` links, as many stations offer under "Listen". Siansa
  opens the playlist and keeps the stream it lists, with its name when the playlist has one.
- **A podcast**: the link to its RSS feed. Siansa reads the feed for the podcast's name, picture and episodes.

You can give it a name; otherwise it's named from the playlist or feed, or after the stream's address. Siansa checks
the link before adding it, so a link that doesn't play says so straight away.

What you add is kept **on this phone only**, for the user in use. It never goes to your music server. To remove a
station or podcast, tap **⋮ › Remove** next to it (or **⋮ › Remove podcast** on its page).

## Listening

Tap a station to play it live in the player. While a station plays:

- There's no seek bar: it says **Live** instead.
- When the station sends what it's playing (most Icecast and Shoutcast stations do), that shows as the title, with
  the station's name under it.
- It isn't scrobbled, isn't added to your listening history and can't be liked: it isn't on your music server.
- Crossfades and beat-matched blends leave it out, and **Keep playing** doesn't add songs after it.

Tap a podcast to see its episodes, newest first, and tap one to play it. Episodes can be skipped through like songs,
but they're never crossfaded, scrobbled or kept in the song cache. A podcast's feed is read again when you open its
page (at most once an hour), or when you tap the refresh button.

## Good to know

- Stations and episodes need a connection; they can't be downloaded.
- Streams that play over HLS (`.m3u8` with segments) work. Very old Shoutcast servers that answer with `ICY 200 OK`
  instead of HTTP may not play.
- Podcasts from Audiobookshelf stay where they are, under the **Podcasts** chip (see
  [Audiobooks and podcasts](books.md)). Your Stations is for podcasts you add by their feed.
