package app.siansa.playback

/**
 * What About this song says under "Playing" (issue #1038): what really reaches the ears, beside the file's own details.
 * [title] is the format playing ("Opus 256 kbit/s") or "Same as the file"; [detail] says where it comes from; [differs]
 * is true when what plays isn't the file as it is on the server, which lights the box. [sound] says what the phone
 * then does with it (the owner, 23 Nov: "actually show what's being played, whether it's resampled or not. Because even
 * in DJ mix it says playing same as file"): untouched, unchanged through the mixer, resampled, blended at song changes.
 */
data class PlayingAs(val title: String, val detail: String, val differs: Boolean, val sound: String? = null, val told: Told? = null) {
    /** What the player knew when this was made, kept so the file's sample rate can be added once the server has said it. */
    data class Told(
        val device: String?, val downloadKbps: Int?, val transcodeKbps: Int, val cached: Boolean, val wifi: Boolean, val cacheOn: Boolean,
        val untouched: Boolean, val blended: String?,
    )

    /** This, with what the phone does with the sound, now that the file's sample rate ([fileHz], null if unknown) is known. */
    fun heard(fileHz: Int?): PlayingAs = told?.let { of(it.device, it.downloadKbps, it.transcodeKbps, it.cached, it.wifi, it.cacheOn, fileHz, it.untouched, it.blended) } ?: this

    companion object {
        const val SAME = "Same as the file"

        /** The one rate Siansa's mixer runs at, where crossfades and DJ blends happen. */
        const val MIXER_HZ = 44_100

        /** Opus always comes out of its decoder at this rate, whatever the file was. */
        const val OPUS_HZ = 48_000

        /** With blends on, the song is the file only between them. */
        const val SAME_BETWEEN = "Same as the file between blends"

        /**
         * From what the player knows: [device] is the TV or speaker it plays on (null on the phone); [downloadKbps] is
         * the Opus bitrate a downloaded song was saved at (0 the original, null not downloaded); [transcodeKbps] the
         * Opus bitrate the server converts the stream to (0 the original); [cached] whether the whole original is in
         * the song cache's store; [cacheOn] whether the song cache is on: off, a song is only there because it was
         * loaded ahead (the owner, 16 Nov: "I have song cache disabled but it says playing from phone cache"); [wifi]
         * whether the phone counts its connection as Wi-Fi.
         */
        fun of(device: String?, downloadKbps: Int?, transcodeKbps: Int, cached: Boolean, wifi: Boolean, cacheOn: Boolean = true): PlayingAs {
            val net = if (wifi) "Wi-Fi" else "mobile data"
            return arrives(device, downloadKbps, transcodeKbps, cached, cacheOn, net)
        }

        /**
         * As [of], and what the phone then does with the sound: [fileHz] is the file's sample rate (null when the
         * server didn't say), [untouched] whether Untouched playback is on, and [blended] what blends the songs at
         * their changes ("DJ mix", "crossfade"; null when nothing does). Nothing is said of a song playing on a TV or
         * speaker, which the phone doesn't play.
         */
        fun of(
            device: String?, downloadKbps: Int?, transcodeKbps: Int, cached: Boolean, wifi: Boolean, cacheOn: Boolean,
            fileHz: Int?, untouched: Boolean, blended: String?,
        ): PlayingAs {
            val base = of(device, downloadKbps, transcodeKbps, cached, wifi, cacheOn)
            if (device != null) return base
            val opus = (downloadKbps ?: 0) > 0 || (downloadKbps == null && transcodeKbps > 0)
            val hz = if (opus) OPUS_HZ else fileHz?.takeIf { it > 0 }
            if (untouched) {
                return base.copy(sound = if (hz != null) "Untouched · ${rate(hz)}" else "Untouched, at its own sample rate")
            }
            val resampled = hz != null && hz != MIXER_HZ
            val through = when {
                resampled -> "Resampled from ${rate(hz!!)} to ${rate(MIXER_HZ)}"
                hz != null -> "Unchanged at ${rate(MIXER_HZ)}"
                else -> "Through Siansa's mixer at ${rate(MIXER_HZ)}"
            }
            val sound = if (blended != null) "$through · blended at song changes ($blended)" else through
            return when {
                base.title != SAME -> base.copy(sound = sound)
                resampled -> base.copy(title = "Resampled to ${rate(MIXER_HZ)}", differs = true, sound = sound)
                blended != null -> base.copy(title = SAME_BETWEEN, sound = sound)
                else -> base.copy(sound = sound)
            }
        }

        /** "44.1 kHz", "96 kHz": never broken at its space. */
        private fun rate(hz: Int): String = app.siansa.net.formatSampleRate(hz).replace(' ', '\u00A0')

        private fun arrives(device: String?, downloadKbps: Int?, transcodeKbps: Int, cached: Boolean, cacheOn: Boolean, net: String): PlayingAs {
            return when {
                device != null -> PlayingAs("On $device", "Sent there by your server", differs = false)
                downloadKbps != null && downloadKbps > 0 -> PlayingAs(opus(downloadKbps), "Downloaded · plays from this phone", differs = true)
                downloadKbps != null -> PlayingAs(SAME, "Downloaded · plays from this phone", differs = false)
                transcodeKbps > 0 -> PlayingAs(opus(transcodeKbps), "Converted by your server · $net", differs = true)
                cached && cacheOn -> PlayingAs(SAME, "From the song cache on this phone", differs = false)
                cached -> PlayingAs(SAME, "Loaded ahead · plays from this phone", differs = false)
                else -> PlayingAs(SAME, "Streamed as it is · $net", differs = false)
            }
        }

        private fun opus(kbps: Int) = "Opus ${kbit(kbps)}"

        /**
         * "256 kbit/s" that never breaks inside: a no-break space after the number, and word joiners around the slash
         * (a narrow box at large text broke it into "320 kbit/" and "s", the owner, 16 Nov).
         */
        fun kbit(kbps: Int): String = "%,d".format(java.util.Locale.ROOT, kbps) + "\u00A0kbit\u2060/\u2060s"

        /** The file's side: its format ("FLAC"; the file type when the server didn't say) and its bit rate. */
        fun file(codec: String?, suffix: String?, bitRateKbps: Int?): Pair<String, String?> =
            (codec?.takeIf { it.isNotBlank() } ?: suffix?.trim()?.removePrefix(".")?.uppercase()?.takeIf { it.isNotEmpty() } ?: "Unknown") to
                bitRateKbps?.takeIf { it > 0 }?.let(::kbit)
    }
}
