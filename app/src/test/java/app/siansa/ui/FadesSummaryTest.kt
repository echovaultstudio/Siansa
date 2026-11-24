package app.siansa.ui

import app.siansa.data.AppSettings
import app.siansa.data.fadeChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class FadesSummaryTest {
    @Test fun theSectionSaysEachFadesLength() {
        assertEquals("In 250 ms · out 150 ms", fadesSummary(AppSettings()))
        assertEquals("In off · out 100 ms", fadesSummary(AppSettings(fadeInMs = 0, fadeOutMs = 100)))
        assertEquals("Off", fadesSummary(AppSettings(fadeInMs = 0, fadeOutMs = 0)))
        // With Untouched playback too (issue #1370).
        assertEquals("In 250 ms · out 150 ms", fadesSummary(AppSettings(untouchedPlayback = true)))
    }

    @Test fun aLengthIsOneOfTheChoices() {
        assertEquals(250, fadeChoice(250))
        assertEquals(0, fadeChoice(-20))
        assertEquals(600, fadeChoice(5_000))
        assertEquals(150, fadeChoice(160))
        assertEquals(listOf(0, 50, 100, 150, 250, 400, 600), AppSettings.FADE_CHOICES)
    }
}
