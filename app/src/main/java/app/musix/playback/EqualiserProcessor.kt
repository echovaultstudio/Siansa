package app.musix.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import app.musix.log.AppLog
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Runs the filters of an [EqDesign] over interleaved samples (-1..1), keeping each channel's filter
 * memory between calls so the sound stays continuous from one buffer to the next. The result can go
 * past -1..1 (pre-amp, boosted bands); [PeakLimiter] brings it back without clipping.
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

    /** One sample of [channel]. */
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
        return x
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
 * goes out. It always takes part and passes the sound through untouched while off, so switching it on or
 * moving a band is heard at once, without restarting the song.
 *
 * Its output always goes through a [PeakLimiter]: a pre-amp or boosted bands can push loud songs past full
 * scale, and cutting those peaks off is heard as crackling (#92). 16-bit audio stays 16-bit; 24- and 32-bit
 * audio comes out as float, which the player then turns into 16-bit.
 */
@OptIn(UnstableApi::class)
class EqualiserProcessor(private val settings: () -> EqSettings) : BaseAudioProcessor() {
    private var applied: EqSettings? = null
    private var bank: EqFilterBank? = null
    private var limiter: PeakLimiter? = null
    private var frame = DoubleArray(0)
    private var framesSinceReport = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val output = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> C.ENCODING_PCM_16BIT
            C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT -> C.ENCODING_PCM_FLOAT
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, output)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val format = inputAudioFormat
        val channels = format.channelCount
        val inBytes = Util.getPcmFrameSize(format.encoding, channels)
        val outBytes = Util.getPcmFrameSize(outputAudioFormat.encoding, channels)
        val frames = inputBuffer.remaining() / inBytes
        val output = replaceOutputBuffer(frames * outBytes)
        val filters = currentBank(format)
        val limiter = limiter ?: PeakLimiter(format.sampleRate, channels, ceilingFor(format.encoding)).also { limiter = it }
        if (frame.size != channels) frame = DoubleArray(channels)
        repeat(frames) {
            for (c in 0 until channels) {
                val x = read(inputBuffer, format.encoding)
                frame[c] = filters?.process(x, c) ?: x
            }
            limiter.process(frame)
            for (c in 0 until channels) write(output, frame[c])
        }
        output.flip()
        framesSinceReport += frames
        if (framesSinceReport >= format.sampleRate * REPORT_SECONDS) report()
    }

    /** The filters for the current setting, or null while the equaliser is off. */
    private fun currentBank(format: AudioProcessor.AudioFormat): EqFilterBank? {
        val current = settings()
        if (!current.active) {
            applied = null
            bank = null
            return null
        }
        if (current != applied || bank == null) {
            val previous = bank
            bank = EqFilterBank(EqDesign.of(current, format.sampleRate), format.channelCount).also { new ->
                previous?.let { new.continueFrom(it) }
            }
            applied = current
        }
        return bank
    }

    private fun read(buffer: ByteBuffer, encoding: Int): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> buffer.getShort() / 32_768.0
        C.ENCODING_PCM_24BIT -> {
            val low = buffer.get().toInt() and 0xFF
            val mid = buffer.get().toInt() and 0xFF
            val high = buffer.get().toInt()
            ((high shl 16) or (mid shl 8) or low) / 8_388_608.0
        }
        C.ENCODING_PCM_32BIT -> buffer.getInt() / 2_147_483_648.0
        else -> buffer.getFloat().toDouble()
    }

    private fun write(buffer: ByteBuffer, sample: Double) {
        if (outputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            buffer.putShort((sample * 32_768.0).roundToInt().coerceIn(-32_768, 32_767).toShort())
        } else {
            buffer.putFloat(sample.toFloat())
        }
    }

    /** Notes in the log how far the limiter had to turn peaks down, when it had to at all. */
    private fun report() {
        framesSinceReport = 0
        val db = limiter?.takeDeepestDb() ?: return
        if (db < -0.5) AppLog.i("Equaliser", "Limiter turned peaks down by up to ${"%.1f".format(-db)} dB")
    }

    override fun onQueueEndOfStream() {
        // Let out the last moment of sound still waiting inside the limiter.
        val limiter = limiter ?: return
        val channels = outputAudioFormat.channelCount
        val outBytes = Util.getPcmFrameSize(outputAudioFormat.encoding, channels)
        val output = replaceOutputBuffer(limiter.latencyFrames * outBytes)
        if (frame.size != channels) frame = DoubleArray(channels)
        repeat(limiter.latencyFrames) {
            frame.fill(0.0)
            limiter.process(frame)
            for (c in 0 until channels) write(output, frame[c])
        }
        output.flip()
        report()
        limiter.reset()
    }

    // After a seek or a new song: start clean, sized for the (possibly new) sample rate and channels.
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        applied = null
        bank = null
        limiter = null
    }

    override fun onReset() {
        applied = null
        bank = null
        limiter = null
        framesSinceReport = 0
    }

    private fun ceilingFor(encoding: Int) = if (encoding == C.ENCODING_PCM_16BIT) 32_767 / 32_768.0 else 1.0

    private companion object {
        const val REPORT_SECONDS = 10
    }
}
