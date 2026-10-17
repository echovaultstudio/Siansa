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

    /**
     * A marker added to the current song's address to make the player load it again (the player keeps
     * playing the old stream when the address is unchanged). It's removed before the request is sent.
     */
    const val RELOAD_PARAM = "musixReload"

    /** [url] with the reload marker set to [n] (any earlier marker replaced). */
    fun withReload(url: String, n: Int): String {
        val clean = withoutReload(url)
        return clean + (if ('?' in clean) "&" else "?") + "$RELOAD_PARAM=$n"
    }

    /** [url] without the reload marker, exactly as it was before it was added. */
    fun withoutReload(url: String): String =
        url.replace(Regex("""[?&]$RELOAD_PARAM=[^&#]*"""), "").let { u ->
            // When the marker was the first parameter, the next one now starts with '&'.
            if ('?' !in u && '&' in u) u.replaceFirst('&', '?') else u
        }

    fun rewrite(uri: Uri, kbps: Int): Uri {
        if (kbps <= 0 || uri.path?.endsWith("/rest/stream") != true) return uri
        // Swap the "raw" (original) request for an Opus conversion.
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != "format" && it != "maxBitRate" && it != "estimateContentLength" }.forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        return builder
            .appendQueryParameter("format", "opus")
            .appendQueryParameter("maxBitRate", kbps.toString())
            // Lets the player know the length of the converted stream, so the seek bar works.
            .appendQueryParameter("estimateContentLength", "true")
            .build()
    }
}
