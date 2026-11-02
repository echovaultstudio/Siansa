package app.siansa.playback.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * One stretch of a player's sound on its way to the mixer (issue #826): what Media3 writes between two flushes (a
 * seek or a new format starts another). The player's thread writes into a ring ([write]); the mixer's thread reads
 * it out at the output's rate through the resampler ([render]); anyone may ask where it is ([positionAt],
 * [predictMix]).
 *
 * Frames are counted from the first one written ("absolute" frames): the player counts the same way, so a position
 * in them is what it expects back. Which moment of which song a frame is, its [MixCore.Deck] knows (from what Media3
 * hands its sink); [inputBase] is where this stream starts in the deck's count. The mixer's frames ("mix" frames) are the output's, counted from when the engine
 * started: both decks of a crossfade are on that one clock, which is what lets a fade put a song exactly where it
 * should be ([cue]) instead of steering it there afterwards.
 *
 * Everything is under one lock: the player's thread writes, the mixer's reads, the main thread cues; each holds it
 * for a buffer at most.
 */
class DeckStream(
    val sampleRate: Int,
    val channels: Int,
    val encoding: Encoding,
    capacityMs: Int = CAPACITY_MS,
) {
    enum class Encoding(val bytes: Int) { PCM_16(2), PCM_FLOAT(4) }

    private val lock = Any()
    private val capacity = max(SincTable.TAPS * 4, sampleRate * capacityMs / 1_000)
    private val ring = FloatArray(capacity * 2)

    /** The next absolute frame to be written, and the oldest still kept. */
    private var end = 0L
    private var first = 0L
    /** Frames below this are dropped as they arrive: a cue to a point further on than what's written. */
    private var dropUntil = 0L
    /** Frames dropped for a cue: more than [MAX_DROP_MS] says the cue was wrong, and it's given up. */
    private var dropped = 0L
    /** A cue was given up (see [dropped]): told once in the log. */
    @Volatile var cueGivenUp = false
        private set
    /** Where the next output sample is read, in absolute frames (fractional between two). */
    private var readPos = 0.0
    /** Where this stream's first frame is in its deck's count of frames handed to the player's sink. */
    @Volatile var inputBase = 0L
    /** The mix frame this stream may sound from, and the one it falls silent at for good. */
    private var gate = Long.MIN_VALUE
    private var cutAt = Long.MAX_VALUE
    /** After a cue, a stream with nothing to play yet keeps time (as if it played), so it comes in on the beat. */
    private var holdGrid = false

    @Volatile var playing = false
        private set
    @Volatile var ended = false
        private set
    @Volatile var released = false
        private set
    /** The player's volume (Media3's setVolume); the mixer glides to it over a block. */
    @Volatile var volume = 1f
    /** The player's speed; the pitch follows (the stream is read faster or slower). */
    @Volatile var speed = 1f

    /** The gain the mixer last applied, where its next block's glide starts. */
    internal var gainNow = 1f
    /** The short fade in and out around play, pause and release, so none of them clicks (0..1). */
    private var envelope = 0.0
    /** Set when the stream first sounds after [play]: Media3 is told then (onPositionAdvancing). */
    private var announced = false
    internal var becameAudible = false

    // Where the stream was at the start of each block the mixer rendered: mix frame, position, ratio.
    private val cpMix = LongArray(CHECKPOINTS)
    private val cpPos = DoubleArray(CHECKPOINTS)
    private val cpRatio = DoubleArray(CHECKPOINTS)
    private var cpCount = 0
    private var cpHead = 0
    /** The mix frame just past the last rendered block. */
    private var renderedTo = Long.MIN_VALUE
    private var lastRatio = 1.0

    private val weights = DoubleArray(SincTable.TAPS)

    /** Put this stream's absolute frame [frame] at mix frame [mixFrame] of an output at [outRate] Hz. */
    class Cue(val frame: Double, val mixFrame: Long, val outRate: Int)

    /** What a cue came to: how many frames of the wanted point weren't there any more (0 when it was). */
    class Cued(val targetFrame: Long, val missingFrames: Long)

    // ---- The player's side ----

    /** Takes as much of [buffer] (PCM, the player's encoding and channels, little endian) as fits. True when all of it was taken. */
    fun write(buffer: ByteBuffer): Boolean = synchronized(lock) {
        if (released) {
            buffer.position(buffer.limit())
            return true
        }
        val frameBytes = channels * encoding.bytes
        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var at = buffer.position()
        // Frames behind where it's read from (a cue further on, or a stream keeping time with nothing to play) are
        // dropped as they arrive; the filter's history before that is kept.
        var dropBelow = max(dropUntil, floor(readPos).toLong() - SincTable.HALF - 2)
        while (buffer.limit() - at >= frameBytes) {
            if (end < dropBelow) {
                if (++dropped > sampleRate.toLong() * MAX_DROP_MS / 1_000 && !cueGivenUp) {
                    // Seconds of sound thrown away waiting for a point that isn't coming: the cue was wrong
                    // (the song's time misread). It plays from here instead of the player waiting for ever.
                    cueGivenUp = true
                    dropUntil = end
                    dropBelow = end
                    readPos = end.toDouble()
                    first = end
                    gate = Long.MIN_VALUE
                    holdGrid = false
                    continue
                }
                at += frameBytes
                end++
                first = end
                continue
            }
            val keepFrom = max(first, floor(readPos).toLong() - SincTable.HALF - 2)
            if (end - keepFrom >= capacity) break
            val l = sample(data, at, 0)
            val r = if (channels >= 2) sample(data, at, 1) else l
            val i = ((end % capacity) * 2).toInt()
            ring[i] = l
            ring[i + 1] = r
            end++
            at += frameBytes
            if (end - first > capacity) first = end - capacity
        }
        buffer.position(at)
        return buffer.limit() - at < frameBytes
    }

    private fun sample(data: ByteBuffer, frameAt: Int, channel: Int): Float = when (encoding) {
        Encoding.PCM_16 -> data.getShort(frameAt + channel * 2) / 32_768f
        Encoding.PCM_FLOAT -> data.getFloat(frameAt + channel * 4)
    }

    fun play() {
        playing = true
    }

    fun pause() {
        playing = false
        synchronized(lock) { announced = false }
    }

    /** No more is coming: the mixer plays what's there to its end. */
    fun endOfStream() {
        ended = true
    }

    fun release() {
        released = true
        playing = false
    }

    /** Frames written and not yet played. */
    val bufferedFrames: Long get() = synchronized(lock) { (end - floor(readPos).toLong()).coerceAtLeast(0L) }

    val capacityFrames: Int get() = capacity

    /** Everything written has been played (or it was released). */
    val finished: Boolean get() = released || (ended && synchronized(lock) { floor(readPos).toLong() >= end })

    // ---- Where it is ----

    /**
     * Where this stream was when mix frame [mix] was being heard, in absolute frames: between the two blocks around
     * it, or on from the last block at its speed (never past what has been rendered).
     */
    fun positionAt(mix: Double): Double = synchronized(lock) {
        if (cpCount == 0) return readPos
        var k = cpCount - 1
        while (k > 0 && cpMix[slot(k)] > mix) k--
        val s = slot(k)
        if (cpMix[s] > mix) return cpPos[s]
        if (k < cpCount - 1) {
            val n = slot(k + 1)
            val span = (cpMix[n] - cpMix[s]).toDouble()
            return cpPos[s] + (cpPos[n] - cpPos[s]) * ((mix - cpMix[s]) / span).coerceIn(0.0, 1.0)
        }
        return minOf(readPos, cpPos[s] + (mix - cpMix[s]) * cpRatio[s])
    }

    private fun slot(k: Int) = (cpHead - cpCount + k + CHECKPOINTS) % CHECKPOINTS

    /** [positionAt] in µs from this stream's first frame, rounded up: Media3 compares it with what it wrote. */
    fun positionUsAt(mix: Double): Long = ceilUs(positionAt(mix))

    private fun ceilUs(frames: Double): Long {
        val whole = floor(frames).toLong()
        return (whole * 1_000_000L + sampleRate - 1) / sampleRate
    }

    /** Where it's read from now, in absolute frames. */
    val readFrame: Double get() = synchronized(lock) { readPos }

    /**
     * The mix frame at which this stream will reach absolute frame [frame], at its speed on an output of [outRate]:
     * from where it was last rendered, or from its gate when it hasn't started. Null when that's behind it.
     */
    fun predictMix(frame: Double, outRate: Int): Double? = synchronized(lock) {
        val ratio = sampleRate.toDouble() * speed / outRate
        val from = when {
            gate != Long.MIN_VALUE && (renderedTo == Long.MIN_VALUE || gate >= renderedTo) -> gate.toDouble()
            renderedTo != Long.MIN_VALUE -> renderedTo.toDouble()
            else -> return null
        }
        val ahead = frame - readPos
        if (ahead < 0) null else from + ahead / ratio
    }

    // ---- Cueing ----

    /**
     * Puts this stream's frame [Cue.frame] at mix frame [Cue.mixFrame]: silent until then, and from then on as if it
     * had been playing all along (issue #826). Says how far the wanted point was behind what's kept (frames that
     * couldn't be played).
     */
    fun cue(cue: Cue): Cued = synchronized(lock) {
        val target = cue.frame.roundToLong()
        // Frames before what's kept can't be played any more: it starts that much later, still on the grid (the
        // gate moves by as long as they'd have taken). The same when the wanted point is before the first frame
        // written: a jump lands on the start of a decoded block, up to 0.1 s past where it was sent (0.52.0-dev:
        // the next song came in that much early, heard as a trainwreck of 13 to 96 ms).
        val ratio = sampleRate.toDouble() * speed / cue.outRate
        // A cue whose moment the mixer is already past (its data came late) starts as far on as the stream would be.
        val late = if (renderedTo != Long.MIN_VALUE && renderedTo > cue.mixFrame) renderedTo - cue.mixFrame else 0L
        val wanted = target + late * ratio
        val missing = (max(first, 0L) - floor(wanted).toLong()).coerceAtLeast(0L)
        readPos = max(wanted, max(first, 0L).toDouble())
        val from = floor(readPos).toLong()
        if (from > end) {
            dropUntil = from
            first = end
        }
        gate = max(cue.mixFrame, if (late > 0) renderedTo else Long.MIN_VALUE) + kotlin.math.ceil(missing / ratio).toLong()
        holdGrid = true
        envelope = 1.0
        announced = false
        dropped = 0
        cueGivenUp = false
        return Cued(target, missing)
    }

    /** Silent for good from mix frame [mix] on, at once (no fade): its sound carries on from another copy. */
    fun cutAt(mix: Long) {
        synchronized(lock) { cutAt = mix }
    }

    /** Silenced, or held back for a cue still ahead: what a fade does to a stream for a while. */
    val held: Boolean get() = synchronized(lock) { cutAt != Long.MAX_VALUE || (gate != Long.MIN_VALUE && gate > renderedTo) }

    /** Plays freely again: no cut, no cue (a fade that set them is gone). */
    fun freeHold() {
        synchronized(lock) {
            cutAt = Long.MAX_VALUE
            gate = Long.MIN_VALUE
            holdGrid = false
            dropUntil = 0
        }
    }

    // ---- The mixer's side ----

    /**
     * Renders [frames] frames into [out] (stereo, interleaved, from frame [offset]; overwritten), mix frames from
     * [mixStart], at an output rate of [outRate]: with the envelope, not yet the volume. False when it gave nothing.
     */
    fun render(out: FloatArray, offset: Int, frames: Int, mixStart: Long, outRate: Int): Boolean = synchronized(lock) {
        val ratio = sampleRate.toDouble() * speed / outRate
        lastRatio = ratio
        val slot = cpHead
        cpMix[slot] = mixStart
        cpPos[slot] = readPos
        cpRatio[slot] = ratio
        cpHead = (cpHead + 1) % CHECKPOINTS
        if (cpCount < CHECKPOINTS) cpCount++
        renderedTo = mixStart + frames

        val step = 1.0 / (RAMP_MS * outRate / 1_000.0)
        // At the output's rate a stream is copied sample for sample; one left between two samples (by a speed
        // change in a blend) is read through the filter like any other (0.52.0-dev crashed there).
        val table = SincTable.forRatio(ratio)
        var gave = false
        for (i in 0 until frames) {
            val o = (offset + i) * 2
            out[o] = 0f
            out[o + 1] = 0f
            val m = mixStart + i
            if (m < gate || m >= cutAt) {
                if (m >= cutAt) envelope = 0.0
                continue
            }
            val target = if (playing && !released) 1.0 else 0.0
            if (envelope <= 0.0 && target <= 0.0) continue
            val whole = floor(readPos).toLong()
            val frac = readPos - whole
            val exact = ratio == 1.0 && frac == 0.0
            val needed = if (exact) whole + 1 else whole + SincTable.HALF + 1
            if (end < needed && !ended) {
                // Nothing to play yet: a cued stream keeps time, any other waits (its position stands still).
                if (holdGrid) readPos += ratio
                continue
            }
            if (ended && whole >= end) break
            var l: Double
            var r: Double
            if (exact) {
                l = frameAt(whole, 0)
                r = frameAt(whole, 1)
            } else {
                table.fill(frac, weights)
                l = 0.0
                r = 0.0
                val from = whole - SincTable.HALF + 1
                if (from >= first && from >= 0 && from + SincTable.TAPS <= end) {
                    // The whole window is in the ring: read it straight, wrapping once at most.
                    var idx = ((from % capacity) * 2).toInt()
                    val wrapAt = capacity * 2
                    for (k in 0 until SincTable.TAPS) {
                        val w = weights[k]
                        l += w * ring[idx]
                        r += w * ring[idx + 1]
                        idx += 2
                        if (idx == wrapAt) idx = 0
                    }
                } else {
                    for (k in 0 until SincTable.TAPS) {
                        val w = weights[k]
                        l += w * frameAt(from + k, 0)
                        r += w * frameAt(from + k, 1)
                    }
                }
            }
            envelope = if (target > envelope) minOf(target, envelope + step) else maxOf(target, envelope - step)
            out[o] = (l * envelope).toFloat()
            out[o + 1] = (r * envelope).toFloat()
            readPos += ratio
            holdGrid = false
            gave = true
            if (!announced && playing) {
                announced = true
                becameAudible = true
            }
        }
        return gave
    }

    private fun frameAt(frame: Long, channel: Int): Double {
        if (frame < first || frame >= end || frame < 0) return 0.0
        return ring[((frame % capacity) * 2).toInt() + channel].toDouble()
    }

    /** Still to be heard or fading: the mixer keeps it while this is true. */
    val audible: Boolean get() = (playing && !released) || synchronized(lock) { envelope > 0.0 }

    companion object {
        /** How much a stream holds: enough for the player to stay ahead, short enough that a change of equaliser
         *  is heard soon (it's applied before the stream). */
        const val CAPACITY_MS = 1_500
        /** The fade around play, pause and release. */
        const val RAMP_MS = 5.0
        /** More sound than this dropped waiting for a cued point means the cue was wrong. */
        const val MAX_DROP_MS = 4_000
        /** Blocks remembered for positions: at 256 frames a block, about 2.7 s at 48 kHz. */
        const val CHECKPOINTS = 512
    }
}
