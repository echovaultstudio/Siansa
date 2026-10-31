package app.siansa

import app.siansa.data.DailyMix
import app.siansa.data.Selector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's mix's own settings over the vibe's (issue #776). */
class DailyMixTest {
    private val vibe = Selector("evening", "Evening", 18, 23, "g", "q", length = 40, repeatDays = 3, leaveOutTopPlayed = 50, surprise = 30)

    @Test fun nothingSetLeavesTheVibeAsItIs() {
        val daily = DailyMix()
        assertFalse(daily.tuned)
        assertEquals(vibe, daily.applyTo(vibe))
        assertTrue(daily.autoUpdate)
    }

    @Test fun eachSettingReplacesOnlyItsOwn() {
        val daily = DailyMix(length = 20, surprise = 70)
        assertTrue(daily.tuned)
        val tuned = daily.applyTo(vibe)
        assertEquals(20, tuned.length)
        assertEquals(70, tuned.surprise)
        assertEquals(50, tuned.leaveOutTopPlayed)  // the vibe's own
        assertEquals(3, tuned.repeatDays)
        assertEquals("Evening", tuned.name)
    }

    @Test fun valuesAreKeptToTheirRanges() {
        val c = DailyMix(length = 500, surprise = -5, leaveOutTopPlayed = 200, repeatDays = 99).clean()
        assertEquals(DailyMix.LENGTHS.last(), c.length)
        assertEquals(0, c.surprise)
        assertEquals(80, c.leaveOutTopPlayed)
        assertEquals(30, c.repeatDays)
        assertEquals(null, DailyMix().clean().length)
    }
}
