# Your Stations: radio and podcasts

**Your Stations** is internet radio and podcasts by their RSS feed, right in **Your Library**: radio stations
under the **Stations** chip, podcasts under the **Podcasts** chip (with Audiobookshelf's, if you use it). They show
in the full list too, and searching Your Library finds them. Siansa works just the same if you never use it.

## Radio from your server

With Navidrome (or another OpenSubsonic server), the radio stations your server keeps are under **Stations**, marked
**From your server**. In Navidrome you add them in its web page (**Radios**). Siansa shows and plays them: change
them on the server (only their picture can be set in Siansa, see below).

Jellyfin and Emby don't have a list Siansa reads, so there you add your own stations (below).

## Adding your own

Tap **+** at the top of Your Library, then **Add station** or **Add podcast**, and paste a link:

- **A radio stream**: the address that plays (for example `https://stream.example.com/live.mp3`).
- **A playlist that points at one**: `.m3u`, `.m3u8` or `.pls` links, as many stations offer under "Listen". Siansa
  opens the playlist and keeps the stream it lists, with its name when the playlist has one.
- **A podcast**: the link to its RSS feed. Siansa reads the feed for the podcast's name, picture and episodes.

You can give it a name; otherwise it's named from the playlist or feed, or after the stream's address. Siansa checks
the link before adding it, so a link that doesn't play says so straight away.

What you add is kept **on this phone only**, for the user in use. It never goes to your music server. To remove a
station or podcast, tap **⋮ › Remove** next to it in Your Library (or **⋮ › Remove podcast** on its page).

## Pictures

Each radio station gets its logo by itself, looked up once and kept with the station, so it isn't fetched again:

1. **Radio Browser** ([radio-browser.info](https://www.radio-browser.info)), a free, open list of radio stations,
   is asked for the station by its address, then by its exact name.
2. If that has no logo, the **station's website** (from your server, or from Radio Browser) is read for the logo
   it names.

If neither has one, the station keeps its coloured radio tile. The picture shows in Your Library, in the player, in
the notification and on the lock screen. Without a connection nothing is looked up; it's tried again later.

## Editing a station

Tap **⋮ › Edit** next to a station to change it:

- **Name** and **Link**: a new link is checked as when adding.
- **Picture link**: the address of a picture to use instead. Leave it empty to have Siansa look for the logo again
  (also a way to refresh it).

For your server's stations only the picture can be set, and it's kept on this phone: change their name and link
on the server.

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
- Podcasts from Audiobookshelf and those you add by their feed are both under the **Podcasts** chip. Audiobookshelf's
  are kept on that server (see [Audiobooks and podcasts](books.md)); the ones you add stay on this phone.
