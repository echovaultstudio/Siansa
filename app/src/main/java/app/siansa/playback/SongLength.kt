package app.siansa.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import app.siansa.log.AppLog
import java.util.Collections

/**
 * Songs whose file says it's far longer than it is (issue #860).
 *
 * Some Dolby Atmos `.m4a` files (DJ mixes from one tool, seen on a TV) say in their header that a 7-minute song lasts
 * an hour and 48 minutes. The player believes the header, so it thinks it has an hour and a half loaded ahead, never
 * loads the next song before this one ends, and only moves on to the next song once the clock reaches the false end:
 * the music stopped at the end of every song, and a gapless DJ mix had a gap between its parts.
 *
 * When the file's length is more than [TOLERANCE_MS] longer than the song's length in the library (from the server,
 * which reads the audio itself), it isn't believed. The song's real end is then read from the file's own audio: its
 * last frame, from the sample table, which these files have right ([lastFrameUs]). Only when that's missing or far from
 * the library's length is the library's length used, rounded up to the whole second after it (Navidrome rounds down).
 * Anything else keeps the file's own length.
 *
 * Never shorter than the real sound (0.52.0-dev, TV, 20:36): played as the library's seconds and a half, a song's last
 * second of audio ran past its end; the player moved on to the next song a second early, the next song started with its
 * clock at "1 s", and in a gapless join to the receiver it stalled there with a minute loaded. An end a moment too late
 * is a moment of quiet; too early, the next song can't start.
 */
object SongLength {
    /** How much longer than the library's length a file may say it is before it's not believed. */
    const val TOLERANCE_MS = 10_000L

    /** Navidrome gives lengths in whole seconds, cut down: the song really ends within the second after. */
    private const val ONE_SECOND_US = 1_000_000L

    /** How far from the library's length the file's last frame may be to be believed: Navidrome's whole seconds and some. */
    private const val LAST_FRAME_NEAR_US = 2_000_000L

    /** A frame's length past its start: the longest of the usual ones (Dolby Digital Plus's 32 ms), a little over. */
    const val FRAME_US = 40_000L

    private val logged: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * The length to play a song as, in microseconds: [fileUs] unless it's far longer than [librarySec] (when known).
     * Then the end of the file's last frame ([lastFrameUs], its start) when that's near the library's length, else the
     * library's length rounded up to the next whole second. [C.TIME_UNSET] stays unknown.
     */
    fun corrected(fileUs: Long, librarySec: Int?, lastFrameUs: Long = C.TIME_UNSET): Long {
        if (fileUs == C.TIME_UNSET || librarySec == null || librarySec <= 0) return fileUs
        val libraryUs = librarySec * 1_000_000L
        if (fileUs <= libraryUs + TOLERANCE_MS * 1000) return fileUs
        if (lastFrameUs != C.TIME_UNSET && lastFrameUs in (libraryUs - LAST_FRAME_NEAR_US)..(libraryUs + LAST_FRAME_NEAR_US)) return lastFrameUs + FRAME_US
        return libraryUs + ONE_SECOND_US
    }

    /**
     * Where the file's last frame starts, from its seek map (the sample table for an MP4: the last frame every song's
     * audio can start at, which for Dolby and AAC audio is the very last), or [C.TIME_UNSET].
     */
    @OptIn(UnstableApi::class)
    internal fun lastFrameUs(seekMap: SeekMap, fileUs: Long): Long {
        if (!seekMap.isSeekable || fileUs == C.TIME_UNSET) return C.TIME_UNSET
        return runCatching { seekMap.getSeekPoints(fileUs).first.timeUs }.getOrDefault(C.TIME_UNSET)
    }

    /** The library's length of the song a stream address is for, when it's a whole song from the server. */
    fun librarySec(uri: Uri): Int? {
        if (Streaming.offsetMs(uri) > 0) return null
        val id = streamSongId(uri.toString()) ?: return null
        return SongRegistry.get(id)?.durationSec
    }

    @OptIn(UnstableApi::class)
    fun extractors(inner: ExtractorsFactory = DefaultExtractorsFactory()): ExtractorsFactory = Factory(inner)

    @OptIn(UnstableApi::class)
    private class Factory(private val inner: ExtractorsFactory) : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> = inner.createExtractors()

        override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
            val made = inner.createExtractors(uri, responseHeaders)
            val librarySec = librarySec(uri) ?: return made
            val id = streamSongId(uri.toString()).orEmpty()
            return Array(made.size) { Checked(made[it], librarySec, id) }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory {
            inner.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
            return this
        }

        override fun setSubtitleParserFactory(subtitleParserFactory: androidx.media3.extractor.text.SubtitleParser.Factory): ExtractorsFactory {
            inner.setSubtitleParserFactory(subtitleParserFactory)
            return this
        }
    }

    /** An extractor that hands the player the corrected length. */
    @OptIn(UnstableApi::class)
    private class Checked(private val inner: Extractor, private val librarySec: Int, private val id: String) : Extractor {
        override fun sniff(input: ExtractorInput): Boolean = inner.sniff(input)
        override fun getSniffFailureDetails() = inner.sniffFailureDetails
        override fun getUnderlyingImplementation(): Extractor = inner.underlyingImplementation
        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = inner.read(input, seekPosition)
        override fun seek(position: Long, timeUs: Long) = inner.seek(position, timeUs)
        override fun release() = inner.release()

        override fun init(output: ExtractorOutput) = inner.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = output.track(id, type)
            override fun endTracks() = output.endTracks()
            override fun seekMap(seekMap: SeekMap) {
                val fileUs = seekMap.durationUs
                val lengthUs = corrected(fileUs, librarySec, lastFrameUs(seekMap, fileUs))
                if (lengthUs == fileUs) {
                    output.seekMap(seekMap)
                    return
                }
                if (logged.add(id)) {
                    AppLog.w("Audio", "Song $id: its file says it lasts ${fileUs / 1_000_000}s but it's ${librarySec}s; playing it as %.3fs, to its last frame".format(java.util.Locale.ROOT, lengthUs / 1e6),
                        event = "playback.length_corrected", fields = mapOf("song_id" to id, "file_s" to fileUs / 1_000_000, "library_s" to librarySec, "played_us" to lengthUs))
                }
                output.seekMap(object : SeekMap {
                    override fun isSeekable(): Boolean = seekMap.isSeekable
                    override fun getDurationUs(): Long = lengthUs
                    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints = seekMap.getSeekPoints(timeUs.coerceAtMost(lengthUs))
                })
            }
        })
    }
}
