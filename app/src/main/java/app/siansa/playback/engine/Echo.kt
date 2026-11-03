package app.siansa.playback.engine

import kotlin.math.PI
import kotlin.math.exp

/**
 * The echo at the end of a blend in the new audio engine (issue #759, #826): a delay on the beat, fed back into
 * itself, as a DJ echoes a track out. Over the song's last bar more and more of it goes to the echo ([send]); then the
 * song is gone and its last beats repeat on the beat, fainter each time and darker, as a tape or a DJ mixer's echo
 * does, until they've died away. The repeats are thinned below [LOW_CUT_HZ] so they never muddy the next song's bass.
 *
 * It takes the song before its volume (a send before the fader), so it rings on after the fader is down. All of it
 * runs on the mixer's thread; [send], [delayMs] and [feedback] may be set from any thread.
 */
class Echo(private val outRate: Int) {
    /** How much of the song goes to the echo (0..1); the mixer glides to it over a block. */
    @Volatile var send = 0f
    /** The time between repeats, ms: a beat of the song going out. */
    @Volatile var delayMs = 500.0
    /** How much of each repeat comes back in the next (0..1). */
    @Volatile var feedback = FEEDBACK

    private val ring = FloatArray(MAX_DELAY_MS * outRate / 1_000 * 2)
    private val frames = ring.size / 2
    private var pos = 0
    private var sendNow = 0f
    // The repeats' filters: a high-pass (thinner) and a low-pass (darker), one pole each, per channel.
    private val hpA = exp(-2 * PI * LOW_CUT_HZ / outRate).toFloat()
    private val lpA = exp(-2 * PI * HIGH_CUT_HZ / outRate).toFloat()
    private val hpIn = FloatArray(2)
    private val hpOut = FloatArray(2)
    private val lp = FloatArray(2)
    private var quietFrames = Long.MAX_VALUE / 2

    /** Still sounding: something was sent, and the repeats haven't died away. */
    val ringing: Boolean get() = sendNow > 0f || send > 0f || quietFrames < outRate.toLong() * QUIET_MS / 1_000

    /** Silent at once, the repeats gone (a new blend starts clean). */
    fun clear() {
        java.util.Arrays.fill(ring, 0f)
        hpIn.fill(0f); hpOut.fill(0f); lp.fill(0f)
        send = 0f
        sendNow = 0f
        quietFrames = Long.MAX_VALUE / 2
    }

    /**
     * Echoes [input] (stereo, interleaved, the song before its volume; null for none) into [out] for [count] frames:
     * the repeats are added to [out], [input] is left as it is.
     */
    fun process(input: FloatArray?, out: FloatArray, count: Int) {
        val delay = (delayMs * outRate / 1_000).toInt().coerceIn(1, frames - 1)
        val s0 = sendNow
        val s1 = send.coerceIn(0f, 1f)
        sendNow = s1
        val fb = feedback.coerceIn(0f, 0.9f)
        var peak = 0f
        for (i in 0 until count) {
            val g = s0 + (s1 - s0) * (i + 1).toFloat() / count
            val read = ((pos - delay + frames) % frames) * 2
            val write = pos * 2
            for (c in 0..1) {
                val repeat = ring[read + c]
                out[i * 2 + c] += repeat
                peak = maxOf(peak, kotlin.math.abs(repeat))
                // What goes back in: the song as sent, and the repeat, fainter, thinner and darker.
                var x = (input?.get(i * 2 + c) ?: 0f) * g + repeat * fb
                val h = hpA * (hpOut[c] + x - hpIn[c])
                hpIn[c] = x
                hpOut[c] = h
                lp[c] = h + lpA * (lp[c] - h)
                x = lp[c]
                ring[write + c] = if (kotlin.math.abs(x) < 1e-9f) 0f else x
            }
            pos = (pos + 1) % frames
        }
        quietFrames = if (peak > QUIET_PEAK || s1 > 0f) 0 else quietFrames + count
    }

    companion object {
        const val MAX_DELAY_MS = 2_000
        /** Each repeat is this much of the one before: about 7 dB fainter, gone in a few seconds. */
        const val FEEDBACK = 0.45f
        const val LOW_CUT_HZ = 300.0
        const val HIGH_CUT_HZ = 6_000.0
        /** Quieter than this (−80 dBFS) for [QUIET_MS], the repeats have died away. */
        const val QUIET_PEAK = 0.0001f
        const val QUIET_MS = 300L
    }
}
