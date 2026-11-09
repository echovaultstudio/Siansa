# Siansa Beats (dev)

The AudioMuse-AI plugin for **Siansa Dev**, and the one plugin going forward: it does everything the Siansa plugin
does, with each song's beat grid from **Siansa Beats** (Beat This!, in the siansa-audiomuse-plugin repository's
`beats/`).

- **Each song's loudness and fade ramps** are measured here (a scheduled tick every 15 minutes), and **similar albums
  and artists** are built here, as the Siansa plugin does: this plugin began as a copy of it (`siansa_analysis`).
- **On install it takes what the Siansa plugin already measured** (its songs, similar lists and job state, copied
  into this plugin's own tables when they're still empty), so nothing is measured again. The Siansa plugin is only
  read, never changed, and keeps working for the Siansa app.
- **Each song's tempo, beats, bars and phrases** come from Siansa Beats (`GET /api/grids`), with its lock-in, bass,
  energy, drops and breakdowns. A song Siansa Beats hasn't analysed yet keeps this plugin's own beat reading, or has
  none (the page's choice). Each profile also carries AudioMuse-AI's key and scale.
- **Bad blends** marked in Siansa Dev are passed on to Siansa Beats' list (`POST api/bad-blends`).

It installs at its own address (`/plugins/siansa_beats/`). Once only Siansa Dev is used, uninstall the Siansa plugin:
until then both measure new songs.

## Install

1. In AudioMuse-AI, **Plugins › Repositories**, add the dev catalog:
   `https://raw.githubusercontent.com/echovaultstudio/siansa-audiomuse-plugin/dev/manifest-dev.json`
2. **Catalog**: install **Siansa Beats (dev)**, then **Apply now (restart)**.
3. Open its page: enter Siansa Beats' address (`http://<server>:8090`, and its password if `BEATS_PASSWORD` is
   set), **Save and test**.

Install it while the Siansa plugin is still installed, so it can take what that one measured. To remove it, uninstall
it in AudioMuse-AI: the Siansa plugin and what it measured aren't touched.

## Publish

`audiomuse-plugin-beats/publish.sh ../siansa-audiomuse-plugin` with that clone on its `dev` branch, then commit
and push there. Bump the version in `plugin.json` for every change.
