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
    class Built(val player: ExoPlayer, val cacheFactory: CacheDataSource.Factory, val gain: SmoothGain)

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
            val key = spec.uri.getQueryParameter("id") ?: spec.uri.toString()
            spec.withUri(Streaming.rewrite(spec.uri, kbpsFor(key)))
        }
        val dataSource = DefaultDataSource.Factory(context, resolving)

        // Start playing after one second of audio, for a snappy start. After running dry mid-song, wait for
        // five seconds' worth so a slow connection gives fewer, longer stretches instead of constant stops.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_000, 5_000)
            .build()

        // The phone's own decoders first; FFmpeg for what it lacks (ALAC on most phones). ReplayGain runs in
        // the audio pipeline (SmoothGain) so level changes and song starts glide instead of clicking.
        val gain = SmoothGain()
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(gain))
                    .build()
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
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
        return Built(player, cacheFactory, gain)
    }
}
