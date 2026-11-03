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
    /**
     * The player's pitch (Media3's PlaybackParameters): as [speed], the stream is read faster or slower and the
     * pitch follows; 1 at another speed keeps the key (Keep the key), by stretching the sound in time instead
     * ([Stretch]). The pitch is moved by [pitch] and the time by [speed] / [pitch].
     */
    @Volatile var pitch = 1f

    /** The gain the mixer last applied, where its next block's glide starts. */
    internal var gainNow = 1f
    /** The short fade in and out around play, pause and release, so none of them clicks (0..1). */
    private var envelope = 0.0
    /**
     * Blocks in a row in which the stream was playing with sound waiting but gave none (held by a cut or a cue):
     * what a stream stuck silent looks like ([stuckMs]).
     */
    private var blockedBlocks = 0
    private var blockedFrames = 0L
    /**
     * The loudest sample read from the song in the last block (before any fade or volume), and what the mixer made of
     * it ([noteOut]): sound in, silence out, for long ([mutedMs]), is a song shown playing with no sound.
     */
    @Volatile var inPeak = 0.0
        private set
    @Volatile var outPeak = 0.0
        private set
    private var mutedFrames = 0L

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
            val keepFrom = max(first, floor(readPos).toLong() - KEEP_BEHIND)
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
        stretch.reset()
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

    /**
     * The mixer's side of the meter: the loudest sample this stream put into the mix in its last block, after its
     * volume and its deck's filter, for [frames] frames.
     */
    internal fun noteOut(peak: Double, frames: Int) {
        outPeak = peak
        if (inPeak > SOUND_PEAK && peak < SILENT_PEAK) mutedFrames += frames else mutedFrames = 0
    }

    /** How long (ms of the output) the song has had sound but the mixer has put out silence for it. */
    fun mutedMs(outRate: Int): Long = mutedFrames * 1000 / outRate

    /** How long (ms of the output) this stream has been playing with sound waiting and given none. */
    fun stuckMs(outRate: Int): Long = synchronized(lock) { blockedFrames * 1000 / outRate }

    /** For the log, when a stream is found stuck: what holds it. */
    fun describe(): String = synchronized(lock) {
        "playing $playing, read ${floor(readPos).toLong()}, written $end, gate ${if (gate == Long.MIN_VALUE) "none" else gate.toString()}, " +
            "cut ${if (cutAt == Long.MAX_VALUE) "none" else cutAt.toString()}, rendered to $renderedTo, volume $volume, " +
            "fade-in ${"%.2f".format(java.util.Locale.ROOT, envelope)}, gain $gainNow, in ${db(inPeak)}, out ${db(outPeak)}, cue given up $cueGivenUp"
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
            blockedBlocks = 0
            blockedFrames = 0
        }
    }

    // ---- The mixer's side ----

    /**
     * Renders [frames] frames into [out] (stereo, interleaved, from frame [offset]; overwritten), mix frames from
     * [mixStart], at an output rate of [outRate]: with the envelope, not yet the volume. False when it gave nothing.
     */
    fun render(out: FloatArray, offset: Int, frames: Int, mixStart: Long, outRate: Int): Boolean = synchronized(lock) {
        val ratio = sampleRate.toDouble() * speed / outRate
        // The rate the sound is read at for its pitch: the same as the ratio unless the key is kept.
        val pitchRatio = sampleRate.toDouble() * pitch / outRate
        val stretchWanted = kotlin.math.abs(pitchRatio - ratio) > 1e-6
        lastRatio = ratio
        val slot = cpHead
        cpMix[slot] = mixStart
        cpPos[slot] = readPos
        cpRatio[slot] = ratio
        cpHead = (cpHead + 1) % CHECKPOINTS
        if (cpCount < CHECKPOINTS) cpCount++
        renderedTo = mixStart + frames
        val waiting = end > floor(readPos).toLong() + 1

        val step = 1.0 / (RAMP_MS * outRate / 1_000.0)
        var peak = 0.0
        // At the output's rate a stream is copied sample for sample; one left between two samples (by a speed
        // change in a blend) is read through the filter like any other (0.52.0-dev crashed there).
        val table = SincTable.forRatio(if (stretchWanted) pitchRatio else ratio)
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
            val stretching = stretchWanted || stretch.active
            val exact = !stretching && ratio == 1.0 && frac == 0.0
            val needed = when {
                exact -> whole + 1
                stretching -> whole + stretch.ahead(pitchRatio) + SincTable.HALF + 1
                else -> whole + SincTable.HALF + 1
            }
            if (end < needed && !ended) {
                // Nothing to play yet: a cued stream keeps time, any other waits (its position stands still).
                if (holdGrid) readPos += ratio
                continue
            }
            if (ended && whole >= end) break
            var l: Double
            var r: Double
            if (stretching) {
                stretch.next(readPos, pitchRatio, stretchWanted, table)
                l = stretch.l
                r = stretch.r
            } else if (exact) {
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
            peak = maxOf(peak, kotlin.math.abs(l), kotlin.math.abs(r))
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
        inPeak = peak
        if (gave || !playing || !waiting) {
            blockedBlocks = 0
            blockedFrames = 0
        } else {
            blockedBlocks++
            blockedFrames += frames
        }
        return gave
    }

    // ---- Keeping the key ----

    private val stretch = Stretch()
    private val grainWeights = DoubleArray(SincTable.TAPS)
    private var readL = 0.0
    private var readR = 0.0

    /**
     * The sound at fractional frame [pos], through [table]'s filter, into [readL] and [readR]; a whole frame read at
     * the song's own rate ([plain]) is copied as it is.
     */
    private fun readAt(pos: Double, table: SincTable, plain: Boolean = false) {
        val whole = floor(pos).toLong()
        val frac = pos - whole
        var l = 0.0
        var r = 0.0
        if (frac == 0.0 && plain) {
            readL = frameAt(whole, 0)
            readR = frameAt(whole, 1)
            return
        }
        table.fill(frac, grainWeights)
        val from = whole - SincTable.HALF + 1
        if (from >= first && from >= 0 && from + SincTable.TAPS <= end) {
            var idx = ((from % capacity) * 2).toInt()
            val wrapAt = capacity * 2
            for (k in 0 until SincTable.TAPS) {
                val w = grainWeights[k]
                l += w * ring[idx]
                r += w * ring[idx + 1]
                idx += 2
                if (idx == wrapAt) idx = 0
            }
        } else {
            for (k in 0 until SincTable.TAPS) {
                val w = grainWeights[k]
                l += w * frameAt(from + k, 0)
                r += w * frameAt(from + k, 1)
            }
        }
        readL = l
        readR = r
    }

    /** The middle of the two channels at whole frame [frame], for lining grains up. */
    private fun monoAt(frame: Long): Double = (frameAt(frame, 0) + frameAt(frame, 1)) * 0.5

    /**
     * Keep the key (issue #826): the sound stretched in time, its pitch kept, by overlapping short grains (WSOLA).
     * Each grain is [GRAIN_MS] of the song read at the pitch's rate, a new one every half grain, laid where the song
     * should be by now ([readPos], which moves on at the speed as always, so where the stream is and every cue stay
     * exact) but nudged up to [SEEK_MS] either way to where it best continues the grain before, so the sound joins up
     * without a stutter. Each grain fades in and out (a Hann window); two at a time add up to the sound at full level.
     * It starts from the plain reading without a gap, and ends the same way when the speed is back at the pitch.
     */
    private inner class Stretch {
        private val grainFrames = (GRAIN_MS * GRAIN_RATE / 1_000).let { it - it % 2 }
        private val hop = grainFrames / 2
        private val window = DoubleArray(grainFrames) { j -> 0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * j / grainFrames) }
        private val seek = (SEEK_MS * sampleRate / 1_000).toInt()
        private val corr = (CORR_MS * sampleRate / 1_000).toInt()
        // Two grains at most: where each starts (fractional frame of the song) and how far into it the output is.
        private val start = DoubleArray(2)
        private val age = IntArray(2) { -1 }
        private var sinceGrain = 0
        /** The latest grain's nudge was 0: it reads the song exactly where it should be. */
        private var onTime = true
        var l = 0.0
        var r = 0.0
        val active: Boolean get() = age[0] >= 0 || age[1] >= 0

        private fun plain(pitchRatio: Double) = pitchRatio == 1.0

        /** How far ahead of where it should be a grain may read, in frames. */
        fun ahead(pitchRatio: Double): Long = (seek + grainFrames * maxOf(pitchRatio, 1.0) + corr).toLong()

        fun reset() {
            age[0] = -1
            age[1] = -1
        }

        /** The next output frame, for the song where it should be at [nominal], read at [pitchRatio]. */
        fun next(nominal: Double, pitchRatio: Double, wanted: Boolean, table: SincTable) {
            if (!active) {
                // From the plain reading: a grain halfway through, reading where the song is now, so nothing dips.
                start[0] = nominal - hop * pitchRatio
                age[0] = hop
                sinceGrain = hop
                onTime = true
            }
            if (sinceGrain >= hop) {
                val latest = if (age[1] < 0 || (age[0] in 0 until age[1])) 0 else 1
                val older = 1 - latest
                // Back at the pitch: the newest grain reads exactly where it should, and once the one before has
                // ended, the plain reading takes over from it seamlessly.
                if (!wanted && onTime && age[older] < 0 && age[latest] >= hop) {
                    reset()
                    readAt(nominal, table)
                    l = readL
                    r = readR
                    return
                }
                val natural = start[latest] + age[latest] * pitchRatio
                val nudge = if (wanted) bestNudge(natural, nominal, pitchRatio) else 0
                // The older grain has run its course: the new one takes its place.
                // At the song's own rate a grain starts on a whole frame (a hundredth of a ms off at most) and is
                // copied as it is, with no filter.
                start[older] = (if (plain(pitchRatio)) kotlin.math.round(nominal) else nominal) + nudge
                age[older] = 0
                onTime = nudge == 0
                sinceGrain = 0
            }
            var sl = 0.0
            var sr = 0.0
            for (g in 0..1) {
                val a = age[g]
                if (a < 0) continue
                if (a >= grainFrames) {
                    age[g] = -1
                    continue
                }
                readAt(start[g] + a * pitchRatio, table, plain(pitchRatio))
                val w = window[a]
                sl += w * readL
                sr += w * readR
                age[g] = a + 1
            }
            sinceGrain++
            l = sl
            r = sr
        }

        /**
         * Where, within [seek] frames of [nominal], the song best carries on from [natural] (where the grain before
         * would go on reading): the most alike over [corr] frames, compared every few frames for speed.
         */
        private fun bestNudge(natural: Double, nominal: Double, pitchRatio: Double): Int {
            val n0 = floor(natural).toLong()
            val c0 = floor(nominal).toLong()
            var best = 0
            var bestScore = Double.NEGATIVE_INFINITY
            var d = -seek
            while (d <= seek) {
                var dot = 0.0
                var energy = 0.0
                var j = 0
                while (j < corr) {
                    val step = (j * pitchRatio).toLong()
                    val a = monoAt(n0 + step)
                    val b = monoAt(c0 + d + step)
                    dot += a * b
                    energy += b * b
                    j += CORR_STEP
                }
                val score = if (energy > 1e-12) dot / kotlin.math.sqrt(energy) else 0.0
                // A tie goes to the smallest nudge, so a steady sound stays where it should be.
                if (score > bestScore + 1e-9 || (kotlin.math.abs(score - bestScore) <= 1e-9 && kotlin.math.abs(d) < kotlin.math.abs(best))) {
                    bestScore = score
                    best = d
                }
                d += SEEK_STEP
            }
            return best
        }
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
        /** Keep the key: each grain's length, how far a grain may be nudged to join up, and over how much it's compared. */
        const val GRAIN_MS = 40
        /** The output rate grains are counted at (the engine's). */
        const val GRAIN_RATE = 44_100
        const val SEEK_MS = 10
        const val CORR_MS = 10
        const val SEEK_STEP = 3
        const val CORR_STEP = 8
        /** Sound kept behind where the stream is read: the filter's half and what a grain may reach back to. */
        const val KEEP_BEHIND = SincTable.HALF + 2 + 2_048L
        /** The fade around play, pause and release. */
        const val RAMP_MS = 5.0
        /** A song's sample louder than this is sound (−60 dBFS); a mix's quieter than this is silence (−100 dBFS). */
        const val SOUND_PEAK = 0.001
        const val SILENT_PEAK = 0.00001

        fun db(peak: Double): String = if (peak <= 0.0) "silent" else "%.0f dB".format(java.util.Locale.ROOT, 20 * kotlin.math.log10(peak))

        /** More sound than this dropped waiting for a cued point means the cue was wrong. */
        const val MAX_DROP_MS = 4_000
        /** Blocks remembered for positions: at 256 frames a block, about 2.7 s at 48 kHz. */
        const val CHECKPOINTS = 512
    }
}
