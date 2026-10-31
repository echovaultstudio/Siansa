package app.siansa.playback

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToLong

/** Where beat-matched blends are used (issue #646): nowhere, in DJ radio, or in every smart crossfade. */
enum class BeatMatchMode(val label: String) {
    OFF("Off"),
    DJ_RADIO("DJ radio"),
    EVERYWHERE("Everywhere");

    fun appliesTo(djRadio: Boolean): Boolean = this == EVERYWHERE || (this == DJ_RADIO && djRadio)

    companion object {
        fun parse(value: String?): BeatMatchMode = entries.firstOrNull { it.name == value } ?: DJ_RADIO
    }
}

/**
 * Beat-matched blends (issue #646), the way a DJ mixes: the blend starts on a bar of the song going out, the
 * next song starts on its first downbeat, and it plays a little faster or slower so its beats keep time with
 * the old song's through the blend. Afterwards it eases back to its own speed, too slowly to hear. The grids
 * come from the Siansa plugin ([BeatEnd]). Two songs that can't be matched but both end and start on a beat
 * get a short blend instead of a long clashing one ([CLASH_MS]). Pure maths; [Crossfader] drives the players.
 */
object BeatMatch {
    /** An end is beat-driven enough to match to at this [BeatEnd.rhythm]… */
    const val MIN_RHYTHM = 0.5f
    /** …and steady enough at this [BeatEnd.steady]. */
    const val MIN_STEADY = 0.5f
    /** A song is played at most this much faster or slower to match (DJ decks go to ±8 %). */
    const val MAX_SHIFT = 0.08
    /** Two rhythmic ends that can't be matched blend over this, so their beats clash only for a moment. */
    const val CLASH_MS = 1_500L
    const val BEATS_PER_BAR = 4
    /** A blend is this many bars, the longest that fits the crossfade length setting. */
    val BAR_CHOICES = listOf(8, 4, 2, 1)
    /** The most whole phrases a blend lasts (two 8-bar phrases at 128 BPM is 30 s). */
    const val MAX_PHRASES = 2
    /** Closer than this, the two songs count as in step. */
    const val IN_STEP_MS = 12L
    /** Further apart than this, the next song jumps into step rather than catching up by speed. */
    const val JUMP_MS = 90L
    /** How many step readings go into the smoothed error (at 20 ms ticks, a tenth of a second). */
    const val STEP_READINGS = 5
    /** After the blend, the next song eases back to its own speed over this long. */
    const val EASE_MS = 25_000L

    /**
     * The speed the next song plays at so its beats keep time with [outgoing]'s (1 = as it is), counting double
     * and half time as the same tempo; null when that would be more than [MAX_SHIFT] off.
     */
    fun speed(outgoing: BeatEnd, incoming: BeatEnd): Float? {
        if (outgoing.bpm <= 0 || incoming.bpm <= 0) return null
        val ratio = listOf(1.0, 2.0, 0.5).map { outgoing.bpm / (incoming.bpm * it) }.minByOrNull { abs(ln(it)) }!!
        return if (abs(ratio - 1) <= MAX_SHIFT) ratio.toFloat() else null
    }

    /** Both ends are beat-driven and steady: a blend between them either keeps the beats in step or clashes. */
    fun bothRhythmic(outgoing: BeatEnd?, incoming: BeatEnd?): Boolean = outgoing?.matchable == true && incoming?.matchableAsIntro == true

    /**
     * One beat-matched blend: it starts at [startMs] of the song going out and lasts [overlapMs] ([bars] whole
     * bars of it), to that song's end; the next song starts at [incomingStartMs] of its own time, at [speed];
     * [onBars] when downbeat meets downbeat, else beat meets beat.
     */
    data class Plan(
        val startMs: Long,
        val overlapMs: Long,
        val incomingStartMs: Long,
        val speed: Float,
        val bars: Int,
        val onBars: Boolean,
        val outgoing: BeatEnd,
        val incoming: BeatEnd,
        /** Phrase on phrase (issue #757): how many bars a phrase is; null when laid bar on bar or beat on beat. */
        val phraseBars: Int? = null,
        /** The end of the song going out that's left out, so it's let go at the end of a phrase (its last part-phrase). */
        val cutMs: Long = 0L,
    ) {
        /** Where the next song should be (its own ms) when the song going out is at [outgoingMs]. */
        fun incomingAt(outgoingMs: Long): Long = incomingStartMs + ((outgoingMs - startMs) * speed.toDouble()).roundToLong()

        /** How far the next song is out of step: its position less where it should be (ms; ahead is positive). */
        fun error(outgoingMs: Long, incomingMs: Long): Long = incomingMs - incomingAt(outgoingMs)

        /** The change of tempo in percent, signed: "+1.3". */
        val shiftPercent: String get() = "%+.1f".format(java.util.Locale.ROOT, (speed - 1) * 100)

        /** For the log: "beat-matched 128.0 → 126.4 BPM (+1.3 %), 7 bars (13.8 s), bar on bar". */
        val why: String
            get() = "beat-matched %.1f → %.1f BPM (%s %%), %d bar%s (%.1f s), %s%s".format(
                java.util.Locale.ROOT, incoming.bpm, outgoing.bpm, shiftPercent, bars, if (bars == 1) "" else "s", overlapMs / 1000.0,
                when {
                    phraseBars != null -> "phrase on phrase ($phraseBars-bar phrases)"
                    onBars -> "bar on bar"
                    else -> "beat on beat"
                },
                if (cutMs > 0) ", let go at the end of its last phrase (%.1f s left out)".format(java.util.Locale.ROOT, cutMs / 1000.0) else "",
            )
    }

    /**
     * Plans the blend from [outgoing]'s end at [endMs] (its length, less any end left out) into [incoming], within
     * [maxMs] (the crossfade length setting). Null when the two can't be matched, or when not even one bar fits.
     * The blend aims at the most bars of [BAR_CHOICES] that fit, and starts on the first downbeat (or beat, when
     * the bars of either song aren't clear) of the old song at or after that point, so it stays within the
     * setting; the next song starts on its first downbeat (or beat).
     */
    /** Whole bars in [overlapMs], to within a tenth of a beat (7.68 s at 125 BPM is 4 bars, not 3.99). */
    fun wholeBars(overlapMs: Double, barMs: Double): Int = floor((overlapMs + barMs / BEATS_PER_BAR / 10) / barMs).toInt()

    fun plan(outgoing: BeatEnd, incoming: BeatEnd, endMs: Long, maxMs: Long): Plan? {
        if (!outgoing.matchable || !incoming.matchableAsIntro || endMs <= 0) return null
        val speed = speed(outgoing, incoming) ?: return null
        val bar = outgoing.beatLengthMs * BEATS_PER_BAR
        phrased(outgoing, incoming, endMs, maxMs, speed, bar)?.let { return it }
        val bars = BAR_CHOICES.firstOrNull { it * bar <= maxMs } ?: return null
        val onBars = outgoing.barMs != null && incoming.barMs != null
        val step = if (onBars) bar else outgoing.beatLengthMs
        val grid = if (onBars) outgoing.barMs!! else outgoing.beatMs
        val wanted = endMs - bars * bar
        // On the grid at or after [wanted], so the blend stays within the setting; a blend that would then be
        // shorter than a bar takes the grid point before instead, when that still fits. At least a beat.
        var start = grid + ceil((wanted - grid) / step) * step
        if (endMs - start < bar && endMs - (start - step) <= maxMs + outgoing.beatLengthMs) start -= step
        if (start <= 0 || endMs - start < outgoing.beatLengthMs) return null
        val incomingStart = (if (onBars) incoming.barMs!! else incoming.beatMs).coerceAtLeast(0.0)
        val overlap = endMs - start
        return Plan(start.roundToLong(), overlap.roundToLong(), incomingStart.roundToLong(), speed, wholeBars(overlap, bar), onBars, outgoing, incoming)
    }

    /**
     * Phrase on phrase (issue #757), as a DJ mixes, when the plugin found the phrases at both ends: the next song's
     * first phrase starts on a phrase boundary of the song going out, the blend lasts whole phrases (up to
     * [MAX_PHRASES], as many as [maxMs] allows, or half a phrase of 8 bars when a whole one doesn't fit), and the
     * song going out is let go at the end of a phrase: a last part-phrase shorter than half a phrase is left out
     * ([Plan.cutMs]); a longer one plays to the song's end, still on the grid. Null when there are no phrases, or
     * nothing fits: then bar on bar.
     */
    internal fun phrased(outgoing: BeatEnd, incoming: BeatEnd, endMs: Long, maxMs: Long, speed: Float, bar: Double): Plan? {
        val outPhrase = outgoing.phraseMs ?: return null
        val inPhrase = incoming.phraseMs ?: return null
        val inBars = incoming.phraseBars ?: return null
        // The longer phrase of the two is the unit, so both songs' phrases end together (8-bar ones are two 4-bar).
        val phraseBars = maxOf(outgoing.phraseBars ?: return null, inBars)
        if (outPhrase < 0) return null
        val phrase = phraseBars * bar
        val slack = outgoing.beatLengthMs  // a blend may run a beat over the setting, as the bar plan does
        // The last phrase boundary of the song going out, and what's after it.
        val last = outPhrase + floor((endMs - outPhrase) / phrase) * phrase
        if (last <= 0) return null
        val rest = endMs - last
        val cut = if (rest < phrase / 2) rest else 0.0
        val blendEnd = endMs - cut
        // Whole phrases before [last], plus the rest when it's kept; the most that fit.
        val lengths = ArrayList<Pair<Double, Double>>()  // start, overlap
        for (k in MAX_PHRASES downTo (if (cut > 0) 1 else 0)) {
            val overlap = k * phrase + (blendEnd - last)
            if (overlap <= maxMs + slack && overlap >= bar) lengths += (last - k * phrase) to overlap
        }
        // Half a phrase of 8 bars, when not even one fits (a short crossfade setting).
        if (lengths.isEmpty() && cut > 0 && phraseBars == 8 && 4 * bar <= maxMs + slack) lengths += (last - 4 * bar) to 4 * bar
        val (start, overlap) = lengths.firstOrNull { it.first > 0 } ?: return null
        return Plan(
            start.roundToLong(), overlap.roundToLong(), inPhrase.coerceAtLeast(0.0).roundToLong(), speed,
            floor(overlap / bar).toInt(), onBars = true, outgoing, incoming, phraseBars = phraseBars, cutMs = cut.roundToLong(),
        )
    }

    /**
     * What the Siansa plugin measured at the two ends of a blend, and why it wasn't laid beat on beat when it wasn't,
     * for the log (issue #744): so a report sent after a blend that sounded off shows which case it was. [applies]:
     * beat-matching is on for this blend (Settings, and DJ radio when it's set for DJ radio only).
     */
    fun note(outgoing: BeatEnd?, incoming: BeatEnd?, applies: Boolean): String {
        val ends = "out: ${outgoing?.describe() ?: "no beat measured"} · in: ${incoming?.describe() ?: "no beat measured"}"
        val reason = when {
            outgoing == null -> "the plugin hasn't measured the beat at the end of the song going out"
            incoming == null -> "the plugin hasn't measured the beat at the start of the next song"
            !outgoing.matchable -> "the end of the song going out isn't beat-driven and steady enough"
            !incoming.matchableAsIntro -> "the start of the next song isn't beat-driven and steady enough" +
                (if (incoming.steady >= MIN_STEADY && incoming.barMs == null) " (steady, but its bars aren't clear)" else "")
            speed(outgoing, incoming) == null -> "%.1f and %.1f BPM are too far apart".format(java.util.Locale.ROOT, outgoing.bpm, incoming.bpm)
            !applies -> "beat-matching is set for DJ radio only"
            else -> null
        }
        return if (reason == null) ends else "$ends · not beat-matched: $reason"
    }

    /** How well a beat-matched blend kept in step (issue #744): how far apart the two songs' beats were, at each check. */
    class StepStats {
        private var checks = 0
        private var total = 0L
        private var worst = 0L

        fun add(errorMs: Long) {
            checks++
            total += abs(errorMs)
            worst = maxOf(worst, abs(errorMs))
        }

        /** "beats typically 6 ms apart, at worst 18 ms (412 checks)". */
        fun describe(): String = if (checks == 0) "beats not checked" else "beats typically ${total / checks} ms apart, at worst $worst ms ($checks checks)"
    }

    /** How long getting the next song to a point takes at first, before Siansa has measured it on this phone (issue #747). */
    const val DEFAULT_SEEK_LAG_MS = 150L
    /** Longer than this, a measured lag is a stall, not the phone's usual delay. */
    const val MAX_SEEK_LAG_MS = 600L

    /**
     * What a jump costs on this phone, learned from the last one (issue #747): the next song was sent [leadMs] ahead
     * of where it should be and, once settled, was [errorMs] out of step (behind is negative), so the jump took
     * [leadMs] − [errorMs]. Averaged with [previous], so one slow jump doesn't throw the next one off. A jump that
     * took longer than any jump does ([MAX_SEEK_LAG_MS]) was a stall (the song buffering), not the phone's usual
     * delay: [previous] stays.
     */
    fun learnedLag(previous: Long, leadMs: Long, errorMs: Long): Long {
        val took = leadMs - errorMs
        if (took > MAX_SEEK_LAG_MS) return previous
        return ((previous + took) / 2).coerceIn(0L, MAX_SEEK_LAG_MS)
    }

    /** The speed nudge that brings the next song back in step over about a second: ahead, a touch slower. */
    fun nudge(errorMs: Long): Float = if (abs(errorMs) <= IN_STEP_MS) 1f else (1f - errorMs / 1000f).coerceIn(0.97f, 1.03f)

    /**
     * The step error that decides a jump or a nudge (issue #759): the middle of the last few readings, as one
     * reading can be 90 ms off and back the next moment (the position arrives in pieces). Null until there are
     * [STEP_READINGS] of them.
     */
    class SmoothedError {
        private val recent = ArrayDeque<Long>()

        fun add(errorMs: Long): Long? {
            recent.addLast(errorMs)
            if (recent.size > STEP_READINGS) recent.removeFirst()
            if (recent.size < STEP_READINGS) return null
            return recent.sorted()[recent.size / 2]
        }
    }

    /** The speed [elapsedMs] after the blend ended: from [matched] back to 1, a straight line over [EASE_MS]. */
    fun eased(matched: Float, elapsedMs: Long): Float {
        val t = (elapsedMs.toDouble() / EASE_MS).coerceIn(0.0, 1.0)
        return (matched + (1f - matched) * t).toFloat()
    }
}
