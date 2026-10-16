package app.musix

import android.app.Application
import app.musix.data.Favorites
import app.musix.data.HistoryStore
import app.musix.data.LibraryRepository
import app.musix.data.LibrarySync
import app.musix.data.MixStore
import app.musix.data.SelectorStore
import app.musix.data.SettingsRepository
import app.musix.log.AppLog
import app.musix.mix.MixGenerator
import app.musix.mix.Radios
import app.musix.mix.SongRadio
import app.musix.net.AudioMuseApi
import app.musix.net.LastFmApi
import app.musix.net.ListenBrainzApi
import app.musix.net.OpenRouterApi
import app.musix.net.SubsonicApi
import app.musix.playback.PlayerConnection
import app.musix.scrobble.Scrobbler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Everything the app shares, created once in [MusixApp]. */
@OptIn(UnstableApi::class)
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsRepository(app)

    /**
     * One HTTP client for API calls, streaming and covers. When Navidrome sits behind a proxy that asks
     * for a login, that login is added to every request to the Navidrome address.
     */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val s = settings.current
            val navidrome = s.navidromeUrl.toHttpUrlOrNull()
            val proxied = s.proxyUser.isNotBlank() && navidrome != null &&
                request.url.host == navidrome.host && request.url.port == navidrome.port &&
                request.header("Authorization") == null
            if (proxied) {
                chain.proceed(
                    request.newBuilder()
                        .header("Authorization", Credentials.basic(s.proxyUser, s.proxyPassword, Charsets.UTF_8))
                        .build()
                )
            } else {
                chain.proceed(request)
            }
        }
        // Request log (only while logging is on). Cover images are left out to keep the log readable.
        .addInterceptor { chain ->
            val request = chain.request()
            if (!AppLog.enabled || request.url.encodedPath.endsWith("/getCoverArt")) return@addInterceptor chain.proceed(request)
            val started = System.nanoTime()
            val where = request.url.host + request.url.encodedPath
            try {
                val response = chain.proceed(request)
                val ms = (System.nanoTime() - started) / 1_000_000
                if (response.isSuccessful) AppLog.i("Http", "${request.method} $where → ${response.code} in ${ms}ms")
                else AppLog.w("Http", "${request.method} ${AppLog.redact(request.url.toString())} → ${response.code} in ${ms}ms")
                response
            } catch (e: Exception) {
                AppLog.w("Http", "${request.method} $where failed: ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
        .build()

    val subsonic = SubsonicApi(http) { settings.current }
    val audioMuse = AudioMuseApi(http) { settings.current }
    val openRouter = OpenRouterApi(http) { settings.current }

    val selectors = SelectorStore(app)
    val mixes = MixStore(app)
    val history = HistoryStore(app)
    val library = LibraryRepository(app)
    val sync = LibrarySync(subsonic, library, settings, scope)

    val lastFm = LastFmApi(http)
    val listenBrainz = ListenBrainzApi(http)
    val scrobbler = Scrobbler(app, settings, subsonic, lastFm, listenBrainz, scope)
    val favorites = Favorites(subsonic, lastFm, settings, library, scope)

    /**
     * Songs kept on the phone as they play (and the next one in the queue), so replays start at once
     * and survive a bad connection. The oldest are removed when the chosen size is reached.
     */
    val songCache: SimpleCache by lazy {
        val megabytes = settings.current.songCacheMb.coerceAtLeast(100)
        SimpleCache(
            File(app.cacheDir, "song_cache"),
            LeastRecentlyUsedCacheEvictor(megabytes * 1024L * 1024L),
            StandaloneDatabaseProvider(app),
        )
    }

    val generator = MixGenerator(this)
    val radios = Radios(this)
    val player = PlayerConnection(app, subsonic)
    val songRadio = SongRadio(this)
}
