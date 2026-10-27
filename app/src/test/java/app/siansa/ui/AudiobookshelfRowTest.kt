package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Audiobookshelf row on Settings › Users (#582): the login on top, the kind of server under it. */
class AudiobookshelfRowTest {
    @Test fun theFirstLineIsTheLogin() {
        assertEquals("max", absTitle("max", "", "http://abs"))
        assertEquals("API token", absTitle("", "tok", "http://abs"))
        assertEquals("Audiobookshelf", absTitle("", "", ""))
        assertEquals("Audiobookshelf", absTitle("max", "", ""))
    }

    @Test fun theSecondLineSaysWhenItIsntSetUp() {
        assertEquals("Audiobookshelf", absLine(ready = true))
        assertEquals("Audiobookshelf · Not set up yet", absLine(ready = false))
    }
}
