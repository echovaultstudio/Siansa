package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Home's menu (issue #872): the glide's hit test and outcome, Play on…'s and Sources' lines, and the dice's faces. */
class HomeMenuLogicTest {
    private val rows = linkedMapOf(
        HomeMenuItem.PLAY_ON to MenuBounds(100f, 200f, 400f, 280f),
        HomeMenuItem.SOURCES to MenuBounds(100f, 300f, 400f, 360f),
        HomeMenuItem.ARRANGE to MenuBounds(100f, 360f, 400f, 420f),
    )

    @Test fun theRowUnderTheFingerIsFound() {
        assertEquals(HomeMenuItem.PLAY_ON, menuRowAt(250f, 240f, rows))
        assertEquals(HomeMenuItem.SOURCES, menuRowAt(101f, 330f, rows))
        assertEquals(HomeMenuItem.ARRANGE, menuRowAt(399f, 419f, rows))
    }

    @Test fun nothingBetweenRowsOrOutsideTheMenu() {
        // The divider between Play on… and Sources.
        assertNull(menuRowAt(250f, 290f, rows))
        // Left of the menu, above it and below it.
        assertNull(menuRowAt(50f, 330f, rows))
        assertNull(menuRowAt(250f, 150f, rows))
        assertNull(menuRowAt(250f, 500f, rows))
        assertNull(menuRowAt(250f, 330f, emptyMap<HomeMenuItem, MenuBounds>()))
    }

    @Test fun aPressCountsAsAGlideOnlyPastTheTouchSlop() {
        assertFalse(glideMoved(3f, 4f, 8f))
        assertFalse(glideMoved(0f, 8f, 8f))
        assertTrue(glideMoved(0f, 9f, 8f))
        assertTrue(glideMoved(-7f, -7f, 8f))
    }

    @Test fun lettingGoPicksTheRowClosesOrStaysOpen() {
        // A plain tap: the menu stays open for its rows to be tapped.
        assertEquals(GlideEnd.STAY_OPEN, glideEnd(moved = false, row = null))
        assertEquals(GlideEnd.STAY_OPEN, glideEnd(moved = false, row = HomeMenuItem.SOURCES))
        // A glide let go on a row opens it; anywhere else closes the menu.
        assertEquals(GlideEnd.PICK, glideEnd(moved = true, row = HomeMenuItem.SOURCES))
        assertEquals(GlideEnd.CLOSE, glideEnd(moved = true, row = null))
    }

    @Test fun playOnSaysWhereTheMusicPlays() {
        assertEquals(PlayOnLine("Play on…", "TV or speaker", false), playOnLine(null, false, null, false))
        assertEquals(PlayOnLine("Playing on SHIELD", "Chromecast", true), playOnLine("SHIELD", false, null, false))
        assertEquals(PlayOnLine("Playing on SHIELD", "Connecting…", true), playOnLine("SHIELD", true, null, false))
        assertEquals(PlayOnLine("Connecting…", "Chromecast", true), playOnLine(null, true, null, false))
        assertEquals(PlayOnLine("Playing on JBL BAR", "DLNA", true), playOnLine(null, false, "JBL BAR", false))
        assertEquals(PlayOnLine("Playing on JBL BAR", "Connecting…", true), playOnLine(null, false, "JBL BAR", true))
        assertEquals(PlayOnLine("Connecting…", "DLNA", true), playOnLine(null, false, null, true))
    }

    @Test fun sourcesSaysWhatTheFilterShows() {
        val all = setOf("1", "2", "3")
        assertEquals("All libraries", sourcesSummary(false, 1, emptySet(), all))
        assertEquals("All libraries", sourcesSummary(false, 1, all, all))
        assertEquals("All libraries", sourcesSummary(false, 1, emptySet(), emptySet()))
        assertEquals("Downloaded only", sourcesSummary(true, 1, emptySet(), all))
        assertEquals("2 of 3 libraries", sourcesSummary(false, 1, setOf("1", "3"), all))
        // A library no longer on the server doesn't count.
        assertEquals("1 of 3 libraries", sourcesSummary(false, 1, setOf("1", "gone"), all))
        assertEquals("2 users", sourcesSummary(false, 2, emptySet(), all))
        assertEquals("Downloaded only · 2 users · 2 of 3 libraries", sourcesSummary(true, 2, setOf("1", "2"), all))
    }

    @Test fun everyFaceHasItsPips() {
        (1..6).forEach { face ->
            val pips = dicePips(face)
            assertEquals(face, pips.size)
            assertEquals("face $face has no pip twice", pips.size, pips.toSet().size)
            pips.forEach { (x, y) -> assertTrue(x in 0f..1f && y in 0f..1f) }
        }
        assertEquals(listOf(0.5f to 0.5f), dicePips(1))
        // Odd faces have the middle pip, even ones don't.
        (1..6).forEach { assertEquals(it % 2 == 1, (0.5f to 0.5f) in dicePips(it)) }
    }

    @Test fun theNextFaceIsAlwaysAnotherOne() {
        val random = Random(7)
        (1..6).forEach { current ->
            val seen = (1..200).map { nextFace(current, random) }.toSet()
            assertFalse(current in seen)
            assertEquals((1..6).toSet() - current, seen)
        }
        assertNotEquals(3, nextFace(3))
    }

    @Test fun theDieLandsFlatAhead() {
        assertEquals(720f, landingAngle(700f, 180f), 0f)
        assertEquals(720f, landingAngle(720f, 180f), 0f)
        assertEquals(180f, landingAngle(0.5f, 180f), 0f)
        assertEquals(180f, landingAngle(135f, 90f), 0f)
    }

    @Test fun animationsOffOnlyAtZero() {
        assertTrue(animationsOff(0f))
        assertFalse(animationsOff(0.5f))
        assertFalse(animationsOff(1f))
    }
}
