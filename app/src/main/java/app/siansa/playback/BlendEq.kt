package app.siansa.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Where a song's vocals are, as Siansa Beats found them by separating its ends (Beats 0.60): [none] for a song the voice
 * model calls instrumental (not separated); else every sung stretch ([sections], ms on bar lines) of the parts that
 * were separated ([heard]). [introFreeBars] / [outroFreeBars]: the bars from the intro's lock to the first vocals, and
 * from the last vocals to the outro's lock.
 */
data class SongVocals(
    val none: Boolean,
    val sections: List<LongRange> = emptyList(),
    val heard: List<LongRange> = emptyList(),
    val introFreeBars: Int? = null,
    val outroFreeBars: Int? = null,
) {
    /** Whether it's known where the vocals are from [from] to [to] (song ms): the song has none, or all of it was heard. */
    fun knows(from: Long, to: Long): Boolean = none || heard.any { from >= it.first - EDGE_MS && to <= it.last + EDGE_MS }

    companion object {
        /** A blend reaching this far past what was heard still counts as known (a rounding at the edge). */
        const val EDGE_MS = 500L
    }
}

/**
 * The three-band EQ over a beat-matched blend (the owner, 19 Nov: "make the 3 way eq change over blend… 2 vocal tracks
 * can be mixed but not where both vocals can be heard"), as a DJ rides a mixer's EQ, each band on its own. [t] is how far
 * through the blend (0 to 1, the old song's own progress).
 *
 * - **Lows**: one swap, at the technique's place ([FadePlan.swapFrom] to [FadePlan.swapTo]): the old song's bass cut by
 *   a sweeping high-pass, the next song's let in from [DeckFilter.KILL_DB] ([DeckFilter.lowCutAt], [DeckFilter.shelfAt]).
 * - **Highs**: the next song's hats come in first, [HIGH_DIP] up to full over the first quarter; the old song's go
 *   down by as much over the last.
 * - **Mids** (where a voice is): with no two vocals at once in the blend, the next song's mids come in over the first
 *   half and the old song's go over the second: both are heard together in the middle, as a blend should.
 *   Where both songs sing at the same moment ([midSwapAt] set, [VocalRule]), the mids are swapped on a bar line
 *   instead: the old song's are cut over the bar before it, the next song's let in over the bar after it, and the
 *   highs (a voice's air) move with them. One voice is heard at a time, never both.
 */
data class BlendEq(val bars: Int, val midSwapAt: Float? = null, val why: String = "") {
    private val bar: Float get() = 1f / bars.coerceAtLeast(1)

    fun incomingMidDb(t: Float): Double = midSwapAt?.let { ramp(t, it, it + bar, DeckFilter.KILL_DB, 0.0) }
        ?: ramp(t, 0f, 0.5f, DeckFilter.KILL_DB, 0.0)

    fun outgoingMidDb(t: Float): Double = midSwapAt?.let { ramp(t, it - bar, it, 0.0, DeckFilter.KILL_DB) }
        ?: ramp(t, 0.5f, 1f, 0.0, DeckFilter.KILL_DB)

    fun incomingHighDb(t: Float): Double = midSwapAt?.let { ramp(t, it, it + bar, HIGH_DIP, 0.0) }
        ?: ramp(t, 0f, 0.25f, HIGH_DIP, 0.0)

    fun outgoingHighDb(t: Float): Double = midSwapAt?.let { ramp(t, it - bar, it, 0.0, HIGH_DIP) }
        ?: ramp(t, 0.75f, 1f, 0.0, HIGH_DIP)

    /** Sets the mids and highs of both decks for [t] (the lows are the bass swap's). */
    fun apply(t: Float, outgoing: DeckFilter?, incoming: DeckFilter) {
        outgoing?.midDb = outgoingMidDb(t)
        outgoing?.highDb = outgoingHighDb(t)
        incoming.midDb = incomingMidDb(t)
        incoming.highDb = incomingHighDb(t)
    }

    /** For the log: "mids swapped at bar 9 of 16 (both sing there)", or "mids and highs crossed over the blend". */
    fun describe(): String = midSwapAt?.let { "mids swapped at bar ${Math.round(it * bars) + 1} of $bars" + (if (why.isNotEmpty()) " ($why)" else "") }
        ?: ("mids and highs crossed over the blend" + (if (why.isNotEmpty()) " ($why)" else ""))

    companion object {
        /** How far the highs dip: the hats faded back, not gone. */
        const val HIGH_DIP = -12.0

        /**
         * From [from] dB before [a] to [to] dB after [b], in between a smooth turn of the level (its amplitude, not its
         * dB: a cut in dB alone hangs on and then drops all at once).
         */
        fun ramp(t: Float, a: Float, b: Float, from: Double, to: Double): Double {
            if (t <= a) return from
            if (t >= b || b <= a) return to
            val s = (1 - cos(PI * ((t - a) / (b - a)).toDouble())) / 2
            val gain = 10.0.pow(from / 20) * (1 - s) + 10.0.pow(to / 20) * s
            return (20 * log10(gain)).coerceIn(DeckFilter.KILL_DB, 0.0)
        }
    }
}

/**
 * Where to swap the mids so two voices are never heard at once ([BlendEq.midSwapAt]).
 *
 * Each song's sung stretches are laid on the blend ([fractions]): the old song's by where it is ([BeatMatch.Plan.startMs]
 * on), the next song's by where it starts and how fast it plays. When they meet for at least [CLASH_BARS] of a bar,
 * every bar line inside the blend is a place to swap, and the one that loses the least singing is chosen: the old song's
 * vocals after the swap and the next song's before it, each weighed by how loud that song is then in the blend (the old
 * one fading out, the next fading in), half for the bar each ramp takes. A phrase line (every 4 bars) wins a near tie.
 */
object VocalRule {
    const val CLASH_BARS = 0.5f
    /** A swap off a phrase line (4 bars) costs this much more, so a phrase line wins a near tie. */
    const val OFF_PHRASE = 0.01
    private const val STEPS = 400

    /** [sections] (song ms) as parts of the blend (0 to 1): the song at [startMs] when the blend starts, [msPerBlend] of it played over the blend. */
    fun fractions(sections: List<LongRange>, startMs: Double, msPerBlend: Double): List<ClosedFloatingPointRange<Float>> {
        if (msPerBlend <= 0) return emptyList()
        return sections.mapNotNull { s ->
            val a = ((s.first - startMs) / msPerBlend).toFloat().coerceIn(0f, 1f)
            val b = ((s.last - startMs) / msPerBlend).toFloat().coerceIn(0f, 1f)
            if (b > a) a..b else null
        }
    }

    /** How much of the blend both sing at once (0 to 1). */
    fun together(out: List<ClosedFloatingPointRange<Float>>, inc: List<ClosedFloatingPointRange<Float>>): Float =
        out.sumOf { o -> inc.sumOf { i -> maxOf(0f, minOf(o.endInclusive, i.endInclusive) - maxOf(o.start, i.start)).toDouble() } }.toFloat()

    /** The bar line (a part of the blend) to swap the mids at, or null when the two voices don't meet. */
    fun swapAt(out: List<ClosedFloatingPointRange<Float>>, inc: List<ClosedFloatingPointRange<Float>>, bars: Int): Float? {
        if (bars < 2 || together(out, inc) < CLASH_BARS / bars) return null
        val bar = 1f / bars
        fun sung(parts: List<ClosedFloatingPointRange<Float>>, t: Float) = parts.any { t >= it.start && t < it.endInclusive }
        fun lost(parts: List<ClosedFloatingPointRange<Float>>, a: Float, b: Float, weight: (Float) -> Double): Double {
            if (b <= a) return 0.0
            var sum = 0.0
            val step = (b - a) / STEPS
            for (i in 0 until STEPS) {
                val t = a + (i + 0.5f) * step
                if (sung(parts, t)) sum += weight(t) * step
            }
            return sum
        }
        val outLevel = { t: Float -> cos(t * PI / 2).pow(2) }  // the old song fading out (its power)
        val inLevel = { t: Float -> sin(t * PI / 2).pow(2) }
        var best: Pair<Float, Double>? = null
        for (k in 1 until bars) {
            val m = k * bar
            val cost = lost(out, m, 1f, outLevel) + lost(out, m - bar, m, outLevel) / 2 +
                lost(inc, 0f, m, inLevel) + lost(inc, m, m + bar, inLevel) / 2 +
                if (k % 4 != 0) OFF_PHRASE else 0.0
            if (best == null || cost < best.second - 1e-9) best = m to cost
        }
        return best?.first
    }

    /**
     * The EQ for a blend of [bars] bars from [outgoing] into [incoming] (null when not known): the old song from
     * [outStartMs] for [overlapMs], the next from [inStartMs] at [speed].
     */
    fun eq(outgoing: SongVocals?, incoming: SongVocals?, outStartMs: Long, inStartMs: Long, overlapMs: Long, speed: Float, bars: Int): BlendEq {
        val inLength = (overlapMs * speed).toLong()
        if (outgoing == null || incoming == null) return BlendEq(bars, why = "where the vocals are isn't known yet")
        if (outgoing.none || incoming.none) return BlendEq(bars, why = if (outgoing.none && incoming.none) "no vocals" else "one is instrumental")
        if (!outgoing.knows(outStartMs, outStartMs + overlapMs) || !incoming.knows(inStartMs, inStartMs + inLength)) {
            return BlendEq(bars, why = "the blend is outside the parts separated")
        }
        val out = fractions(outgoing.sections, outStartMs.toDouble(), overlapMs.toDouble())
        val inc = fractions(incoming.sections, inStartMs.toDouble(), inLength.toDouble())
        val swap = swapAt(out, inc, bars) ?: return BlendEq(bars, why = if (out.isEmpty() || inc.isEmpty()) "only one sings" else "their vocals don't meet")
        val both = together(out, inc) * bars
        return BlendEq(bars, swap, "both sing for %.1f bars of it".format(java.util.Locale.ROOT, both))
    }
}
