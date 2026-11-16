package app.siansa.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // A cold end: the next song's start a pause after the old song's last sound (its tail, from the fade's start).
        val cold = FadePlan(400, 150, "cold", 0, coldEnd = true, gapMs = 600)
        assertEquals(0L to 305_000L, EngineFade.incomingCue(cold, 304_000))
        // Both keep a beat: no pause, straight on as the old song's last sound ends.
        assertEquals(0L to 304_400L, EngineFade.incomingCue(cold.copy(gapMs = 0), 304_000))
    }

    @Test fun theBeatErrorIsWhereTheNextSongIsLessWhereItShouldBe() {
        val beat = BeatMatch.plan(outro, intro, 310_000, 16_000)!!
        val oldUs = (beat.startMs + 4_000) * 1000
        val dueMs = beat.incomingAt(beat.startMs + 4_000)
        assertEquals(0L, EngineFade.beatErrorMs(beat, oldUs, dueMs * 1000))
        assertEquals(12L, EngineFade.beatErrorMs(beat, oldUs, (dueMs + 12) * 1000))
        assertEquals(-7L, EngineFade.beatErrorMs(beat, oldUs, (dueMs - 7) * 1000))
    }

    @Test fun aBlendThatNoLongerFitsBecomesABlendOfWhatsLeftNeverNone() {
        // Moved to 14 s before the end of a 300 s song: 14 - 2.5 (ready) - 2.5 (handover) = 9 s left to blend.
        val beat = BeatMatch.plan(outro, intro, 310_000, 16_000)!!
        val matched = FadePlan(16_000, 16_000, "smart: on the beat", 0, beat, technique = Technique.entries.first(), filterHz = 400.0)
        val left = EngineFade.whatsLeft(matched, 300_000, 286_000, 2_500, 1_500)!!
        assertEquals(9_000L, left.overlapMs)
        assertEquals(9_000L, left.fadeInMs)
        // Not laid on the beat any more, and a plain fade.
        assertNull(left.beat)
        assertNull(left.technique)
        assertEquals(0.0, left.filterHz, 0.0)
        // A plain one too, with its own fade-in kept when shorter.
        val plain = EngineFade.whatsLeft(FadePlan(12_000, 3_000, "fixed", 0), 300_000, 286_000, 2_500, 1_500)!!
        assertEquals(9_000L to 3_000L, plain.overlapMs to plain.fadeInMs)
        // Too close for even a short one, and a cold end, which is meant to have none: left as they are.
        assertNull(EngineFade.whatsLeft(matched, 300_000, 294_000, 2_500, 1_500))
        assertNull(EngineFade.whatsLeft(FadePlan(400, 150, "cold", 0, coldEnd = true), 300_000, 286_000, 2_500, 1_500))
    }
}
