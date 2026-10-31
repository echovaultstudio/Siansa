# Servers, users and libraries

## Server types

| Type | What works |
|---|---|
| **Navidrome** (OpenSubsonic) | Everything. Navidrome is what Siansa is tested on most. If your Navidrome has the AudioMuse-AI Navidrome plugin, "similar songs" and Song path can use Navidrome's own sonic similarity. |
| **Other OpenSubsonic servers** (Gonic, Airsonic, Ampache…) | Browsing, playback, playlists, favourites, scrobbling. Some extras depend on what the server supports (lyrics with timings, sonic similarity). |
| **Emby** | Browsing, playback, playlists, favourites, play history and "now playing" on Emby. The smart features go through AudioMuse-AI directly. Without AudioMuse, similar songs use Emby's Instant Mix. |

## Connecting

**Settings › Connections**, under **Servers**: open the server in use for its address, user name and password, with
**Test** and **Save**. The first time, Siansa asks for them when it starts.

- The address can include the port (`http://192.168.1.10:4533`) and a path if your server lives under one.
- **Server behind a login page?** If a reverse proxy asks for its own login before your server, tap **Add a proxy
  login** and enter that user name and password too.
- Passwords are stored encrypted with Android's keystore, and never written to the log or to backups.

## Several users

**Settings › Connections › Servers** holds every login on this phone, one card each. Each user has their own
library, play history and settings. A user can be on the same server or a different one, of either type.

- Open another user's card to change its server address, user name, password, proxy login, name and name in
  AudioMuse without switching to it; **Save** keeps the changes. **Switch to it** (Siansa restarts) saves them first;
  **Remove** takes it off the phone.
- **Add a music server** for another login (on the same server or a different one).
- **Name shown in Siansa**, in the card of the server in use, shows a name like "Home" instead of its address.
- **Remove** a user to take their library, history and settings off the phone. Nothing changes on the server.
- **Name a server** with the pencil next to a user, for example "Home" or "Kids". That user then shows the name
  instead of the address ("max on Home"), here and in the source filter. The name is each user's own, so two
  logins on one server can be told apart, and it only changes what Siansa shows, nothing on the server. Leave the
  name empty to show the address again.

## Showing several users' music together

The **filter button at the top of Home** opens the source filter. There you can:

- **Show other users' music next to yours**, for users on the **same server** as you (for example two logins on
  one Navidrome). Their albums, artists, songs and playlists appear in your library, and everything about their
  music (streaming, covers, lyrics, scrobbles) uses *their* login. Users on another server (another address, or
  Emby next to Navidrome) can't be shown together: switch to them in **Settings › Connections** instead.
  Siansa recognises your server by its songs, so a user who reaches it at another address (a domain instead
  of an IP) still counts as the same server; while it checks, that user shows "Checking whether it's your server…".
- **Hide your own music**, to see only another user's.
- **Pick which libraries to show** (Navidrome's music folders, Emby's libraries).
- **Only downloaded music**: hides everything that isn't on the phone. Handy on a flight.
- **Sync** one server, or **Sync all** to sync every music library that's shown and Audiobookshelf at once. Each
  server shows how its sync is going on one small line under its name, with a tick once it's done.

Every server set up in **Settings › Connections** is listed, Audiobookshelf too (with how many books and podcasts it
has after a sync). Audiobookshelf's books aren't hidden from here: Home's **Arrange** hides their rows.

Song radios, mixes, the dice and recently played only use what the filter shows.

**Playlists** follow the filter too: hiding your own music hides your own playlists. When you add songs to a
playlist, a playlist on your server only takes its own user's songs (Kids' playlists take Kids' songs), and a
playlist on this phone takes anyone's. Playlists that can't take the songs are greyed out.

**Scrobbling for other users**: in **Settings › Scrobbling › Users** you can turn a user off, so songs from their
library aren't sent anywhere while you listen as you.

## Playlists on your server

A playlist on your server opens straight away from the copy kept on the phone. While it's open, a turning cog at
the top shows Siansa asking your server whether it changed; new songs (for example from a playlist your server
rewrites every hour) show as soon as the answer comes. No library sync is needed.

## Library sync

Siansa keeps a copy of each user's library list on the phone (**Settings › Connections**, in the card of the server
in use: **Library sync**):

- It checks for changes when the app starts. If your server hasn't scanned since the last sync, nothing is
  fetched again.
- **Sync now** runs a sync by hand.
- Every library on the server is kept on the phone, so turning one on or off is instant.
- An album that's in more than one library shows once for each, with the library's name.
- Duplicate albums from different libraries are merged.

## Tags

Siansa shows your music as your server reads its tags, so tag your files the way your server asks
(Navidrome's tagging guidelines, or Emby's music naming guide).

- **Several artists on a song:** tag each one (Navidrome: `ARTISTS`; Emby: separate them with `; `). Every
  artist gets a page, and the song shows up there.
- **Compilations:** set the compilation tag (`COMPILATION=1`). An album by "Various Artists" (or "VA") counts as
  a compilation too, also on Emby, which has no such tag.
- **Song details** (⋮ › **Show details**) shows what the song's tags say, as far as your server passes them on:
  all its artists, album artist, composer, lyricist and other roles, genres, moods, release date, BPM, ISRC,
  explicit or clean, comment and MusicBrainz ID.
- **An album's page** shows its type and year under the artist ("Album · 2011"), with the year it first came out
  when this is a later release. At the foot of the page are its version (for example "Deluxe edition"), how many
  songs and how long, and its **genres** and **record labels** as tags: tap one for every album in your library with
  it. Navidrome has all of these; Emby has the genres and the label (its studio). The details need a connection:
  offline, the page shows what's on the phone. Labels are read during the library sync, so a label's page fills
  after the first sync on the new version.
