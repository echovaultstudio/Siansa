package app.musix.playback

import kotlin.math.roundToInt

/**
 * The spectrogram as pixels: time runs left to right (newest at the right edge), frequency from 0 Hz at
 * the bottom to half the sample rate at the top, brighter where there's more sound. Also tracks where the
 * sound stops, for [verdictText].
 */
class Spectrogram(val sampleRate: Int, val width: Int = 300, val height: Int = 200) {
    private val analyser = SpectrumAnalyser()
    private val bins = analyser.size / 2
    private val hop = analyser.size / 2
    val cutoff = CutoffDetector(sampleRate, bins)

    /** ARGB pixels, row by row from the top. */
    val pixels = IntArray(width * height) { BLACK }
    private var pending = FloatArray(analyser.size * 4)
    private var pendingCount = 0

    /** Adds newly played samples (mono, -1..1), drawing a column for each step of [hop] samples. */
    fun add(samples: FloatArray) {
        if (pendingCount + samples.size > pending.size) pending = pending.copyOf(pendingCount + samples.size)
        samples.copyInto(pending, pendingCount)
        pendingCount += samples.size
        var offset = 0
        while (pendingCount - offset >= analyser.size) {
            val frame = analyser.frame(pending, offset)
            cutoff.add(frame)
            addColumn(frame)
            offset += hop
        }
        pending.copyInto(pending, 0, offset, pendingCount)
        pendingCount -= offset
    }

    private fun addColumn(frameDb: FloatArray) {
        for (row in 0 until height) {
            val base = row * width
            System.arraycopy(pixels, base + 1, pixels, base, width - 1)
            // Row 0 is the top: the highest frequencies.
            val from = ((height - 1 - row) * bins) / height
            val to = maxOf(from + 1, ((height - row) * bins) / height)
            var loudest = -200f
            for (k in from until to) if (frameDb[k] > loudest) loudest = frameDb[k]
            pixels[base + width - 1] = colour(loudest)
        }
    }

    companion object {
        const val BLACK = 0xFF000000.toInt()
        const val FLOOR_DB = -120f

        private val stops = intArrayOf(0x000000, 0x1B0B4A, 0x7A1B6D, 0xD9442B, 0xFBB032, 0xFFF6C8)

        /** Black for silence through purple and red to pale yellow at full level, like Spek. */
        fun colour(db: Float): Int {
            val t = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f) * (stops.size - 1)
            val i = t.toInt().coerceAtMost(stops.size - 2)
            val f = t - i
            fun mix(shift: Int): Int {
                val a = (stops[i] shr shift) and 0xFF
                val b = (stops[i + 1] shr shift) and 0xFF
                return (a + (b - a) * f).roundToInt()
            }
            return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }

        private val LOSSLESS = setOf("FLAC", "ALAC", "WAV", "PCM", "AIFF", "APE", "WAVPACK", "TTA", "DSF", "DFF")

        fun isLossless(codec: String?): Boolean = codec?.uppercase() in LOSSLESS

        fun kHz(hz: Double): String = "%.1f kHz".format(hz / 1_000)

        /** What the spectrogram says about the song, in plain words. */
        fun verdictText(verdict: SourceVerdict, cutoffHz: Double?, codec: String?, transcodeKbps: Int): String {
            if (transcodeKbps > 0) {
                return "This song is streaming as Opus $transcodeKbps right now, so this shows the Opus version, not your file. " +
                    "Set streaming to Original in Settings › Playback to check the file."
            }
            val at = cutoffHz?.let { kHz(it) }
            val lossless = isLossless(codec)
            return when (verdict) {
                SourceVerdict.LISTENING -> "Listening… Give it about ten seconds of music."
                SourceVerdict.FULL_RANGE ->
                    if (lossless) "Sound reaches about $at, as a real lossless file does."
                    else "Sound reaches about $at."
                SourceVerdict.STOPS_NEAR_20K ->
                    if (lossless) "Sound stops at about $at. That's typical of a high-quality MP3, AAC or Opus (256 to 320k), " +
                        "so this ${codec?.uppercase()} may have been made from one. Some real recordings also stop there."
                    else "Sound stops at about $at, as expected for a high-quality lossy file."
                SourceVerdict.STOPS_LOW ->
                    if (lossless) "Sound stops at about $at. That's typical of an MP3 or AAC at 128 to 192k, " +
                        "so this ${codec?.uppercase()} was probably made from a lossy file."
                    else "Sound stops at about $at, as expected for a lossy file."
            }
        }
    }
}
