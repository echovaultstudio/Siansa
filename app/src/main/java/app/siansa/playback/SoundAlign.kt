package app.siansa.playback

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Finds how far the silent tail is behind the main player by the sound itself (issue #818). Pure maths over two
 * [SoundTap.Snapshot]s; [Crossfader] applies the answer.
 *
 * Both players play the same song, so the tail's last [WINDOW_MS] of sound occur somewhere in the main player's
 * last few seconds. The place they match best (normalised cross-correlation) says which frame of the main
 * player's stream each frame of the tail's is; with where each stream's output stands right now
 * ([SoundTap.Snapshot.framesAt]) that gives how far behind the tail is heard, to a tenth of a ms. A match is
 * trusted when its peak stands clearly above the rest ([MIN_PEAK], [MIN_MARGIN]) and the window isn't near silence.
 */
object SoundAlign {
    class Match(
        /** How far the tail is behind the main player (ahead is negative), in frames of the song. */
        val behindFrames: Long,
        val sampleRate: Int,
        /** The match's strength, 0..1, and the best elsewhere: the gap between them is how sure it is. */
        val peak: Float,
        val elsewhere: Float,
    ) {
        val behindMs: Long get() = (behindFrames * 1000.0 / sampleRate).roundToLong()
        val sure: Boolean get() = peak >= MIN_PEAK && peak - elsewhere >= MIN_MARGIN
        override fun toString(): String = "%+d ms (match %.2f, elsewhere %.2f)".format(java.util.Locale.ROOT, behindMs, peak, elsewhere)
    }

    /**
     * Compares the tail ([follower]) with the main player ([reference]) as they stand at [nowNanos], looking for
     * the tail within [rangeMs] either side of [expectedBehindMs]. Null when there's nothing to compare: too
     * little sound kept, different sample rates, or the tail's window is as good as silent.
     */
    fun match(reference: SoundTap.Snapshot, follower: SoundTap.Snapshot, nowNanos: Long, expectedBehindMs: Long, rangeMs: Long): Match? {
        if (reference.sampleRate != follower.sampleRate || reference.decimation != follower.decimation) return null
        val d = follower.decimation
        val rate = follower.decimatedRate
        val window = WINDOW_MS * rate / 1000
        if (follower.sound.size < window || reference.sound.size < window) return null
        val f = follower.sound
        val fStart = f.size - window
        var energy = 0.0
        for (i in 0 until window) energy += f[fStart + i] * f[fStart + i]
        val rms = sqrt(energy / window)
        if (rms < MIN_RMS) return null

        // The window's first frame in the tail's numbering, and where both outputs stand now.
        val windowFrame = follower.endFrame - window.toLong() * d
        val followerNow = follower.framesAt(nowNanos)
        val referenceNow = reference.framesAt(nowNanos)
        val referenceFirstFrame = reference.endFrame - reference.sound.size.toLong() * d
        val r = reference.sound

        var bestScore = -2f
        var bestBehind = 0L
        val scores = ArrayList<Pair<Long, Float>>()
        val rangeFrames = rangeMs * follower.sampleRate / 1000
        val expectedFrames = expectedBehindMs * follower.sampleRate / 1000
        // Each candidate "behind" maps the tail's frames onto the main player's: the window's frame windowFrame is
        // the main player's frame windowFrame + c, where c = referenceNow - followerNow - behind.
        var behind = expectedFrames - rangeFrames
        while (behind <= expectedFrames + rangeFrames) {
            val c = referenceNow - followerNow - behind
            val refFrame = windowFrame + c
            val refIndex = ((refFrame - referenceFirstFrame) / d).roundToLong()
            if (refIndex >= 0 && refIndex + window <= r.size) {
                val i0 = refIndex.toInt()
                var dot = 0.0
                var rr = 0.0
                for (i in 0 until window) {
                    val x = r[i0 + i]
                    dot += x * f[fStart + i]
                    rr += x * x
                }
                val score = if (rr <= 0.0) 0f else (dot / sqrt(rr * energy)).toFloat()
                scores.add(behind to score)
                if (score > bestScore) {
                    bestScore = score
                    bestBehind = behind
                }
            }
            behind += d
        }
        if (scores.isEmpty()) return null
        val apart = APART_MS * follower.sampleRate / 1000
        val elsewhere = scores.filter { abs(it.first - bestBehind) > apart }.maxOfOrNull { it.second } ?: -1f
        return Match(bestBehind, follower.sampleRate, bestScore, elsewhere)
    }

    /** The tail's last stretch of sound that is looked for in the main player's. */
    const val WINDOW_MS = 300
    /** A window quieter than this (about -60 dBFS) is as good as silence: nothing to match. */
    const val MIN_RMS = 0.001
    /** A match is trusted from this strength, and when it stands this far above the best match elsewhere. */
    const val MIN_PEAK = 0.5f
    const val MIN_MARGIN = 0.15f
    /** "Elsewhere" is further than this from the best match: the same beat a bar later, not the same place. */
    const val APART_MS = 10
    /** Where the tail is looked for: this far either side of what the players' positions say. */
    const val RANGE_MS = 1_200L
    /** Lined up to within this, the tail is in step by its sound. */
    const val IN_STEP_MS = 6L
}
