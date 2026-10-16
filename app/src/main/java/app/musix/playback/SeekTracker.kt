package app.musix.playback

import kotlin.math.abs

/**
 * Decides where the now playing bar is drawn, so it moves smoothly:
 * - after you seek, it stays where you let go until the player reports a position near it (it used
 *   to jump back to the old spot for a moment first);
 * - on a new song it starts from that song's own position and length, never mixing the old song's
 *   position with the new song's length;
 * - while playing it never steps back by a fraction of a second when the player corrects its estimate.
 */
class SeekTracker(
    /** How close the player must report to a seek target to count as having arrived. */
    private val arriveMs: Long = 1_000,
    /** Give up waiting for a seek after this long and show what the player reports. */
    private val settleMs: Long = 2_500,
    /** Backward corrections smaller than this are ignored while playing. */
    private val jitterMs: Long = 1_000,
) {
    data class Shown(val positionMs: Long, val durationMs: Long) {
        val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private var track: String? = null
    private var target: Long? = null
    private var targetAt = 0L
    private var last = 0L

    /** You let go of the bar (or tapped it) at [positionMs]. */
    fun seekTo(positionMs: Long, nowMs: Long) {
        target = positionMs.coerceAtLeast(0)
        targetAt = nowMs
        last = target ?: 0
    }

    /** What to draw, given the player's [reportedMs] for the song [trackId]. */
    fun update(trackId: String?, reportedMs: Long, durationMs: Long, playing: Boolean, nowMs: Long): Shown {
        val duration = durationMs.coerceAtLeast(0)
        val reported = if (duration > 0) reportedMs.coerceIn(0, duration) else reportedMs.coerceAtLeast(0)
        if (trackId != track) {
            track = trackId
            target = null
            last = reported
            return Shown(reported, duration)
        }
        target?.let { t ->
            if (abs(reported - t) <= arriveMs || nowMs - targetAt >= settleMs) {
                target = null
            } else {
                return Shown(if (duration > 0) t.coerceAtMost(duration) else t, duration)
            }
        }
        val shown = if (playing && reported < last && last - reported < jitterMs) last else reported
        last = shown
        return Shown(shown, duration)
    }
}
