# Diagnostic log

Settings › Logs › **Keep a log** turns it on. Nothing is written while it's off, and nothing leaves the phone
unless you share it. Code: `app/src/main/java/app/siansa/log/` (issue #237, building on #180).

## What's kept

| File (in the app's private storage, `logs/`) | What | Limit |
|---|---|---|
| `siansa.log`, `siansa.1.log` | The readable log shown under Settings › Logs | 2 parts of 15 MB |
| `events.jsonl`, `events.1.jsonl`, `events.2.jsonl` | The same entries as JSON, one per line | 3 parts of 10 MB |
| `crash-last.jsonl` | The last 500 entries before the last crash (kept even with logging off, never sent) | one crash |

**Share** sends `siansa-diagnostics.zip`: both logs, `recent.jsonl` (the last 500 entries in memory),
`crash-last.jsonl` if there is one, and `manifest.json` (app version, Android version, phone, session id,
logging state, the files in it with their sizes, and which were cut short or missing). **Save logs** still
saves the readable log as a text file.

## An entry

```json
{"timestamp":"2026-10-19T17:45:15.134Z","level":"INFO","component":"Player","event":"playback.first_audio",
 "session_id":"3f9c2a1b","trace_id":"song-8d02c4e1","thread":"main","message":"Started playing 86ms after it was chosen",
 "fields":{"latency_ms":86,"song_id":"0tGYBodo0wheudDdAPrB7o"}}
```

- `timestamp`: UTC with milliseconds. `level`: TRACE, DEBUG, INFO, WARN, ERROR, FATAL.
- `component`: the part of the app (the tag of the readable line: Player, Audio, Load, Net, Sync, Downloads…).
- `event`: what happened (below); plain messages are `log`.
- `session_id`: new at every app start. `trace_id`: the playback operation in progress, new for each song
  started, so its buffering, requests and errors can be read together. Approximate: everything logged while
  that song is the current one carries it.
- `error`: type, message, up to 5 causes and the stack, when there is one.

## Events

| Event | Fields |
|---|---|
| `app.start` | `startup_ms` (process start to the app's setup) |
| `app.foreground`, `app.background`, `app.activity_created`, `app.activity_destroyed` | `restored`, `finishing` |
| `app.crash` (FATAL) | error |
| `diagnostics.on`, `diagnostics.off`, `diagnostics.dropped` | app and phone; `dropped` = detail left out when the log couldn't keep up |
| `playback.track_started` | `song_id`, `duration_s`, `source` (stream or offline) |
| `playback.first_audio` | `latency_ms` from choosing the song to sound (WARN over 1.5 s) |
| `playback.play`, `playback.pause` | `reason`, `by_user` |
| `playback.buffering_started`, `playback.buffering_ended` | `position_ms`; `duration_ms` (WARN over 1.5 s) |
| `playback.format` | `container`, `codec`, `sample_rate`, `channels`, `bitrate` |
| `playback.unsupported_format`, `playback.decoder_error`, `playback.error` | `code`, `category` (network, format, decoder, audio_output, player) |
| `playback.recovery`, `playback.recovery_failed` | `action` (transcode_opus, restart_decoder), `reason`, `attempt(s)` |
| `audio.underrun` | `count`, `buffer_ms`, `since_fed_ms` |
| `audio.interrupted`, `audio.focus_lost`, `audio.output_error` | `reason`, `since_start_ms`, `recovery` |
| `stream.load_failed` | `song_id`, `duration_ms`, `error_type` |
| `network.request`, `network.failed` | `method`, `host`, `path`, `params` (names only), `duration_ms`, `status`, `bytes`, `protocol`, `error_type` |
| `sync.started`, `sync.progress`, `sync.completed`, `sync.failed` | `full`, `albums_on_server`, `changed`, `removed`, `artists`, `albums`, `songs` |
| `download.failed`, `download.batch_done`, `download.storage_full` | `song_id`, `status`, `songs`, `bytes`, `limit_mb` |

Everything else the app logs (about 300 places) is an entry with `event: "log"` and its message.

## Detail

DEBUG and TRACE entries are written per part of the app: Settings › Logs › **Detail for** (Requests,
Streaming, Audio, Player, Downloads, Screen speed, Phone state, App and screens). All are on by default.
INFO and above are always written while logging is on.

## Privacy

- Fields whose name says they hold a secret (password, token, salt, key, api key, cookie, authorization, jwt…,
  any case, with or without `-`/`_`) are replaced with `•••` at any depth of the fields.
- Every string (messages, fields, error messages and stacks) also goes through patterns for addresses with
  tokens (`t=`, `s=`, `p=`…), login headers, `token=…`, JSON `"password":"…"`, AI services' keys (OpenRouter, OpenAI, Anthropic, Groq, Google) and
  `user:password@` in addresses.
- Server addresses and IP addresses stay (issue #180: the log is a tool for tracing problems), and so do the
  words of sound searches (issue #229).
- Tests: `StructuredLogTest` puts known secrets into messages, nested fields, errors and an archive and
  checks none comes out.

## Speed and size

Writing happens on one background thread with a queue of 5,000 entries, never on the caller's thread (the
audio thread included). When the queue is nearly full, DEBUG and TRACE entries are left out first and a
`diagnostics.dropped` entry says how many. Files rotate at their limits; a failed write is dropped.

## Not covered (yet)

- Native crashes and ANRs aren't caught live; Android's record of them is logged at the next start
  (`Exit` entries, Android 11+).
- `trace_id` is per song being played, not per request chain: work started elsewhere at the same time
  carries the same id.
- Bluetooth and output-device changes are logged only as Android reports them to the player (headphones
  disconnected, audio focus).
