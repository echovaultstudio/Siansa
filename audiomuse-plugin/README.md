# Siansa Analysis: the AudioMuse-AI plugin for Siansa

Our own plugin for [AudioMuse-AI](https://github.com/NeptuneHub/AudioMuse-AI), written for the Siansa app
(issue #188).

It does two things, each with its own progress, so one never waits for the other:

| Part | What it gives Siansa | How it keeps current |
|---|---|---|
| **Volume & fade profiles** | Each song's loudness (EBU R128 / BS.1770, in LUFS) for levelling, how it starts and ends (the MixRamp idea) for SmoothFade crossfades, and the beat grid at each end (tempo to a fraction of a BPM, where the beats and bars fall, how beat-driven and how steady the end is) for beat-matched blends in DJ radio. | A song is measured right after AudioMuse-AI analyses it, from the file it already has. Older songs and songs that failed are fetched and measured a batch at a time by the tick; songs the app asks for come first. |
| **Similar albums & artists** | For an album or artist, the ones that sound most alike, from AudioMuse-AI's MusiCNN analysis. At most two albums per artist, other editions of the same album left out. | The library and the sound analysis each have a digest of exactly what the build reads, and a generation that goes up when it changes. A build remembers the generations it came from; when either moves on, the tick builds again and swaps the new lists in at once. The old lists stay in use until then. |

To measure the whole library at once, use **Measure all songs** on the settings page. It runs in rounds
of 12 minutes every 15 minutes until every song is measured, and can be stopped at any time. Each song is
downloaded from the music server and read in full, so a large library takes hours or days; and while a
round runs, AudioMuse-AI doesn't start its own jobs (analysis, clustering, scheduled tasks), which is why
the rounds leave a gap. Without it, the tick measures one batch in at most 4 minutes.

The **tick** is a scheduled task (`plugin.siansa_analysis.tick`, every 15 minutes, set up when the plugin is
installed). Change or switch it off under Administration › Scheduled Tasks. The settings page (Plugins ›
Siansa Analysis › Settings) shows the three sections, Library, Volume & fade profiles and Similar albums &
artists, each with its own status, and buttons to measure or build now.

## API

Under `/plugins/siansa_analysis/`, for a logged-in user or an API token. Song ids may be the music server's
own ids or AudioMuse-AI's.

| Call | Answer |
|---|---|
| `GET api/health` | `{"plugin": "siansa_analysis", "plugin_version", "status": "ok", "profiles", "similar"}` |
| `GET api/profiles?ids=a,b` (≤ 500) | `{"schema_version": 1, "profiles": [{"track_id", "ref_lufs", "start_ramp", "end_ramp", "duration_ms", "intro_bpm", "intro_beat_ms", "intro_bar_ms", "intro_rhythm", "intro_steady", "outro_bpm", "outro_beat_ms", "outro_bar_ms", "outro_rhythm", "outro_steady"}], "pending": [ids]}`. The `intro_*` / `outro_*` fields are there once the song's beats have been measured, and an end without a beat has none; `*_bar_ms` only when the bars are clear. |
| `GET api/status` | Library counts and generations, and each part's status, counts and job |
| `GET api/similar/albums?track_id=…` | `{"name", "artist", "generation", "similar": [{"album", "artist", "track_id", "score"}]}` |
| `GET api/similar/artists?track_id=…` or `?artist=…` | `{"name", "generation", "similar": [{"artist", "track_id", "score"}]}` |

Ramps are base64 lists of 3-byte entries: a signed 8-bit level in dB relative to the song's loudness, and a
little-endian 16-bit count of 100 ms chunks (from the start for `start_ramp`, before the end for
`end_ramp`). Levels: −60, −48, −36, −30, −24, −21, −18, −15, −12, −9, −6, −3, 0, +3, +6 dB; a ramp stops at
the first level the song never reaches. In the similar lists, `track_id` is one song of that album or
artist, so the app can open it on its own server.

## Install

In AudioMuse-AI: Plugins › Repositories, add

```
https://raw.githubusercontent.com/echovaultstudio/siansa-audiomuse-plugin/main/manifest.json
```

then install Siansa Analysis from the Catalog tab and apply the restart. AudioMuse-AI checks the catalog for
new versions by itself. Only built-in libraries are used (numpy, scipy, PyAV), so it also works on the
standalone builds. It needs AudioMuse-AI 3.6.4 or later. A song PyAV can't decode (its copy of FFmpeg lacks
something the file needs) is read with the `ffmpeg` tool instead, when AudioMuse-AI's install has it.

Songs measured by a version before 0.3.0 keep their loudness and fades and are measured again, in the
background, for their beat grids (the plugin's page says how many are waiting). That reads every file once
more, so a big library takes a while; Siansa blends a song the plain way until its grid is there.

Fetching songs for older ones works with Navidrome, Jellyfin, Emby and Lyrion; Plex songs are measured
when AudioMuse-AI analyses them.

## Publishing (for Siansa development)

The code lives in the Siansa repository (`audiomuse-plugin/`); the public repository above is the catalog
AudioMuse-AI installs from. To publish, add a new version at the top of `versions` in `plugin.json`, then:

```sh
audiomuse-plugin/publish.sh ../siansa-audiomuse-plugin
```

and commit and push there. A published version's zip is never replaced, because installed copies check its
checksum. To try a build on the home network instead, `audiomuse-plugin/build.sh http://<lan-ip>:8000`
makes a catalog to serve from any computer AudioMuse-AI can reach.

## Tests

```sh
cd audiomuse-plugin && python3 -m unittest discover -s tests
```

CI runs them on every pull request (needs numpy, scipy, Flask; PyAV for the file test).
