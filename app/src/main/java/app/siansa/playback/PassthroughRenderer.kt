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
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

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
        if (mode() == PassthroughMode.OFF || !Passthrough.isBitstream(format.sampleMimeType) ||
            sink.getFormatSupport(format) != AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        ) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        return super.supportsFormat(mediaCodecSelector, format)
    }

    companion object {
        fun create(
            context: Context,
            selector: MediaCodecSelector,
            handler: Handler?,
            listener: AudioRendererEventListener?,
            mode: () -> PassthroughMode,
        ): PassthroughRenderer =
            PassthroughRenderer(context, selector, handler, listener, DefaultAudioSink.Builder(context).build(), mode)

        /**
         * Whether this TV's output takes [mime] as a bitstream right now, for the TV's settings page. Asks a sink of
         * its own, which reads the same capabilities the player does.
         */
        fun outputTakes(context: Context, mime: String): Boolean {
            val sink = DefaultAudioSink.Builder(context).build()
            return try {
                val format = Format.Builder().setSampleMimeType(mime).setChannelCount(6).setSampleRate(48_000).build()
                sink.getFormatSupport(format) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            } finally {
                sink.release()
            }
        }
    }
}
