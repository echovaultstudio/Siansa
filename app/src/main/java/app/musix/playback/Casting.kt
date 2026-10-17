package app.musix.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import app.musix.data.Song
import app.musix.data.cover
import app.musix.log.AppLog
import app.musix.net.SubsonicApi
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

/** Google's standard TV app for audio and video, so musix needs no TV app of its own. */
private val RECEIVER_APP_ID = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID

/** Google Cast setup, named in the manifest. */
class CastSetup : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID)
            // musix's own media session already gives the notification and lock-screen controls.
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

    /** Finds Cast devices that can play musix. */
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
class CastItems(private val api: SubsonicApi) : MediaItemConverter {
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
            // The musix TV app plays the original file instead when the TV can (FLAC and so on),
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
        const val SONG_ID = "musixSongId"
        const val ORIGINAL_URL = "originalUrl"
        const val TV_COVER = 800
    }
}
