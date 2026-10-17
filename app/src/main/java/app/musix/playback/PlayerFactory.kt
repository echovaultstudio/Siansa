package app.musix.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.musix.AppGraph
import app.musix.log.AppLog

/**
 * Builds the player exactly as the app uses it, so the playback service and the automated tests run
 * the same streaming setup.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {
    class Built(val player: ExoPlayer, val cacheFactory: CacheDataSource.Factory)

    /** [kbpsFor] picks, per song id, Opus kbit/s or 0 for the original file. */
    fun build(context: Context, graph: AppGraph, kbpsFor: (String) -> Int, handleAudioFocus: Boolean = true): Built {
        // Streams use their own HTTP queue and connections (graph.streamHttp), so covers and API calls never
        // hold them up, and report their size and arrival speed to StreamStats.
        val http = OkHttpDataSource.Factory(graph.streamHttp).setTransferListener(StreamStats.transferListener)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(graph.songCache)
            .setUpstreamDataSourceFactory(http)
            .setCacheKeyFactory { spec -> songCacheKey(spec.uri) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(PlaybackDiagnostics.cacheListener)
        val switching = CachingSwitchDataSource.Factory(cacheFactory, http)
        // Each stream is pointed at the original file or an Opus conversion when it opens.
        val resolving = ResolvingDataSource.Factory(switching) { spec ->
            val uri = spec.uri.toString().let { if (Streaming.RELOAD_PARAM in it) android.net.Uri.parse(Streaming.withoutReload(it)) else spec.uri }
            val key = uri.getQueryParameter("id") ?: uri.toString()
            spec.withUri(Streaming.rewrite(uri, kbpsFor(key)))
        }
        val dataSource = DefaultDataSource.Factory(context, resolving)

        // Start playing after one second of audio, for a snappy start. After running dry mid-song, wait for
        // five seconds' worth so a slow connection gives fewer, longer stretches instead of constant stops.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_000, 5_000)
            .build()

        // FFmpeg's decoders first (FLAC, ALAC, MP3, AAC...); the phone's own for the rest (Opus).
        // media3 splits FLAC into frames by searching for headers without checking the frame number,
        // so audio that happens to look like a header splits a real frame in two (Skinty Fia at 0:59).
        // The phone's FLAC decoder then outputs only silence and the song freezes; FFmpeg skips the
        // broken pieces (about 85 ms) and plays on.
        // The equaliser sits in the audio path after either decoder, so it works the same on every phone.
        val equaliser = EqualiserProcessor { graph.equaliser.state.value }
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessors(arrayOf(equaliser))
                    .build()
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        AppLog.i("Player", "FFmpeg decoders " + if (FfmpegLibrary.isAvailable()) "ready (${FfmpegLibrary.getVersion()})" else "not available")

        val player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        return Built(player, cacheFactory)
    }
}
