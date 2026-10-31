# API

What Siansa asks of each server. Useful if you run a proxy, read your server's logs, or build something next to
Siansa.

## Your music server

### Navidrome and other OpenSubsonic servers

The Subsonic API (`/rest/…`, version 1.16.1, JSON), with OpenSubsonic extensions where the server has them:

| Purpose | Calls |
|---|---|
| Connect and check | `ping`, `getOpenSubsonicExtensions`, `getMusicFolders`, `getScanStatus` |
| Library sync and browsing | `getArtists`, `getArtist`, `getArtistInfo2`, `getAlbum`, `getAlbumList2`, `getSong`, `search3`, `getTopSongs`, `getRandomSongs`, `getStarred2` |
| Playback | `stream` (original with `format=raw`, or Opus with `format=opus&maxBitRate=…`), `getCoverArt` |
| Lyrics | `getLyricsBySongId` (with word timings when the server has them), `getLyrics` |
| Favourites and plays | `star`, `unstar`, `scrobble` |
| Playlists | `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist` |
| Similar music | `getSimilarSongs`, `getSimilarSongs2`, and `getSonicSimilarTracks` / `findSonicPath` when the AudioMuse-AI Navidrome plugin is installed |

Siansa logs in with a salted token (`u`, `t`, `s`), never the plain password.

### Emby

| Purpose | Calls |
|---|---|
| Connect | `POST /Users/AuthenticateByName`, `GET /System/Info/Public` |
| Library | `/Users/{user}/Views`, `/Users/{user}/Items`, `/Artists/AlbumArtists`, `/Items/{id}/Similar`, `/Songs/{id}/InstantMix` |
| Playback | `/Audio/{id}/stream?Static=true`, `/Items/{id}/Images/Primary`, lyrics from the item's subtitle stream |
| Favourites | `/Users/{user}/FavoriteItems/{id}` |
| Plays | `POST /Users/{user}/PlayedItems/{id}?DatePlayed=yyyyMMddHHmmss`, and `/Sessions/Playing`, `/Sessions/Playing/Progress`, `/Sessions/Playing/Stopped` for "now playing" |
| Playlists | `/Playlists`, `/Playlists/{id}/Items` |

## AudioMuse-AI

Every call names the music server with `server=<name>` (in the address, and in the body for JSON requests) when
a server name is set. See [AudioMuse-AI](audiomuse.md#several-music-servers-in-one-audiomuse-ai).

| Call | Used for |
|---|---|
| `POST /api/clap/search` | Search by sound, vibes, mood radios, Flow |
| `GET /api/clap/concepts` | The sound concepts for steering and Flow's dials |
| `GET /api/similar_tracks?item_id=…` | Song radio, similar songs, Keep playing |
| `GET /api/similar_tracks?mood=…&centroid_index=…` | Mood radios around AudioMuse-AI's mood centres |
| `GET /api/mood_centroids` | The mood centres |
| `GET /api/find_path` | Song path |
| `POST /api/alchemy` | The surprise part of mixes |
| `POST /api/sonic_fingerprint/generate` | Your sound, sonic fingerprint |
| `GET /api/sync?ids=…` | Each song's tempo, key, energy, moods and genres (DJ order, Flow sliders) |

## The Siansa Analysis plugin

Under `/plugins/siansa_analysis/`, for a logged-in user or an API token. Song ids may be the music server's own
ids or AudioMuse-AI's. Every call also takes `server=<name>` (or `server_id`), like AudioMuse-AI itself.

| Call | Answer |
|---|---|
| `GET api/health` | `{"plugin": "siansa_analysis", "plugin_version", "status": "ok", "profiles", "similar"}` |
| `GET api/profiles?ids=a,b` (up to 500) | `{"schema_version": 1, "profiles": [{"track_id", "ref_lufs", "start_ramp", "end_ramp", "duration_ms", "intro_bpm", "intro_beat_ms", "intro_bar_ms", "intro_phrase_ms", "intro_phrase_bars", "intro_rhythm", "intro_steady", "outro_bpm", "outro_beat_ms", "outro_bar_ms", "outro_phrase_ms", "outro_phrase_bars", "outro_rhythm", "outro_steady"}], "pending": [ids]}`. The `intro_*` / `outro_*` fields (plugin 0.3.0) are there once a song's beats are measured; an end without a beat has none, `*_bar_ms` only when the bars are clear, and `*_phrase_ms` with `*_phrase_bars` (4 or 8; plugin 0.4.0) only when the phrases are: a phrase start near that end, an intro's first and an outro's last. |
| `GET api/status` | Library counts and generations, and each part's status, counts and job |
| `GET api/similar/albums?track_id=…` | `{"name", "artist", "generation", "similar": [{"album", "artist", "track_id", "score"}]}` |
| `GET api/similar/artists?track_id=…` or `?artist=…` | `{"name", "generation", "similar": [{"artist", "track_id", "score"}]}` |

**Ramps** are base64 lists of 3-byte entries: a signed 8-bit level in dB relative to the song's loudness, and a
little-endian 16-bit count of 100 ms chunks (from the start for `start_ramp`, before the end for `end_ramp`).
Levels: −60, −48, −36, −30, −24, −21, −18, −15, −12, −9, −6, −3, 0, +3, +6 dB; a ramp stops at the first level the
song never reaches. In the similar lists, `track_id` is one song of that album or artist, so the app can open it on
its own server.

## Other services

| Service | When | What's sent |
|---|---|---|
| Audiobookshelf (your own server) | Set up in Connections | Your login; which book you play and where you are in it (progress, bookmarks) |
| The AI service you pick: OpenRouter (`openrouter.ai`), OpenAI (`api.openai.com`), Anthropic (`api.anthropic.com`), Google Gemini (`generativelanguage.googleapis.com`), Mistral (`api.mistral.ai`), Groq (`api.groq.com`), or Ollama or another at the address you enter | AI on, for each vibe | See [AI features](ai.md) |
| Last.fm | Connected | Plays, now playing, loved tracks; top artists and songs for your taste |
| ListenBrainz / Koito | Connected | Plays, now playing; top artists and songs |
| Open-Meteo | Weather in Use the moment on | Your location rounded to about 10 km |
| Deezer (`api.deezer.com`) | Artists you might like | Artist names only |
