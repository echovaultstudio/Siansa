package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** The arithmetic of a crossfade on the new audio engine (issue #826). */
class EngineFadeTest {
    private val outro = BeatEnd(bpm = 128.0, beatMs = 300_000.0, barMs = 300_000.0, rhythm = 0.9f, steady = 0.9f)
    private val intro = BeatEnd(bpm = 126.0, beatMs = 1_000.0, barMs = 1_000.0, rhythm = 0.9f, steady = 0.9f)

    @Test fun theOldSongIsHandedOverAFewSecondsBeforeTheBlend() {
        assertEquals(297_500L, EngineFade.handoverAt(300_000, 200_000))
        // Never sooner than a moment from now, nor after the blend starts.
        assertEquals(299_000L, EngineFade.handoverAt(300_000, 297_500))
        assertEquals(300_000L, EngineFade.handoverAt(300_000, 299_500))
    }

    @Test fun theNextSongComesInOnItsDownbeatWithTheBlendsStartOrAtItsStartWithTheFade() {
        val beat = BeatMatch.plan(outro, intro, 310_000, 16_000)!!
        val plan = FadePlan(beat.overlapMs, beat.overlapMs, beat.why, 0, beat)
        assertEquals(beat.incomingStartMs to beat.startMs, EngineFade.incomingCue(plan, 294_000))
        val plain = FadePlan(6_000, 2_000, "fixed", 0)
        assertEquals(0L to 304_000L, EngineFade.incomingCue(plain, 304_000))
    }

    @Test fun theBeatErrorIsWhereTheNextSongIsLessWhereItShouldBe() {
        val beat = BeatMatch.plan(outro, intro, 310_000, 16_000)!!
        val oldUs = (beat.startMs + 4_000) * 1000
        val dueMs = beat.incomingAt(beat.startMs + 4_000)
        assertEquals(0L, EngineFade.beatErrorMs(beat, oldUs, dueMs * 1000))
        assertEquals(12L, EngineFade.beatErrorMs(beat, oldUs, (dueMs + 12) * 1000))
        assertEquals(-7L, EngineFade.beatErrorMs(beat, oldUs, (dueMs - 7) * 1000))
    }
}
