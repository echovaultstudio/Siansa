package app.siansa.playback

import app.siansa.mix.camelot
import app.siansa.mix.keyMatch
import kotlin.math.abs

/**
 * How a DJ blend is made (the owner, 9 Nov: "smart crossfades need to be smarter", not the same bass swap every time).
 * Smart picks one for each change in DJ playback ([pick]); Fixed always uses [BASS_SWAP].
 */
enum class Technique(val label: String) {
    /** 32 bars, both songs playing long together, the bass swapped in the middle: close tempos, keys that mix, ends with room. */
    LONG_BLEND("long blend"),
    /** The usual: 16 bars, the bass swapped over the first half. */
    BASS_SWAP("bass swap"),
    /** 4 bars, the bass swapped at once: keys that clash, a beat not sure enough, or no room left for a longer blend. */
    QUICK_SWAP("quick swap"),
    /** 16 bars, the old song swept out with a rising high-pass filter: cooling down, or keys that only half mix. */
    FILTER_FADE("filter fade"),
    /** The next song comes in so its drop lands where the blend ends, the old song's bass kept until then. */
    DROP_SWAP("drop swap"),
    /** Tempos too far apart to match: the old song's last bar echoes away on its own beat as the next one starts. */
    ECHO_OUT("echo out"),
}

/** What [TechniquePicker.pick] chose, with how many bars and why (for the log). */
data class TechniqueChoice(val technique: Technique, val bars: Int, val why: String, val dropAtMs: Long? = null)

object TechniquePicker {
    /** The most bars a blend can have ([Technique.LONG_BLEND]): a song's blend is planned early enough for these. */
    const val LONGEST_BARS = 32

    /**
     * The bars to look ahead for when planning a blend out of a song: Smart may pick a long blend whatever the
     * listener's own number of bars, so it's planned early enough for the longest. Planned for the listener's bars
     * only (16 by default), a 32-bar blend was always found "too close to the end" and the songs changed with no
     * blend at all (the owner's log, 12 Nov).
     */
    fun lookBars(smart: Boolean, blendBars: Int): Int = if (smart) maxOf(blendBars, LONGEST_BARS) else blendBars

    /** Ends this sure of their beat (Siansa Beats' share on the grid, inside the lock) can run long. */
    const val SURE = 0.95f
    /** Below this, a blend is kept quick. */
    const val UNSURE = 0.9f
    /** A tempo shift this small is heard as none over a long blend. */
    const val LONG_SHIFT = 0.02
    /** Keys mixing this well run long or as usual ([keyMatch]: the same 1, wheel neighbours 0.9, relative 0.85). */
    const val KEYS_MIX = 0.85
    /** Below this, the keys clash ([keyMatch]: 0.5 two steps, 0.4 a step and the other mode, 0.1 far). */
    const val KEYS_CLASH = 0.45
    /** An end this many bars without bass has room for a long blend: the two basslines never meet. */
    const val ROOMY_BARS = 8
    /** The next song's intro this many dB quieter than the end going out: the energy goes down, a filter fade. */
    const val COOLING_DB = 3.0
    /** A drop swap is this many bars, the most that fit between the next song's lock and its drop. */
    val DROP_BARS = listOf(16, 8)
    /** A drop further into the next song than this (bars from its lock) would skip too much of it. */
    const val DROP_WITHIN_BARS = 64

    /**
     * The technique for a beat-matched blend from [outgoing] into [incoming] (both matchable, at [speed]), from
     * what's known of them: [keys] how well their keys mix (null unknown), their beat confidence, the bass at their
     * ends and their loudness, [incomingDrops] (ms), whether either is from a mixed CD ([mix]), whether there's no
     * room left for a longer blend before the song ends ([noRoom]: the listener moved near its end), and the technique
     * used last ([last]: the next best is taken rather than the same twice running, when one fits).
     *
     * Moving in the song doesn't force a quick swap by itself any more (the owner's sessions, 12 Nov: with the plan made
     * a minute before the end, every jump into the last minute landed on it and every blend was a 4-bar swap): the
     * blend picked is kept whenever it still fits, and [noRoom] is only set when it doesn't.
     */
    fun pick(
        outgoing: BeatEnd,
        incoming: BeatEnd,
        speed: Float,
        keys: Double?,
        incomingDrops: List<Long> = emptyList(),
        mix: Boolean = false,
        noRoom: Boolean = false,
        last: Technique? = null,
        vocals: Int? = null,
    ): TechniqueChoice {
        val options = options(outgoing, incoming, speed, keys, incomingDrops, mix, noRoom, vocals)
        // The first is the best fit; a change of technique only among the ones that fit as well (not a forced one).
        val first = options.first()
        if (first.technique == last && !first.forced) options.drop(1).firstOrNull { !it.forced && it.technique != last }?.let { return it.choice }
        return first.choice
    }

    private class Option(val choice: TechniqueChoice, val forced: Boolean) {
        val technique get() = choice.technique
    }

    private fun options(
        outgoing: BeatEnd,
        incoming: BeatEnd,
        speed: Float,
        keys: Double?,
        drops: List<Long>,
        mix: Boolean,
        noRoom: Boolean,
        vocals: Int?,
    ): List<Option> {
        val sure = minOf(outgoing.rhythm, incoming.rhythm)
        val shift = abs(speed - 1.0)
        val out = ArrayList<Option>()
        fun forced(t: Technique, bars: Int, why: String) = out.add(Option(TechniqueChoice(t, bars, why), forced = true))
        fun fits(t: Technique, bars: Int, why: String, dropAt: Long? = null) = out.add(Option(TechniqueChoice(t, bars, why, dropAt), forced = false))
        // Cases that leave no choice.
        if (noRoom) forced(Technique.QUICK_SWAP, 4, "no room left for a longer blend")
        if (mix) forced(Technique.BASS_SWAP, BeatMatch.MIX_TRACK_BARS, "a track from a mixed CD: inside its lock")
        if (keys != null && keys < KEYS_CLASH) forced(Technique.QUICK_SWAP, 4, "the keys clash")
        if (sure < UNSURE) forced(Technique.QUICK_SWAP, 4, "the beat is only %.0f %% sure".format(java.util.Locale.ROOT, sure * 100))
        if (out.isNotEmpty()) return out
        // The next song's first drop, at least 8 bars after where it locks in and within [DROP_WITHIN_BARS]: the
        // next song comes in 16 (or 8) bars before it, so it lands where the old song goes.
        val bar = incoming.beatLengthMs * BeatMatch.BEATS_PER_BAR
        val from = incoming.lockMs ?: incoming.barMs ?: incoming.beatMs
        val first = drops.firstOrNull { d -> d - from >= DROP_BARS.last() * bar - bar / 4 && d - from <= DROP_WITHIN_BARS * bar }
        val drop = first?.let { d -> DROP_BARS.first { (d - from) >= it * bar - bar / 4 } to d }
        val keysMix = keys == null || keys >= KEYS_MIX
        val roomy = (outgoing.bassBars ?: 0) >= ROOMY_BARS || (incoming.bassBars ?: 0) >= ROOMY_BARS
        val cooling = (incoming.energyDb ?: 0.0) <= (outgoing.energyDb ?: 0.0) - COOLING_DB
        // Where both songs sing right up to the change (Siansa Beats' vocals, 0.60), a DJ keeps it short: the voices swap
        // within a few bars instead of being cut off one under the other for long.
        if (vocals != null && vocals < SINGING_EDGES_BARS) {
            fits(Technique.QUICK_SWAP, 4, "both sing right up to the change: the voices swap within 4 bars")
        }
        // A long blend only where the two voices won't meet in it (unknown: as before).
        val voicesFit = vocals == null || vocals >= LONGEST_BARS
        if (shift <= LONG_SHIFT && keysMix && sure >= SURE && roomy && voicesFit) {
            fits(Technique.LONG_BLEND, LONGEST_BARS, "close tempos, keys that mix, and room without bass at an end" +
                if (vocals != null) ", with no two voices at once" else "")
        }
        if (drop != null && !cooling) {
            fits(Technique.DROP_SWAP, drop.first, "the next song's drop lands where the old one goes", drop.second)
        }
        if (cooling || !keysMix) {
            fits(Technique.FILTER_FADE, 16, if (cooling) "the next song starts quieter: the energy goes down" else "the keys only half mix")
        }
        fits(Technique.BASS_SWAP, 16, "the usual")
        if (!cooling && keysMix) fits(Technique.FILTER_FADE, 16, "for a change")
        return out
    }

    /** Fewer bars than this without a voice, both ends together: both sing right up to the change. */
    const val SINGING_EDGES_BARS = 4

    /**
     * The bars without a voice where two songs meet ([SongVocals]): the old song's after its last vocals and the next
     * song's before its first, together; [Int.MAX_VALUE] when either has none (no two voices can meet); null when it
     * isn't known for both.
     */
    fun vocalRoom(outgoing: SongVocals?, incoming: SongVocals?): Int? {
        if (outgoing == null || incoming == null) return null
        if (outgoing.none || incoming.none) return Int.MAX_VALUE
        val out = outgoing.outroFreeBars ?: return null
        val inc = incoming.introFreeBars ?: return null
        return out + inc
    }

    /** How well two songs' keys (AudioMuse-AI's) mix, or null when either isn't known. */
    fun keys(a: TrackProfile?, b: TrackProfile?): Double? {
        val x = camelot(a?.key, a?.scale) ?: return null
        val y = camelot(b?.key, b?.scale) ?: return null
        return keyMatch(x, y)
    }
}
