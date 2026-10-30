package app.siansa

import app.siansa.ui.FULL_FADE
import app.siansa.ui.fadeScroll
import org.junit.Assert.assertEquals
import org.junit.Test

/** The cover's fade on album, playlist and mix pages (issue #682). */
class CollectionFadeTest {
    private val cover = 600f
    private val full = cover * FULL_FADE

    @Test fun aLongPageFadesAsItScrolls() {
        assertEquals(300f, fadeScroll(300f, cover, null), 0.01f)  // end not in view yet
        assertEquals(300f, fadeScroll(300f, cover, 2000f), 0.01f)
    }

    @Test fun aShortPageFadesFullyAtItsEnd() {
        assertEquals(full, fadeScroll(400f, cover, 400f), 0.01f)  // scrolled to the end: fully faded
        assertEquals(full / 2, fadeScroll(200f, cover, 400f), 0.01f)  // halfway: halfway
        assertEquals(0f, fadeScroll(0f, cover, 400f), 0.01f)
    }

    @Test fun aPageThatCantScrollDoesntFade() {
        assertEquals(0f, fadeScroll(0f, cover, 0f), 0.01f)
    }
}
