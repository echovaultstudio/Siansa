package app.siansa.playback

/**
 * Listens to the sound going through one player's audio path (issue #818), so the silent tail of a crossfade can
 * be lined up with the main player by what they're both playing rather than by positions the player reports
 * (which aren't real for seconds after a start, a jump or a change of speed over Bluetooth).
 *
 * The player thread hands it every buffer ([begin], then [add] per frame): it keeps the last few seconds as mono,
 * [DECIMATION] frames to a sample, and for each buffer when it came and how many frames had gone out before it.
 * A buffer is handed to the processors only once the last one has fully gone into the phone's output buffer,
 * which is then full, so those moments say where the output stands: both players' output buffers are the same
 * size and fill the same way, so comparing the two players' streams at one moment says how far apart they're
 * heard. [SoundAlign] does the comparing on a [snapshot].
 *
 * It also carries a correction for the tail: [drop] frames to swallow (the tail is behind) or [insert] frames of
 * silence to put in (ahead), which the processor applies at the start of its next buffer. The frames dropped less
 * those inserted are media time that went by without playout, which [mediaAdjustUs] adds to the position.
 */
class SoundTap {
    class Mark(val nanos: Long, val framesBefore: Long)

    /** A copy for [SoundAlign]: the kept sound, oldest first, and the frame index just past its last sample. */
    class Snapshot(val sampleRate: Int, val decimation: Int, val sound: FloatArray, val endFrame: Long, val marks: List<Mark>) {
        val decimatedRate: Int get() = sampleRate / decimation

        /** Where the output stood (frames gone out) at [nanos], from the buffer moments: a line at the sample rate. */
        fun framesAt(nanos: Long): Double {
            if (marks.isEmpty()) return endFrame.toDouble()
            // Each mark says "at this moment this many frames had gone out"; the output runs at the sample rate, so
            // each one predicts the same line. The middle one is used, as a late buffer can put one mark off.
            val predicted = marks.map { it.framesBefore + (nanos - it.nanos) * sampleRate / 1e9 }.sorted()
            return predicted[predicted.size / 2]
        }
    }

    private val lock = Any()
    private var sampleRate = 0
    private var channels = 1
    private var ring = FloatArray(0)
    /** Decimated samples ever written: the next write goes to ring[(written % size)]. */
    private var written = 0L
    /** Output frames since the last flush: the frame index of the next frame. */
    private var frames = 0L
    private var accumulator = 0f
    private var accumulated = 0
    private val marks = ArrayDeque<Mark>()
    private var keptSeconds = MAIN_KEEP_SECONDS

    /** Pending correction, read and cleared by the processor at its next buffer. */
    @Volatile private var pendingDrop = 0L
    @Volatile private var pendingInsert = 0L
    /** Frames dropped less inserted since the last flush: media time that passed without being heard. */
    @Volatile private var adjustFrames = 0L

    /** How many seconds of sound to keep: a main player keeps enough for the tail's window to be found in it. */
    fun keep(seconds: Int) {
        keptSeconds = seconds
    }

    /** A new buffer of [framesIn] frames arrives at [nanos]; the sound was [sampleRate] Hz, [channels] channels. */
    fun begin(sampleRate: Int, channels: Int, nanos: Long) {
        synchronized(lock) {
            if (this.sampleRate != sampleRate || this.channels != channels || ring.isEmpty()) {
                this.sampleRate = sampleRate
                this.channels = channels
                ring = FloatArray((sampleRate / DECIMATION) * keptSeconds)
                written = 0
                accumulator = 0f
                accumulated = 0
            }
            marks.addLast(Mark(nanos, frames))
            while (marks.size > MARKS) marks.removeFirst()
        }
    }

    /** One output frame, as the sum of its channels (the processor adds them up; the level doesn't matter). */
    fun add(monoSum: Float) {
        // Not under the lock: only the player thread writes; snapshot() copies under it and a torn sample at the
        // edge doesn't matter to a correlation.
        frames++
        accumulator += monoSum
        if (++accumulated == DECIMATION) {
            ring[(written % ring.size).toInt()] = accumulator / (DECIMATION * channels)
            written++
            accumulator = 0f
            accumulated = 0
        }
    }

    /** After a seek or a new song: the stream starts again from nothing. */
    fun flush() {
        synchronized(lock) {
            written = 0
            frames = 0
            accumulator = 0f
            accumulated = 0
            marks.clear()
            pendingDrop = 0
            pendingInsert = 0
            adjustFrames = 0
        }
    }

    /** The frames gone out since the last flush. */
    val framesOut: Long get() = frames

    fun snapshot(): Snapshot? = synchronized(lock) {
        if (sampleRate == 0 || written == 0L) return null
        val size = ring.size
        val count = minOf(written, size.toLong()).toInt()
        val out = FloatArray(count)
        val start = ((written - count) % size).toInt()
        for (i in 0 until count) out[i] = ring[(start + i) % size]
        Snapshot(sampleRate, DECIMATION, out, written * DECIMATION, marks.toList())
    }

    /** Asks the processor to swallow [frames] frames (positive) or put in -[frames] frames of silence (negative). */
    fun correct(frames: Long) {
        if (frames > 0) pendingDrop += frames else if (frames < 0) pendingInsert += -frames
    }

    /** How many of the next [available] input frames to swallow: taken by the processor at the start of a buffer. */
    fun takeDrop(available: Int): Int {
        val drop = minOf(pendingDrop, available.toLong()).toInt()
        if (drop > 0) {
            pendingDrop -= drop
            adjustFrames += drop
        }
        return drop
    }

    /** How many frames of silence to put out first: taken by the processor at the start of a buffer. */
    fun takeInsert(): Int {
        val insert = minOf(pendingInsert, MAX_INSERT_FRAMES).toInt()
        if (insert > 0) {
            pendingInsert -= insert
            adjustFrames -= insert
        }
        return insert
    }

    /** Media time that went by without playout (dropped less inserted), for the player's position. */
    fun mediaAdjustUs(): Long = if (sampleRate == 0) 0L else adjustFrames * 1_000_000L / sampleRate

    val pending: Boolean get() = pendingDrop > 0 || pendingInsert > 0

    companion object {
        /** How much sound a main player keeps: the tail's window, taken [SoundAlign.RANGE_MS] back, lies within it
         *  for a tail up to that far behind. */
        const val MAIN_KEEP_SECONDS = 4
        /** Frames to a kept sample: 44.1 kHz is kept at about 11 kHz, which places a match to a tenth of a ms. */
        const val DECIMATION = 4
        /** How many buffer moments are kept to place the output: at 5 to 90 ms a buffer, the last second or so. */
        const val MARKS = 24
        /** Silence put in per buffer at most, so one buffer can't grow past what the output takes. */
        const val MAX_INSERT_FRAMES = 4_096L
    }
}
