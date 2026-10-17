package app.musix.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Runs the filters of an [EqDesign] over interleaved samples (-1..1), keeping each channel's filter
 * memory between calls so the sound stays continuous from one buffer to the next.
 */
class EqFilterBank(val design: EqDesign, val channels: Int) {
    private val count = design.filters.size
    private val b0 = DoubleArray(count) { design.filters[it].b0 }
    private val b1 = DoubleArray(count) { design.filters[it].b1 }
    private val b2 = DoubleArray(count) { design.filters[it].b2 }
    private val a1 = DoubleArray(count) { design.filters[it].a1 }
    private val a2 = DoubleArray(count) { design.filters[it].a2 }
    private val z1 = DoubleArray(count * channels)
    private val z2 = DoubleArray(count * channels)

    /** One sample of [channel], returned clipped to -1..1. */
    fun process(sample: Double, channel: Int): Double {
        var x = sample * design.inputGain
        var state = channel
        for (f in 0 until count) {
            // Transposed direct form II: steady with low bands at high sample rates.
            val y = b0[f] * x + z1[state]
            z1[state] = b1[f] * x - a1[f] * y + z2[state]
            z2[state] = b2[f] * x - a2[f] * y
            x = y
            state += channels
        }
        return x.coerceIn(-1.0, 1.0)
    }

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    /** Carries on from [previous]'s filter memory, so moving a band while a song plays doesn't click. */
    fun continueFrom(previous: EqFilterBank) {
        if (previous.channels != channels || previous.z1.size != z1.size) return
        previous.z1.copyInto(z1)
        previous.z2.copyInto(z2)
    }
}

/**
 * The equaliser in the player's audio path: after decoding (FFmpeg or the phone's own), before the sound
 * goes out. It always takes part for 16-bit and float audio and passes it through untouched while off, so
 * switching it on or moving a band is heard at once, without restarting the song.
 */
@OptIn(UnstableApi::class)
class EqualiserProcessor(private val settings: () -> EqSettings) : BaseAudioProcessor() {
    private var applied: EqSettings? = null
    private var bank: EqFilterBank? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val current = settings()
        if (!current.active) {
            applied = null
            bank = null
            output.put(inputBuffer)
            output.flip()
            return
        }
        val format = inputAudioFormat
        if (current != applied || bank == null) {
            val previous = bank
            bank = EqFilterBank(EqDesign.of(current, format.sampleRate), format.channelCount).also { new ->
                previous?.let { new.continueFrom(it) }
            }
            applied = current
        }
        val filters = bank!!
        val channels = format.channelCount
        var channel = 0
        if (format.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.remaining() >= 2) {
                val y = filters.process(inputBuffer.getShort() / 32_768.0, channel)
                output.putShort((y * 32_767.0).toInt().toShort())
                if (++channel == channels) channel = 0
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                output.putFloat(filters.process(inputBuffer.getFloat().toDouble(), channel).toFloat())
                if (++channel == channels) channel = 0
            }
        }
        output.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        bank?.reset()
    }

    override fun onReset() {
        applied = null
        bank = null
    }
}
