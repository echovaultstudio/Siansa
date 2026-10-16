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

        val built = PlayerFactory.build(this, graph, ::kbpsFor)
        val player = built.player
        cacheFactory = built.cacheFactory
        events = Events(player).also { player.addListener(it) }
        player.addAnalyticsListener(PlaybackDiagnostics(player, this) { waited -> onStall(player, waited) })
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
            .setBitmapLoader(CoverBitmapLoader(this, graph.scope))
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
        val chosen = Streaming.bitrate(this, graph.settings.current)
        val slow = chosen == 0 && SystemClock.elapsedRealtime() < slowUntil
        (if (slow) slowKbps else chosen).also {
            AppLog.i("Stream", "Song $songId: " + when {
                slow -> "Opus $it kbit/s (connection was too slow for the original)"
                it > 0 -> "Opus $it kbit/s"
                else -> "original"
            })
        }
    }

    // ---- Slow connection: switch to Opus for a while instead of stopping every few seconds ----

    @Volatile private var slowUntil = 0L
    @Volatile private var slowKbps = 0
    private val stalls = ArrayDeque<Long>()

    private fun onStall(player: ExoPlayer, waitedMs: Long) {
        if (waitedMs < 1500) return
        val now = SystemClock.elapsedRealtime()
        stalls.addLast(now)
        while (stalls.isNotEmpty() && now - stalls.first() > 120_000) stalls.removeFirst()
        if (stalls.size < 3) return
        stalls.clear()
        switchToOpus(player, "stopped 3 times in 2 minutes")
    }

    /** Started when playback begins waiting for data; switches early if the wait drags on and data is too slow. */
    private val bufferingWatch = Runnable {
        val player = session?.player as? ExoPlayer ?: return@Runnable
        if (player.playbackState != Player.STATE_BUFFERING || !player.playWhenReady) return@Runnable
        val id = player.currentMediaItem?.mediaId ?: return@Runnable
        val arriving = StreamStats.recentKbps()
        val needed = StreamStats.neededKbps(id) ?: return@Runnable
        if (arriving < 0 || arriving < needed * 0.9) switchToOpus(player, "waited 8 s for the song to start or continue")
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun switchToOpus(player: ExoPlayer, why: String) {
        val item = player.currentMediaItem ?: return
        if ((chosenKbps[item.mediaId] ?: 0) > 0) return
        if (!graph.settings.current.autoQuality) {
            AppLog.w("Stream", "Playback $why; the automatic switch to Opus is off in Settings → Playback")
            return
        }
        val now = SystemClock.elapsedRealtime()
        val arriving = StreamStats.recentKbps()
        // Pick an Opus bitrate that fits comfortably in what's arriving (Opus 160 already sounds very good).
        val kbps = if (arriving <= 0) 160 else listOf(320, 256, 192, 160, 128, 96).firstOrNull { it <= arriving * 0.7 } ?: 96
        slowKbps = kbps
        slowUntil = now + 15 * 60_000
        stalls.clear()
        handler.removeCallbacks(bufferingWatch)
        precacheWriter?.cancel()
        precacheJob?.cancel()
        AppLog.w(
            "Stream",
            "Playback $why: the connection is too slow for the original file (arriving at ${if (arriving > 0) "$arriving kbit/s" else "an unknown speed"}, " +
                "song needs ~${StreamStats.neededKbps(item.mediaId) ?: "?"} kbit/s). Switching to Opus $kbps kbit/s for 15 minutes.",
        )
        chosenKbps.clear()
        chosenKbps[item.mediaId] = kbps
        val uri = item.localConfiguration?.uri ?: return
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        player.replaceMediaItem(index, item.buildUpon().setUri(Streaming.rewrite(uri, kbps)).build())
        player.seekTo(index, position)
    }

    /**
     * Downloads the next song in the queue into the song cache while this one plays, so the change
     * is instant and survives a dropped connection. Only originals are cached.
     */
    private var precachedFor: String? = null

    private fun precacheNext(player: Player) {
        precacheWriter?.cancel()
        precacheJob?.cancel()
        precachedFor = player.currentMediaItem?.mediaId
        val index = player.nextMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val item = player.getMediaItemAt(index)
        val uri = item.localConfiguration?.uri ?: return
        if (kbpsFor(item.mediaId) > 0) return
        val factory = cacheFactory ?: return
        val writer = CacheWriter(factory.createDataSourceForDownloading(), DataSpec(uri), null, null)
        precacheWriter = writer
        // Only called once the current song has fully loaded, so the two never share the connection.
        AppLog.i("Cache", "Pre-caching next song ${item.mediaId}")
        precacheJob = scope.launch {
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
        handler.removeCallbacks(bufferingWatch)
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
            val started = startedAtMs
            val heard = listenedMs
            scope.launch { runCatching { graph.history.finish(song.id, started, heard, song.durationSec) } }
            current = null
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The same song swapped for another quality or format: keep counting it as one listen.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem?.mediaId != null && mediaItem.mediaId == current?.id) return
            precacheWriter?.cancel()
            precacheJob?.cancel()
            precachedFor = null
            finishCurrent()
            val song = mediaItem?.toSong() ?: return
            current = song
            startedAtMs = System.currentTimeMillis()
            listenedMs = 0
            playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else null
            AppLog.i("Player", "Now playing ${song.id} \"${song.title}\"")
            focusRescued = false
            restarted = false
            if (player.playWhenReady) armStartWatch()
            player.volume = replayGainVolume(song, graph.settings.current)
            graph.scrobbler.nowPlaying(song)
            scope.launch {
                runCatching { graph.history.add(PlayEvent(song.id, song.title, song.artist, song.genre, System.currentTimeMillis())) }
            }
        }

        // The current song is fully downloaded: now the next one can be fetched without slowing it down.
        override fun onIsLoadingChanged(isLoading: Boolean) {
            if (isLoading) return
            val id = player.currentMediaItem?.mediaId ?: return
            val duration = player.duration
            if (precachedFor != id && duration > 0 && player.bufferedPosition >= duration - 1000) precacheNext(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onIsPlayingChanged2(isPlaying)
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
            handler.removeCallbacks(bufferingWatch)
            if (playbackState == Player.STATE_BUFFERING) handler.postDelayed(bufferingWatch, 8_000)
            if (playbackState == Player.STATE_ENDED) finishCurrent()
        }

        // ---- Start watchdog: a chosen song must actually start playing ----

        private var startRequestedAt = 0L
        private var startRescues = 0
        private var focusRescued = false

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) armStartWatch() else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                // Lost the audio moments after a song was chosen: almost always the app we took over
                // from answering late. Take it back once instead of sitting paused.
                val sinceStart = SystemClock.elapsedRealtime() - startRequestedAt
                if (sinceStart < 3_000 && !focusRescued) {
                    focusRescued = true
                    AppLog.w("Player", "Audio focus lost ${sinceStart}ms after the song was chosen; asking for it again")
                    handler.postDelayed({ player.play() }, 300)
                }
            }
        }

        private fun armStartWatch() {
            startRequestedAt = SystemClock.elapsedRealtime()
            startRescues = 0
            lastBuffered = -1L
            lastBytes = StreamStats.totalBytes()
            handler.removeCallbacks(startWatch)
            handler.postDelayed(startWatch, 2_500)
        }

        private var lastBuffered = -1L
        private var lastBytes = -1L
        private var restarted = false

        /**
         * 2.5 s after a song is chosen (or play is pressed) it should be audible. If it isn't:
         * - held back by audio focus: ask for the audio again;
         * - still waiting for data and nothing new has arrived since the last look: restart the
         *   download from the current position (a stalled connection otherwise waits for a timeout).
         * Checks again every 2.5 s until it plays, up to a few times, logging what it found.
         */
        private val startWatch = object : Runnable {
            override fun run() {
                if (!player.playWhenReady || player.isPlaying || player.mediaItemCount == 0) return
                val id = player.currentMediaItem?.mediaId ?: return
                val waited = SystemClock.elapsedRealtime() - startRequestedAt
                val buffered = player.bufferedPosition
                when {
                    player.playbackState == Player.STATE_READY && player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> {
                        AppLog.w("Player", "Song $id ready but held back (reason ${player.playbackSuppressionReason}) after ${waited}ms; asking for the audio again")
                        player.pause()
                        player.play()
                    }
                    player.playbackState == Player.STATE_IDLE -> {
                        AppLog.w("Player", "Song $id stopped before it started (${player.playerError?.errorCodeName ?: "no error"}) after ${waited}ms; preparing it again")
                        player.prepare()
                    }
                    // Only when nothing at all has arrived since the last look (a big FLAC can be arriving
                    // for a while before the player counts any of it as buffered), and once per song.
                    player.playbackState == Player.STATE_BUFFERING && lastBytes >= 0 && StreamStats.totalBytes() == lastBytes && !restarted -> {
                        restarted = true
                        AppLog.w(
                            "Player",
                            "Song $id waiting ${waited}ms and no data arrived in the last 2.5 s (${buffered / 1000.0}s loaded); restarting its download",
                        )
                        player.seekTo(player.currentMediaItemIndex, player.currentPosition)
                    }
                    else -> AppLog.i("Player", "Song $id not playing yet after ${waited}ms (state ${player.playbackState}, ${buffered / 1000.0}s loaded)")
                }
                lastBuffered = buffered
                lastBytes = StreamStats.totalBytes()
                if (++startRescues < 5) handler.postDelayed(this, 2_500)
            }
        }

        private fun onIsPlayingChanged2(isPlaying: Boolean) {
            if (!isPlaying || startRequestedAt == 0L) return
            val waited = SystemClock.elapsedRealtime() - startRequestedAt
            startRequestedAt = 0L
            lastBuffered = -1L
            handler.removeCallbacks(startWatch)
            if (waited > 1500) AppLog.w("Player", "Started playing ${waited}ms after it was chosen")
            else AppLog.d("Player", "Started playing ${waited}ms after it was chosen")
        }
    }
}
