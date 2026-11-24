package app.siansa.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * How a beat-matched blend rides the three-band EQ (the owner, 19 Nov: "build a few different blend types… so the
 * crossover is not so predictable each time"). Each blend picks one, never the same twice in a row where another
 * fits, as a DJ would mix it a little differently each time.
 *
 * All but [GLIDE] are laid on the bars and booked on the mixer to the frame ([DeckFilter.book]): a swap "on the bar"
 * lands on the old song's own bar line, within a millisecond, not on the blend's 20 ms tick. So they're only picked
 * when the blend is on the bars and its phrases are known ([choose]); else the blend glides as before.
 */
enum class EqStyle(val label: String) {
    /** The smooth one: the bass swapped over a span, the mids and highs crossed over the blend ([BlendEq], the ticks). */
    GLIDE("glide"),

    /** Both basses swapped at once on the phrase line halfway; the next song's highs up over the 4 bars before it. */
    HARD_SWAP("bass swap on the bar"),

    /** The next song teased in on its highs alone, its mids later, and its bass at once on the last phrase line. */
    TEASE("tease, then swap"),

    /** The basses swapped on one phrase line and the mids on the next, each at once. */
    SPLIT("bass, then mids, on the bar"),
}

/**
 * One band's level over a blend, in bars from its start: [startDb] until the first move, then each [Move] in turn.
 * A move of 0 bars is "at once": [DeckFilter.INSTANT_MS], short enough to land on the kick, long enough not to click.
 */
data class Lane(val startDb: Double, val moves: List<Move> = emptyList()) {
    data class Move(val atBar: Float, val bars: Float, val toDb: Double)

    /** The level at [bar] (bars from the blend's start), with a move of 0 bars taking [instantBars]. */
    fun at(bar: Float, instantBars: Float): Double {
        var level = startDb
        for (m in moves) {
            if (bar < m.atBar) break
            val length = if (m.bars <= 0f) instantBars else m.bars
            level = if (bar >= m.atBar + length) m.toDb else ramp((bar - m.atBar) / length, level, m.toDb)
        }
        return level
    }

    companion object {
        /** A smooth turn from [from] dB to [to] dB, [s] of the way (0 to 1), as [BlendEq.ramp]: by level, not by dB. */
        fun ramp(s: Float, from: Double, to: Double): Double {
            val c = (1 - cos(PI * s.coerceIn(0f, 1f))) / 2
            val gain = 10.0.pow(from / 20) * (1 - c) + 10.0.pow(to / 20) * c
            return (20 * log10(gain)).coerceIn(DeckFilter.KILL_DB, 0.0)
        }
    }
}

/** The six lanes of a blend: the old song's lows, mids and highs, and the next song's. [note] says it for the log. */
data class EqScore(
    val style: EqStyle,
    val bars: Int,
    val outLow: Lane, val outMid: Lane, val outHigh: Lane,
    val inLow: Lane, val inMid: Lane, val inHigh: Lane,
    val note: String,
)

object EqStyles {
    private const val KILL = DeckFilter.KILL_DB
    private const val DIP = BlendEq.HIGH_DIP

    /** The bar of [bars] nearest [part] of the way through, on a phrase line (4 bars) where the blend has room. */
    private fun barAt(part: Float, bars: Int): Int {
        val raw = (part * bars).roundToInt().coerceIn(1, bars - 1)
        if (bars < 8) return raw
        return ((raw / 4f).roundToInt() * 4).coerceIn(4, bars - 4)
    }

    /**
     * The score of [style] for a blend of [bars] bars. [vocalSwapBar]: where both songs would sing at once
     * ([VocalRule]), the bar the mids must swap on, at once, so one voice is heard at a time: it wins over the style's.
     * Null for [EqStyle.GLIDE], which isn't booked.
     */
    fun score(style: EqStyle, bars: Int, vocalSwapBar: Int? = null): EqScore? {
        if (style == EqStyle.GLIDE || bars < 2) return null
        val n = bars.toFloat()
        val v = vocalSwapBar?.takeIf { it in 1 until bars }?.toFloat()
        fun midsAt(bar: Float): Pair<Lane, Lane> =
            Lane(0.0, listOf(Lane.Move(bar, 0f, KILL))) to Lane(KILL, listOf(Lane.Move(bar, 0f, 0.0)))
        val score = when (style) {
            EqStyle.HARD_SWAP -> {
                val s = barAt(0.5f, bars).toFloat()
                val lead = minOf(4f, s)
                var outMid = Lane(0.0, listOf(Lane.Move(s, n - s, KILL)))
                var inMid = Lane(KILL, listOf(Lane.Move(0f, s, 0.0)))
                if (v != null) midsAt(v).let { outMid = it.first; inMid = it.second }
                EqScore(
                    style, bars,
                    outLow = Lane(0.0, listOf(Lane.Move(s, 0f, KILL))),
                    outMid = outMid,
                    outHigh = Lane(0.0, listOf(Lane.Move(n - minOf(4f, n - s), minOf(4f, n - s), DIP))),
                    inLow = Lane(KILL, listOf(Lane.Move(s, 0f, 0.0))),
                    inMid = inMid,
                    inHigh = Lane(DIP, listOf(Lane.Move(s - lead, lead, 0.0))),
                    note = "both basses swapped at once on bar ${s.toInt() + 1} of $bars",
                )
            }
            EqStyle.TEASE -> {
                val s = barAt(0.75f, bars).toFloat()
                val midsIn = maxOf(0f, s - minOf(4f, s / 2))
                var outMid = Lane(0.0, listOf(Lane.Move(s, n - s, KILL)))
                var inMid = Lane(KILL, listOf(Lane.Move(midsIn, s - midsIn, 0.0)))
                if (v != null) midsAt(v).let { outMid = it.first; inMid = it.second }
                EqScore(
                    style, bars,
                    outLow = Lane(0.0, listOf(Lane.Move(s, 0f, KILL))),
                    outMid = outMid,
                    outHigh = Lane(0.0, listOf(Lane.Move(n - minOf(2f, n - s), minOf(2f, n - s), DIP))),
                    inLow = Lane(KILL, listOf(Lane.Move(s, 0f, 0.0))),
                    inMid = inMid,
                    inHigh = Lane(DIP, listOf(Lane.Move(0f, minOf(4f, s), 0.0))),
                    note = "the next song teased in on its highs, its bass in at once on bar ${s.toInt() + 1} of $bars",
                )
            }
            EqStyle.SPLIT -> {
                val s = barAt(0.5f, bars).toFloat()
                // The mids a phrase later (one bar in a short blend), or where the voices would meet.
                val m = v ?: minOf(s + if (bars >= 8) 4f else 1f, n - 1f)
                EqScore(
                    style, bars,
                    outLow = Lane(0.0, listOf(Lane.Move(s, 0f, KILL))),
                    outMid = Lane(0.0, listOf(Lane.Move(m, 0f, KILL))),
                    outHigh = Lane(0.0, listOf(Lane.Move(m, n - m, DIP))),
                    inLow = Lane(KILL, listOf(Lane.Move(s, 0f, 0.0))),
                    inMid = Lane(KILL, listOf(Lane.Move(m, 0f, 0.0))),
                    inHigh = Lane(DIP, listOf(Lane.Move(0f, s, 0.0))),
                    note = "basses swapped at once on bar ${s.toInt() + 1}, mids on bar ${m.toInt() + 1} of $bars",
                )
            }
            EqStyle.GLIDE -> return null
        }
        return if (v != null) score.copy(note = score.note + ", mids on bar ${v.toInt() + 1} so one voice sings at a time") else score
    }

    /**
     * The style for a blend: [EqStyle.GLIDE] unless it's [onBars] with its phrases known ([phrased]) and of a
     * [technique] that rides the EQ (a long blend, a bass swap, a quick swap; a filter fade, a drop swap and an echo
     * out keep their own). Otherwise any of the bar-booked ones or a glide, never [last] again.
     */
    fun choose(technique: Technique?, onBars: Boolean, phrased: Boolean, last: EqStyle?, random: Random = Random): EqStyle {
        val rides = technique == null || technique == Technique.LONG_BLEND || technique == Technique.BASS_SWAP || technique == Technique.QUICK_SWAP
        if (!rides || !onBars || !phrased) return EqStyle.GLIDE
        val options = EqStyle.entries.filter { it != last }
        return options[random.nextInt(options.size)]
    }
}
