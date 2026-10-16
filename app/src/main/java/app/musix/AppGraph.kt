package app.musix

import android.app.Application
import app.musix.data.HistoryStore
import app.musix.data.LibraryRepository
import app.musix.data.LibrarySync
import app.musix.data.MixStore
import app.musix.data.SelectorStore
import app.musix.data.SettingsRepository
import app.musix.mix.MixGenerator
import app.musix.net.AudioMuseApi
import app.musix.net.OpenRouterApi
import app.musix.net.SubsonicApi
import app.musix.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Everything the app shares, created once in [MusixApp]. */
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
        .build()

    val subsonic = SubsonicApi(http) { settings.current }
    val audioMuse = AudioMuseApi(http) { settings.current }
    val openRouter = OpenRouterApi(http) { settings.current }

    val selectors = SelectorStore(app)
    val mixes = MixStore(app)
    val history = HistoryStore(app)
    val library = LibraryRepository(app)
    val sync = LibrarySync(subsonic, library, settings, scope)

    val generator = MixGenerator(this)
    val player = PlayerConnection(app, subsonic)
}
