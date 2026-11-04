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
 * How the TV screen plays the phone's stream (issue #114): through Google's Cast player, loaded by the page as a
 * buffered stream of unknown length (known to play on the owner's SHIELD), or in the page's own
 * audio element; in FLAC or WAV.
 */
data class StreamWay(val format: StreamFormat, val throughCast: Boolean) {
    val label: String get() = format.label + if (throughCast) " through Google's player" else " in the page's own player"

    /** For the TV page's 'start' message. */
    val player: String get() = if (throughCast) "cast" else "element"

    companion object {
        /**
         * The order they're tried in: Google's player first, as a buffered stream like this is known to play on the
         * SHIELD (its earlier failure here was the phone sending too little, now fixed); then the page's own player;
         * WAV last.
         */
        val ORDER = listOf(
            StreamWay(StreamFormat.Flac, throughCast = true),
            StreamWay(StreamFormat.Flac, throughCast = false),
            StreamWay(StreamFormat.Wav, throughCast = false),
        )
    }
}

/**
 * Which way the TV gets its stream, and when to give up (issue #114): each way is tried once without the stream
 * getting anywhere, in [StreamWay.ORDER] from [first], then the TV plays the queue instead. A way that played starts
 * the count again.
 */
class StreamTries(first: StreamWay = StreamWay.ORDER.first()) {
    sealed class Next {
        data class Retry(val way: StreamWay) : Next()
        data object GiveUp : Next()
    }

    var way: StreamWay = first
        private set
    val format: StreamFormat get() = way.format
    private val tried = linkedSetOf(first)
    private var everWorked = false

    /** The stream went nowhere this [way]: the next one to try, or give up. */
    fun failed(): Next {
        val other = StreamWay.ORDER.firstOrNull { it !in tried } ?: return Next.GiveUp
        tried += other
        way = other
        return Next.Retry(other)
    }

    /** The stream got going this [way]. True the first time. */
    fun worked(): Boolean {
        tried.clear()
        tried += way
        val first = !everWorked
        everWorked = true
        return first
    }

    fun triedLabels(): String = tried.joinToString(", ") { it.label }
}
