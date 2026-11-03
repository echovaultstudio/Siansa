package app.siansa.playback.engine

import app.siansa.playback.DeckFilter
import app.siansa.playback.PeakLimiter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The mixing itself (issue #826), without the phone's audio output, so it can be tested: every deck's streams read
 * at the output's rate, each at its volume (glided across a block, so a change of level never clicks) and through
 * its deck's bass swap, summed, and kept under full scale by a limiter. [AudioEngine] runs it on its thread and
 * writes the result out.
 */
class MixCore(val outRate: Int) {
    /**
     * One player's place in the mix: its streams (one at a time, a second while a new one takes over), its bass swap
     * (which stays with the player across songs and seeks), and which moment of which song each frame is.
     *
     * Media3 doesn't tell an output what it plays, so [MixSink] tells the deck: the frames handed to the player's sink
     * are counted from its last flush, and at the first buffer after a flush and at each change of song it marks
     * "this frame is this moment of the song" ([mark]). Frames come out of the player's processors in the same
     * count, a few ms later (the limiter's look-ahead): the same for every deck, so it cancels out wherever two decks
     * are lined up against each other.
     */
    class Deck(val name: String) {
        val filter = DeckFilter()
        internal val streams = CopyOnWriteArrayList<DeckStream>()

        private class Mark(val inputFrame: Long, val mediaUs: Long)
        private val marks = ArrayList<Mark>()
        /**
         * Where the player was sent by its last seek (µs of the song), to check the first mark after the next flush
         * against, and until when that holds ([EXPECT_FOR_NS]). Never a mark already there: the seek's report can come
         * before the player has flushed, and the mark there is then the song before it (0.52.0-dev: "correcting" those
         * put songs' times a song off, their cues were given up and they faded in from nothing, silent).
         */
        private var expectUs: Long? = null
        private var expectUntil = 0L
        /** The next mark is the first after a flush. */
        private var afterFlush = true
        /** Where the deck says what it corrected (the engine's log). */
        @Volatile var note: (String) -> Unit = {}
        /** The input frame the buffer being handed over starts at: a stream made now starts there. */
        @Volatile internal var bufferStart = 0L
        /** A cue for the next stream this deck gets (after a seek): the song's time [mediaUs] at mix frame [mixFrame]. */
        @Volatile private var nextCue: Triple<Long, Long, Int>? = null
        @Volatile var lastCued: DeckStream.Cued? = null
            private set

        /** The stream playing now: the newest that isn't released. */
        val current: DeckStream? get() = streams.lastOrNull { !it.released }

        internal fun add(stream: DeckStream) {
            stream.inputBase = bufferStart
            streams.add(stream)
            nextCue?.let { (mediaUs, mix, outRate) ->
                frameOf(stream, mediaUs)?.let { frame ->
                    lastCued = stream.cue(DeckStream.Cue(frame, mix, outRate))
                    nextCue = null
                }
            }
        }

        /** The player's sink was flushed (a seek): the count starts again. */
        internal fun flushed() {
            synchronized(marks) {
                marks.clear()
                afterFlush = true
            }
            bufferStart = 0
        }

        /**
         * Input frame [inputFrame] (counted since the last flush) is the song's time [mediaUs]. The first after a
         * seek is checked against where the player was sent ([expectSeek]): when the player had already read into
         * the next song before a seek there, Media3 doesn't pass that song's offset on, and the time comes out a whole
         * song too far (0.52.0-dev: the next song waited for ever, its sound all "before its cue").
         */
        internal fun mark(inputFrame: Long, mediaUs: Long) {
            synchronized(marks) {
                var at = mediaUs
                if (afterFlush) {
                    afterFlush = false
                    val expected = expectUs
                    if (expected != null && System.nanoTime() <= expectUntil && kotlin.math.abs(at - expected) > STALE_US) at = corrected(at, expected)
                    expectUs = null
                }
                if (marks.size >= MAX_MARKS) marks.removeAt(0)
                marks.add(Mark(inputFrame, at))
            }
        }

        /**
         * The player is being sent to the song's time [mediaUs] (a seek): the first mark after the next flush, within a
         * moment, is checked against that. Called before the seek where it can be (a fade's own jump), so it's never
         * late for it.
         */
        fun expectSeek(mediaUs: Long) {
            synchronized(marks) {
                expectUs = mediaUs
                expectUntil = System.nanoTime() + EXPECT_FOR_NS
            }
        }

        private fun corrected(read: Long, expected: Long): Long {
            note("Deck $name: the song's time after a seek read ${read / 1000} ms, sent to ${expected / 1000} ms; going by where it was sent")
            return expected
        }

        /**
         * The frame of [stream] that is the song's time [mediaUs]: by the latest mark at or before where the stream
         * is read now (the song it's playing, not one already handed over behind it). Null without a mark.
         */
        fun frameOf(stream: DeckStream, mediaUs: Long): Double? = synchronized(marks) {
            val here = stream.readFrame + stream.inputBase
            val m = marks.lastOrNull { it.inputFrame <= here } ?: marks.firstOrNull() ?: return null
            (m.inputFrame - stream.inputBase) + (mediaUs - m.mediaUs) * stream.sampleRate / 1_000_000.0
        }

        /** The song's time, in µs, of [stream]'s frame [frame]. Null without a mark. */
        fun mediaUsOf(stream: DeckStream, frame: Double): Long? = synchronized(marks) {
            val input = frame + stream.inputBase
            val m = marks.lastOrNull { it.inputFrame <= input } ?: marks.firstOrNull() ?: return null
            m.mediaUs + kotlin.math.round((input - m.inputFrame) * 1_000_000.0 / stream.sampleRate).toLong()
        }

        /**
         * Puts the song's time [mediaUs] of this deck's next stream (the one its player starts after a seek) at mix
         * frame [mixFrame] (issue #826).
         */
        fun cueNext(mediaUs: Long, mixFrame: Long, outRate: Int, seekUs: Long = mediaUs) {
            lastCued = null
            nextCue = Triple(mediaUs, mixFrame, outRate)
            expectSeek(seekUs)
        }

        /** Puts the song's time [mediaUs] of the stream playing now at [mixFrame]. Null when there's none, or no mark. */
        fun cueNow(mediaUs: Long, mixFrame: Long, outRate: Int): DeckStream.Cued? {
            val stream = current ?: return null
            val frame = frameOf(stream, mediaUs) ?: return null
            return stream.cue(DeckStream.Cue(frame, mixFrame, outRate)).also { lastCued = it }
        }

        /** When (mix frame) the stream playing now reaches the song's time [mediaUs]. Null when it can't say. */
        fun mixFrameAt(mediaUs: Long, outRate: Int): Double? {
            val stream = current ?: return null
            val frame = frameOf(stream, mediaUs) ?: return null
            return stream.predictMix(frame, outRate)
        }

        /** The song's time, in µs, being heard at mix frame [mix] from the stream playing now. */
        fun mediaUsAt(mix: Double): Long? {
            val stream = current ?: return null
            return mediaUsOf(stream, stream.positionAt(mix))
        }
    }

    val decks = CopyOnWriteArrayList<Deck>()
    private val limiter = PeakLimiter(outRate, 2, CEILING)
    private var scratch = FloatArray(0)
    private val frame = DoubleArray(2)

    /** How many frames the limiter holds back: what's heard is this much behind what's mixed. */
    val latencyFrames: Int get() = limiter.latencyFrames

    fun deck(name: String): Deck = Deck(name).also { decks.add(it) }

    /** Anything playing or still fading: while nothing is, the output can rest. */
    val active: Boolean get() = decks.any { d -> d.streams.any { it.audible } }

    /**
     * Mixes [frames] frames into [out] (stereo, interleaved) for mix frames from [mixStart]. Returns the streams that
     * started to sound in this block (their players are told so).
     */
    fun render(out: FloatArray, frames: Int, mixStart: Long): List<DeckStream> {
        java.util.Arrays.fill(out, 0, frames * 2, 0f)
        if (scratch.size < frames * 2) scratch = FloatArray(frames * 2)
        var started: MutableList<DeckStream>? = null
        for (deck in decks) {
            val filtering = deck.filter.prepare(outRate, 2)
            for (stream in deck.streams) {
                if (!stream.audible) {
                    if (stream.released) deck.streams.remove(stream)
                    continue
                }
                if (!stream.render(scratch, 0, frames, mixStart, outRate)) {
                    stream.gainNow = stream.volume
                    continue
                }
                if (stream.becameAudible) {
                    stream.becameAudible = false
                    (started ?: mutableListOf<DeckStream>().also { started = it }).add(stream)
                }
                val g0 = stream.gainNow
                val g1 = stream.volume
                stream.gainNow = g1
                for (i in 0 until frames) {
                    val g = g0 + (g1 - g0) * (i + 1).toFloat() / frames
                    var l = (scratch[i * 2] * g).toDouble()
                    var r = (scratch[i * 2 + 1] * g).toDouble()
                    if (filtering) {
                        l = deck.filter.process(l, 0)
                        r = deck.filter.process(r, 1)
                    }
                    out[i * 2] += l.toFloat()
                    out[i * 2 + 1] += r.toFloat()
                }
            }
        }
        for (i in 0 until frames) {
            frame[0] = out[i * 2].toDouble()
            frame[1] = out[i * 2 + 1].toDouble()
            limiter.process(frame)
            out[i * 2] = frame[0].toFloat()
            out[i * 2 + 1] = frame[1].toFloat()
        }
        return started ?: emptyList()
    }

    companion object {
        /** Marks kept per deck: a few per song is plenty. */
        const val MAX_MARKS = 64
        /** A first mark after a seek further than this from where the player was sent is misread. */
        const val STALE_US = 1_500_000L
        /** How long a seek's expectation waits for its flush: a seek is flushed within milliseconds. */
        const val EXPECT_FOR_NS = 1_500_000_000L
        /** The mix's ceiling: a little under full scale, as two songs at the top of a blend can add up past it. */
        const val CEILING = 0.98
    }
}
