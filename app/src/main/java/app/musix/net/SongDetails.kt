package app.musix.net

import app.musix.data.PlayEvent
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/** A song's file and play details from Navidrome's getSong, for the "Show details" page. */
data class SongDetails(
    val codec: String? = null,
    val bitRateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val sizeBytes: Long? = null,
    val path: String? = null,
    val playCount: Long? = null,
)

/** Reads the details out of a Subsonic song object (OpenSubsonic adds sample rate, bit depth and channels). */
fun parseSongDetails(o: JsonObject) = SongDetails(
    codec = codecName(o.str("suffix"), o.str("contentType")),
    bitRateKbps = o.int("bitRate")?.takeIf { it > 0 },
    sampleRateHz = o.int("samplingRate")?.takeIf { it > 0 },
    bitDepth = o.int("bitDepth")?.takeIf { it > 0 },
    channels = o.int("channelCount")?.takeIf { it > 0 },
    sizeBytes = o.long("size")?.takeIf { it > 0 },
    path = o.str("path")?.takeIf { it.isNotBlank() },
    playCount = o.long("playCount"),
)

/** "FLAC", "MP3", "ALAC"… from the file's extension, or from its MIME type when there is none. */
fun codecName(suffix: String?, contentType: String?): String? {
    val ext = suffix?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        ?: contentType?.substringAfter('/')?.substringBefore(';')?.trim()?.lowercase()?.removePrefix("x-")
            ?.takeIf { it.isNotEmpty() }
        ?: return null
    return when (ext) {
        "mpeg", "mp3" -> "MP3"
        "m4a", "mp4", "aac" -> "AAC"
        "ogg", "oga", "vorbis" -> "Vorbis"
        "opus" -> "Opus"
        "flac" -> "FLAC"
        "wav", "wave" -> "WAV"
        "alac" -> "ALAC"
        "aiff", "aif" -> "AIFF"
        "wv", "wavpack" -> "WavPack"
        "ape" -> "APE"
        "dsf", "dff" -> "DSD"
        "wma", "ms-wma" -> "WMA"
        else -> ext.uppercase(Locale.ROOT)
    }
}

/** "44.1 kHz", "96 kHz". */
fun formatSampleRate(hz: Int): String {
    val khz = hz / 1000.0
    return if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.US, "%.1f kHz", khz)
}

/** "850 KB", "32.4 MB", "1.2 GB". */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L * 1024 -> "${(bytes + 1023) / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

/** "Mono", "Stereo", or "6 channels". */
fun channelsLabel(channels: Int): String = when (channels) {
    1 -> "Mono"
    2 -> "Stereo"
    else -> "$channels channels"
}

/** How many times musix on this phone skipped the song early (Navidrome doesn't count skips). */
fun skipCount(history: List<PlayEvent>, songId: String): Int = history.count { it.id == songId && it.skipped }
