package app.siansa.playback

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Whether each song's beat grid holds during a DJ blend (the owner's DJ plan, siansa-dev #63): the kicks heard in its
 * sound ([app.siansa.playback.engine.KickListener]), at their time in the song, against the beats its grid says. A grid
 * whose kicks land more than [OFF_MS] off it, over at least [MIN_KICKS] kicks, is off: the blend was laid on a wrong
 * grid, and the beats were heard out though the engine kept the two grids together. Only logged (the owner, 16 Nov):
 * the blend can be marked as bad, and the song analysed again in Siansa Beats.
 */
object GridCheck {
    /** What was heard of one song: how many kicks fell near a beat of its grid, and how far off they were (ms, median). */
    data class Side(val kicks: Int, val offMs: Long?) {
        val off: Boolean get() = offMs != null && kicks >= MIN_KICKS && abs(offMs) > OFF_MS
        val sure: Boolean get() = offMs != null && kicks >= MIN_KICKS

        fun words(): String = when {
            offMs == null || kicks < MIN_KICKS -> "too few kicks heard ($kicks)"
            else -> "kicks ${if (offMs >= 0) "+" else ""}$offMs ms off its grid ($kicks kicks)"
        }
    }

    /** Kicks [kicksMs] (ms of the song) against [grid]: those within a quarter beat of one of its beats, and their median offset. */
    fun side(kicksMs: List<Double>, grid: BeatEnd): Side {
        val beat = grid.beatLengthMs
        if (beat <= 0) return Side(0, null)
        val offsets = kicksMs.mapNotNull { t ->
            val k = Math.round((t - grid.beatMs) / beat)
            val d = t - (grid.beatMs + k * beat)
            d.takeIf { abs(it) <= beat / 4 }
        }.sorted()
        if (offsets.isEmpty()) return Side(0, null)
        val middle = if (offsets.size % 2 == 1) offsets[offsets.size / 2] else (offsets[offsets.size / 2 - 1] + offsets[offsets.size / 2]) / 2
        return Side(offsets.size, middle.roundToLong())
    }

    /** The log's line for a blend from [fromId] into [toId]. */
    fun describe(fromId: String, from: Side, toId: String, to: Side): String {
        val off = listOfNotNull(fromId.takeIf { from.off }, toId.takeIf { to.off })
        val verdict = when {
            off.isNotEmpty() -> "the grid of ${off.joinToString(" and ")} is off: analyse ${if (off.size == 1) "it" else "them"} again in Siansa Beats, or mark the blend"
            from.sure && to.sure -> "both grids hold"
            else -> "not enough kicks to tell"
        }
        return "Grid check, $fromId into $toId: $fromId ${from.words()}, $toId ${to.words()}: $verdict"
    }

    /** Kicks further than this from their grid are heard as out (DJs keep within 10 to 20 ms). */
    const val OFF_MS = 20L
    /** At least this many kicks near the grid's beats before a grid is called off or holding. */
    const val MIN_KICKS = 8
    /**
     * How much later the listener hears a kick than its beat: the low-pass and the rise to [KickListener.RISE] take a
     * moment. Measured on a made kick (GridCheckTest), and taken off every kick.
     */
    const val HEARD_LATE_MS = 3.5
}
