package app.siansa.playback.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import app.siansa.log.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Siansa's own audio output (issue #826): one AudioTrack that every music player's sound is mixed into. Each player
 * still decodes and processes its songs (Media3, the equaliser), but hands the result to a [MixCore.Deck] instead of
 * an AudioTrack of its own ([MixerOutputProvider]); this thread mixes the decks and writes the mix out.
 *
 * Both songs of a crossfade are then on one clock: where each is, is a count of frames this engine took from it,
 * the same for both and exact to the sample. The old way, two AudioTracks each reporting a position that was off by
 * 50 to 250 ms for seconds after a start or a seek, is what kept beat-matched blends out of step (#807, #813, #818).
 *
 * The output runs at [OUTPUT_RATE]: most music is 44.1 kHz and is copied through untouched; other rates and a song
 * sped up or slowed down in a blend are read through [SincTable]'s filter.
 */
class AudioEngine(context: Context) {
    val outRate = OUTPUT_RATE
    val core = MixCore(outRate)
    val sessionId: Int = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()

    private val wakeLock = ReentrantLock()
    private val woken = wakeLock.newCondition()
    @Volatile private var running = true
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private var trackPlaying = false

    /** Mix frames written to the output in all; the current AudioTrack's frame 0 is mix frame [trackBase]. */
    @Volatile var writtenMix = 0L
        private set
    private var trackBase = 0L
    // The output's last timestamp: the mix frame being heard at a moment (System.nanoTime()).
    @Volatile private var stampMix = Long.MIN_VALUE
    @Volatile private var stampNanos = 0L
    @Volatile private var stampLive = false
    private val stamp = AudioTimestamp()
    private var blocksSinceStamp = 0

    /** The player output each stream belongs to, told when its stream first sounds. */
    internal val outputs = ConcurrentHashMap<DeckStream, DeckOutput>()

    fun deck(name: String): MixCore.Deck = core.deck(name)

    fun provider(deck: MixCore.Deck): MixerOutputProvider = MixerOutputProvider(this, deck)

    /** Something may have something to play: the thread starts or wakes. */
    fun wake() {
        if (thread == null) start()
        wakeLock.withLock { woken.signalAll() }
    }

    @Synchronized
    private fun start() {
        if (thread != null || !running) return
        thread = Thread({ loop() }, "siansa-mixer").apply {
            isDaemon = true
            start()
        }
        AppLog.i("Engine", "Mixer started: $outRate Hz, one output for all players (session $sessionId)")
    }

    fun release() {
        running = false
        wake()
        thread?.join(500)
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }

    /**
     * The mix frame being heard now: from the output's timestamp, on at its rate while it plays, and less what the
     * mix's limiter holds back. Never past what was written.
     */
    fun heardMix(nanos: Long = System.nanoTime()): Double {
        val at = stampMix
        val heard = if (at == Long.MIN_VALUE) {
            // No timestamp yet (just started): what was written less the output's buffer.
            (writtenMix - (track?.bufferSizeInFrames ?: 0)).toDouble()
        } else {
            at + if (stampLive) (nanos - stampNanos) * outRate / 1e9 else 0.0
        }
        return (minOf(heard, writtenMix.toDouble()) - core.latencyFrames).coerceAtLeast(0.0)
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val block = FloatArray(BLOCK * 2)
        while (running) {
            if (!core.active) {
                rest()
                continue
            }
            val t = track ?: open() ?: run {
                Thread.sleep(RETRY_MS)
                null
            } ?: continue
            if (!trackPlaying) {
                t.play()
                trackPlaying = true
            }
            val started = core.render(block, BLOCK, writtenMix)
            var off = 0
            var dead = false
            while (off < block.size && running) {
                val n = t.write(block, off, block.size - off, AudioTrack.WRITE_BLOCKING)
                if (n < 0) {
                    AppLog.w("Engine", "Output write failed ($n); opening it again")
                    dead = true
                    break
                }
                off += n
            }
            writtenMix += BLOCK
            for (stream in started) outputs[stream]?.advancing()
            if (dead) {
                close()
                continue
            }
            if (++blocksSinceStamp >= STAMP_EVERY) {
                blocksSinceStamp = 0
                takeStamp(t)
            }
        }
        close()
    }

    /** Nothing to play: the output pauses (what it holds plays out first) and the thread waits to be woken. */
    private fun rest() {
        track?.let { t ->
            if (trackPlaying) {
                takeStamp(t)
                t.pause()
                trackPlaying = false
                stampLive = false
            }
        }
        wakeLock.withLock { woken.await(IDLE_WAIT_MS, TimeUnit.MILLISECONDS) }
    }

    private fun takeStamp(t: AudioTrack) {
        if (t.getTimestamp(stamp)) {
            stampMix = trackBase + stamp.framePosition
            stampNanos = stamp.nanoTime
            stampLive = trackPlaying
        }
    }

    private fun open(): AudioTrack? = try {
        val min = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val size = maxOf(min, outRate * BUFFER_MS / 1_000 * 8)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(outRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(sessionId)
            .build()
            .also {
                track = it
                trackBase = writtenMix
                stampMix = Long.MIN_VALUE
                trackPlaying = false
                AppLog.i("Engine", "Output open: $outRate Hz float stereo, buffer ${it.bufferSizeInFrames * 1000 / outRate} ms")
            }
    } catch (e: Exception) {
        AppLog.e("Engine", "Couldn't open the output: ${e.message}")
        null
    }

    private fun close() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        trackPlaying = false
        stampMix = Long.MIN_VALUE
    }

    companion object {
        /** The output's rate: most music is 44.1 kHz and plays through untouched. */
        const val OUTPUT_RATE = 44_100
        /** Frames mixed at a time: 5.8 ms. A volume or a filter moves at most once a block. */
        const val BLOCK = 256
        /** The output's buffer: what's written ahead of what's heard. */
        const val BUFFER_MS = 120
        const val IDLE_WAIT_MS = 250L
        const val RETRY_MS = 300L
        /** How often (in blocks) the output's timestamp is read again: about every 50 ms. */
        const val STAMP_EVERY = 8
    }
}
