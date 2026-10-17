package app.musix.playback

/**
 * What now playing shows under the seek bar about the song's stream: its format (FLAC, ALAC, MP3...)
 * when the original file plays, or Opus and its bitrate when Navidrome converts it.
 */
object StreamQuality {
    /** Session extras the playback service sends: the song they're about, and its Opus kbit/s (0 = original). */
    const val EXTRA_SONG_ID = "app.musix.quality.song"
    const val EXTRA_KBPS = "app.musix.quality.kbps"

    /** Custom session command: play the current song (and the next ones) at this Opus kbit/s, or 0 for the original. */
    const val ACTION_SET_QUALITY = "app.musix.SET_QUALITY"

    /** The choices offered: the original file, then Opus bitrates (the same as in Settings → Playback). */
    val choices = listOf(0, 96, 128, 160, 192, 256, 320)

    fun choiceLabel(kbps: Int): String = if (kbps <= 0) "Original" else "Opus $kbps"

    /** A short name for an audio format from the player's MIME types, or null when it isn't known yet. */
    fun codecName(sampleMimeType: String?, containerMimeType: String? = null): String? {
        val mime = sampleMimeType?.lowercase()?.trim().orEmpty()
        if (mime.isEmpty()) return null
        return when (mime) {
            "audio/flac" -> "FLAC"
            "audio/alac" -> "ALAC"
            "audio/mpeg" -> "MP3"
            "audio/mpeg-l1", "audio/mpeg-l2" -> "MP2"
            "audio/opus" -> "OPUS"
            "audio/vorbis" -> "VORBIS"
            "audio/mp4a-latm", "audio/aac" -> "AAC"
            "audio/ac3" -> "AC3"
            "audio/eac3", "audio/eac3-joc" -> "E-AC3"
            "audio/true-hd" -> "TRUEHD"
            "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
            "audio/raw" -> if (containerMimeType?.lowercase()?.contains("wav") == true) "WAV" else "PCM"
            else -> {
                if (!mime.startsWith("audio/")) return null
                mime.removePrefix("audio/").removePrefix("x-").substringBefore(';').uppercase().ifEmpty { null }
            }
        }
    }

    /**
     * The label under the seek bar: "OPUS 160" while Navidrome converts the song at 160 kbit/s, otherwise
     * the original file's format ("FLAC"), or null when nothing is known yet.
     */
    fun label(codec: String?, transcodeKbps: Int): String? =
        if (transcodeKbps > 0) "OPUS $transcodeKbps" else codec
}
