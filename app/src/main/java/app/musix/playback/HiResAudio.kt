package app.musix.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import app.musix.log.AppLog
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Converts audio above 48 kHz (88.2/96/192 kHz FLAC) to 48 kHz before it reaches Android, while
 * [enabled]. Everything at 48 kHz or below passes through untouched. Read when a song's format is
 * set up, so a change applies once the player is restarted.
 */
@OptIn(UnstableApi::class)
class HiResTo48k : AudioProcessor {
    @Volatile var enabled = true

    private val sonic = SonicAudioProcessor()
    private var active = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        active = enabled && inputAudioFormat.sampleRate > TARGET_HZ && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        if (!active) return AudioFormat.NOT_SET
        sonic.setOutputSampleRateHz(TARGET_HZ)
        val out = sonic.configure(inputAudioFormat)
        AppLog.i("Audio", "Converting ${inputAudioFormat.sampleRate} Hz to $TARGET_HZ Hz in the app")
        return out
    }

    override fun isActive() = active && sonic.isActive
    override fun queueInput(inputBuffer: ByteBuffer) = sonic.queueInput(inputBuffer)
    override fun queueEndOfStream() = sonic.queueEndOfStream()
    override fun getOutput(): ByteBuffer = sonic.output
    override fun isEnded() = sonic.isEnded
    override fun flush() = sonic.flush()
    override fun reset() {
        sonic.reset()
        active = false
    }

    private companion object {
        const val TARGET_HZ = 48_000
    }
}

/**
 * Logs gaps and overlaps in the decoded audio: each buffer's timestamp should follow straight on
 * from the end of the previous one. A missing stretch means the decoder dropped audio (heard as a
 * crackle or pop), even though Android itself never ran short.
 */
@OptIn(UnstableApi::class)
class GapLoggingAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private var sampleRate = 0
    private var frameBytes = 0
    private var expectedUs = C.TIME_UNSET
    private var lastBuffer: ByteBuffer? = null
    private var lastPts = C.TIME_UNSET
    private var gaps = 0

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val pcm = inputFormat.sampleMimeType == "audio/raw" && Util.isEncodingLinearPcm(inputFormat.pcmEncoding)
        sampleRate = if (pcm) inputFormat.sampleRate else 0
        frameBytes = if (pcm) Util.getPcmFrameSize(inputFormat.pcmEncoding, inputFormat.channelCount) else 0
        expectedUs = C.TIME_UNSET
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // The same buffer is offered again until the sink has taken all of it: only check it once.
        if (sampleRate > 0 && frameBytes > 0 && !(buffer === lastBuffer && presentationTimeUs == lastPts)) {
            if (expectedUs != C.TIME_UNSET) {
                val diffUs = presentationTimeUs - expectedUs
                if (abs(diffUs) > 2_000) {
                    gaps++
                    AppLog.w(
                        "Audio",
                        "Decoded audio ${if (diffUs > 0) "gap" else "overlap"} #$gaps at %.2fs: %d ms ${if (diffUs > 0) "missing" else "repeated"}"
                            .format(presentationTimeUs / 1e6, abs(diffUs) / 1000),
                    )
                }
            }
            val frames = buffer.remaining() / frameBytes
            expectedUs = presentationTimeUs + frames * 1_000_000L / sampleRate
            lastBuffer = buffer
            lastPts = presentationTimeUs
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun flush() {
        expectedUs = C.TIME_UNSET
        lastBuffer = null
        super.flush()
    }

    override fun reset() {
        expectedUs = C.TIME_UNSET
        lastBuffer = null
        gaps = 0
        super.reset()
    }
}
