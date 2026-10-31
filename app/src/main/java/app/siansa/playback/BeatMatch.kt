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
    /** Closer than this, the two songs count as in step. */
    const val IN_STEP_MS = 12L
    /** Further apart than this, the next song jumps into step rather than catching up by speed. */
    const val JUMP_MS = 90L
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
    fun bothRhythmic(outgoing: BeatEnd?, incoming: BeatEnd?): Boolean = outgoing?.matchable == true && incoming?.matchable == true

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
    ) {
        /** Where the next song should be (its own ms) when the song going out is at [outgoingMs]. */
        fun incomingAt(outgoingMs: Long): Long = incomingStartMs + ((outgoingMs - startMs) * speed.toDouble()).roundToLong()

        /** How far the next song is out of step: its position less where it should be (ms; ahead is positive). */
        fun error(outgoingMs: Long, incomingMs: Long): Long = incomingMs - incomingAt(outgoingMs)

        /** The change of tempo in percent, signed: "+1.3". */
        val shiftPercent: String get() = "%+.1f".format(java.util.Locale.ROOT, (speed - 1) * 100)

        /** For the log: "beat-matched 128.0 → 126.4 BPM (+1.3 %), 7 bars (13.8 s), bar on bar". */
        val why: String
            get() = "beat-matched %.1f → %.1f BPM (%s %%), %d bar%s (%.1f s), %s".format(
                java.util.Locale.ROOT, incoming.bpm, outgoing.bpm, shiftPercent, bars, if (bars == 1) "" else "s", overlapMs / 1000.0, if (onBars) "bar on bar" else "beat on beat",
            )
    }

    /**
     * Plans the blend from [outgoing]'s end at [endMs] (its length, less any end left out) into [incoming], within
     * [maxMs] (the crossfade length setting). Null when the two can't be matched, or when not even one bar fits.
     * The blend aims at the most bars of [BAR_CHOICES] that fit, and starts on the first downbeat (or beat, when
     * the bars of either song aren't clear) of the old song at or after that point, so it stays within the
     * setting; the next song starts on its first downbeat (or beat).
     */
    fun plan(outgoing: BeatEnd, incoming: BeatEnd, endMs: Long, maxMs: Long): Plan? {
        if (!outgoing.matchable || !incoming.matchable || endMs <= 0) return null
        val speed = speed(outgoing, incoming) ?: return null
        val bar = outgoing.beatLengthMs * BEATS_PER_BAR
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
        return Plan(start.roundToLong(), overlap.roundToLong(), incomingStart.roundToLong(), speed, floor(overlap / bar).toInt(), onBars, outgoing, incoming)
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
            !incoming.matchable -> "the start of the next song isn't beat-driven and steady enough"
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
    fun nudge(errorMs: Long): Float = if (abs(errorMs) <= IN_STEP_MS) 1f else (1f - errorMs / 1000f).coerceIn(0.98f, 1.02f)

    /** The speed [elapsedMs] after the blend ended: from [matched] back to 1, a straight line over [EASE_MS]. */
    fun eased(matched: Float, elapsedMs: Long): Float {
        val t = (elapsedMs.toDouble() / EASE_MS).coerceIn(0.0, 1.0)
        return (matched + (1f - matched) * t).toFloat()
    }
}
