package app.musix.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Applies ReplayGain inside the audio pipeline, gliding to each new level over a few milliseconds.
 *
 * Setting the player's volume jumps straight to the new level, and it lands a moment after the next
 * song has started, which clicks. Here the level slides instead, and every fresh start (a new song
 * that needed the audio output reset, a skip, a seek) fades in from silence the same way, so the
 * output never starts mid-waveform with a pop.
 */
@OptIn(UnstableApi::class)
class SmoothGain(private val rampMs: Int = 30) : BaseAudioProcessor() {
    /** The level to glide to, 0..1. Set from any thread. */
    @Volatile var target = 1f

    private var gain = 0f
    private var stepPerFrame = 1f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        stepPerFrame = 1f / (inputAudioFormat.sampleRate * rampMs / 1000f).coerceAtLeast(1f)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val goal = target
        if (gain == 1f && goal == 1f) {
            output.put(inputBuffer)
        } else {
            val channels = inputAudioFormat.channelCount
            val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            val frameBytes = channels * if (float) 4 else 2
            while (inputBuffer.remaining() >= frameBytes) {
                gain = when {
                    gain < goal -> minOf(goal, gain + stepPerFrame)
                    gain > goal -> maxOf(goal, gain - stepPerFrame)
                    else -> gain
                }
                repeat(channels) {
                    if (float) {
                        output.putFloat(inputBuffer.getFloat() * gain)
                    } else {
                        output.putShort((inputBuffer.getShort() * gain).roundToInt().coerceIn(-32768, 32767).toShort())
                    }
                }
            }
            inputBuffer.position(inputBuffer.limit())
        }
        output.flip()
    }

    // A flush means the output restarts (new song, skip or seek): come in from silence.
    override fun onFlush() {
        gain = 0f
    }

    override fun onReset() {
        gain = 0f
    }
}
