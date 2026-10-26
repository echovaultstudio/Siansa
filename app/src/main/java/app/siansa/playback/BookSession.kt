package app.siansa.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import app.siansa.AppGraph
import app.siansa.MainActivity
import app.siansa.log.AppLog
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The service's player and session for audiobooks (issue #521): a plain ExoPlayer with the book's files one after
 * another, apart from the music player and its crossfades, server seeks and casting. A controller that connects
 * with [HINT_BOOKS] gets this session; the music session stays the service's main one.
 *
 * Starting a book pauses the music and the other way round, so the two never play at once.
 */
@OptIn(UnstableApi::class)
class BookSession(private val service: PlaybackService, private val graph: AppGraph, private val pauseMusic: () -> Unit) {
    val player: ExoPlayer = ExoPlayer.Builder(service)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(service, OkHttpDataSource.Factory(graph.streamHttp))))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    val session: MediaSession

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) pauseMusic()
            }
        })
        val openApp = PendingIntent.getActivity(service, 1, Intent(service, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(service, player)
            .setId(SESSION_ID)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Items arrive from the app's controller without their URI: the app sets it, so keep as sent.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)
            })
            .build()
        AppLog.d("BookPlayer", "Book session ready")
    }

    fun release() {
        session.release()
        player.release()
    }

    companion object {
        const val SESSION_ID = "siansa-books"
        /** A controller's connection hint asking for the book session. */
        const val HINT_BOOKS = "siansa.books"
        const val ID_PREFIX = "book:"
        const val EXTRA_OFFSET_SEC = "offsetSec"
        const val EXTRA_DURATION_SEC = "durationSec"
        const val EXTRA_BOOK_ID = "bookId"

        /**
         * Which file [sec] of the whole book falls in and where in it: [tracks] as (start offset, duration) in
         * seconds. Past the end: the last file's end.
         */
        fun locate(tracks: List<Pair<Double, Double>>, sec: Double): Pair<Int, Double> {
            if (tracks.isEmpty()) return 0 to 0.0
            val i = tracks.indexOfLast { it.first <= sec }.coerceAtLeast(0)
            val (start, duration) = tracks[i]
            return i to (sec - start).coerceIn(0.0, duration)
        }
    }
}
