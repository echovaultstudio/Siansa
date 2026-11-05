package app.siansa.playback.dlna

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import app.siansa.log.AppLog

/**
 * The local networks (Wi-Fi or Ethernet) the phone is on, by id. A TV or speaker on DLNA is only reachable on
 * one, so once the phone has none left it has left the house (issue #624). Kept apart from Android so it's tested.
 */
internal class LocalNetworks {
    private val now = HashSet<String>()
    private var seen = false

    fun available(id: String) {
        now += id
        seen = true
    }

    fun lost(id: String) {
        now -= id
    }

    /** True once a local network was seen and none is left: never before one was seen. */
    val left: Boolean get() = seen && now.isEmpty()
}

/**
 * Follows the phone's Wi-Fi and Ethernet while music plays on a DLNA device (issue #624). When the last one goes
 * and doesn't come back within [GRACE_MS] (moving between two access points can drop it for a moment),
 * [onLeft] is called on the main thread, so the music comes back to the phone straight away instead of after
 * the device stops answering.
 */
internal class LanWatch(context: Context, private val onLeft: () -> Unit) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val networks = LocalNetworks()
    private val check = Runnable { if (networks.left) { AppLog.i("DLNA", "The phone left the Wi-Fi"); onLeft() } }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                networks.available(network.toString())
                main.removeCallbacks(check)
            }
        }

        override fun onLost(network: Network) {
            main.post {
                networks.lost(network.toString())
                if (networks.left) {
                    AppLog.i("DLNA", "Wi-Fi gone; giving it ${GRACE_MS / 1000} s to come back")
                    main.removeCallbacks(check)
                    main.postDelayed(check, GRACE_MS)
                }
            }
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching { cm?.registerNetworkCallback(request, callback) }
            .onFailure { AppLog.w("DLNA", "Can't follow the Wi-Fi: ${it.javaClass.simpleName}") }
    }

    fun stop() {
        main.removeCallbacks(check)
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }

    companion object {
        const val GRACE_MS = 5_000L
    }
}
