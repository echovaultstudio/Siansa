package app.siansa.playback

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
import android.os.Handler
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.siansa.AppGraph
import app.siansa.data.OutsideMedia
import app.siansa.log.AppLog
import app.siansa.playback.engine.AudioEngine
import app.siansa.playback.engine.MixCore
import app.siansa.playback.engine.MixSink

/**
 * Builds the player exactly as the app uses it, so the playback service and the automated tests run
 * the same streaming setup.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {
    class Built(
        val player: ExoPlayer,
        val cacheFactory: CacheDataSource.Factory,
        /** The player's bass swap (issue #807): in its deck of the mixer when there is one, else in its equaliser. */
        val deck: DeckFilter,
        /** Its deck in the mixer (issue #826), when it plays through the [AudioEngine]. */
        val mix: MixCore.Deck? = null,
    )

    /** [kbpsFor] picks, per song id, Opus kbit/s or 0 for the original file. */
    /**
     * With an [engine] and its [mixDeck] the player's sound goes to the mixer (issue #826) instead of an AudioTrack of
     * its own: its speed is passed down to it, so it's read faster or slower there and counted exactly.
     */
    fun build(
        context: Context,
        graph: AppGraph,
        kbpsFor: (String) -> Int,
        handleAudioFocus: Boolean = true,
        engine: AudioEngine? = null,
        mixDeck: MixCore.Deck? = null,
        /** The TV app's main player (issue #860): Dolby and DTS songs may go to the receiver as a bitstream. */
        passthrough: Boolean = false,
    ): Built {
        // Streams use their own HTTP queue and connections (graph.streamHttp), so covers and API calls never
        // hold them up, and report their size and arrival speed to StreamStats.
        val http = OkHttpDataSource.Factory(graph.streamHttp).setTransferListener(StreamStats.transferListener)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(graph.songCache)
            .setUpstreamDataSourceFactory(http)
            .setCacheKeyFactory { spec -> songCacheKey(spec.uri) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(PlaybackDiagnostics.cacheListener)
        // With the song cache off (Settings › Data) and nothing loaded ahead, songs stream straight from the server.
        // With it off but songs loaded ahead, they pass through its store and are removed once played.
        val switching = CachingSwitchDataSource.Factory(cacheFactory, http) {
            Preload.usesCacheStore(graph.settings.current, Streaming.isUnmetered(context))
        }
        if (!songCacheOn(graph.settings.current)) AppLog.i("Cache", "Song cache is off: songs aren't kept after they play")
        // Each stream is pointed at the original file or an Opus conversion when it opens.
        val resolving = ResolvingDataSource.Factory(switching) { spec ->
            // A radio station or podcast episode (issue #353) plays from its own address as it is.
            if (OutsideMedia.isMarked(spec.uri.toString())) spec else {
                val uri = spec.uri.toString().let { if (Streaming.RELOAD_PARAM in it) android.net.Uri.parse(Streaming.withoutReload(it)) else spec.uri }
                val key = streamSongId(uri) ?: uri.toString()
                spec.withUri(Streaming.rewrite(uri, kbpsFor(key)))
            }
        }
        // Songs downloaded for offline listening play from their file, with no network at all: on every kind of
        // server, as the song's id is read from Navidrome's `?id=` and from Emby's and Jellyfin's `/Audio/<id>/` (#903).
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(context, resolving)) { spec ->
            val id = if ((spec.uri.scheme == "http" || spec.uri.scheme == "https") && !OutsideMedia.isMarked(spec.uri.toString())) streamSongId(spec.uri) else null
            id?.let { graph.downloads.fileFor(it) }?.let { spec.withUri(android.net.Uri.fromFile(it)) } ?: spec
        }

        // Start playing after one second of audio, for a snappy start. After running dry mid-song, wait for
        // five seconds' worth so a slow connection gives fewer, longer stretches instead of constant stops.
        // A minute is kept loaded ahead at all times (issue #816): the next song is only loaded once this one is,
        // and a DJ blend starts 30 s before the end, so with loading paused until 15 s were left the next song
        // began every blend with nothing loaded, up to 400 ms late and off the beat.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(BUFFER_AHEAD_MS, BUFFER_AHEAD_MS, 1_000, 5_000)
            .build()

        // FFmpeg's decoders first (FLAC, ALAC, MP3, AAC...); the phone's own for the rest (Opus).
        // media3 splits FLAC into frames by searching for headers without checking the frame number,
        // so audio that happens to look like a header splits a real frame in two (Skinty Fia at 0:59).
        // The phone's FLAC decoder then outputs only silence and the song freezes; FFmpeg skips the
        // broken pieces (about 85 ms) and plays on.
        // The equaliser sits in the audio path after either decoder, so it works the same on every phone.
        // Float output stays off: with it, media3 1.9 sends hi-res and float audio (everything FFmpeg
        // decodes) straight out and skips the equaliser.
        val equaliser = EqualiserProcessor { graph.equaliser.state.value }
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
                if (engine != null && mixDeck != null) {
                    MixSink(
                        DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioOutputPlaybackParameters(true)
                            .setAudioProcessors(arrayOf(equaliser))
                            .setAudioOutputProvider(engine.provider(mixDeck))
                            .build(),
                        mixDeck,
                    )
                } else {
                    DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                        .setAudioProcessors(arrayOf(equaliser))
                        .build()
                }

            // Passthrough (issue #860) goes first, so it takes the Dolby and DTS songs the receiver can decode before
            // FFmpeg would; it refuses everything else, which goes on to the usual renderers below.
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>,
            ) {
                if (passthrough) {
                    out.add(PassthroughRenderer.create(context, mediaCodecSelector, eventHandler, eventListener) { graph.settings.current.audioPassthrough })
                }
                super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        AppLog.i("Player", "FFmpeg decoders " + if (FfmpegLibrary.isAvailable()) "ready (${FfmpegLibrary.getVersion()})" else "not available")

        val player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, SongLength.extractors()))
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
        return Built(player, cacheFactory, mixDeck?.filter ?: equaliser.deck, mixDeck)
    }

    /** How much of a song (and then of the next) is loaded ahead: a minute, more than the longest blend. */
    const val BUFFER_AHEAD_MS = 60_000
}
