# Siansa Beats (dev)

The AudioMuse-AI plugin for **Siansa Dev's experiment with Siansa Beats** (Beat This!, in the siansa-audiomuse-plugin
repository's `beats/`). Not for the Siansa app: it keeps using the Siansa plugin.

- **Each song's loudness and fade ramps** come from what the Siansa plugin (`siansa_analysis`) already measured: its
  table is read, never written. This plugin measures nothing and has no scheduled task.
- **Each song's tempo, beats, bars and phrases** come from Siansa Beats (`GET /api/grids`), in the fields the Siansa
  plugin sends. A song Siansa Beats hasn't analysed yet keeps the Siansa plugin's beat grid, or has none (the
  plugin page's choice).

It installs next to the Siansa plugin, at its own address (`/plugins/siansa_beats/`).

## Install

1. In AudioMuse-AI, **Plugins › Repositories**, add the dev catalog:
   `https://raw.githubusercontent.com/echovaultstudio/siansa-audiomuse-plugin/dev/manifest-dev.json`
2. **Catalog**: install **Siansa Beats (dev)**, then **Apply now (restart)**.
3. Open its page: enter Siansa Beats' address (`http://<server>:8090`, and its password if `BEATS_PASSWORD` is
   set), **Save and test**.

To remove it, uninstall it in AudioMuse-AI: the Siansa plugin and what it measured aren't touched.

## Publish

`audiomuse-plugin-beats/publish.sh ../siansa-audiomuse-plugin` with that clone on its `dev` branch, then commit
and push there. Bump the version in `plugin.json` for every change.
