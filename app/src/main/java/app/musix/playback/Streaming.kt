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

    fun rewrite(uri: Uri, kbps: Int): Uri = convert(uri, "opus", kbps)

    /**
     * The stream a Chromecast plays: MP3 320 converted by Navidrome, which every Cast device can play
     * (the original file may be hi-res FLAC or ALAC, which they can't).
     */
    fun forCast(uri: Uri): Uri = convert(uri, "mp3", CAST_KBPS)

    const val CAST_KBPS = 320
    const val CAST_MIME = "audio/mpeg"

    private fun convert(uri: Uri, format: String, kbps: Int): Uri {
        if (kbps <= 0 || uri.path?.endsWith("/rest/stream") != true) return uri
        // Swap the "raw" (original) request for a conversion.
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != "format" && it != "maxBitRate" }.forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        return builder
            .appendQueryParameter("format", format)
            .appendQueryParameter("maxBitRate", kbps.toString())
            // Lets the player know the length of the converted stream, so the seek bar works.
            .appendQueryParameter("estimateContentLength", "true")
            .build()
    }
}
