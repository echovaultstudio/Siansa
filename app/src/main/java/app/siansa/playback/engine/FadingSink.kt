package app.siansa.playback.engine

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import app.siansa.log.AppLog
import java.nio.ByteBuffer

/**
 * The listener's fades (issue #1368) for Untouched playback (issue #1370): the player's own output, with its volume
 * moved for a moment when the listener starts or stops something. The song's samples are never touched, only how loud
 * the output plays them, so what comes out at full volume is still the song as it is.
 *
 * - **In** ([fadeInMs]): from silence up, when sound first goes out after a pause, a stop, or a skip or seek that cut
 *   something short, and at the very first start. Not after a song that played to its end: the next one, an album's
 *   next track, starts whole.
 * - **Out** ([fadeOutMs]): at a pause, and before a skip or seek throws away what was still to play. The player's
 *   thread waits for it (there's no mixer here to play the old song out behind the new one), so a skip is that much
 *   later.
 *
 * Every call comes on the player's playback thread, as for any sink. [now] and [sleep] are the clock, given so a test
 * needn't wait.
 */
@OptIn(UnstableApi::class)
class FadingSink(
    sink: AudioSink,
    private val fadeInMs: () -> Int,
    private val fadeOutMs: () -> Int,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : ForwardingAudioSink(sink) {
    private var wanted = 1f       // the volume the player asked for
    private var level = 1f        // the fade's share of it, 0 to 1
    private var playing = false
    private var due = true        // whether the next sound to go out fades in: the first is the listener's
    private var risingSince = -1L // when the fade in began, or -1

    override fun setVolume(volume: Float) {
        wanted = volume
        super.setVolume(volume * level)
    }

    override fun play() {
        playing = true
        // Played again with sound waiting (after a pause): the fade starts now. With none yet, when the first arrives.
        if (due && super.hasPendingData()) rise() else if (due) hold()
        super.play()
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (due && playing) rise()
        step()
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        step()  // asked every few milliseconds while it plays: the fade's clock
        return super.getCurrentPositionUs(sourceEnded)
    }

    override fun pause() {
        fall()
        playing = false
        super.pause()
        due = true
        hold()
    }

    override fun flush() {
        // With sound still to play, the listener cut it short (a skip, a seek): out first, and the next fades in.
        // With none (a song that played to its end), nothing to fade and the next starts whole.
        val cut = playing && super.hasPendingData()
        if (cut) fall()
        super.flush()
        if (cut) due = true
        if (due) hold() else whole()
    }

    override fun reset() {
        super.reset()
        playing = false
        due = true
        whole()
    }

    /** Silent until the fade in begins (or whole, with no fade in chosen). */
    private fun hold() {
        risingSince = -1L
        set(if (fadeInMs() > 0) 0f else 1f)
    }

    private fun whole() {
        risingSince = -1L
        set(1f)
    }

    private fun rise() {
        due = false
        if (fadeInMs() <= 0) return whole()
        risingSince = now()
        set(0f)
    }

    private fun step() {
        if (risingSince < 0) return
        val share = levelAt(now() - risingSince, fadeInMs())
        set(share)
        if (share >= 1f) risingSince = -1L
    }

    /** Down to silence over the fade out, waiting here for it: only with sound playing that would otherwise be cut. */
    private fun fall() {
        val ms = fadeOutMs()
        if (ms <= 0 || !playing || level <= 0f || !super.hasPendingData()) return
        risingSince = -1L
        val from = level
        val steps = (ms / STEP_MS).coerceAtLeast(1)
        for (i in 1..steps) {
            set(from * (1f - i.toFloat() / steps))
            sleep(STEP_MS.toLong())
        }
        AppLog.d("Engine", "Untouched: faded out over $ms ms")
    }

    private fun set(share: Float) {
        if (share == level) return
        level = share
        super.setVolume(wanted * level)
    }

    companion object {
        const val STEP_MS = 10

        /** The fade in's share [since] ms after it began, of [ms] in all. */
        internal fun levelAt(since: Long, ms: Int): Float = if (ms <= 0) 1f else (since.toFloat() / ms).coerceIn(0f, 1f)
    }
}
