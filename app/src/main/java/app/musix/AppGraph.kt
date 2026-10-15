package app.musix

import android.app.Application
import app.musix.data.HistoryStore
import app.musix.data.MixStore
import app.musix.data.SelectorStore
import app.musix.data.SettingsRepository
import app.musix.mix.MixGenerator
import app.musix.net.AudioMuseApi
import app.musix.net.OpenRouterApi
import app.musix.net.SubsonicApi
import app.musix.playback.PlayerConnection
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Everything the app shares, created once in [MusixApp]. */
class AppGraph(val app: Application) {
    val settings = SettingsRepository(app)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    val subsonic = SubsonicApi(http) { settings.current }
    val audioMuse = AudioMuseApi(http) { settings.current }
    val openRouter = OpenRouterApi(http) { settings.current }

    val selectors = SelectorStore(app)
    val mixes = MixStore(app)
    val history = HistoryStore(app)

    val generator = MixGenerator(this)
    val player = PlayerConnection(app, subsonic)
}
