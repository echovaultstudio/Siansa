package app.siansa.playback

import androidx.media3.common.PlaybackParameters
import kotlin.math.abs
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
        /** Off unless chosen (issue #822): blends laid on the beat still drift out of step on many phones. */
        fun parse(value: String?): BeatMatchMode = entries.firstOrNull { it.name == value } ?: OFF
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
    /** With Siansa Beats' lock, the rhythm is measured inside it and has to reach this (the owner, 9 Nov). */
    const val MIN_LOCKED_RHYTHM = 0.8f
    /** A blend into or out of a track cut from a mixed CD is kept to this many bars, inside its lock. */
    const val MIX_TRACK_BARS = 8
    /** …and steady enough at this [BeatEnd.steady]. */
    const val MIN_STEADY = 0.5f
    /** A song is played at most this much faster or slower to match (DJ decks go to ±8 %). */
    const val MAX_SHIFT = 0.08
    /** A shift past this is heard as a change of tone over a long blend (issue #807): the blend is kept to
     *  [BIG_SHIFT_BARS] bars, so the song is back at its own speed sooner. */
    const val BIG_SHIFT = 0.04
    const val BIG_SHIFT_BARS = 8
    /** The tempos a DJ counts in (issue #807): a grid read at half time is doubled into this range ([BeatEnd.inDjRange]). */
    const val DJ_RANGE_LOW = 100.0
    const val DJ_RANGE_HIGH = 200.0
    /** Two rhythmic ends that can't be matched blend over this, so their beats clash only for a moment. */
    const val CLASH_MS = 1_500L
    const val BEATS_PER_BAR = 4
    /** A blend is this many bars, the longest that fits the blend length setting (and the song). */
    val BAR_CHOICES = listOf(16, 8, 4, 2, 1)
    /** The most whole phrases a blend lasts (two 8-bar phrases is the 16-bar blend length). */
    const val MAX_PHRASES = 2

    /**
     * The longest blend of [bars] whole bars at [outgoing]'s tempo: those bars plus the part-bar a song's end
     * rarely falls exactly on (all but half a beat of one more bar).
     */
    fun maxMsFor(outgoing: BeatEnd, bars: Int): Long =
        (outgoing.beatLengthMs * ((bars + 1) * BEATS_PER_BAR - 0.5)).toLong()

    /** The bars a blend from [outgoing] into [incoming] may have within [blendBars]: fewer for a big shift of tempo. */
    fun barsFor(outgoing: BeatEnd, incoming: BeatEnd, blendBars: Int): Int {
        val shift = speed(outgoing, incoming)?.let { abs(it - 1.0) } ?: return blendBars
        return if (shift > BIG_SHIFT) minOf(blendBars, BIG_SHIFT_BARS) else blendBars
    }
    /** Closer than this, the two songs count as in step. */
    const val IN_STEP_MS = 12L
    /** Further apart than this, the next song jumps into step rather than catching up by speed. */
    const val JUMP_MS = 150L
    /** This far behind, the next song stalled loading (issue #807): worth a jump even after the one allowed. */
    const val STALL_MS = 400L
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

    /**
     * A track made on a grid (issue #820): its tempo is a whole number of BPM (a DAW's), to [GRID_BPM_TOLERANCE],
     * and its beat is steady. Such a track's downbeats and phrases are counted from its first beat, as DJ software
     * assumes, so they're known even where the plugin couldn't hear them.
     */
    fun onAGrid(end: BeatEnd): Boolean =
        end.bpm > 0 && abs(end.bpm - end.bpm.roundToLong()) <= GRID_BPM_TOLERANCE && (end.steady >= MIN_STEADY || end.grid)

    /**
     * [end] taken as a track made on a grid (0.52.0-dev): a tempo read within [GRID_SNAP_BPM] of a whole number, with
     * a beat that keeps to it within about 14 ms ([GRID_MIN_STEADY]), is that whole number, and its beat counts as
     * steady. House and drum and bass read 0.34 to 0.38 steady (beats 12 ms off a straight line, the plugin's reading
     * of busy drums, not a wandering tempo) and 121.9 or 124.1 BPM, so they were never beat-matched and got a join of
     * a few seconds. The new audio engine keeps both songs on one clock and checks the beats through the blend, so
     * the old, stricter bar (two players drifting apart) isn't needed for them. Unchanged otherwise.
     */
    fun gridded(end: BeatEnd): BeatEnd {
        if (end.grid || end.bpm <= 0 || end.steady >= MIN_STEADY || end.steady < GRID_MIN_STEADY) return end
        val whole = end.bpm.roundToLong()
        if (abs(end.bpm - whole) > GRID_SNAP_BPM) return end
        return end.copy(bpm = whole.toDouble(), grid = true)
    }

    /**
     * The end of a song going out with its bars and 8-bar phrases assumed from the song's grid (issue #820), when
     * the plugin found none and the song is [onAGrid] at both ends at one tempo: the first beat (or the first
     * downbeat, when found) is a downbeat and a phrase start, and the beat at the end is a whole number of beats
     * after it, else nothing is assumed. A downbeat the plugin found at the end is kept, and must sit on the
     * counted bars for the phrases to be assumed. Unchanged when nothing is missing or the song isn't on a grid.
     */
    fun assumedGrid(start: BeatEnd?, end: BeatEnd): BeatEnd {
        if (end.barMs != null && end.phraseMs != null) return end
        if (start == null || !onAGrid(end) || !onAGrid(start) || start.bpm.roundToLong() != end.bpm.roundToLong()) return end
        val beat = 60_000.0 / end.bpm.roundToLong()
        val first = start.barMs ?: start.beatMs
        val counted = (end.beatMs - first) / beat
        val beats = counted.roundToLong()
        if (beats < BEATS_PER_BAR || abs(counted - beats) > GRID_BEAT_TOLERANCE) return end
        val phraseBeats = ASSUMED_PHRASE_BARS * BEATS_PER_BAR
        // The last downbeat and phrase start at or before the end's beat, counted from the first.
        val bar = end.barMs ?: (end.beatMs - (beats % BEATS_PER_BAR) * beat)
        val barBeats = ((bar - first) / beat).roundToLong()
        if (barBeats % BEATS_PER_BAR != 0L) return end  // the plugin's downbeat isn't on the counted bars: the first beat wasn't one
        val phrase = end.phraseMs ?: (bar - (barBeats % phraseBeats) * beat)
        return end.copy(barMs = bar, phraseMs = phrase, phraseBars = end.phraseBars ?: ASSUMED_PHRASE_BARS, assumed = true)
    }

    /**
     * The start of the next song with its first downbeat and phrase assumed to be its first beat (issue #820),
     * when the plugin found none and the song is [onAGrid]: as DJ software assumes of a track made on a grid.
     */
    fun assumedIntro(intro: BeatEnd): BeatEnd {
        if (intro.barMs != null && intro.phraseMs != null) return intro
        if (!onAGrid(intro) || intro.beatMs < 0) return intro
        val bar = intro.barMs ?: intro.beatMs
        return intro.copy(barMs = bar, phraseMs = intro.phraseMs ?: bar, phraseBars = intro.phraseBars ?: ASSUMED_PHRASE_BARS, assumed = true)
    }

    /** A tempo this close to a whole number of BPM, steady, is a grid's (issue #820). */
    const val GRID_BPM_TOLERANCE = 0.05
    /** A tempo read this close to a whole number, its beat [GRID_MIN_STEADY] steady, is taken as a grid's ([gridded]). */
    const val GRID_SNAP_BPM = 0.15
    const val GRID_MIN_STEADY = 0.3f
    /** The end's beat must be this close to a whole number of beats from the first for the grid to be assumed. */
    const val GRID_BEAT_TOLERANCE = 0.3
    /** Phrases assumed of a track on a grid: 8 bars, the common unit of house and techno. */
    const val ASSUMED_PHRASE_BARS = 8

    /** Both ends are beat-driven and steady: a blend between them either keeps the beats in step or clashes. */
    fun bothRhythmic(outgoing: BeatEnd?, incoming: BeatEnd?): Boolean = outgoing?.matchable == true && incoming?.matchable == true

    /**
     * The end keeps a beat, steady or not (issue #796): drum and bass breaks often read as unsteady (or at the wrong
     * tempo), so they can't be matched, but two of them laid on each other out of step still clash.
     */
    fun beatDriven(end: BeatEnd?): Boolean = end != null && end.bpm > 0 && end.rhythm >= MIN_RHYTHM

    /** No beat at all at this end (the plugin found none, or barely one): a long blend over it can't clash. */
    fun beatless(end: BeatEnd?): Boolean = end == null || end.bpm <= 0 || end.rhythm < SOME_BEAT

    /** Some beat at this end, if not beat-driven: two of them laid on each other out of step still clash. */
    fun someBeat(end: BeatEnd?): Boolean = !beatless(end)

    /**
     * Below this [BeatEnd.rhythm] an end has no beat to speak of; from it to [MIN_RHYTHM] it has some (a house remix's
     * outro read 0.46: 0.52.0-dev).
     */
    const val SOME_BEAT = 0.25f

    /** Both ends keep a beat, steady or not: an unmatched blend between them clashes, so it's kept short. */
    fun wouldClash(outgoing: BeatEnd?, incoming: BeatEnd?): Boolean = beatDriven(outgoing) && beatDriven(incoming)

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
                    phraseBars != null -> "phrase on phrase ($phraseBars-bar phrases${if (outgoing.assumed || incoming.assumed) ", assumed" else ""})"
                    onBars -> "bar on bar${if (outgoing.assumed || incoming.assumed) " (assumed)" else ""}"
                    else -> "beat on beat"
                },
                if (cutMs > 0) ", let go at the end of its last phrase (%.1f s left out)".format(java.util.Locale.ROOT, cutMs / 1000.0) else "",
            )
    }

    /**
     * Plans the blend from [outgoing]'s end at [endMs] (its length, less any end left out) into [incoming], within
     * [maxMs] (from the blend length setting, [maxMsFor]). Null when the two can't be matched, or when not even one bar fits.
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
        // On the grid at or before [wanted], so the blend has its whole bars, when that still fits [maxMs]; else the
        // grid point after it. A blend that would then be shorter than a bar takes the point before when that
        // nearly fits. At least a beat.
        var start = grid + floor((wanted - grid) / step) * step
        if (endMs - start > maxMs) start += step
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

        /** The smoothed errors ([SmoothedError]), which decide jumps and nudges: single readings can be 90 ms off. */
        private val settled = ArrayList<Long>()

        fun add(errorMs: Long) {
            checks++
            total += abs(errorMs)
            worst = maxOf(worst, abs(errorMs))
        }

        fun addSettled(errorMs: Long) {
            settled += abs(errorMs)
        }

        /**
         * "beats typically 6 ms apart, at worst 18 ms (412 checks)", and from the smoothed errors (issue #796) how far
         * apart they mostly were and how much of the blend they were within [AUDIBLE_MS]: the single readings jump
         * about, so the typical figure alone didn't tell a blend out of step from a noisy one.
         */
        fun describe(): String {
            if (checks == 0) return "beats not checked"
            val raw = "beats typically ${total / checks} ms apart, at worst $worst ms ($checks checks)"
            if (settled.isEmpty()) return raw
            val middle = settled.sorted()[settled.size / 2]
            val within = settled.count { it <= AUDIBLE_MS } * 100 / settled.size
            return "$raw; smoothed, $middle ms apart, within $AUDIBLE_MS ms $within % of the time"
        }
    }

    /** Beats further apart than this are heard as two (a flam): DJs keep within about 10 to 20 ms. */
    const val AUDIBLE_MS = 20L

    /** How long getting the next song to a point takes at first, before Siansa has measured it on this phone (issue
     *  #747). Phones do it in 20 to 70 ms (issue #773: 150 sent the song ahead by a jump's worth). */
    const val DEFAULT_SEEK_LAG_MS = 60L
    /** Longer than this, a measured lag is a stall, not the phone's usual delay. */
    const val MAX_SEEK_LAG_MS = 300L
    /** A lead this far off what the phone actually takes is a bad reading, not a lesson. */
    const val LAG_LESSON_MS = 150L

    /**
     * What a jump costs on this phone, learned from the last one (issue #747): the next song was sent [leadMs] ahead
     * of where it should be and, once settled, was [errorMs] out of step (behind is negative), so the jump took
     * [leadMs] − [errorMs]. Averaged with [previous], so one slow jump doesn't throw the next one off. A jump that
     * took longer than any jump does ([MAX_SEEK_LAG_MS]) was a stall (the song buffering), not the phone's usual
     * delay: [previous] stays.
     */
    fun learnedLag(previous: Long, leadMs: Long, errorMs: Long): Long {
        val took = leadMs - errorMs
        // A stall, or a reading taken once the song had already drifted (issue #773): nothing to learn.
        if (took > MAX_SEEK_LAG_MS || abs(took - previous) > LAG_LESSON_MS) return previous
        return ((previous + took) / 2).coerceIn(0L, MAX_SEEK_LAG_MS)
    }

    /** The most a nudge may change the speed: DJ software syncs within about 1 %, and more is heard as pitch. */
    const val NUDGE_MAX = 0.01f
    /** A nudge brings the song in step over about this long; quicker swings are heard as warble (issue #773). */
    const val NUDGE_OVER_MS = 2_500f

    /** The speed nudge that brings the next song back in step over a couple of seconds: ahead, a touch slower. */
    fun nudge(errorMs: Long): Float =
        if (abs(errorMs) <= IN_STEP_MS) 1f else (1f - errorMs / NUDGE_OVER_MS).coerceIn(1f - NUDGE_MAX, 1f + NUDGE_MAX)

    /**
     * Whether a changed speed is worth sending to the player (issue #773): every change re-tunes the time-stretcher
     * and is heard as a flutter, so a nudge within [NUDGE_STEP] of the one playing is left as it is.
     */
    const val NUDGE_STEP = 0.0015f
    /**
     * How a player plays at [speed] during and after a blend (issue #816): resampled, so the pitch follows the
     * speed like a DJ's pitch fader, unless the listener keeps the key (time-stretched, which can stutter on
     * drums). The player's own speed ([baseSpeed], 1 for music) stays stretched: only the blend's shift moves the
     * pitch, by at most 0.7 of a semitone at 4 %.
     */
    fun parameters(speed: Float, baseSpeed: Float, keepKey: Boolean): PlaybackParameters {
        val pitch = if (keepKey || baseSpeed <= 0f) 1f else speed / baseSpeed
        return PlaybackParameters(speed, pitch)
    }

    fun worthChanging(playing: Float, wanted: Float): Boolean = abs(playing - wanted) >= NUDGE_STEP

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

    /**
     * The speed to send while easing back after a blend, [elapsedMs] after it ended, with the song playing at
     * [playing] (both times [base], the listener's own speed); null when it isn't worth a change yet (issue #796).
     * Every change re-tunes the time-stretcher and is heard (issue #773), so the ease goes in steps of [NUDGE_STEP]
     * at least: about 20 for a 3 % match, where a change every 200 ms made 125 of them.
     */
    fun easeStep(matched: Float, elapsedMs: Long, playing: Float, base: Float = 1f): Float? {
        if (elapsedMs >= EASE_MS) return if (playing != base) base else null
        val wanted = base * eased(matched, elapsedMs)
        return wanted.takeIf { abs(playing - it) >= NUDGE_STEP * base }
    }

    /** The speed [elapsedMs] after the blend ended: from [matched] back to 1, a straight line over [EASE_MS]. */
    fun eased(matched: Float, elapsedMs: Long): Float {
        val t = (elapsedMs.toDouble() / EASE_MS).coerceIn(0.0, 1.0)
        return (matched + (1f - matched) * t).toFloat()
    }
}
