package app.siansa

import app.siansa.data.Album
import app.siansa.mix.Rediscovery
import app.siansa.ui.HomeSection
import app.siansa.ui.homeRowOpensFullList
import app.siansa.ui.rediscoverSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Home rows whose heading opens the full list. */
class HomeSectionsTest {
    @Test fun onlyRowsWithMoreThanFourItemsOpenTheFullList() {
        assertFalse(homeRowOpensFullList(0))
        assertFalse(homeRowOpensFullList(4))
        assertTrue(homeRowOpensFullList(5))
        assertTrue(homeRowOpensFullList(20))
    }

    @Test fun everySectionHasItsOwnPage() {
        val ids = HomeSection.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        HomeSection.entries.forEach {
            assertEquals(it, HomeSection.byId(it.id))
            assertEquals("home/${it.id}", it.route)
        }
        assertEquals(HomeSection.Radios, HomeSection.byId("radios"))
        assertNull(HomeSection.byId("nope"))
    }

    @Test fun rediscoverSaysHowLongAgo() {
        val today = LocalDate.of(2026, 10, 16)
        val album = Album("a1", "Album", artist = "Someone")
        assertEquals("Last played 5 months ago", rediscoverSubtitle(Rediscovery(album, today.minusMonths(5), 3), today))
        assertEquals("Last played 1 year ago", rediscoverSubtitle(Rediscovery(album, today.minusMonths(14), 3), today))
        assertEquals("Last played 2 years ago", rediscoverSubtitle(Rediscovery(album, today.minusMonths(25), 3), today))
        assertEquals("Someone", rediscoverSubtitle(Rediscovery(album, null, 3), today))
    }
}
