package app.siansa.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import app.siansa.data.AppSettings

/**
 * Chooses original quality or Opus for each stream, at the moment it starts playing, from the
 * network in use: Wi-Fi and Ethernet use the Wi-Fi setting, anything else the mobile one.
 * Navidrome does the conversion with its own "opus" transcoding profile.
 */
object Streaming {
    /** Wi-Fi or Ethernet, without a VPN: a VPN counts as mobile data (issue #299). */
    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return countsAsWifi(
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }

    /** Whether a connection counts as Wi-Fi: Wi-Fi, Ethernet or unmetered, but never through a VPN (issue #299). */
    fun countsAsWifi(vpn: Boolean, wifi: Boolean, ethernet: Boolean, notMetered: Boolean): Boolean =
        !vpn && (wifi || ethernet || notMetered)

    /** Whether a VPN is on, on any of the phone's networks (issue #299). */
    fun vpnOn(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    /** Kbit/s for Opus on the current network, or 0 for the original file. */
    fun bitrate(context: Context, settings: AppSettings): Int =
        if (isUnmetered(context)) settings.transcodeWifiKbps else settings.transcodeMobileKbps

    /**
     * A marker added to the current song's address to make the player load it again (the player keeps
     * playing the old stream when the address is unchanged). It's removed before the request is sent.
     */
    const val RELOAD_PARAM = "siansaReload"

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

    /**
     * The Opus conversion of a song. Without an estimated length: with one, the player first reads the end of
     * the stream to learn the song's length, and Navidrome is still converting at that point, so the song
     * didn't start (or, after a switch, didn't continue) for a long time (issue #162). The song's length comes
     * from the library instead, and seeking is done by Navidrome ([ServerSeekPlayer]).
     */
    fun rewrite(uri: Uri, kbps: Int): Uri = convert(uri, "opus", kbps, estimateLength = false)

    /** Navidrome's start offset for a converted stream, in seconds (issue #162). */
    const val OFFSET_PARAM = "timeOffset"

    /** Where a stream asked to start part-way in starts in its song, in ms (0 when it starts at the beginning). */
    fun offsetMs(uri: Uri?): Long = (uri?.getQueryParameter(OFFSET_PARAM)?.toLongOrNull() ?: 0L).coerceAtLeast(0) * 1000

    /** Whether Navidrome can start this stream part-way in (a song stream; it does so when it converts). */
    fun canStartAt(uri: Uri): Boolean = uri.path?.endsWith("/rest/stream") == true

    /** [uri] starting at the whole second of [positionMs] (or at the beginning, for 0). */
    fun startingAt(uri: Uri, positionMs: Long): Uri {
        val seconds = (positionMs / 1000).coerceAtLeast(0)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != OFFSET_PARAM && it != "estimateContentLength" }.forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        if (seconds > 0) builder.appendQueryParameter(OFFSET_PARAM, seconds.toString())
        return builder.build()
    }

    /**
     * The stream a Chromecast plays: MP3 320 converted by Navidrome, which every Cast device can play
     * (the original file may be hi-res FLAC or ALAC, which they can't).
     */
    fun forCast(uri: Uri): Uri = convert(uri, "mp3", CAST_KBPS, estimateLength = true)

    const val CAST_KBPS = 320
    const val CAST_MIME = "audio/mpeg"

    private fun convert(uri: Uri, format: String, kbps: Int, estimateLength: Boolean): Uri {
        if (kbps <= 0 || uri.path?.endsWith("/rest/stream") != true) return uri
        // Swap the "raw" (original) request for a conversion.
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != "format" && it != "maxBitRate" && it != "estimateContentLength" }.forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        builder
            .appendQueryParameter("format", format)
            .appendQueryParameter("maxBitRate", kbps.toString())
        // Lets the player know the length of the converted stream, so the seek bar works. Not for a stream
        // that starts part-way in: the estimate is for the whole song, so the player would wait for data
        // that never comes at the end.
        if (estimateLength && uri.getQueryParameter(OFFSET_PARAM) == null) builder.appendQueryParameter("estimateContentLength", "true")
        return builder.build()
    }
}

/**
 * File types the phone can't open (issue #354): Media3 has no way into their container, whatever decoders the app
 * has, so Navidrome converts them to Opus. WMA (ASF), AIFF, Monkey's Audio, WavPack, Musepack, TTA, DSD and others.
 */
object Formats {
    val needsConverting: Set<String> = setOf(
        "wma", "asf", "aif", "aiff", "aifc", "ape", "wv", "mpc", "mp+", "mpp", "tta", "tak", "dsf", "dff", "ra", "rm", "au", "snd", "shn", "ofr",
    )

    /** Whether a song of this file type has to be converted to play on the phone. */
    fun mustConvert(suffix: String?): Boolean = suffix?.trim()?.lowercase()?.removePrefix(".") in needsConverting

    /**
     * Whether a playback error means the phone can't read the song itself (rather than the network failing), so
     * playing it converted by Navidrome should work.
     */
    fun convertible(errorCode: Int): Boolean = errorCode in setOf(
        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    )
}
