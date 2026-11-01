# Troubleshooting

First steps for any problem: update Siansa, turn on **Debug logs** (**Settings › Help**), make the problem happen a
few times, then use **Report a problem** there. See [Reporting problems](privacy-and-logs.md#report-a-problem).

## Can't connect to my server

- Use **Test** in **Settings › Servers and services**. It shows what the server answered.
- Try the address in the phone's browser. If that doesn't load either, it's the network: are you on the same
  Wi-Fi, or on your VPN when away from home?
- Include the port (`:4533` for Navidrome, `:8096` for Emby) and `http://` or `https://`.
- Behind a login page (Authelia, Authentik, a proxy's basic auth)? Add the **proxy login**.

## "Not found on Navidrome. It may have been removed."

The album, artist or song isn't on the server Siansa asked. Common reasons:

- It was removed or moved on the server. **Sync now** in Library sync.
- It belongs to another user's server that you've since hidden in the source filter. Show that user again.

## AudioMuse doesn't know this song

Song radio, similar songs or similar albums say "AudioMuse doesn't know this song".

1. **Has AudioMuse-AI analysed it?** New songs need its analysis first. Check in AudioMuse-AI, or run an analysis.
2. **Several music servers in AudioMuse-AI?** Each server's name must be set in **Settings › Servers and services**, in the
   card of that music server (Server name in AudioMuse), exactly as AudioMuse-AI shows it. See
   [Several music servers](audiomuse.md#several-music-servers-in-one-audiomuse-ai).
3. **Does AudioMuse-AI know the song on that server?** It links a second server's songs by analysis or by
   **Align** in its setup. To check, ask AudioMuse-AI directly, replacing `TOKEN`, `NAME` and `ID`:
   ```bash
   # The servers AudioMuse-AI knows, with their exact names
   curl -s -H "Authorization: Bearer TOKEN" http://AUDIOMUSE:8000/api/servers
   # Does it know this song on that server? An empty "tracks" list means no.
   curl -s -H "Authorization: Bearer TOKEN" "http://AUDIOMUSE:8000/api/sync?server=NAME&ids=ID&include_embeddings=false&limit=1"
   ```
   If it doesn't know the song, run **Align** for that server in AudioMuse-AI's setup, or an analysis.
4. **Similar albums and artists** also need the Siansa Analysis plugin, up to date (**Plugins** in AudioMuse-AI).
   Without it, Similar albums says so: "The Siansa Analysis plugin isn't installed on this AudioMuse", and Home's
   artist radios and "Fans also like" quietly do without (the log says why, once an hour).

## Mixes or Flow come out empty

- Is AudioMuse-AI set up and does **Test** work?
- Do the songs AudioMuse-AI finds belong to music you're showing? With your own music hidden in the source filter,
  searches use the other shown user's server; make sure its server name is set.
- The log line "Left out N songs that aren't in the libraries you sync" means AudioMuse-AI found songs, but not in
  the music shown.

## Songs show the wrong length, or the seek bar jumps

Some files carry a wrong length in their header (often Dolby E-AC3 .m4a). Siansa uses your server's length when
the file's is far off. If it's still wrong, rescan the file on your server and send a report.

## Plays don't show up on my server, Last.fm or ListenBrainz

- Check **Settings › Scrobbling and history**: is the service on, and is the user switched on under **Users**?
- A song counts only after the chosen share of it, or four minutes. Songs of 30 seconds or less never count.
- Offline plays wait and are sent later.

## "unexpected end of stream" in the log

A request to AudioMuse-AI or your server was cut off, usually a kept-alive connection the server had already
closed. Siansa tries once more by itself; it's only a problem if the second try fails too.

## Crossfades or levelling don't seem to do anything

- Levelling needs ReplayGain tags in your files, or the Siansa plugin as the source.
- Smart crossfades need the plugin's measurements; until a song is measured, the fixed length is used.
- Crossfades don't happen while casting, with Repeat one, or while the sleep timer lets a song finish.

## Nothing on a TV or speaker when casting

The TV or speaker must be on the same Wi-Fi as the phone, and able to reach your music server's address. It plays
straight from your server.
