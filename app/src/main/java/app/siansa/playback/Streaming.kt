package app.siansa.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import app.siansa.data.AppSettings

/**
 * Chooses original quality or Opus for each stream, at the moment it starts playing, from the
 * network in use: Wi-Fi and Ethernet use the Wi-Fi setting, anything else the mobile one.
 * The server does the conversion: Navidrome with its own "opus" transcoding profile, Emby and Jellyfin with their
 * ffmpeg (issue #1204).
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
    fun rewrite(uri: Uri, kbps: Int): Uri =
        if (isEmbyStream(uri)) convertOnEmby(uri, kbps) else convert(uri, "opus", kbps, estimateLength = false)

    /**
     * The Opus conversion of a song on Emby or Jellyfin (issue #1204): Opus in Ogg, from their ffmpeg, starting at
     * [TICKS_PARAM] when it's there. Each stream carries its own play session: without one, both servers hand out
     * the conversion they made first for that song, and so ignore where it's asked to start. The session is named
     * by the song, the bitrate and the start, so the very same stream asked for again is the same conversion.
     */
    fun convertOnEmby(uri: Uri, kbps: Int): Uri {
        if (kbps <= 0 || !isEmbyStream(uri)) return uri
        val builder = uri.buildUpon().clearQuery()
        val replaced = setOf("Static", "Container", EMBY_CODEC, EMBY_BITRATE, EMBY_SESSION)
        uri.queryParameterNames.filter { name -> replaced.none { it.equals(name, ignoreCase = true) } }.forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        val id = uri.pathSegments.let { it.getOrNull(it.indexOfLast { s -> s.equals("Audio", ignoreCase = true) } + 1) }.orEmpty()
        return builder
            .appendQueryParameter("Static", "false")
            .appendQueryParameter("Container", "ogg")
            .appendQueryParameter(EMBY_CODEC, "opus")
            .appendQueryParameter(EMBY_BITRATE, (kbps * 1000).toString())
            .appendQueryParameter(EMBY_SESSION, "siansa-$id-opus$kbps-${offsetMs(uri) / 1000}")
            .build()
    }

    /** Navidrome's start offset for a converted stream, in seconds (issue #162). */
    const val OFFSET_PARAM = "timeOffset"

    /** Emby's and Jellyfin's start offset for a converted stream, in ticks of 100 ns (issue #1204). */
    const val TICKS_PARAM = "StartTimeTicks"

    private const val TICKS_PER_MS = 10_000L

    /** A Navidrome (or other Subsonic) song stream, `/rest/stream`. */
    fun isSubsonicStream(uri: Uri): Boolean = uri.path?.endsWith("/rest/stream") == true

    /** An Emby or Jellyfin song stream, `/Audio/<id>/stream` (issue #903). */
    fun isEmbyStream(uri: Uri): Boolean {
        val parts = uri.pathSegments ?: return false
        val at = parts.indexOfLast { it.equals("Audio", ignoreCase = true) }
        return at >= 0 && parts.getOrNull(at + 2)?.startsWith("stream", ignoreCase = true) == true
    }

    /** Where a stream asked to start part-way in starts in its song, in ms (0 when it starts at the beginning). */
    fun offsetMs(uri: Uri?): Long {
        if (uri == null) return 0
        uri.getQueryParameter(TICKS_PARAM)?.toLongOrNull()?.let { return (it / TICKS_PER_MS).coerceAtLeast(0) }
        return (uri.getQueryParameter(OFFSET_PARAM)?.toLongOrNull() ?: 0L).coerceAtLeast(0) * 1000
    }

    /** Whether the server can start this stream part-way in (a song stream; it does so when it converts). */
    fun canStartAt(uri: Uri): Boolean = isSubsonicStream(uri) || isEmbyStream(uri)

    /** [uri] starting at the whole second of [positionMs] (or at the beginning, for 0). */
    fun startingAt(uri: Uri, positionMs: Long): Uri {
        val seconds = (positionMs / 1000).coerceAtLeast(0)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != OFFSET_PARAM && it != TICKS_PARAM && it != "estimateContentLength" }.forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        if (seconds > 0) {
            if (isEmbyStream(uri)) builder.appendQueryParameter(TICKS_PARAM, (seconds * 1000 * TICKS_PER_MS).toString())
            else builder.appendQueryParameter(OFFSET_PARAM, seconds.toString())
        }
        return builder.build()
    }

    /** The format a stream is converted to ("opus", "mp3"), or null for the original file. */
    fun formatOf(uri: Uri): String? {
        if (isEmbyStream(uri)) {
            if (uri.getQueryParameter("Static").equals("true", ignoreCase = true)) return null
            return uri.getQueryParameter(EMBY_CODEC)?.lowercase()
        }
        return uri.getQueryParameter("format")?.takeIf { it != "raw" }
    }

    /** The bitrate a converted stream was asked for, in kbit/s, or null. */
    fun kbpsOf(uri: Uri): Int? =
        if (isEmbyStream(uri)) uri.getQueryParameter(EMBY_BITRATE)?.toIntOrNull()?.div(1000)
        else uri.getQueryParameter("maxBitRate")?.toIntOrNull()

    /** How a stream is described in the log: "original", or the format and bitrate ("opus 256"). */
    fun describe(uri: Uri): String = formatOf(uri)?.let { f -> listOfNotNull(f, kbpsOf(uri)).joinToString(" ") } ?: "original"

    /**
     * Whether a song is played as its original file even when Opus is chosen: a file that is Opus already
     * (issue #1204), which is small as it is. Asked for Opus from one, Navidrome sends the file untouched from its
     * beginning, wherever it was asked to start (a seek started the song again), and Emby 4.8 answers "Error starting
     * ffmpeg" (HTTP 500). Emby and Jellyfin call the file's type "ogg", Opus or Vorbis.
     */
    fun keepsOriginal(kind: app.siansa.net.SourceKind, suffix: String?): Boolean {
        val type = suffix?.trim()?.lowercase()?.removePrefix(".") ?: return false
        return type == "opus" || (kind.embyLike && (type == "ogg" || type == "oga"))
    }

    private const val EMBY_CODEC = "AudioCodec"
    private const val EMBY_BITRATE = "AudioBitRate"
    private const val EMBY_SESSION = "PlaySessionId"

    /**
     * The stream a Chromecast plays: MP3 320 converted by Navidrome, which every Cast device can play
     * (the original file may be hi-res FLAC or ALAC, which they can't).
     */
    fun forCast(uri: Uri): Uri = convert(uri, "mp3", CAST_KBPS, estimateLength = true)

    const val CAST_KBPS = 320
    const val CAST_MIME = "audio/mpeg"

    /**
     * The Opus 320 conversion for a TV or speaker (issue #114): Navidrome's "opus audio" profile, Opus in Ogg, which
     * Chromecast and Google TV play (the owner's SHIELD plays it) and many DLNA devices that list Ogg.
     * Without an estimated length: Opus is variable, so an estimate would leave the device waiting at the end for data
     * that never comes.
     */
    fun forCastOpus(uri: Uri): Uri = convert(uri, "opus", CAST_KBPS, estimateLength = false)

    const val CAST_OPUS_MIME = "audio/ogg"

    private fun convert(uri: Uri, format: String, kbps: Int, estimateLength: Boolean): Uri {
        if (kbps <= 0 || !isSubsonicStream(uri)) return uri
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
