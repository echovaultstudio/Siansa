package app.musix.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.Tracks
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.musix.AppGraph
import app.musix.MainActivity
import app.musix.MusixApp
import app.musix.data.PlayEvent
import app.musix.data.Song
import app.musix.log.AppLog
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InterruptedIOException
import java.util.Collections

private const val FALLBACK_KBPS = 320

/** Background playback with a media notification, lock-screen controls, ReplayGain, transcoding and scrobbling. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var graph: AppGraph
    private var events: Events? = null
    private var cacheFactory: CacheDataSource.Factory? = null
    private var precacheJob: Job? = null
    private var precacheWriter: CacheWriter? = null

    // The quality chosen when a song's stream first opens, so a network change can't switch format mid-song.
    private val chosenKbps: MutableMap<String, Int> = Collections.synchronizedMap(object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 200
    })

    override fun onCreate() {
        super.onCreate()
        graph = (application as MusixApp).graph

        // Streams go through the app's HTTP client (so a proxy login applies), and each stream is pointed
        // at the original file or an Opus conversion depending on the network at that moment.
        val http = OkHttpDataSource.Factory(graph.http)
        cacheFactory = CacheDataSource.Factory()
            .setCache(graph.songCache)
            .setUpstreamDataSourceFactory(http)
            .setCacheKeyFactory { spec -> songCacheKey(spec.uri) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(PlaybackDiagnostics.cacheListener)
        val switching = CachingSwitchDataSource.Factory(cacheFactory!!, http)
        val resolving = ResolvingDataSource.Factory(switching) { spec ->
            val key = spec.uri.getQueryParameter("id") ?: spec.uri.toString()
            spec.withUri(Streaming.rewrite(spec.uri, kbpsFor(key)))
        }
        val dataSource = DefaultDataSource.Factory(this, resolving)

        // Start playing after one second of audio instead of the default 2.5, for a snappier start.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_000, 2_000)
            .build()

        // The phone's own decoders first; FFmpeg for what it lacks (ALAC on most phones).
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        AppLog.i("Player", "FFmpeg decoders " + if (FfmpegLibrary.isAvailable()) "ready (${FfmpegLibrary.getVersion()})" else "not available")

        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        events = Events(player).also { player.addListener(it) }
        player.addAnalyticsListener(PlaybackDiagnostics(player, this))
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                AppLog.i("Audio", "Decoder $decoderName")
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Items arrive from the app's controller without their URI, so it's restored here.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri ?: Uri.parse(graph.subsonic.streamUrl(item.mediaId))
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        // Send any listens that were waiting from an earlier session.
        scope.launch { runCatching { graph.scrobbler.flush() } }
    }

    private fun kbpsFor(songId: String): Int = chosenKbps.getOrPut(songId) {
        Streaming.bitrate(this, graph.settings.current).also {
            AppLog.i("Stream", "Song $songId: " + if (it > 0) "Opus $it kbit/s" else "original")
        }
    }

    /**
     * Downloads the next song in the queue into the song cache while this one plays, so the change
     * is instant and survives a dropped connection. Only originals are cached.
     */
    private fun precacheNext(player: Player) {
        precacheWriter?.cancel()
        precacheJob?.cancel()
        val index = player.nextMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val item = player.getMediaItemAt(index)
        val uri = item.localConfiguration?.uri ?: return
        if (kbpsFor(item.mediaId) > 0) return
        val factory = cacheFactory ?: return
        val writer = CacheWriter(factory.createDataSourceForDownloading(), DataSpec(uri), null, null)
        precacheWriter = writer
        precacheJob = scope.launch {
            // Give the song that's starting the connection to itself first; downloading the next song at
            // the same moment slowed every start down.
            kotlinx.coroutines.delay(20_000)
            try {
                writer.cache()
                AppLog.i("Cache", "Pre-cached next song ${item.mediaId}")
            } catch (e: InterruptedIOException) {
                // Cancelled because the queue moved on.
            } catch (e: Exception) {
                AppLog.w("Cache", "Pre-caching ${item.mediaId} failed: ${e.message}")
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        precacheWriter?.cancel()
        events?.finishCurrent()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    /** Tracks how long each song is actually heard, for scrobbling, and applies ReplayGain. */
    private inner class Events(private val player: ExoPlayer) : Player.Listener {
        private var current: Song? = null
        private var startedAtMs = 0L
        private var listenedMs = 0L
        private var playingSince: Long? = null

        fun finishCurrent() {
            val song = current ?: return
            playingSince?.let { listenedMs += SystemClock.elapsedRealtime() - it }
            playingSince = null
            graph.scrobbler.finished(song, startedAtMs, listenedMs)
            current = null
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            finishCurrent()
            val song = mediaItem?.toSong() ?: return
            current = song
            startedAtMs = System.currentTimeMillis()
            listenedMs = 0
            playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else null
            AppLog.i("Player", "Now playing ${song.id} \"${song.title}\"")
            player.volume = replayGainVolume(song, graph.settings.current)
            graph.scrobbler.nowPlaying(song)
            precacheNext(player)
            scope.launch {
                runCatching { graph.history.add(PlayEvent(song.id, song.title, song.artist, song.genre, System.currentTimeMillis())) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val now = SystemClock.elapsedRealtime()
            if (isPlaying) {
                if (playingSince == null) playingSince = now
            } else {
                playingSince?.let { listenedMs += now - it }
                playingSince = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLog.e("Player", "Playback error ${error.errorCodeName} on ${current?.id ?: "?"}", error)
        }

        /**
         * Logs each song's format, and rescues songs whose audio the phone can't decode: instead of
         * silently skipping through them, the song is played again as Opus converted by Navidrome.
         */
        override fun onTracksChanged(tracks: Tracks) {
            val item = player.currentMediaItem ?: return
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audio.isEmpty()) return
            val format = audio.first().getTrackFormat(0)
            val desc = listOfNotNull(
                format.containerMimeType,
                format.sampleMimeType,
                format.codecs,
                format.sampleRate.takeIf { it > 0 }?.let { "$it Hz" },
                format.channelCount.takeIf { it > 0 }?.let { "$it ch" },
                format.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbit/s" },
            ).joinToString(" · ")
            val playable = audio.any { g -> (0 until g.length).any { g.isTrackSupported(it) } }
            if (playable) {
                AppLog.i("Audio", "Format of ${item.mediaId}: $desc")
                return
            }
            if (chosenKbps[item.mediaId] == FALLBACK_KBPS) {
                AppLog.e("Audio", "Can't play ${item.mediaId} even as Opus: $desc")
                return
            }
            AppLog.w("Audio", "This phone can't decode ${item.mediaId} ($desc); playing it as Opus $FALLBACK_KBPS kbit/s from Navidrome")
            chosenKbps[item.mediaId] = FALLBACK_KBPS
            val uri = item.localConfiguration?.uri ?: return
            player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setUri(Streaming.rewrite(uri, FALLBACK_KBPS)).build())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) finishCurrent()
        }
    }
}
