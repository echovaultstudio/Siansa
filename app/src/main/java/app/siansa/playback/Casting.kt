package app.siansa.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import app.siansa.BuildConfig
import app.siansa.data.Song
import app.siansa.data.cover
import app.siansa.log.AppLog
import app.siansa.net.MusicSource
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
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

/**
 * The TV app that plays while casting: Siansa's own screen (issue #114) once registered, else Google's standard one.
 * A TV that can't show Siansa's screen still gets Google's (see [CastScreens]).
 */
private val RECEIVER_APP_ID = CastReceiver.pick(BuildConfig.CAST_RECEIVER_ID)

/**
 * Which Cast receiver (TV app) to use (issue #114). Siansa's own, `cast-receiver/index.html`, needs an app id from
 * the Google Cast console, set at build time as CAST_RECEIVER_ID; without a valid one, Google's Default Media
 * Receiver, which plays the same songs with its own plain screen.
 */
object CastReceiver {
    /** Google's Default Media Receiver. */
    const val DEFAULT: String = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID

    private val appId = Regex("[0-9A-F]{8}")

    /** [configured] when it's a Cast app id (8 hex digits, any case, spaces around ignored), else [DEFAULT]. */
    fun pick(configured: String?): String {
        val id = configured.orEmpty().trim().uppercase()
        return if (appId.matches(id)) id else DEFAULT
    }

    /** Whether [id] is Siansa's own TV screen rather than Google's. */
    fun isOwn(id: String): Boolean = id != DEFAULT
}

/**
 * Which TV screen each Cast device gets (issue #114). Siansa's own where the TV can show it; Google's Default Media
 * Receiver where it can't, so casting never goes away: while Siansa's Cast app is unpublished in the Cast console,
 * only TVs registered there can load it, and any TV can fail to start it. A failed start of Siansa's screen is tried
 * once more with Google's, straight away; the next cast the user starts tries Siansa's screen again, as a TV that was
 * still restarting its Cast app (APPLICATION_NOT_FOUND right after a session ended, 0.52.0-dev) can show it then.
 */
class CastScreens(private val own: String) {
    enum class Why {
        /** Siansa's own screen. */
        Own,
        /** The app was built without its own screen's id. */
        NoOwnScreen,
        /** The TV isn't offered Siansa's screen (unpublished app, TV not registered). */
        NotOffered,
    }

    data class Choice(val appId: String, val why: Why)

    /**
     * The screen for a TV, when Cast lists Siansa's screen for it ([ownOffered]) or not. A cast the user starts
     * always tries Siansa's screen where it's offered, even on a TV where it failed to start before.
     */
    fun pick(ownOffered: Boolean): Choice = when {
        !CastReceiver.isOwn(own) -> Choice(CastReceiver.DEFAULT, Why.NoOwnScreen)
        !ownOffered -> Choice(CastReceiver.DEFAULT, Why.NotOffered)
        else -> Choice(own, Why.Own)
    }

    /**
     * Starting a cast with the screen [used] on the TV [deviceId] failed. True when it's worth trying again with
     * Google's screen: Siansa's own failed (not cancelled by the user), and the TV is known. Only that one retry uses
     * Google's screen; nothing is remembered. Google's screen failing isn't retried: there's nothing left to fall
     * back to.
     */
    fun startFailed(deviceId: String?, used: String?, cancelled: Boolean): Boolean =
        !cancelled && !deviceId.isNullOrBlank() && used != null && CastReceiver.isOwn(used) && used == own

    /** Words for the log. */
    fun describe(choice: Choice): String = when (choice.why) {
        Why.Own -> "Siansa's own TV screen (receiver ${choice.appId})"
        Why.NoOwnScreen -> "Google's standard TV screen (no Siansa screen in this build)"
        Why.NotOffered -> "Google's standard TV screen: this TV isn't offered Siansa's screen (unpublished Cast app, TV not registered)"
    }
}

/** Google Cast setup, named in the manifest. */
class CastSetup : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val configured = BuildConfig.CAST_RECEIVER_ID
        when {
            CastReceiver.isOwn(RECEIVER_APP_ID) ->
                AppLog.i("Cast", "Casting to Siansa's own TV screen (receiver $RECEIVER_APP_ID) where the TV can show it, else Google's standard one")
            configured.isNotBlank() -> AppLog.w("Cast", "CAST_RECEIVER_ID \"$configured\" isn't a Cast app id, so casting uses Google's standard TV screen")
            else -> AppLog.i("Cast", "Casting to Google's standard TV screen (Default Media Receiver $RECEIVER_APP_ID)")
        }
        return CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID)
            // siansa's own media session already gives the notification and lock-screen controls.
            .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).setMediaSessionEnabled(false).build())
            // Disconnecting stops the music on the TV too.
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** What the cast button shows. */
data class CastStatus(
    /** False on phones without Google Play services: no cast button then. */
    val available: Boolean = false,
    val connecting: Boolean = false,
    /** The TV or speaker playing, when casting. */
    val device: String? = null,
    /**
     * Whether Cast sees any TV or speaker on the network. Google Play services keeps this up to date by itself
     * (passive discovery), so reading it costs Siansa nothing.
     */
    val devicesFound: Boolean = false,
)

/** Google Cast for the whole app: one shared [CastContext], and whether and where we're casting. */
object Cast {
    private var context: CastContext? = null
    private var started = false
    private var unavailable = false
    private val waiting = ArrayList<(CastContext) -> Unit>()
    private val _status = MutableStateFlow(CastStatus())
    val status: StateFlow<CastStatus> = _status.asStateFlow()

    private val screens = CastScreens(RECEIVER_APP_ID)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The TV last picked in the app, for when a failed start doesn't say which it was. */
    private var lastPicked: String? = null

    /** The TV screen's last log line, and when (elapsedRealtime). */
    @Volatile private var lastTvLine: String? = null
    @Volatile private var lastTvLineAt = 0L

    /** When a TV was last picked in Play on (elapsedRealtime), to say in the log how long casting took to start. */
    @Volatile private var pickedAt = 0L

    private fun sincePicked(): String {
        val ms = android.os.SystemClock.elapsedRealtime() - pickedAt
        return if (pickedAt > 0 && ms in 0..60_000) ", ${ms} ms after it was picked" else ""
    }

    /** When this phone last ended a cast (elapsedRealtime), to tell that from the TV ending it. */
    @Volatile private var endedHereAt = -ENDED_HERE_MS

    /** Whether a cast that ended [sinceAskedMs] after the phone asked to end it was ended by the phone. */
    fun endedFromPhone(sinceAskedMs: Long): Boolean = sinceAskedMs in 0..ENDED_HERE_MS

    private const val ENDED_HERE_MS = 5_000L

    /**
     * Finds every Cast device: those that can play Google's standard screen (all of them), and those offered
     * Siansa's own, so a TV shows up whether or not it can show Siansa's screen (issue #114).
     */
    val selector: MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastReceiver.DEFAULT))
        .addControlCategory(CastMediaControlIntent.categoryForCast(RECEIVER_APP_ID))
        .build()

    /** Cast devices that are offered Siansa's own screen. */
    private val ownSelector: MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(RECEIVER_APP_ID))
        .build()

    /** The Cast device behind [route], if it is one. */
    fun deviceId(route: MediaRouter.RouteInfo): String? = CastDevice.getFromBundle(route.extras)?.deviceId

    /**
     * Casts to [route]: with Siansa's own TV screen where the TV is offered it, else Google's (issue #114).
     * Main thread only.
     */
    fun select(route: MediaRouter.RouteInfo) {
        val device = deviceId(route)
        val choice = screens.pick(route.matchesSelector(ownSelector))
        lastPicked = device
        pickedAt = android.os.SystemClock.elapsedRealtime()
        AppLog.i("Cast", "${route.name}: ${screens.describe(choice)}")
        context?.let { use(it, choice.appId) }
        route.select()
    }

    /** Sets which TV app the next cast starts. Changing it ends a cast in progress, so only when it differs. */
    private fun use(c: CastContext, appId: String) {
        if (c.castOptions.receiverApplicationId == appId) return
        try {
            c.setReceiverApplicationId(appId)
        } catch (e: Exception) {
            AppLog.w("Cast", "Couldn't switch the TV screen to $appId: ${e.message}")
        }
    }

    /** Watches casts starting, to say which screen each TV got and to fall back to Google's when Siansa's fails. */
    private fun watch(app: Context, c: CastContext) {
        c.sessionManager.addSessionManagerListener(object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                val used = c.castOptions.receiverApplicationId
                val screen = if (CastReceiver.isOwn(used)) "Siansa's own TV screen" else "Google's standard TV screen"
                AppLog.i("Cast", "Casting to ${session.castDevice?.friendlyName ?: "a Cast device"} with $screen ($used)" + sincePicked())
                listenToTv(session)
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                val device = session.castDevice?.deviceId ?: lastPicked
                val name = session.castDevice?.friendlyName ?: "the TV"
                val used = c.castOptions.receiverApplicationId
                val why = CastStatusCodes.getStatusCodeString(error)
                if (device == null || !screens.startFailed(device, used, cancelled = error == CastStatusCodes.CANCELED)) {
                    AppLog.w("Cast", "Casting to $name failed with receiver $used: $why")
                    return
                }
                AppLog.w("Cast", "Siansa's TV screen didn't start on $name ($why): trying again with Google's standard TV screen")
                use(c, CastReceiver.DEFAULT)
                // Cast lets go of the TV first; then pick it again.
                mainHandler.postDelayed({ reselect(app, device) }, RETRY_DELAY_MS)
            }
            override fun onSessionStarting(session: CastSession) {
                AppLog.i("Cast", "Starting the cast to ${session.castDevice?.friendlyName ?: "the TV"}: the TV opens its screen" + sincePicked())
            }
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionEnded(session: CastSession, error: Int) {
                if (error == CastStatusCodes.SUCCESS) return
                // Ending the cast from the phone (Play on › This phone, hold to stop) ends it with a code too: 2161
                // (0.52.0-dev), which isn't the TV failing.
                if (endedFromPhone(android.os.SystemClock.elapsedRealtime() - endedHereAt)) {
                    AppLog.i("Cast", "Casting ended from this phone (Cast code $error)")
                } else {
                    AppLog.w("Cast", "Casting ended: ${CastStatusCodes.getStatusCodeString(error)}")
                }
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                AppLog.i("Cast", "Back in touch with ${session.castDevice?.friendlyName ?: "the TV"}" + if (wasSuspended) " after a moment away" else "")
                listenToTv(session)
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                AppLog.w("Cast", "Couldn't get back in touch with the TV: ${CastStatusCodes.getStatusCodeString(error)}")
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                // Cast's reasons are GoogleApiClient's: 1 service disconnected, 2 network lost.
                val why = when (reason) {
                    2 -> "the phone's network dropped"
                    1 -> "Google Play services let go"
                    else -> "reason $reason"
                }
                // What the TV screen last said, to tell a crash of it from the network (0.52.0-dev: "reason 2274" on the
                // SHIELD a few seconds after a 37-song queue).
                val last = lastTvLine?.let { line ->
                    "; the TV screen last said, ${(android.os.SystemClock.elapsedRealtime() - lastTvLineAt) / 1000} s before: \"$line\""
                }.orEmpty()
                AppLog.w("Cast", "Lost touch with ${session.castDevice?.friendlyName ?: "the TV"} ($why, code $reason)$last")
            }
        }, CastSession::class.java)
    }

    /**
     * Siansa's TV screen reports what the remote does there (keys, the TV's own seeks, and what the page made of
     * them) on [TV_LOG_NAMESPACE]; written to the log as "CastTV", for tracing remote problems (issue #114).
     */
    private fun listenToTv(session: CastSession) {
        try {
            session.setMessageReceivedCallbacks(TV_LOG_NAMESPACE) { _, _, message ->
                AppLog.i("CastTV", message.take(TV_LOG_MAX))
                lastTvLine = message.take(TV_LOG_MAX)
                lastTvLineAt = android.os.SystemClock.elapsedRealtime()
            }
        } catch (e: Exception) {
            AppLog.d("Cast", "The TV's own log isn't available: ${e.message}")
        }
    }

    /** Picks the TV [device] again once the router lists it, giving up after a while. Main thread. */
    private fun reselect(app: Context, device: String) {
        val router = MediaRouter.getInstance(app)
        fun find() = router.routes.firstOrNull { !it.isDefault && it.isEnabled && deviceId(it) == device }
        find()?.let { if (!it.isSelected) it.select(); return }
        val search = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastReceiver.DEFAULT))
            .build()
        val callback = object : MediaRouter.Callback() {
            var done = false
            fun check() {
                if (done) return
                val route = find() ?: return
                done = true
                router.removeCallback(this)
                if (!route.isSelected) route.select()
            }
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = check()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = check()
        }
        router.addCallback(search, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        mainHandler.postDelayed({
            if (!callback.done) {
                callback.done = true
                router.removeCallback(callback)
                AppLog.w("Cast", "Couldn't find the TV again to cast with Google's standard screen; pick it again in Play on")
            }
        }, FIND_AGAIN_MS)
    }

    /** Where Siansa's TV screen sends its log lines (cast-receiver/index.html). */

    const val TV_LOG_NAMESPACE = "urn:x-cast:app.siansa.log"

    private const val TV_LOG_MAX = 300

    private const val RETRY_DELAY_MS = 1_000L
    private const val FIND_AGAIN_MS = 15_000L

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
                watch(app, c)
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

    /** The Cast session in progress, if any. */
    fun currentSession(): CastSession? = context?.sessionManager?.currentCastSession

    /** Stops casting; the music comes back to the phone, paused. */
    fun disconnect() {
        endedHereAt = android.os.SystemClock.elapsedRealtime()
        context?.sessionManager?.endCurrentSession(true)
    }

    private fun update(c: CastContext) {
        val state = c.castState
        _status.value = CastStatus(
            available = true,
            connecting = state == CastState.CONNECTING,
            device = if (state == CastState.CONNECTED) deviceName() ?: "TV" else null,
            devicesFound = state != CastState.NO_DEVICES_AVAILABLE,
        )
    }
}

/**
 * Which songs Siansa's TV screen may play as their original file rather than the MP3 (issue #114). Only file types
 * every Cast device plays: FLAC, MP3, Ogg (Vorbis, Opus), WAV and AAC. An .m4a or .mp4 can hold AAC or ALAC, and
 * Navidrome only says the file type, so those and ALAC are never sent as they are: an ALAC original ended the cast on
 * a SHIELD (0.52.0-dev, Apple Music Live albums). In lossless quality ([CastQuality]) the server converts them, and
 * other lossless files Cast can't play, to FLAC; anything else gets the MP3.
 */
object CastOriginals {
    private val types = mapOf(
        "flac" to "audio/flac",
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/ogg",
        "wav" to "audio/wav",
        "aac" to "audio/aac",
    )

    /**
     * Files Cast devices can't play that hold lossless audio, or may (.m4a and .mp4 are AAC or ALAC; only the file type
     * is known): with [CastQuality.Lossless] the server converts these to FLAC. Lossy ones Cast can't play (WMA,
     * Musepack…) still go as the MP3.
     */
    private val toFlac = setOf("m4a", "mp4", "m4b", "alac", "ape", "wv", "aif", "aiff", "aifc", "dsf", "dff", "tta", "tak", "shn", "ofr")

    private fun clean(suffix: String?) = suffix?.trim()?.lowercase()?.removePrefix(".")

    /** The MIME type to play a file of type [suffix] as it is on a Cast device, or null when it gets the MP3. */
    fun typeOf(suffix: String?): String? = types[clean(suffix)]

    /** Whether a file of type [suffix] is converted to FLAC for the TV in lossless quality. */
    fun convertsToFlac(suffix: String?): Boolean = clean(suffix) in toFlac

    /**
     * What Siansa's TV screen plays instead of the MP3 for a song of type [suffix] whose original is at [streamUrl]
     * (issue #114), or null when it plays the MP3: always with [CastQuality.Mp3]; in lossless, the original where every
     * Cast device plays its type, else the server's FLAC conversion of it (Navidrome and other Subsonic servers only;
     * Jellyfin and Emby addresses get the MP3, as before).
     */
    fun source(suffix: String?, quality: CastQuality, streamUrl: String): CastSource? {
        if (quality != CastQuality.Lossless) return null
        typeOf(suffix)?.let { return CastSource(streamUrl, it, converted = false) }
        if (!convertsToFlac(suffix)) return null
        return flacUrl(streamUrl)?.let { CastSource(it, FLAC, converted = true) }
    }

    /**
     * A Subsonic stream address asking for the song as FLAC (format=flac, replacing format=raw), or null for any other
     * address. No maxBitRate (FLAC has none) and no estimated length: an estimate would be wrong for FLAC, and the TV
     * would wait at the song's end for data that never comes.
     */
    fun flacUrl(streamUrl: String): String? {
        val path = streamUrl.substringBefore('?').substringBefore('#')
        if (!path.endsWith("/rest/stream") && !path.endsWith("/rest/stream.view")) return null
        val format = Regex("""([?&])format=[^&#]*""")
        if (format.containsMatchIn(streamUrl)) return format.replace(streamUrl) { it.groupValues[1] + "format=flac" }
        return streamUrl + (if ('?' in streamUrl) "&" else "?") + "format=flac"
    }

    const val FLAC = "audio/flac"
}

/** What the TV plays instead of a lossy stream: [url], of MIME [type]; [converted] when the server converts it (to FLAC). */
data class CastSource(val url: String, val type: String, val converted: Boolean)

/**
 * Turns queue songs into what the TV plays and back. The TV fetches each song from Navidrome itself: the original
 * (lossless quality), else Opus 320, else MP3 320 for Cast devices that can't play Opus, with the cover at TV size.
 */
@OptIn(UnstableApi::class)
class CastItems(private val api: MusicSource) : MediaItemConverter {
    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem = toMediaQueueItem(mediaItem, null)

    /** As above, marked with the phone's key for its queue [entry] ([TvPlayer]), which the TV reports back. */
    fun toMediaQueueItem(mediaItem: MediaItem, entry: Long?): MediaQueueItem {
        val song = mediaItem.toSong()
        val custom = JSONObject().put(SONG_ID, mediaItem.mediaId)
        entry?.let { custom.put(TvPlayer.ENTRY_KEY, it) }
        // In lossless quality Siansa's TV screen plays the original file instead where every Cast device can (FLAC and
        // so on), or the server's FLAC conversion of it (ALAC…), and falls back to Opus, then MP3, when it can't.
        CastOriginals.source(CastFormats.suffixOf(song), CastSettings.quality.value, api.streamUrl(mediaItem.mediaId))?.let { source ->
            custom.put(ORIGINAL_URL, source.url).put(ORIGINAL_TYPE, source.type)
            if (source.converted) custom.put(CONVERTED, true)
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, song.title)
            putString(MediaMetadata.KEY_ARTIST, song.artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, song.album)
            song.track?.let { putInt(MediaMetadata.KEY_TRACK_NUMBER, it) }
            song.cover?.let { addImage(WebImage(Uri.parse(api.coverUrl(it, TV_COVER)))) }
        }
        // The lossy stream the TV falls back to: Opus 320 (MP3 320 in that quality, or where the server can't convert),
        // with MP3 320 after it for TVs that can't play Opus (Siansa's TV screen goes original → Opus → MP3).
        val raw = Uri.parse(api.streamUrl(mediaItem.mediaId))
        val mp3 = Streaming.forCast(raw).toString()
        val opus = Streaming.forCastOpus(raw).toString().takeIf { CastSettings.quality.value != CastQuality.Mp3 && it != raw.toString() }
        if (opus != null) custom.put(OPUS_URL, opus).put(MP3_URL, mp3)
        val info = MediaInfo.Builder(mediaItem.mediaId)
            .setContentUrl(opus ?: mp3)
            .setContentType(if (opus != null) Streaming.CAST_OPUS_MIME else Streaming.CAST_MIME)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .apply { if (song.durationSec > 0) setStreamDuration(song.durationSec * 1000L) }
            .setCustomData(custom)
            .build()
        // The TV loads the next song ahead (issue #689), so it can go straight on instead of fetching it once this one ends.
        return MediaQueueItem.Builder(info).setAutoplay(true).setPreloadTime(CAST_PRELOAD_SECONDS).build()
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
        const val ORIGINAL_TYPE = "originalType"
        /** The Opus and MP3 addresses, for the TV screen's fallbacks. */
        const val OPUS_URL = "opusUrl"
        const val MP3_URL = "mp3Url"
        /** The original is the server's conversion (to FLAC), so the TV screen can tell its failures apart. */
        const val CONVERTED = "converted"
        const val TV_COVER = 800
    }
}

/**
 * How many seconds ahead the Cast device loads a song (issue #689). Cast counts it back from where the song starts,
 * the end of the one before, so it's the same for every song: a short song before it just means loading starts
 * as soon as that one does.
 */
const val CAST_PRELOAD_SECONDS = 20.0

/** The queue, song and position on the TV, in play order. */
data class CastSpot<T>(val items: List<T>, val index: Int, val positionMs: Long)

/** Hold to stop while casting (issue #491): set by the app, read once by the playback service as casting ends. */
object StopCasting {
    @Volatile var requested: Boolean = false
}

/** Bringing the music back from the TV to the phone. */
object CastHandBack {
    /**
     * The TV's queue as it is now, unless the TV already forgot songs while it was stopping (an empty queue, or songs
     * it can no longer name): then the spot [saved] just before casting ended, else the queue the phone last saw on
     * the TV ([known]): Back on the TV's remote closed Siansa's TV screen and the queue came back empty (0.52.0-dev).
     */
    fun <T> pick(live: CastSpot<T>, saved: CastSpot<T>?, id: (T) -> String, known: CastSpot<T>? = null): CastSpot<T> {
        fun lost(spot: CastSpot<T>?) = spot == null || spot.items.isEmpty() || spot.items.any { id(it).isBlank() }
        if (!lost(live)) return live
        return when {
            saved != null && saved.items.isNotEmpty() -> saved
            known != null && known.items.isNotEmpty() -> known
            else -> live
        }
    }

    /**
     * What comes back to the phone: nothing after hold to stop ([stopped], issue #491), so the cleared queue stays
     * cleared even when the TV still listed it as casting ended; otherwise [pick].
     */
    fun <T> afterEnd(live: CastSpot<T>, saved: CastSpot<T>?, stopped: Boolean, known: CastSpot<T>? = null, id: (T) -> String): CastSpot<T> =
        if (stopped) CastSpot(emptyList(), 0, 0L) else pick(live, saved, id, known)

    /** [spot] as seen [sinceMs] ago, moved on by that time when it was [playing]. */
    fun <T> movedOn(spot: CastSpot<T>, playing: Boolean, sinceMs: Long): CastSpot<T> =
        if (playing && sinceMs > 0) spot.copy(positionMs = spot.positionMs + sinceMs) else spot

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
