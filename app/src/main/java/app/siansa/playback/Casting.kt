package app.siansa.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.log.AppLog
import app.siansa.net.MusicSource
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.Executors

/** Google's standard TV app for audio and video, so siansa needs no TV app of its own. */
private val RECEIVER_APP_ID = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID

/** Google Cast setup, named in the manifest. */
class CastSetup : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID)
            // siansa's own media session already gives the notification and lock-screen controls.
            .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).setMediaSessionEnabled(false).build())
            // Disconnecting stops the music on the TV too.
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** What the cast button shows. */
data class CastStatus(
    /** False on phones without Google Play services: no cast button then. */
    val available: Boolean = false,
    val connecting: Boolean = false,
    /** The TV or speaker playing, when casting. */
    val device: String? = null,
)

/** Google Cast for the whole app: one shared [CastContext], and whether and where we're casting. */
object Cast {
    private var context: CastContext? = null
    private var started = false
    private var unavailable = false
    private val waiting = ArrayList<(CastContext) -> Unit>()
    private val _status = MutableStateFlow(CastStatus())
    val status: StateFlow<CastStatus> = _status.asStateFlow()

    /** Finds Cast devices that can play siansa. */
    val selector: MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(RECEIVER_APP_ID))
        .build()

    /**
     * Starts Cast once, in the background, and calls [onReady] on the main thread when it's ready.
     * Phones without Google Play services never call it (and never wait on Cast). Main thread only.
     */
    fun whenReady(ctx: Context, onReady: (CastContext) -> Unit) {
        context?.let { onReady(it); return }
        if (unavailable) return
        waiting += onReady
        if (started) return
        started = true
        val app = ctx.applicationContext
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) != ConnectionResult.SUCCESS) {
            AppLog.i("Cast", "No Google Play services on this phone, so no casting")
            unavailable = true
            waiting.clear()
            return
        }
        val main = ContextCompat.getMainExecutor(app)
        CastContext.getSharedInstance(app, Executors.newSingleThreadExecutor())
            .addOnSuccessListener(main) { c ->
                context = c
                update(c)
                c.addCastStateListener { update(c) }
                val ready = waiting.toList()
                waiting.clear()
                ready.forEach { it(c) }
            }
            .addOnFailureListener(main) { e ->
                AppLog.w("Cast", "Casting isn't available on this phone: ${e.message}")
                unavailable = true
                waiting.clear()
            }
    }

    fun deviceName(): String? = context?.sessionManager?.currentCastSession?.castDevice?.friendlyName

    /** Stops casting; the music comes back to the phone, paused. */
    fun disconnect() {
        context?.sessionManager?.endCurrentSession(true)
    }

    private fun update(c: CastContext) {
        val state = c.castState
        _status.value = CastStatus(
            available = true,
            connecting = state == CastState.CONNECTING,
            device = if (state == CastState.CONNECTED) deviceName() ?: "TV" else null,
        )
    }
}

/**
 * Turns queue songs into what the TV plays and back. The TV fetches each song from Navidrome
 * itself, converted to MP3 so any Cast device can play it, with the cover at TV size.
 */
@OptIn(UnstableApi::class)
class CastItems(private val api: MusicSource) : MediaItemConverter {
    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val song = mediaItem.toSong()
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, song.title)
            putString(MediaMetadata.KEY_ARTIST, song.artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, song.album)
            song.track?.let { putInt(MediaMetadata.KEY_TRACK_NUMBER, it) }
            song.cover?.let { addImage(WebImage(Uri.parse(api.coverUrl(it, TV_COVER)))) }
        }
        val info = MediaInfo.Builder(mediaItem.mediaId)
            .setContentUrl(Streaming.forCast(Uri.parse(api.streamUrl(mediaItem.mediaId))).toString())
            .setContentType(Streaming.CAST_MIME)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .apply { if (song.durationSec > 0) setStreamDuration(song.durationSec * 1000L) }
            // The siansa TV app plays the original file instead when the TV can (FLAC and so on),
            // and falls back to the MP3 above when it can't.
            .setCustomData(JSONObject().put(SONG_ID, mediaItem.mediaId).put(ORIGINAL_URL, api.streamUrl(mediaItem.mediaId)))
            .build()
        return MediaQueueItem.Builder(info).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val info = mediaQueueItem.media
        val id = info?.customData?.optString(SONG_ID)?.takeIf { it.isNotEmpty() } ?: info?.contentId.orEmpty()
        SongRegistry.get(id)?.let { return it.toMediaItem(api) }
        val metadata = info?.metadata
        return Song(
            id = id,
            title = metadata?.getString(MediaMetadata.KEY_TITLE).orEmpty(),
            artist = metadata?.getString(MediaMetadata.KEY_ARTIST).orEmpty(),
            album = metadata?.getString(MediaMetadata.KEY_ALBUM_TITLE).orEmpty(),
        ).toMediaItem(api)
    }

    private companion object {
        const val SONG_ID = "siansaSongId"
        const val ORIGINAL_URL = "originalUrl"
        const val TV_COVER = 800
    }
}

/** The queue, song and position on the TV, in play order. */
data class CastSpot<T>(val items: List<T>, val index: Int, val positionMs: Long)

/** Bringing the music back from the TV to the phone. */
object CastHandBack {
    /**
     * The TV's queue as it is now, unless the TV already forgot songs while it was stopping (an empty queue,
     * or songs it can no longer name): then the spot [saved] just before casting ended.
     */
    fun <T> pick(live: CastSpot<T>, saved: CastSpot<T>?, id: (T) -> String): CastSpot<T> {
        val lost = live.items.isEmpty() || live.items.any { id(it).isBlank() }
        return if (lost && saved != null && saved.items.isNotEmpty()) saved else live
    }

    /** Leaves out songs with no id, which the phone couldn't play, keeping the same song current. */
    fun <T> withoutNameless(spot: CastSpot<T>, id: (T) -> String): CastSpot<T> {
        if (spot.items.none { id(it).isBlank() }) return spot
        val kept = spot.items.withIndex().filter { id(it.value).isNotBlank() }
        if (kept.isEmpty()) return CastSpot(emptyList(), 0, 0L)
        val at = kept.indexOfFirst { it.index >= spot.index }
        // The current song itself had no id: start the next one from the top.
        val sameSong = at >= 0 && kept[at].index == spot.index
        return CastSpot(kept.map { it.value }, if (at >= 0) at else kept.lastIndex, if (sameSong) spot.positionMs else 0L)
    }
}
