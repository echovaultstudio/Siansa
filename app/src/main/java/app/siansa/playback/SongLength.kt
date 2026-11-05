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
 * which reads the audio itself), the library's length is used instead. Anything else keeps the file's own length.
 */
object SongLength {
    /** How much longer than the library's length a file may say it is before it's not believed. */
    const val TOLERANCE_MS = 10_000L

    /** Navidrome gives lengths in whole seconds, cut down: the song really ends within the second after. */
    private const val HALF_SECOND_US = 500_000L

    private val logged: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /**
     * The length to play a song as, in microseconds: [fileUs] unless it's far longer than [librarySec] (when known).
     * [C.TIME_UNSET] stays unknown.
     */
    fun corrected(fileUs: Long, librarySec: Int?): Long {
        if (fileUs == C.TIME_UNSET || librarySec == null || librarySec <= 0) return fileUs
        val libraryUs = librarySec * 1_000_000L
        return if (fileUs > libraryUs + TOLERANCE_MS * 1000) libraryUs + HALF_SECOND_US else fileUs
    }

    /** The library's length of the song a stream address is for, when it's a whole song from the server. */
    fun librarySec(uri: Uri): Int? {
        if (Streaming.offsetMs(uri) > 0) return null
        val id = runCatching { uri.getQueryParameter("id") }.getOrNull() ?: return null
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
            val id = uri.getQueryParameter("id").orEmpty()
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
                val lengthUs = corrected(fileUs, librarySec)
                if (lengthUs == fileUs) {
                    output.seekMap(seekMap)
                    return
                }
                if (logged.add(id)) {
                    AppLog.w("Audio", "Song $id: its file says it lasts ${fileUs / 1_000_000}s but it's ${librarySec}s; playing it as ${librarySec}s",
                        event = "playback.length_corrected", fields = mapOf("song_id" to id, "file_s" to fileUs / 1_000_000, "library_s" to librarySec))
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
