package app.siansa.tvapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** The TV artist page's top songs, folded (issue #943). */
class TvTopSongsTest {
    @Test
    fun threeUntilOpened() {
        assertEquals(3, tvTopShown(10, open = false))
        assertEquals(10, tvTopShown(10, open = true))
        assertEquals(2, tvTopShown(2, open = false))
        assertEquals(0, tvTopShown(0, open = false))
    }
}
