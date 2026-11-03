package app.siansa.playback.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Media3's sink for a music player on the [AudioEngine], told on the way through which moment of which song each
 * frame is (issue #826), for its [MixCore.Deck]: Media3 gives a buffer its time in the renderer's clock, and on each
 * change of song the offset to the song's own clock; the output it writes to is told neither. Counts the frames
 * handed over since the last flush, and marks the deck at the first buffer after a flush and at each change of song.
 */
@OptIn(UnstableApi::class)
class MixSink(sink: AudioSink, private val deck: MixCore.Deck) : ForwardingAudioSink(sink) {
    private var frameBytes = 0
    private var encoding = Format.NO_VALUE
    private var inputFrames = 0L
    private var offsetUs = 0L
    private var markNext = true
    /** The last buffer was taken whole: the next call brings a new one (Media3 offers a buffer again until it is). */
    private var lastTaken = true

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        frameBytes = if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType && inputFormat.pcmEncoding != Format.NO_VALUE) {
            Util.getPcmFrameSize(inputFormat.pcmEncoding, inputFormat.channelCount)
        } else 0
        encoding = inputFormat.pcmEncoding
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        // Each call is a change of song, even with the offset it had: a stale offset (after a seek near a song's end)
        // can be the next song's, and that song must still be marked.
        if (outputStreamOffsetUs != C.TIME_UNSET) {
            offsetUs = outputStreamOffsetUs
            markNext = true
        }
        super.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (lastTaken) {
            deck.bufferStart = inputFrames
            if (markNext && presentationTimeUs != C.TIME_UNSET) {
                deck.mark(inputFrames, presentationTimeUs - offsetUs)
                markNext = false
            }
            if (frameBytes > 0) inputFrames += buffer.remaining() / frameBytes
            if (loud(buffer)) deck.handedSound(System.nanoTime())
        }
        val taken = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        lastTaken = taken
        return taken
    }

    /** Whether the player hands over sound in [buffer] (louder than −60 dBFS), looking at every 8th sample. */
    private fun loud(buffer: ByteBuffer): Boolean {
        val start = buffer.position()
        val end = buffer.limit()
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                var i = start
                while (i + 1 < end) {
                    val v = (buffer.get(i).toInt() and 0xFF) or (buffer.get(i + 1).toInt() shl 8)
                    if (kotlin.math.abs(v.toShort().toInt()) > LOUD_16) return true
                    i += 16
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val little = buffer.order() == java.nio.ByteOrder.LITTLE_ENDIAN
                var i = start
                while (i + 3 < end) {
                    val bits = if (little) {
                        (buffer.get(i).toInt() and 0xFF) or ((buffer.get(i + 1).toInt() and 0xFF) shl 8) or
                            ((buffer.get(i + 2).toInt() and 0xFF) shl 16) or (buffer.get(i + 3).toInt() shl 24)
                    } else buffer.getInt(i)
                    if (kotlin.math.abs(java.lang.Float.intBitsToFloat(bits)) > DeckStream.SOUND_PEAK) return true
                    i += 32
                }
            }
        }
        return false
    }

    override fun flush() {
        inputFrames = 0
        markNext = true
        lastTaken = true
        deck.flushed()
        super.flush()
    }

    override fun reset() {
        inputFrames = 0
        markNext = true
        lastTaken = true
        deck.flushed()
        super.reset()
    }

    companion object {
        /** −60 dBFS in 16-bit samples. */
        const val LOUD_16 = 32
    }
}
