package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A held row near the edge of Arrange Home's list scrolls it (issue #701). */
class AutoScrollTest {
    private val edge = 100f

    @Test fun inTheMiddleNothingScrolls() {
        assertEquals(0f, autoScrollSpeed(top = 300f, bottom = 400f, viewportStart = 0f, viewportEnd = 1000f, edge = edge), 0f)
    }

    @Test fun nearTheBottomItScrollsDownFasterTheDeeper() {
        val shallow = autoScrollSpeed(top = 820f, bottom = 920f, viewportStart = 0f, viewportEnd = 1000f, edge = edge)
        val deep = autoScrollSpeed(top = 880f, bottom = 980f, viewportStart = 0f, viewportEnd = 1000f, edge = edge)
        assertTrue(shallow > 0f && deep > shallow)
        assertEquals(AUTO_SCROLL_MAX, autoScrollSpeed(top = 950f, bottom = 1100f, viewportStart = 0f, viewportEnd = 1000f, edge = edge), 0.001f)
    }

    @Test fun nearTheTopItScrollsUp() {
        assertTrue(autoScrollSpeed(top = 20f, bottom = 120f, viewportStart = 0f, viewportEnd = 1000f, edge = edge) < 0f)
    }
}
