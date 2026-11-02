package app.siansa.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The bass swap of a beat-matched blend (issue #807), the way a DJ mixes with the EQ: the low end of the song going
 * out is taken away over the first half of the blend (a high-pass filter sweeping up from nothing to [LOW_CUT_HZ])
 * while the next song's low end is held back and let in over the same time (a low shelf rising from
 * [SHELF_START_DB] to nothing). Two kick drums a few ms apart are what a blend out of step is heard as; one at a
 * time, the join is a mix and not a flam.
 *
 * One of these sits in each player's audio path ([EqualiserProcessor]), after the equaliser and before the
 * limiter. [Crossfader] sets [lowCutHz] and [shelfDb] from the fade's ticks; the player thread reads them with each
 * buffer and moves its filters there in small steps ([SLEW]), so a step can't click. At rest both are off and the
 * sound passes untouched.
 */
class DeckFilter {
    /** High-pass cut-off in Hz; 0 (or less) is off. */
    @Volatile var lowCutHz: Double = 0.0
    /** Low-shelf gain in dB at [SHELF_HZ]; 0 is off, negative holds the bass back. */
    @Volatile var shelfDb: Double = 0.0

    /** Where the player thread's filters are now, gliding towards the targets above. */
    private var cutNow = 0.0
    private var shelfNow = 0.0
    private var cutFilter: Biquad? = null
    private var shelfFilter: Biquad? = null
    private var sampleRate = 0
    private var channels = 0
    // Filter memory, one pair per filter per channel (transposed direct form II).
    private var z = DoubleArray(0)

    /** Both at rest: the sound passes untouched, as before a blend. */
    val idle: Boolean get() = lowCutHz <= 0.0 && shelfDb == 0.0 && cutNow <= 0.0 && shelfNow == 0.0

    fun reset() {
        lowCutHz = 0.0
        shelfDb = 0.0
        cutNow = 0.0
        shelfNow = 0.0
        cutFilter = null
        shelfFilter = null
        z.fill(0.0)
    }

    /**
     * Called once per buffer on the player thread: follows the targets a step and rebuilds the filters when they
     * moved. Returns whether there is anything to apply.
     */
    fun prepare(sampleRate: Int, channels: Int): Boolean {
        if (this.sampleRate != sampleRate || this.channels != channels) {
            this.sampleRate = sampleRate
            this.channels = channels
            z = DoubleArray(4 * channels)
            cutFilter = null
            shelfFilter = null
        }
        val cutTarget = lowCutHz.coerceIn(0.0, LOW_CUT_HZ)
        val shelfTarget = shelfDb.coerceIn(SHELF_START_DB, 0.0)
        val cutBefore = cutNow
        val shelfBefore = shelfNow
        cutNow = slew(cutNow, cutTarget, LOW_CUT_HZ * SLEW)
        shelfNow = slew(shelfNow, shelfTarget, -SHELF_START_DB * SLEW)
        if (cutNow != cutBefore || cutFilter == null) cutFilter = if (cutNow >= MIN_CUT_HZ) Biquad.highPass(cutNow, sampleRate) else null
        if (shelfNow != shelfBefore || shelfFilter == null) shelfFilter = if (abs(shelfNow) >= 0.1) Biquad.lowShelf(SHELF_HZ, shelfNow, sampleRate) else null
        if (cutFilter == null) z.fill(0.0, 0, 2 * channels)
        if (shelfFilter == null) z.fill(0.0, 2 * channels, 4 * channels)
        return cutFilter != null || shelfFilter != null
    }

    private fun slew(now: Double, target: Double, step: Double): Double =
        if (abs(target - now) <= step) target else now + if (target > now) step else -step

    /** One sample of [channel], after [prepare]. */
    fun process(sample: Double, channel: Int): Double {
        var x = sample
        cutFilter?.let { x = run(it, x, channel) }
        shelfFilter?.let { x = run(it, x, 2 * channels + channel) }
        return x
    }

    private fun run(f: Biquad, x: Double, state: Int): Double {
        val y = f.b0 * x + z[state]
        z[state] = f.b1 * x - f.a1 * y + z[state + channels]
        z[state + channels] = f.b2 * x - f.a2 * y
        return y
    }

    /** For the log: "low cut 180 Hz, bass -9 dB". */
    fun describe(): String = "low cut %.0f Hz, bass %+.0f dB".format(java.util.Locale.ROOT, cutNow, shelfNow)

    companion object {
        /** Where the high-pass ends up at the end of the sweep: the kick and bass gone, the rest kept. */
        const val LOW_CUT_HZ = 200.0
        /** A sweep below this is as good as off. */
        const val MIN_CUT_HZ = 20.0
        /** The low shelf's turnover, and how far the next song's bass starts held back. */
        const val SHELF_HZ = 150.0
        const val SHELF_START_DB = -12.0
        /** How much of the whole range a filter may move per buffer (buffers are 5–20 ms): a sweep of the whole
         *  range takes at least 25 buffers, so the fade's own ticks set the pace. */
        const val SLEW = 0.04

        /**
         * The high-pass target [t] of the way through the swap (0..1): a sweep from nothing up to [LOW_CUT_HZ],
         * quick at first (where it takes nothing but rumble) and easing in at the top.
         */
        fun lowCutAt(t: Double): Double = LOW_CUT_HZ * sin(t.coerceIn(0.0, 1.0) * PI / 2)

        /** The next song's shelf [t] of the way through the swap: from [SHELF_START_DB] back up to nothing. */
        fun shelfAt(t: Double): Double = SHELF_START_DB * (1 - t.coerceIn(0.0, 1.0))
    }
}

/** A second-order high-pass (Audio EQ Cookbook), Butterworth, normalised so a0 = 1. */
fun Biquad.Companion.highPass(hz: Double, sampleRate: Int): Biquad {
    val w0 = 2 * PI * hz / sampleRate
    val alpha = sin(w0) / (2 * (1 / sqrt(2.0)))
    val c = cos(w0)
    val a0 = 1 + alpha
    return Biquad(
        b0 = ((1 + c) / 2) / a0,
        b1 = -(1 + c) / a0,
        b2 = ((1 + c) / 2) / a0,
        a1 = -2 * c / a0,
        a2 = (1 - alpha) / a0,
    )
}

/** A low shelf (Audio EQ Cookbook) of [gainDb] below [hz], normalised so a0 = 1. */
fun Biquad.Companion.lowShelf(hz: Double, gainDb: Double, sampleRate: Int): Biquad {
    val a = 10.0.pow(gainDb / 40)
    val w0 = 2 * PI * hz / sampleRate
    val c = cos(w0)
    val alpha = sin(w0) / 2 * sqrt(2.0)  // shelf slope 1
    val s = 2 * sqrt(a) * alpha
    val a0 = (a + 1) + (a - 1) * c + s
    return Biquad(
        b0 = (a * ((a + 1) - (a - 1) * c + s)) / a0,
        b1 = (2 * a * ((a - 1) - (a + 1) * c)) / a0,
        b2 = (a * ((a + 1) - (a - 1) * c - s)) / a0,
        a1 = (-2 * ((a - 1) + (a + 1) * c)) / a0,
        a2 = ((a + 1) + (a - 1) * c - s) / a0,
    )
}
