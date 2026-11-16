package app.siansa.playback

/**
 * What About this song says under "Playing" (issue #1038): what really reaches the ears, beside the file's own details.
 * [title] is the format playing ("Opus 256 kbit/s") or "Same as the file"; [detail] says where it comes from; [differs]
 * is true when what plays isn't the file as it is on the server, which lights the box.
 */
data class PlayingAs(val title: String, val detail: String, val differs: Boolean) {
    companion object {
        const val SAME = "Same as the file"

        /**
         * From what the player knows: [device] is the TV or speaker it plays on (null on the phone); [downloadKbps] is
         * the Opus bitrate a downloaded song was saved at (0 the original, null not downloaded); [transcodeKbps] the
         * Opus bitrate the server converts the stream to (0 the original); [cached] whether the whole original is in
         * the song cache; [wifi] whether the phone counts its connection as Wi-Fi.
         */
        fun of(device: String?, downloadKbps: Int?, transcodeKbps: Int, cached: Boolean, wifi: Boolean): PlayingAs {
            val net = if (wifi) "Wi-Fi" else "mobile data"
            return when {
                device != null -> PlayingAs("On $device", "Sent there by your server", differs = false)
                downloadKbps != null && downloadKbps > 0 -> PlayingAs(opus(downloadKbps), "Downloaded · plays from this phone", differs = true)
                downloadKbps != null -> PlayingAs(SAME, "Downloaded · plays from this phone", differs = false)
                transcodeKbps > 0 -> PlayingAs(opus(transcodeKbps), "Converted by your server · $net", differs = true)
                cached -> PlayingAs(SAME, "From the song cache on this phone", differs = false)
                else -> PlayingAs(SAME, "Streamed as it is · $net", differs = false)
            }
        }

        private fun opus(kbps: Int) = "Opus $kbps kbit/s"

        /** The file's side: its format ("FLAC"; the file type when the server didn't say) and its bit rate. */
        fun file(codec: String?, suffix: String?, bitRateKbps: Int?): Pair<String, String?> =
            (codec?.takeIf { it.isNotBlank() } ?: suffix?.trim()?.removePrefix(".")?.uppercase()?.takeIf { it.isNotEmpty() } ?: "Unknown") to
                bitRateKbps?.takeIf { it > 0 }?.let { "%,d kbit/s".format(it) }
    }
}
