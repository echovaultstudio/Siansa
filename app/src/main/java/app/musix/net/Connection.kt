package app.musix.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.musix.data.AppSettings
import app.musix.data.Song
import app.musix.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Whether the phone can reach the music server and the AI backend (AudioMuse) right now. The server
 * counts as reachable on a network until a check says otherwise, so a LAN server works on Wi-Fi without
 * internet. Checked again whenever the network changes, and every few minutes while one is unreachable.
 */
data class ConnectionState(
    /** The phone has a network at all (not in flight mode, not out of coverage). */
    val network: Boolean = true,
    /** Navidrome answered the last check (null: not checked yet). */
    val server: Boolean? = null,
    /** AudioMuse answered the last check (null: not set up or not checked yet). */
    val audioMuse: Boolean? = null,
) {
    /** Music has to come from the phone: no network, or the server doesn't answer. */
    val offline: Boolean get() = !network || server == false

    /** Mixes, mood playlists, sound search, song radio and song path can work. */
    fun aiAvailable(settings: AppSettings): Boolean = aiProblem(settings) == null

    /** Why the AI tools can't be used, in plain words, or null when they can. */
    fun aiProblem(settings: AppSettings): String? = when {
        !settings.audioMuseReady -> "AudioMuse isn't set up. Add it in Settings → Connections."
        !network -> "You're offline."
        // Its picks would mostly be songs that aren't on the phone.
        settings.downloadedOnly -> "Only downloaded music is on."
        audioMuse == false -> "AudioMuse isn't answering."
        else -> null
    }
}

/**
 * What to play when [songs] are chosen at [start] while offline: only the downloaded ones, starting at the
 * chosen song or the first downloaded one after it. [skipped] is true when the chosen song itself can't play.
 */
data class OfflinePick(val songs: List<Song>, val start: Int, val skipped: Boolean)

fun offlinePick(songs: List<Song>, start: Int, isDownloaded: (String) -> Boolean): OfflinePick {
    val chosen = songs.getOrNull(start)
    val playable = songs.filter { isDownloaded(it.id) }
    if (playable.isEmpty()) return OfflinePick(emptyList(), 0, skipped = true)
    val skipped = chosen == null || !isDownloaded(chosen.id)
    // The chosen song, or else the first downloaded song after it (wrapping round to the start).
    val after = songs.drop(start.coerceAtLeast(0)) + songs.take(start.coerceAtLeast(0))
    val first = after.first { isDownloaded(it.id) }
    return OfflinePick(playable, playable.indexOfFirst { it.id == first.id }.coerceAtLeast(0), skipped)
}

class Connection(
    context: Context,
    private val http: OkHttpClient,
    private val settings: () -> AppSettings,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    val current: ConnectionState get() = _state.value

    private val quick = http.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var checking: Job? = null

    init {
        _state.update { it.copy(network = hasNetwork()) }
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = changed()
                override fun onLost(network: Network) = changed()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = Unit
            })
        }.onFailure { AppLog.w("Connection", "Can't follow network changes: ${it.javaClass.simpleName}") }
        check("start")
    }

    private fun hasNetwork(): Boolean = runCatching {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(true)

    private fun changed() {
        val network = hasNetwork()
        if (network != _state.value.network) AppLog.i("Connection", if (network) "Network back" else "No network")
        _state.update { it.copy(network = network, server = if (network) it.server else false, audioMuse = if (network) it.audioMuse else false) }
        check("network changed")
    }

    /** Checks the server and AudioMuse again (for example after a request to them failed). */
    fun check(why: String) {
        if (checking?.isActive == true) return
        checking = scope.launch(Dispatchers.IO) {
            if (!hasNetwork()) {
                _state.update { it.copy(network = false, server = false, audioMuse = if (settings().audioMuseReady) false else null) }
                return@launch
            }
            val s = settings()
            val server = if (s.navidromeUrl.isBlank()) null else answers(s.navidromeUrl.trimEnd('/') + "/rest/ping.view")
            val audioMuse = if (s.audioMuseUrl.isBlank()) null else answers(s.audioMuseUrl.trimEnd('/') + "/")
            val before = _state.value
            _state.update { it.copy(network = true, server = server, audioMuse = audioMuse) }
            if (before.server != server || before.audioMuse != audioMuse) {
                AppLog.i("Connection", "Checked ($why): server ${label(server)}, AudioMuse ${label(audioMuse)}")
            }
            // While something can't be reached, look again every few minutes.
            if (server == false || audioMuse == false) {
                delay(RECHECK_MS)
                checking = null
                check("retry")
            }
        }
    }

    /** Any HTTP answer counts: the host is up, even if it asks for a login. */
    private suspend fun answers(url: String): Boolean = withTimeoutOrNull(6_000) {
        runCatching { quick.newCall(Request.Builder().url(url).head().build()).execute().use { true } }.getOrDefault(false)
    } ?: false

    private fun label(ok: Boolean?) = when (ok) { true -> "reachable"; false -> "not reachable"; null -> "not set up" }

    private companion object {
        const val RECHECK_MS = 3 * 60_000L
    }
}
