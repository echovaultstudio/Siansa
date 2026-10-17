package app.musix.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Hands decoded audio to the spectrogram while it is on screen. Holds the most recent samples (mixed to
 * mono) for [take] to collect; old ones are dropped if nobody collects them in time.
 */
object SpectrumFeed {
    /** Set while the spectrogram is showing; the player does no extra work otherwise. */
    @Volatile var listening = false

    private val ring = FloatArray(1 shl 17)
    private var written = 0L
    private var read = 0L
    @Volatile var sampleRate = 0
        private set

    @Synchronized
    internal fun put(samples: FloatArray, count: Int, rate: Int) {
        if (rate != sampleRate) {
            // A song at another sample rate starts afresh.
            sampleRate = rate
            read = written
        }
        for (i in 0 until count) {
            ring[(written and (ring.size - 1).toLong()).toInt()] = samples[i]
            written++
        }
    }

    /** The samples that arrived since the last call (at most the ring's size), oldest first. */
    @Synchronized
    fun take(): FloatArray {
        if (written - read > ring.size) read = written - ring.size
        val out = FloatArray((written - read).toInt())
        for (i in out.indices) out[i] = ring[((read + i) and (ring.size - 1).toLong()).toInt()]
        read = written
        return out
    }

    @Synchronized
    fun clear() {
        read = written
    }
}

/**
 * Sits first in the player's audio path, before any processing, and copies what was decoded to
 * [SpectrumFeed] while the spectrogram is showing. The audio itself passes through untouched.
 */
@OptIn(UnstableApi::class)
class SpectrumTap : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (SpectrumFeed.listening) copyToFeed(inputBuffer.duplicate().order(inputBuffer.order()))
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    private fun copyToFeed(buffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        val frameBytes = channels * if (float) 4 else 2
        val frames = buffer.remaining() / frameBytes
        if (mono.size < frames) mono = FloatArray(frames)
        for (f in 0 until frames) {
            var mix = 0f
            repeat(channels) { mix += if (float) buffer.getFloat() else buffer.getShort() / 32_768f }
            mono[f] = mix / channels
        }
        SpectrumFeed.put(mono, frames, format.sampleRate)
    }

    private var mono = FloatArray(0)
}
