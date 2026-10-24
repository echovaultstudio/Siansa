package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** An artist's popular songs: three, and six with Show more (issue #328). */
class ArtistTopSongsTest {
    @Test fun threeAtFirstSixWithShowMore() {
        assertEquals(3, topRowsShown(10, expanded = false))
        assertEquals(6, topRowsShown(10, expanded = true))
    }

    @Test fun neverMoreThanThereAre() {
        assertEquals(2, topRowsShown(2, expanded = false))
        assertEquals(4, topRowsShown(4, expanded = true))
        assertEquals(0, topRowsShown(0, expanded = true))
    }
}
