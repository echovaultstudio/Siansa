package app.siansa.playback

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import app.siansa.log.AppLog

/**
 * Audio passthrough on the TV app (issue #860): takes only Dolby and DTS songs, and only when passthrough is on and the
 * TV says the receiver behind it takes that format, and sends them to an AudioTrack of their own as a bitstream.
 * It sits before the other audio renderers, so FFmpeg never decodes these songs to PCM; everything else is left to
 * them and plays through the audio engine as usual.
 *
 * Its own [DefaultAudioSink] reads what the HDMI output supports from Android (AudioCapabilities) and follows changes
 * to it, such as the receiver being switched on.
 */
@OptIn(UnstableApi::class)
class PassthroughRenderer private constructor(
    context: Context,
    selector: MediaCodecSelector,
    handler: Handler?,
    listener: AudioRendererEventListener?,
    private val sink: AudioSink,
    private val mode: () -> PassthroughMode,
) : MediaCodecAudioRenderer(context, selector, handler, listener, sink) {

    override fun getName(): String = "PassthroughRenderer"

    override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int {
        val mime = format.sampleMimeType
        if (!Passthrough.isBitstream(mime)) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        val mode = mode()
        val support = sink.getFormatSupport(format)
        val failed = Passthrough.failed
        val take = mode != PassthroughMode.OFF && failed == null && support == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        // What was decided for each format, once: the first thing to look at when a Dolby or DTS song doesn't play.
        val key = "$mime/${format.channelCount}/${format.sampleRate}/$mode/$support/${failed != null}"
        if (Passthrough.logged.add(key)) {
            val what = "${Passthrough.name(mime)}, ${format.channelCount} ch, ${format.sampleRate} Hz"
            val decision = when {
                take -> "sent to the receiver unchanged"
                mode == PassthroughMode.OFF -> "decoded on the TV (passthrough is off)"
                failed != null -> "decoded on the TV (passthrough gave up earlier: $failed)"
                else -> "decoded on the TV (the TV's output doesn't take it as a bitstream: ${supportName(support)})"
            }
            AppLog.i("Passthrough", "$what: $decision", event = "passthrough.decision", fields = mapOf(
                "mime" to mime, "channels" to format.channelCount, "sample_rate" to format.sampleRate,
                "mode" to mode.name, "sink_support" to supportName(support), "taken" to take,
            ))
        }
        if (!take) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        return super.supportsFormat(mediaCodecSelector, format)
    }

    private fun supportName(support: Int): String = when (support) {
        AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY -> "directly"
        AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING -> "only decoded"
        AudioSink.SINK_FORMAT_UNSUPPORTED -> "not at all"
        else -> "code $support"
    }

    /** A bitstream's buffer from its format alone (see [sink]); decoded audio keeps Media3's own size. */
    private object SteadyBuffer : DefaultAudioSink.AudioTrackBufferSizeProvider {
        override fun getBufferSizeInBytes(
            minBufferSizeInBytes: Int,
            encoding: Int,
            outputMode: Int,
            pcmFrameSize: Int,
            sampleRate: Int,
            bitrate: Int,
            maxAudioTrackPlaybackSpeed: Double,
        ): Int = DefaultAudioSink.AudioTrackBufferSizeProvider.DEFAULT.getBufferSizeInBytes(
            minBufferSizeInBytes, encoding, outputMode, pcmFrameSize, sampleRate,
            if (outputMode == DefaultAudioSink.OUTPUT_MODE_PASSTHROUGH) Format.NO_VALUE else bitrate,
            maxAudioTrackPlaybackSpeed,
        )
    }

    companion object {
        fun create(
            context: Context,
            selector: MediaCodecSelector,
            handler: Handler?,
            listener: AudioRendererEventListener?,
            mode: () -> PassthroughMode,
        ): PassthroughRenderer =
            PassthroughRenderer(context, selector, handler, listener, sink(context), mode)

        /**
         * The passthrough sink, with an output buffer that's the same size for every song of a format. Media3 sizes it
         * from each file's bitrate, and keeps the output open from one song to the next only when nothing about it
         * changes: two Atmos songs of different bitrates closed and reopened it, a gap (and the receiver finding the
         * signal again) between songs meant to run into each other, such as a DJ mix. Sized for the format's highest
         * rate instead, it stays open, gapless as in Kodi.
         */
        private fun sink(context: Context): AudioSink {
            val output = AudioTrackAudioOutputProvider.Builder(context).setAudioTrackBufferSizeProvider(SteadyBuffer).build()
            return DefaultAudioSink.Builder(context).setAudioOutputProvider(output).build()
        }

        /**
         * Whether this TV's output takes [mime] as a bitstream right now, for the TV's settings page. Asks a sink of
         * its own, which reads the same capabilities the player does.
         */
        fun outputTakes(context: Context, mime: String): Boolean {
            val sink = sink(context)
            return try {
                val format = Format.Builder().setSampleMimeType(mime).setChannelCount(6).setSampleRate(48_000).build()
                sink.getFormatSupport(format) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            } finally {
                sink.release()
            }
        }
    }
}
