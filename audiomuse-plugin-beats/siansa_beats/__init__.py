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
    # One scheduled task, the tick, every 15 minutes: it does all the regular work. The settings page's buttons
    # start their jobs themselves, so they need no tasks of their own in Scheduled Tasks (0.5.5: measure_missing
    # and rebuild_similar were listed there and asked for a cron expression nobody needed).
    try:
        ctx.add_cron_task("tick", jobs.tick, description=TICK_DESCRIPTION)
    except TypeError:  # an AudioMuse-AI without task descriptions
        ctx.add_cron_task("tick", jobs.tick)


TICK_DESCRIPTION = ("Siansa: measures new songs' loudness, fades and beats, and rebuilds similar albums and "
                    "artists when the library changes. Every 15 minutes is enough; keep it on.")
