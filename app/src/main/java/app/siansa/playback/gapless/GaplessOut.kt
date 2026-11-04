package app.siansa.playback.gapless

import app.siansa.playback.engine.MixOut

/**
 * The mix on its way to the TV (issue #114, gapless casting): each block from the [app.siansa.playback.engine.AudioEngine]
 * becomes 16-bit samples in the [ring] that [LiveServer] streams, and the mixer waits to stay [leadMs] ahead of what
 * the TV plays ([TvClock], [Pacer]), the way a write to the phone's own output waits for room.
 */
class GaplessOut(
    private val ring: PcmRing,
    private val clock: TvClock,
    private val rate: Int,
    /** The lead the mixer keeps once the TV plays. */
    private val baseLeadMs: Long,
    private val name: String,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = System::nanoTime,
) : MixOut {
    private val dither = Dither()
    private var pcm = ShortArray(0)
    private val leadFrames = baseLeadMs * rate / 1000

    /** The mix frame after the last one written. */
    @Volatile private var written = 0L

    /**
     * How far (ms) the mix is written ahead of what the TV plays now: [baseLeadMs] once it plays steadily, more while it
     * starts or catches up. Cues (crossfades) are set this much further ahead, so they're never behind what was sent.
     */
    override val leadMs: Long
        get() = maxOf(baseLeadMs, ((written - clock.heard(now())) * 1000 / rate).toLong() + LEAD_MARGIN_MS)

    /** Set to end: the next write says no and the mix goes back to the phone. */
    @Volatile var stopped = false

    /** Mix frames that didn't follow on from the ones before (the ring started again), for the log. */
    @Volatile var restarts = 0
        private set

    override fun write(block: FloatArray, frames: Int, at: Long): Boolean {
        if (stopped) return false
        if (at != ring.end) {
            if (ring.end != 0L || ring.origin != 0L) restarts++
            ring.reset(at)
        }
        val samples = frames * ring.channels
        if (pcm.size < samples) pcm = ShortArray(samples)
        dither.convert(block, 0, pcm, 0, samples)
        ring.write(pcm, frames)
        val written = at + frames
        this.written = written
        while (!stopped) {
            val wait = Pacer.waitMs(written, clock.paceHeard(now()), leadFrames, rate)
            if (wait <= 0) break
            sleep(wait)
        }
        return !stopped
    }

    override fun heard(nanos: Long): Double = clock.heard(nanos)

    override fun describe(): String = name

    companion object {
        /** A little more than measured, for what's written while a cue is set. */
        const val LEAD_MARGIN_MS = 200L
    }
}
