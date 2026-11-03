package app.siansa.playback.engine

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import app.siansa.log.AppLog
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Where a music player's sound goes instead of an AudioTrack of its own (issue #826): one stream of its deck in the
 * [AudioEngine]'s mix. Media3's DefaultAudioSink drives it as it would an AudioTrack (play, pause, write, the speed,
 * the volume), and asks it where it is: the frames the mixer has taken from it up to what's heard now, on the one
 * clock all players share.
 *
 * Speed is passed down to it (the sink's "audio output playback parameters"), so the mixer plays the song faster or
 * slower by reading it at another rate and counts exactly how far it got; Media3's Sonic isn't in the way.
 */
@OptIn(UnstableApi::class)
class DeckOutput(
    private val engine: AudioEngine,
    private val deck: MixCore.Deck,
    private val config: AudioOutputProvider.OutputConfig,
    channels: Int,
) : AudioOutput {
    val stream = DeckStream(
        config.sampleRate,
        channels,
        if (config.encoding == C.ENCODING_PCM_FLOAT) DeckStream.Encoding.PCM_FLOAT else DeckStream.Encoding.PCM_16,
    )
    private val listeners = CopyOnWriteArraySet<AudioOutput.Listener>()
    @Volatile private var parameters = PlaybackParameters.DEFAULT

    init {
        deck.add(stream)
        engine.outputs[stream] = this
        // Each output's life in the log (0.52.0-dev: songs played back to back came out silent): whether Media3 keeps
        // one output across a change of song or makes a new one, and what the new one starts as.
        AppLog.d("Engine", "Deck ${deck.name}: new output ${id()}, ${config.sampleRate} Hz, ${if (config.encoding == C.ENCODING_PCM_FLOAT) "float" else "16-bit"}, $channels ch · ${stream.describe()}")
    }

    private fun id() = Integer.toHexString(System.identityHashCode(this))

    /** The mixer first played this stream's sound since it was told to play: Media3 is told the position moves. */
    internal fun advancing() {
        val now = System.currentTimeMillis()
        listeners.forEach { it.onPositionAdvancing(now) }
    }

    override fun play() {
        stream.play()
        engine.wake()
    }

    override fun pause() = stream.pause()

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, presentationTimeUs: Long): Boolean {
        val all = stream.write(buffer)
        if (stream.playing) engine.wake()
        return all
    }

    /** Media3 releases an output on every flush; one that's flushed anyway starts as a new stream. */
    override fun flush() {
        stream.pause()
    }

    override fun stop() {
        AppLog.d("Engine", "Deck ${deck.name}: output ${id()} told to play out what it has · ${stream.describe()}")
        stream.endOfStream()
    }

    override fun release() {
        AppLog.d("Engine", "Deck ${deck.name}: output ${id()} released")
        stream.release()
        engine.outputs.remove(stream)
        listeners.forEach { it.onReleased() }
    }

    override fun setVolume(volume: Float) {
        stream.volume = volume
    }

    override fun isOffloadedPlayback(): Boolean = false

    override fun getAudioSessionId(): Int = engine.sessionId

    override fun getSampleRate(): Int = config.sampleRate

    override fun getBufferSizeInFrames(): Long = stream.capacityFrames.toLong()

    override fun getPositionUs(): Long = stream.positionUsAt(engine.heardMix())

    override fun getPlaybackParameters(): PlaybackParameters = parameters

    override fun isStalled(): Boolean = false

    override fun addListener(listener: AudioOutput.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AudioOutput.Listener) {
        listeners.remove(listener)
    }

    override fun setPlaybackParameters(playbackParams: PlaybackParameters) {
        parameters = playbackParams
        stream.speed = playbackParams.speed
    }

    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) = Unit

    override fun setOffloadEndOfStream() = Unit

    // The echo at the end of a blend used an effect on a player's own AudioTrack; with one output for all, it's
    // not there yet (issue #826).
    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit
}

/** Hands each music player's sound to its [MixCore.Deck] in the [AudioEngine] (issue #826): PCM only, as decoded. */
@OptIn(UnstableApi::class)
class MixerOutputProvider(private val engine: AudioEngine, private val deck: MixCore.Deck) : AudioOutputProvider {
    override fun getFormatSupport(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport {
        val format = formatConfig.format
        val ok = MimeTypes.AUDIO_RAW == format.sampleMimeType &&
            (format.pcmEncoding == C.ENCODING_PCM_16BIT || format.pcmEncoding == C.ENCODING_PCM_FLOAT) &&
            format.channelCount in 1..8
        return if (ok) {
            AudioOutputProvider.FormatSupport.Builder()
                .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                .build()
        } else {
            AudioOutputProvider.FormatSupport.UNSUPPORTED
        }
    }

    override fun getOutputConfig(formatConfig: AudioOutputProvider.FormatConfig): AudioOutputProvider.OutputConfig {
        val format = formatConfig.format
        if (MimeTypes.AUDIO_RAW != format.sampleMimeType) {
            throw AudioOutputProvider.ConfigurationException("Only decoded audio goes to the mixer: $format")
        }
        val channels = format.channelCount
        val mask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> Util.getAudioTrackChannelConfig(channels)
        }
        val frameBytes = Util.getPcmFrameSize(format.pcmEncoding, channels)
        return AudioOutputProvider.OutputConfig.Builder()
            .setEncoding(format.pcmEncoding)
            .setSampleRate(format.sampleRate)
            .setChannelMask(mask)
            .setBufferSize(format.sampleRate * DeckStream.CAPACITY_MS / 1_000 * frameBytes)
            .setAudioAttributes(formatConfig.audioAttributes)
            .setAudioSessionId(engine.sessionId)
            .setUsePlaybackParameters(true)
            .build()
    }

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput =
        DeckOutput(engine, deck, config, Integer.bitCount(config.channelMask).coerceAtLeast(1))

    override fun addListener(listener: AudioOutputProvider.Listener) = Unit

    override fun removeListener(listener: AudioOutputProvider.Listener) = Unit

    override fun release() = Unit
}
