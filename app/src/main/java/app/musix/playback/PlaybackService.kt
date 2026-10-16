package app.musix.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.musix.AppGraph
import app.musix.MainActivity
import app.musix.MusixApp
import app.musix.data.PlayEvent
import app.musix.log.AppLog
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Background playback with a media notification, lock-screen controls, ReplayGain and scrobbling. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = (application as MusixApp).graph
        // Streams go through the app's HTTP client, so a proxy login for Navidrome applies here too.
        val dataSource = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(graph.http))
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
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
        player.addListener(Events(player))

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
                        val uri = item.requestMetadata.mediaUri
                            ?: android.net.Uri.parse(graph.subsonic.streamUrl(item.mediaId))
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class Events(private val player: ExoPlayer) : Player.Listener {
        private var currentId: String? = null

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = currentId
            if (previous != null && (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                submit(previous)
            }
            currentId = mediaItem?.mediaId
            if (mediaItem != null) AppLog.i("Player", "Now playing ${mediaItem.mediaId} \"${mediaItem.mediaMetadata.title}\"")
            val song = mediaItem?.toSong()
            player.volume = replayGainVolume(song, graph.settings.current)
            if (song != null) {
                scope.launch {
                    runCatching { graph.subsonic.scrobble(song.id, submission = false) }
                    runCatching {
                        graph.history.add(PlayEvent(song.id, song.title, song.artist, song.genre, System.currentTimeMillis()))
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLog.e("Player", "Playback error ${error.errorCodeName} on ${currentId ?: "?"}", error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) currentId?.let { submit(it) }
        }

        private fun submit(id: String) {
            scope.launch { runCatching { graph.subsonic.scrobble(id, submission = true) } }
        }
    }
}
