package app.siansa.playback

import androidx.media3.common.MimeTypes
import java.util.Collections

/** Settings › Audio passthrough on the TV app (issue #860). */
enum class PassthroughMode(val label: String) {
    OFF("Off"),
    DOLBY_DTS("Dolby and DTS formats");

    companion object {
        fun parse(name: String?): PassthroughMode = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/**
 * Audio passthrough (issue #860): Dolby and DTS music goes unchanged over HDMI to the AV receiver or soundbar, which
 * decodes it (Atmos included). Only the TV app sends a bitstream; phones always decode.
 *
 * A bitstream can't be mixed, so those songs go around the audio engine: no crossfades, DJ blends, levelling, EQ or
 * volume in Siansa for them. Everything else plays as usual.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object Passthrough {
    /** The formats sent as a bitstream, by the type Media3 reads from the file. */
    val bitstreamMimes: Set<String> = setOf(
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,
        MimeTypes.AUDIO_TRUEHD,
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_DTS_EXPRESS,
        MimeTypes.AUDIO_DTS_X,
    )

    /**
     * File types that hold only Dolby or DTS audio, or (Matroska) usually do here. An .m4a can hold E-AC-3 too, but
     * mostly holds AAC or ALAC, so it's only known once it plays ([played]).
     */
    val bitstreamSuffixes: Set<String> = setOf("ac3", "eac3", "ec3", "thd", "truehd", "mlp", "dts", "dtshd", "mka")

    /** Whether Media3's type [mime] is one passthrough sends unchanged. */
    fun isBitstream(mime: String?): Boolean = mime != null && mime in bitstreamMimes

    /** Whether a song of file type [suffix] is likely to go to the receiver as a bitstream. */
    fun likely(mode: PassthroughMode, suffix: String?): Boolean =
        mode != PassthroughMode.OFF && clean(suffix) in bitstreamSuffixes

    /**
     * Containers that may hold Dolby or DTS music without saying so in their file type: Dolby Atmos music is most often
     * sold as .m4a or .mp4 (E-AC-3), and films' soundtracks as .mkv. Most .m4a files are AAC or ALAC.
     */
    val maybeBitstreamSuffixes: Set<String> = setOf("m4a", "mp4", "mkv")

    /**
     * Whether a song must stream as its original file: a bitstream can't be converted to Opus by Navidrome and stay a
     * bitstream, so a Dolby or DTS file, or a file that may hold one, always streams untouched while passthrough is on.
     * On a TV the original is what plays anyway, unless a lower quality is picked under Streaming.
     */
    fun keepsOriginal(mode: PassthroughMode, suffix: String?): Boolean =
        likely(mode, suffix) || (mode != PassthroughMode.OFF && clean(suffix) in maybeBitstreamSuffixes)

    private fun clean(suffix: String?): String? = suffix?.trim()?.lowercase()?.removePrefix(".")

    /**
     * Why passthrough was given up for this run of the app, or null while it's on: the TV said the receiver takes a
     * format, but the output couldn't be opened for it (or failed while it played). Those songs are then decoded on the
     * TV instead of failing, and the log says why.
     */
    @Volatile var failed: String? = null

    /** Formats already written to the log with what passthrough decided for them, so each is said once. */
    val logged: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** Songs that went to the receiver as a bitstream in this session, by id: an .m4a with E-AC-3 is known from then on. */
    val played: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

    /** Whether the song [id] of file type [suffix] goes, or went, to the receiver as a bitstream. */
    fun involves(mode: PassthroughMode, id: String?, suffix: String?): Boolean =
        mode != PassthroughMode.OFF && id != null && (likely(mode, suffix) || id in played)

    /** A plain name for a bitstream type, for the log and the TV's settings ("Dolby Atmos (E-AC-3 JOC)"). */
    fun name(mime: String?): String = when (mime) {
        MimeTypes.AUDIO_AC3 -> "Dolby Digital (AC-3)"
        MimeTypes.AUDIO_E_AC3 -> "Dolby Digital Plus (E-AC-3)"
        MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos (E-AC-3 JOC)"
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
        MimeTypes.AUDIO_DTS_X -> "DTS:X"
        else -> mime ?: "unknown"
    }
}
