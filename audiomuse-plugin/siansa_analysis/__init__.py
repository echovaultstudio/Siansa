"""Siansa Analysis: the AudioMuse-AI plugin for the Siansa app (issue #188).

* Measures every song's loudness and how it starts and ends, for levelling and SmoothFade in Siansa.
* Finds similar albums and artists from AudioMuse-AI's sound analysis, and rebuilds them when the library
  or the analysis changes.

See README.md next to this folder for what each part does and how to install it.
"""

from . import jobs, store
from .web import bp


def register(ctx):
    ctx.add_blueprint(bp)
    ctx.on_install(store.migrate)
    ctx.on_song_analyzed(jobs.on_song_analyzed)
    ctx.add_cron_task("tick", jobs.tick)
    ctx.add_task("measure_missing", jobs.prepare_now)
    ctx.add_task("rebuild_similar", jobs.rebuild_now)
