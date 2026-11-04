package app.siansa.playback.engine

/**
 * Somewhere other than this phone's own output for the mix to go (issue #114: gapless casting sends it to the TV).
 * While one is set, the [AudioEngine] writes every block of the mix to it instead of its AudioTrack, and asks it what
 * is heard, so every player's position follows the far end.
 */
interface MixOut {
    /**
     * Takes [frames] frames of the mix (interleaved stereo float in [block]) that start at mix frame [at], and may wait
     * to keep pace with the far end, as a write to an AudioTrack does. False when it can take no more: the mix comes
     * back to the phone's output.
     */
    fun write(block: FloatArray, frames: Int, at: Long): Boolean

    /** The mix frame heard at the far end at [nanos] (System.nanoTime()). */
    fun heard(nanos: Long): Double

    /** Nothing is playing for now (a pause, the end of the music). */
    fun rest() {}

    /** How far (ms) the mix is written ahead of what's heard there, by design: cues are set that much earlier. */
    val leadMs: Long

    /** For the engine's status in the log. */
    fun describe(): String
}
