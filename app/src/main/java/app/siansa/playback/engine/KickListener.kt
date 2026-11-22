package app.siansa.playback.engine

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Hears the kicks in one deck's sound while a DJ blend plays (the owner's DJ plan, siansa-dev #63: "beat grid check per
 * blend"). The engine lines the two songs up by their beat grids, and its own check measures each deck against its
 * grid, so it reads 0 ms apart even when a grid is wrong. This listens to the sound itself: the low end (kick and
 * bass) is followed, and each sharp rise is a kick, at the mix frame it was heard.
 *
 * Fed on the audio thread, block by block ([feed]); read on the main thread ([take]). Nothing is allocated while fed.
 */
class KickListener(sampleRate: Int) {
    private val low = 1 - exp(-2 * PI * LOW_HZ / sampleRate)
    private val attack = 1 - exp(-1.0 / (ATTACK_S * sampleRate))
    private val release = 1 - exp(-1.0 / (RELEASE_S * sampleRate))
    private val slowRate = 1 - exp(-1.0 / (SLOW_S * sampleRate))
    private val refractory = (REFRACTORY_S * sampleRate).toLong()
    private val warmUp = (SLOW_S * sampleRate).toLong()
    private var fed = 0L
    private var lp1 = 0.0
    private var lp2 = 0.0
    private var env = 0.0
    private var slow = 0.0
    private var above = false
    private var last = Long.MIN_VALUE / 2
    private val heard = LongArray(CAPACITY)
    private val count = AtomicInteger(0)
    private var read = 0

    /** [frames] stereo frames of the deck's sound (interleaved, before its volume and filter), from mix frame [mixStart]. */
    fun feed(stereo: FloatArray, frames: Int, mixStart: Long) {
        for (i in 0 until frames) {
            val x = (stereo[i * 2] + stereo[i * 2 + 1]) * 0.5
            lp1 += low * (x - lp1)
            lp2 += low * (lp1 - lp2)
            val level = abs(lp2)
            env += (if (level > env) attack else release) * (level - env)
            slow += slowRate * (env - slow)
            val threshold = max(slow * RISE, FLOOR)
            val now = env > threshold
            val at = mixStart + i
            // The average needs a moment to learn the song's level: before that, the first sound would be "a kick".
            if (fed++ < warmUp) {
                above = now
                continue
            }
            if (now && !above && at - last >= refractory) {
                last = at
                val n = count.get()
                if (n < CAPACITY) {
                    heard[n] = at
                    count.set(n + 1)
                }
            }
            above = now
        }
    }

    /** The kicks heard since the last call (mix frames), oldest first. Main thread. */
    fun take(): LongArray {
        val n = count.get()
        if (n <= read) return EMPTY
        val out = heard.copyOfRange(read, n)
        read = n
        return out
    }

    companion object {
        /** The low end: kick and bass, under this. */
        const val LOW_HZ = 150.0
        const val ATTACK_S = 0.001
        const val RELEASE_S = 0.060
        /** The level the kicks rise out of: the low end's average over this long. */
        const val SLOW_S = 0.400
        /** A kick: the low end rising to this many times its average. */
        const val RISE = 1.8
        /** Never a kick in the quiet: under this, nothing is heard. */
        const val FLOOR = 0.01
        /** Two kicks are never closer than this (a beat at 240 BPM). */
        const val REFRACTORY_S = 0.25
        /** Kicks kept per blend: a 32-bar blend at 175 BPM has 128. */
        const val CAPACITY = 512
        private val EMPTY = LongArray(0)
    }
}
