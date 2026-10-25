package app.siansa.playback.dlna

/**
 * Which songs a DLNA TV or speaker can play as they are, without Siansa's MP3 320 (beta testers #23): the
 * original file goes to devices that list its format, when the person turned that on for the device.
 */
internal object DlnaFormats {
    /** Names of the same format that devices and servers use. */
    private val SAME: List<Set<String>> = listOf(
        setOf("audio/flac", "audio/x-flac"),
        setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"),
        setOf("audio/mpeg", "audio/mp3", "audio/mpeg3", "audio/x-mpeg"),
        setOf("audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/x-aac"),
        setOf("audio/ogg", "application/ogg", "audio/x-ogg", "audio/vorbis"),
        setOf("audio/opus", "audio/x-opus"),
        setOf("audio/aiff", "audio/x-aiff"),
        setOf("audio/x-ms-wma", "audio/wma"),
        setOf("audio/x-ape", "audio/ape"),
        setOf("audio/x-dsf", "audio/dsf", "audio/x-dsd"),
    )

    /** Shown in the device's settings, in this order. */
    private val NAMES: List<Pair<String, String>> = listOf(
        "audio/flac" to "FLAC", "audio/wav" to "WAV", "audio/aiff" to "AIFF", "audio/mp4" to "M4A",
        "audio/ogg" to "OGG", "audio/opus" to "Opus", "audio/x-ape" to "APE", "audio/x-dsf" to "DSD",
        "audio/x-ms-wma" to "WMA", "audio/mpeg" to "MP3",
    )

    /** [mime] without parameters, in lower case. */
    fun plain(mime: String?): String? = mime?.substringBefore(';')?.trim()?.lowercase()?.takeIf { '/' in it }

    /** Whether a device that plays [formats] takes a file of type [mime]. */
    fun accepts(formats: Set<String>?, mime: String?): Boolean {
        val type = plain(mime) ?: return false
        if (formats.isNullOrEmpty()) return false
        if ("*" in formats || "*/*" in formats || "audio/*" in formats) return true
        val names = SAME.firstOrNull { type in it } ?: setOf(type)
        return names.any { it in formats }
    }

    /** "FLAC, WAV, MP3": the formats it says it plays that people know by name. */
    fun names(formats: Set<String>?): List<String> =
        NAMES.filter { (mime, _) -> accepts(formats, mime) && !(formats.orEmpty().any { it.endsWith("/*") || it == "*" }) }.map { it.second }

    /** Whether the device plays anything besides MP3, so sending the original file can make a difference. */
    fun playsMoreThanMp3(formats: Set<String>?): Boolean =
        !formats.isNullOrEmpty() && (names(formats) - "MP3").isNotEmpty() || formats.orEmpty().any { it == "*" || it.endsWith("/*") }
}
