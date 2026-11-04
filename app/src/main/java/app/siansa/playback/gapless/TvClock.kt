package app.siansa.playback.gapless

/**
 * Which mix frame the TV is playing (issue #114, gapless casting), from what the TV screen reports: for the stream it
 * plays, its media element's time, and whether that time is moving. Stream n started at mix frame `from`, so the TV
 * hears `from + time × rate`, and on from the report at the phone's clock while it plays.
 *
 * The TV is the clock: the phone's mixer writes only so far ahead of it ([Pacer]), as it would ahead of its own
 * speaker, so the TV's buffer stays the same size however the two clocks drift, and everything that follows the
 * phone's position (the song changes, the progress bar, the lock screen) shows what the TV plays, not what the
 * phone has sent.
 */
class TvClock(private val rate: Int) {
    private var stream = 0
    private var from = 0L
    private var time = 0.0
    private var playing = false
    private var atNanos = 0L
    private var reported = false
    private var furthest = 0.0

    /** The TV was asked for stream [n] from mix frame [fromFrame]: until it says otherwise, it's heard there. */
    @Synchronized fun started(n: Int, fromFrame: Long, nanos: Long) {
        stream = n
        from = fromFrame
        time = 0.0
        playing = false
        reported = false
        furthest = 0.0
        atNanos = nanos
    }

    /**
     * The TV's report for stream [n]: its time [timeSec] in that stream, and whether it's [moving] (playing and not
     * waiting for data). A report of another stream (an old one still playing as the TV switches) is ignored.
     */
    @Synchronized fun report(n: Int, timeSec: Double, moving: Boolean, nanos: Long): Boolean {
        if (n != stream || timeSec.isNaN() || timeSec < 0) return false
        time = timeSec
        if (timeSec > furthest) furthest = timeSec
        playing = moving
        atNanos = nanos
        reported = true
        return true
    }

    /** The mix frame heard on the TV at [nanos] (the phone's System.nanoTime()). */
    @Synchronized fun heard(nanos: Long): Double {
        val base = from + time * rate
        return if (playing) base + (nanos - atNanos).coerceAtLeast(0) * rate / 1e9 else base
    }

    /**
     * What the mixer paces itself by: [heard], except when the TV has stood still for over [STALL_MS] (waiting for data,
     * or not yet started): then it counts on from there by the phone's clock, up to [STALL_EXTRA_MS] more, so the TV
     * always gets data to start or carry on with and the two can't wait for each other. Chrome buffers by bytes, not
     * by time: a stream that slowed to a trickle could leave it waiting for more than the mixer would send.
     */
    @Synchronized fun paceHeard(nanos: Long): Double {
        val base = heard(nanos)
        if (playing) return base
        val stillMs = (nanos - atNanos) / 1_000_000 - STALL_MS
        if (stillMs <= 0) return base
        // Before the stream has got anywhere, more: a TV may want more in hand to start than to carry on.
        val most = if (started()) STALL_EXTRA_MS else START_EXTRA_MS
        return base + minOf(stillMs, most) * rate / 1000.0
    }

    /** Whether the TV has got anywhere in its current stream (past [STARTED_SEC]). */
    @Synchronized fun started(): Boolean = furthest >= STARTED_SEC

    /** Whether the TV has reported on the stream it was last asked for. */
    @Synchronized fun hasReport(): Boolean = reported

    /** How long (ms) since the TV last said anything about the current stream (or since it was asked for it). */
    @Synchronized fun quietMs(nanos: Long): Long = (nanos - atNanos) / 1_000_000

    @Synchronized fun isMoving(): Boolean = playing

    @Synchronized fun currentStream(): Int = stream

    companion object {
        /** The TV standing still this long (ms) counts as waiting for data. */
        const val STALL_MS = 1_000L
        /** And then the mixer goes on this far (ms) at most without hearing from it. */
        const val STALL_EXTRA_MS = 3_000L
        /** The same before the stream has started at all. */
        const val START_EXTRA_MS = 6_000L
        /** How far into a stream counts as started. */
        const val STARTED_SEC = 0.3
    }
}

/**
 * How the mixer keeps pace with the TV (issue #114): it writes up to [leadFrames] ahead of what the TV plays and then
 * waits, so the TV always has about that much in hand for Wi-Fi hiccups, and a song change or a pause on the phone
 * reaches the TV that much later (pauses and skips are sent at once, apart from the stream).
 */
object Pacer {
    /** How long (ms) the mixer waits before writing on, with [written] frames written and [heard] heard. 0: write now. */
    fun waitMs(written: Long, heard: Double, leadFrames: Long, rate: Int): Long {
        val ahead = written - heard - leadFrames
        if (ahead <= 0) return 0
        return (ahead * 1000 / rate).toLong().coerceIn(1, MAX_WAIT_MS)
    }

    /** At most this long at a time: the mixer looks again (to stop, or to follow a skip). */
    const val MAX_WAIT_MS = 20L
}
