package app.siansa.playback.gapless

/**
 * The phone's stream to the TV (issue #114): FLAC first; WAV, plain samples behind a header that says "endless", when
 * a TV doesn't start FLAC. Both 16-bit, at the mixer's rate.
 */
enum class StreamFormat(val key: String, val mime: String, val label: String) {
    Flac("flac", "audio/flac", "FLAC"),
    Wav("wav", "audio/wav", "WAV");

    companion object {
        fun of(key: String): StreamFormat? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A WAV header for an endless stream: 16-bit PCM, little-endian. Its lengths can't be known, so they say the most a
 * RIFF file can hold (0xFFFFFFFF), as streaming servers do; players then play on for as long as samples come.
 */
object WavHeader {
    fun of(sampleRate: Int, channels: Int): ByteArray {
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt(-1) // RIFF size: unknown
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1) // PCM
        b.putShort(channels.toShort())
        b.putInt(sampleRate)
        b.putInt(sampleRate * channels * 2)
        b.putShort((channels * 2).toShort())
        b.putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(-1) // data size: unknown
        return b.array()
    }

    /** [frames] interleaved 16-bit samples of [pcm] as little-endian bytes. */
    fun samples(pcm: ShortArray, frames: Int, channels: Int): ByteArray {
        val out = ByteArray(frames * channels * 2)
        for (i in 0 until frames * channels) {
            val v = pcm[i].toInt()
            out[2 * i] = v.toByte()
            out[2 * i + 1] = (v shr 8).toByte()
        }
        return out
    }
}

/**
 * Which format the TV gets its stream in, and when to give up (issue #114): each format is tried once without the
 * stream getting anywhere, then the TV plays the queue instead. A format that played starts the count again.
 */
class StreamTries(first: StreamFormat) {
    sealed class Next {
        data class Retry(val format: StreamFormat) : Next()
        data object GiveUp : Next()
    }

    var format: StreamFormat = first
        private set
    private val tried = linkedSetOf(first)
    private var everWorked = false

    /** The stream went nowhere in [format]: the next one to try, or give up. */
    fun failed(): Next {
        val other = StreamFormat.entries.firstOrNull { it !in tried } ?: return Next.GiveUp
        tried += other
        format = other
        return Next.Retry(other)
    }

    /** The stream got going in [format]. True the first time. */
    fun worked(): Boolean {
        tried.clear()
        tried += format
        val first = !everWorked
        everWorked = true
        return first
    }

    fun triedLabels(): String = tried.joinToString(" and ") { it.label }
}
