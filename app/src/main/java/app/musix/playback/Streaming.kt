package app.musix.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import app.musix.data.AppSettings

/**
 * Chooses original quality or Opus for each stream, at the moment it starts playing, from the
 * network in use: Wi-Fi and Ethernet use the Wi-Fi setting, anything else the mobile one.
 * Navidrome does the conversion with its own "opus" transcoding profile.
 */
object Streaming {
    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Kbit/s for Opus on the current network, or 0 for the original file. */
    fun bitrate(context: Context, settings: AppSettings): Int =
        if (isUnmetered(context)) settings.transcodeWifiKbps else settings.transcodeMobileKbps

    fun rewrite(uri: Uri, kbps: Int): Uri {
        if (kbps <= 0 || uri.path?.endsWith("/rest/stream") != true) return uri
        return uri.buildUpon()
            .appendQueryParameter("format", "opus")
            .appendQueryParameter("maxBitRate", kbps.toString())
            // Lets the player know the length of the converted stream, so the seek bar works.
            .appendQueryParameter("estimateContentLength", "true")
            .build()
    }
}
