package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A long artist name fades out before the title bar's heart and Play (the owner, 14 Nov). */
class HeroNameFadeTest {
    @Test fun atRestTheNameIsWhereItWasLaidOut() {
        assertEquals(0f, nameShift(120f, 0f), 0.001f)
        assertEquals(1f, nameScale(0.4f, 0f), 0.001f)
        assertEquals(1000f - 40f, nameFadeEnd(1000f, 40f, 0f, 1f), 0.001f)
    }

    @Test fun inTheBarTheFadeEndIsTheButtonsEdgeInTheNamesOwnPlace() {
        assertEquals(120f, nameShift(120f, 1f), 0.001f)
        assertEquals(0.4f, nameScale(0.4f, 1f), 0.001f)
        // The buttons start at 800 px on screen; the name, laid out from 40 and moved 120, is drawn at 0.4 of its size:
        // its own 1,600th pixel lands there.
        assertEquals(1600f, nameFadeEnd(800f, 40f, 120f, 0.4f), 0.001f)
    }
}
