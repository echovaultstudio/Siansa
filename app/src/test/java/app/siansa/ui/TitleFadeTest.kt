package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A long album title fades out before the title bar's heart and Play, only once it's in the bar (the owner, 14 Nov). */
class TitleFadeTest {
    @Test fun underTheCoverTheTitleIsWhole() {
        assertEquals(900f, titleFadeEnd(docked = 500f, whole = 900f, t = 0f), 0.001f)
    }

    @Test fun inTheBarItHasFadedOutBeforeTheButtons() {
        assertEquals(500f, titleFadeEnd(docked = 500f, whole = 900f, t = 1f), 0.001f)
    }

    @Test fun onTheWayTheFadeComesInWithIt() {
        val half = titleFadeEnd(docked = 500f, whole = 900f, t = 0.5f)
        assertEquals(700f, half, 0.001f)
    }

    @Test fun aTitleThatFitsTheBarIsNeverFaded() {
        listOf(0f, 0.5f, 1f).forEach { assertEquals(900f, titleFadeEnd(docked = 1200f, whole = 900f, t = it), 0.001f) }
    }
}
