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
    private var inputFrames = 0L
    private var offsetUs = 0L
    private var markNext = true
    /** The last buffer was taken whole: the next call brings a new one (Media3 offers a buffer again until it is). */
    private var lastTaken = true

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        frameBytes = if (MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType && inputFormat.pcmEncoding != Format.NO_VALUE) {
            Util.getPcmFrameSize(inputFormat.pcmEncoding, inputFormat.channelCount)
        } else 0
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        if (outputStreamOffsetUs != C.TIME_UNSET && outputStreamOffsetUs != offsetUs) {
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
        }
        val taken = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        lastTaken = taken
        return taken
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
}
