"""Siansa Beats (dev): the AudioMuse-AI plugin Siansa Dev reads its song profiles from, in the experiment with
Siansa Beats (Beat This!, the siansa-audiomuse-plugin repository's beats/).

* Each song's loudness and fade ramps: what the Siansa plugin (siansa_analysis) measured, read from its table and
  never changed. This plugin measures nothing and has no scheduled task, so it costs AudioMuse-AI nothing.
* Each song's tempo, beats, bars and phrases at both ends: from Siansa Beats (GET /api/grids), in the same fields
  the Siansa plugin sends, so the app reads them the same way.

It installs next to the Siansa plugin (its own id, its own address: /plugins/siansa_beats/), so the Siansa app keeps
using the Siansa plugin, untouched; only Siansa Dev asks this one.
"""

from .web import bp


def register(ctx):
    ctx.add_blueprint(bp)
