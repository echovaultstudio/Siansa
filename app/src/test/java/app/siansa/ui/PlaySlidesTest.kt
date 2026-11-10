package app.siansa.ui

import app.siansa.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sliding from now playing's play button (issue #919). */
class PlaySlidesTest {
    @Test fun theDefaultsAreTheOwnersPicks() {
        val slides = PlaySlides()
        assertTrue(slides.on)
        assertTrue(slides.grooves)
        assertEquals(PlaySlide.DJ_MIX, slides.of(SlideWay.UP))
        assertEquals(PlaySlide.STEADY, slides.of(SlideWay.DOWN))
        assertEquals(PlaySlide.SONG_PATH, slides.of(SlideWay.LEFT))
        assertEquals(PlaySlide.SONIC_MATCH, slides.of(SlideWay.RIGHT))
    }

    @Test fun theLongerWayWins() {
        assertEquals(SlideWay.UP, slideWay(5f, -40f, 20f))
        assertEquals(SlideWay.DOWN, slideWay(-10f, 30f, 20f))
        assertEquals(SlideWay.LEFT, slideWay(-50f, 20f, 20f))
        assertEquals(SlideWay.RIGHT, slideWay(25f, 3f, 20f))
    }

    @Test fun backInTheMiddleIsNoWay() {
        assertNull(slideWay(0f, 0f, 20f))
        assertNull(slideWay(10f, -12f, 20f))
    }

    @Test fun distanceIsAlongTheWay() {
        assertEquals(40f, slideDistance(SlideWay.UP, 5f, -40f), 0f)
        assertEquals(-40f, slideDistance(SlideWay.DOWN, 5f, -40f), 0f)
        assertEquals(30f, slideDistance(SlideWay.LEFT, -30f, 2f), 0f)
        assertEquals(12f, slideDistance(SlideWay.RIGHT, 12f, 50f), 0f)
    }

    @Test fun eachWayCanBeChangedAndKept() {
        val changed = PlaySlides().with(SlideWay.UP, PlaySlide.START_A_MIX).with(SlideWay.LEFT, PlaySlide.NOTHING).copy(grooves = false)
        assertEquals(changed, PlaySlides.parse(changed.format()))
        assertEquals(PlaySlide.START_A_MIX, changed.of(SlideWay.UP))
        assertEquals(PlaySlide.STEADY, changed.of(SlideWay.DOWN))
        val off = PlaySlides(on = false)
        assertFalse(PlaySlides.parse(off.format()).on)
    }

    @Test fun nothingKeptOrSomethingUnknownIsTheDefault() {
        assertEquals(PlaySlides(), PlaySlides.parse(null))
        assertEquals(PlaySlides(), PlaySlides.parse(""))
        assertEquals(PlaySlides(), PlaySlides.parse("on;grooves;GONE;STEADY"))
    }

    @Test fun everyChoiceButNothingHasAnIconAndAName() {
        for (slide in PlaySlide.entries) {
            assertTrue(slide.label.isNotBlank())
            if (slide == PlaySlide.NOTHING) assertNull(slide.icon) else assertTrue(slide.icon != null)
        }
    }

    @Test fun songPathNeedsAudioMuseAndMixesWaitForIt() {
        val without = AppSettings()
        assertEquals("Needs AudioMuse", slideUnavailable(PlaySlide.SONG_PATH, without, "AudioMuse isn't set up."))
        // Without AudioMuse a Steady radio still starts, from the server's similar songs; a DJ mix doesn't (DjReady).
        assertNull(slideUnavailable(PlaySlide.STEADY, without, "AudioMuse isn't set up."))
        assertEquals("Needs AudioMuse", slideUnavailable(PlaySlide.DJ_MIX, without, "AudioMuse isn't set up.", "Needs AudioMuse"))
        assertNull(slideUnavailable(PlaySlide.SONIC_MATCH, without, "AudioMuse isn't set up."))
    }

    @Test fun withAudioMuseUnreachableNothingButSonicMatchRuns() {
        val ready = AppSettings(audioMuseUrl = "http://192.168.1.10:8000")
        assertNull(slideUnavailable(PlaySlide.SONG_PATH, ready, null))
        assertNull(slideUnavailable(PlaySlide.DJ_MIX, ready, null))
        for (slide in listOf(PlaySlide.SONG_PATH, PlaySlide.STEADY, PlaySlide.START_A_MIX)) {
            assertEquals("Not available now", slideUnavailable(slide, ready, "You're offline."))
        }
        assertEquals("Not available now", slideUnavailable(PlaySlide.DJ_MIX, ready, "You're offline.", "Not available now"))
        assertNull(slideUnavailable(PlaySlide.SONIC_MATCH, ready, "You're offline."))
    }
}
