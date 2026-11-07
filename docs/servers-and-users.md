# Servers, users and libraries

## Server types

| Type | What works |
|---|---|
| **Navidrome** | Everything. Siansa is tested on it most. |
| **Other OpenSubsonic servers** (Gonic, Airsonic, Ampache…) | Browsing, playback, playlists, favourites, scrobbling. Extras depend on the server. |
| **Jellyfin** (10.9 or later) and **Emby** | Browsing, playback, playlists, favourites, play history, lyrics. The smart features go through AudioMuse-AI; without it, similar songs use the server's Instant Mix. |

Siansa is tested against real Navidrome, Emby and Jellyfin servers, the newest and the oldest supported.

## Connecting

**Settings › Connections › Servers**: the address (with the port, `http://192.168.1.10:4533`, and a path if it has
one), user name and password, then **Test** and **Save**.

- **Behind a login page?** Tap **Add a proxy login** for the reverse proxy's own user name and password.
- **API key** (OpenSubsonic): enter it instead of the user name and password, if your server takes keys.
- Passwords and keys are kept encrypted on the phone, and never go into the log or backups.

## Several users

Each login on the phone is a "user", with its own library, history and settings, on the same server or another one.

- **Add a music server** for another login. It's added when you save, without a restart.
- **Switch to it** on another user's card (Siansa restarts), or **Remove** it (nothing changes on the server).
- **Name shown in Siansa**, or the pencil beside a user, shows a name like "Home" instead of the address.

## The source filter

**Sources**, in Home's round menu:

- **Show other users' music next to yours**, for users on the same server. Their music plays with their login.
- **Hide your own music**, to see only someone else's.
- **Pick which libraries to show** (Navidrome's music folders, Jellyfin's and Emby's libraries).
- **Only downloaded music**, for a flight.
- **Sync** one server, or **Sync all**.

Mixes, radios, the dice and Recently played only use what the filter shows. Under **Settings › Scrobbling and history
› Users**, turn off scrobbling for another user's music.

## Library sync

Siansa keeps a list of each user's library on the phone (never the music files), so browsing and search are instant.
It checks for changes when the app starts, and fetches nothing if your server hasn't scanned since. **Library sync ›
Sync now** runs one by hand. Playlists on your server open from the phone's copy and update while open.

## Tags

Siansa shows your music as your server reads its tags, so tag the way your server asks.

- **Several artists on a song**: tag each (Navidrome `ARTISTS`; Emby and Jellyfin separate them with `; `). Each gets a
  page.
- **Compilations**: `COMPILATION=1`. "Various Artists" counts too.
- **Album pictures on Emby and Jellyfin**: an album without its own picture shows its first song's.
- **Song details** (⋮ › **Show details**) shows every tag your server passes on: roles, genres, moods, BPM, ISRC and
  more.
- **An album's page** shows its type and year, its version ("Deluxe edition"), and its genres and record labels as
  tags: tap one for every album with it.
