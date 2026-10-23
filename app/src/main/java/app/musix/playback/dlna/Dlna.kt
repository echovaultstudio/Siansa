package app.musix.playback.dlna

import android.content.Context
import android.net.wifi.WifiManager
import app.musix.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * UPnP/DLNA for the whole app: finds TVs and speakers on the Wi-Fi while the device sheet is open, and
 * remembers which one music plays on. The playback service follows [selected] and moves the music there.
 * Device addresses stay out of the log; only names and what happened are logged.
 */
object Dlna {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var search: Job? = null

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    /** TVs and speakers found on the Wi-Fi, by name. */
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _selected = MutableStateFlow<DlnaDevice?>(null)
    /** The TV or speaker music plays on, or null for this phone (or a Chromecast). */
    val selected: StateFlow<DlnaDevice?> = _selected.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    /** True from picking a device until it has started playing (or failed). */
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Plays on [device]; null brings the music back to the phone. */
    fun select(device: DlnaDevice?) {
        if (device?.id == _selected.value?.id) return
        AppLog.i("DLNA", if (device != null) "Play on ${device.name}" else "Back to the phone")
        _connecting.value = device != null
        _selected.value = device
    }

    internal fun connected() {
        _connecting.value = false
    }

    /** Called by the player when the device stops answering, so the music comes back to the phone. */
    internal fun lost(device: DlnaDevice, why: String) {
        AppLog.w("DLNA", "${device.name} stopped answering ($why); music back on the phone")
        if (_selected.value?.id == device.id) {
            _connecting.value = false
            _selected.value = null
        }
    }

    /**
     * Looks for TVs and speakers until [stopSearch], asking again every few seconds (issue #361: they show at
     * once). Devices found before are asked directly first, which takes a fraction of a second, and each device
     * that answers the search is listed as soon as its description is read, rather than after the whole search.
     */
    fun startSearch(context: Context) {
        if (search?.isActive == true) return
        val app = context.applicationContext
        val wifi = app.getSystemService(WifiManager::class.java)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        search = scope.launch {
            val started = System.currentTimeMillis()
            var firstLogged = false
            fun found(device: DlnaDevice, location: String, how: String) {
                _devices.update { old -> (old.filterNot { it.id == device.id } + device).sortedBy { it.name.lowercase() } }
                val known = prefs.getStringSet(KNOWN, emptySet()).orEmpty()
                val kept = rememberLocation(known, location)
                if (kept != known) prefs.edit().putStringSet(KNOWN, kept).apply()
                if (!firstLogged) {
                    firstLogged = true
                    AppLog.i("DLNA", "First TV or speaker (${device.name}) after ${System.currentTimeMillis() - started} ms, $how")
                }
            }
            // Android drops multicast answers unless an app asks for them.
            val lock = runCatching { wifi?.createMulticastLock("musix-dlna")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
            try {
                // The ones seen before: straight to their description, no waiting for the search.
                prefs.getStringSet(KNOWN, emptySet()).orEmpty().forEach { location ->
                    launch { describe(location)?.let { found(it, location, "known from before") } }
                }
                var round = 0
                while (isActive) {
                    round++
                    runCatching { searchOnce { device, location -> found(device, location, "by search") } }
                        .onFailure { AppLog.w("DLNA", "Search failed: ${it.javaClass.simpleName}") }
                    if (round == 1) AppLog.i("DLNA", "Found ${_devices.value.size} TVs and speakers")
                    delay(if (round < 3) 2_000 else 8_000)
                }
            } finally {
                runCatching { lock?.release() }
            }
        }
    }

    fun stopSearch() {
        search?.cancel()
        search = null
    }

    /**
     * One SSDP search. Renderers are asked to answer within a second; each one is described and passed to
     * [onFound] as soon as it answers, while the search keeps listening for slower ones.
     */
    private suspend fun searchOnce(onFound: (DlnaDevice, String) -> Unit) = coroutineScope {
        val locations = HashSet<String>()
        withContext(Dispatchers.IO) {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = 250
                socket.bind(null)
                val request = Upnp.searchRequest(SEARCH_WAIT_S).toByteArray()
                val group = InetAddress.getByName(Upnp.SSDP_ADDRESS)
                repeat(2) { socket.send(DatagramPacket(request, request.size, group, Upnp.SSDP_PORT)) }
                val until = System.currentTimeMillis() + LISTEN_MS
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val headers = Upnp.ssdpHeaders(String(packet.data, 0, packet.length))
                    val location = headers["location"]?.takeIf { it.startsWith("http") } ?: continue
                    if (locations.add(location)) {
                        this@coroutineScope.launch { describe(location)?.let { onFound(it, location) } }
                    }
                }
            }
        }
    }

    private suspend fun describe(location: String): DlnaDevice? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(location).build()).execute().use { r ->
                if (!r.isSuccessful) null else Upnp.parseDescription(r.body?.string().orEmpty(), location)
            }
        }.getOrNull()
    }

    private const val PREFS = "dlna"
    private const val KNOWN = "known_locations"
    /** How long devices may wait before answering (SSDP's MX; 1 is the least it allows). */
    private const val SEARCH_WAIT_S = 1
    /** How long one search listens for answers. */
    private const val LISTEN_MS = 2_500L

    /** Sends a SOAP [action] to a device and returns its answer; throws when the device refuses it. */
    internal suspend fun soap(url: String, service: String, action: String, vararg args: Pair<String, String>): String =
        withContext(Dispatchers.IO) {
            val body = Upnp.soapBody(service, action, args.toList()).toRequestBody("text/xml; charset=\"utf-8\"".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("SOAPACTION", Upnp.soapAction(service, action))
                .build()
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IOException("$action refused: ${Upnp.fault(text) ?: "HTTP ${r.code}"}")
                text
            }
        }
}

/** The device addresses kept for next time: [location] added, at most [max], the newest kept (issue #361). */
internal fun rememberLocation(known: Set<String>, location: String, max: Int = 12): Set<String> {
    if (location in known) return known
    val list = known.toList() + location
    return list.takeLast(max).toSet()
}
