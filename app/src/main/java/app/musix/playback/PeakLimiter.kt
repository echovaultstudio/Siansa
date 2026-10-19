package app.musix.playback

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps peaks under [ceiling] by turning the sound down smoothly just before them and back up afterwards,
 * instead of cutting the tops off (which is what crackles). Looks [LOOKAHEAD_MS] ahead, so every sample
 * comes out that much later; all channels move together so the stereo image stays put. Audio that never
 * goes over the ceiling passes through unchanged.
 */
class PeakLimiter(sampleRate: Int, private val channels: Int, private val ceiling: Double = 1.0) {
    private val lookahead = max(1, sampleRate * LOOKAHEAD_MS / 1_000)
    private val window = lookahead + 1
    private val release = 1 - exp(-1.0 / (sampleRate * RELEASE_MS / 1_000.0))

    // The last [window] frames, waiting to come out.
    private val delay = DoubleArray(window * channels)
    // The gain each of those frames needs on its own, and the smallest of them (a running minimum).
    private val needed = DoubleArray(window) { 1.0 }
    private val minQueue = IntArray(window)
    private var minHead = 0
    private var minSize = 0
    // The smoothed gain over the same frames, and their sum for the running average.
    private val envelope = DoubleArray(window) { 1.0 }
    private var envelopeSum = window.toDouble()
    private var held = 1.0
    private var pos = 0

    /** The deepest the sound was turned down since [takeDeepestDb] was last called, in dB (0 = never). */
    private var deepest = 1.0

    /** Takes one frame of [channels] samples in [samples] and replaces it with the frame from [LOOKAHEAD_MS] ago. */
    fun process(samples: DoubleArray) {
        var peak = 0.0
        for (c in 0 until channels) peak = max(peak, abs(samples[c]))
        val need = if (peak > ceiling) ceiling / peak else 1.0

        // Running minimum of the needed gain over the last [window] frames. The slot about to be
        // overwritten holds the frame that just left the window.
        if (minSize > 0 && minQueue[minHead] == pos) { minHead = (minHead + 1) % window; minSize-- }
        while (minSize > 0 && needed[minQueue[(minHead + minSize - 1) % window]] >= need) minSize--
        needed[pos] = need
        minQueue[(minHead + minSize) % window] = pos
        minSize++
        val lowest = needed[minQueue[minHead]]

        // Down at once, back up slowly; then averaged over the window so the gain glides into each peak.
        held = if (lowest < held) lowest else held + (lowest - held) * release
        envelopeSum += held - envelope[pos]
        envelope[pos] = held
        // Rounding in the running sum must never nudge untouched audio.
        val gain = (envelopeSum / window).let { if (it > 1 - 1e-9) 1.0 else it }
        deepest = min(deepest, gain)
        framesSeen++
        if (gain < LIMITING) framesLimited++

        // Swap in the frame from [lookahead] frames ago, scaled.
        val outSlot = (pos + 1) % window
        val base = pos * channels
        val outBase = outSlot * channels
        for (c in 0 until channels) {
            delay[base + c] = samples[c]
            samples[c] = (delay[outBase + c] * gain).coerceIn(-ceiling, ceiling)
        }
        pos = outSlot
    }

    fun takeDeepestDb(): Double {
        songDeepest = min(songDeepest, deepest)
        return (20 * log10(deepest)).also { deepest = 1.0 }
    }

    // For the per-song note in the log (issue #159): how much of the time the limiter was turning sound down.
    private var framesSeen = 0L
    private var framesLimited = 0L
    private var songDeepest = 1.0

    /** Since the last call: the share of time spent turning peaks down (0..1), and the deepest it went, in dB. */
    fun takeSongStats(): Pair<Double, Double>? {
        songDeepest = min(songDeepest, deepest)
        if (framesSeen == 0L) return null
        val share = framesLimited.toDouble() / framesSeen
        val db = 20 * log10(songDeepest)
        framesSeen = 0
        framesLimited = 0
        songDeepest = 1.0
        return share to db
    }

    /** Forgets what is waiting, e.g. after a seek. */
    fun reset() {
        delay.fill(0.0)
        needed.fill(1.0)
        envelope.fill(1.0)
        envelopeSum = window.toDouble()
        minHead = 0
        minSize = 0
        held = 1.0
        pos = 0
    }

    /** How many frames are still inside, to let out at the end of a song. */
    val latencyFrames: Int get() = lookahead

    companion object {
        const val LOOKAHEAD_MS = 2
        const val RELEASE_MS = 80
        /** Below this gain (about -0.01 dB) the limiter counts as working. */
        const val LIMITING = 0.999
    }
}
