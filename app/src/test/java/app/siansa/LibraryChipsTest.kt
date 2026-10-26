package app.siansa

import app.siansa.ui.LibraryChips
import org.junit.Assert.assertEquals
import org.junit.Test

/** Your Library's chips in the order last opened, and a sort kept per page (issue #497). */
class LibraryChipsTest {
    private val all = listOf("FAVOURITES", "PLAYLIST", "MIX", "MUSIC", "LISTENING")

    @Test fun beforeAnyIsOpenedTheDefaultOrder() {
        assertEquals(LibraryChips.DEFAULT, LibraryChips.order(emptyList(), all))
    }

    @Test fun theLastOpenedComesFirst() {
        var used = LibraryChips.used(emptyList(), "LISTENING")
        used = LibraryChips.used(used, "MIX")
        used = LibraryChips.used(used, "LISTENING")
        assertEquals(listOf("LISTENING", "MIX"), used)
        assertEquals(listOf("LISTENING", "MIX", "PLAYLIST", "MUSIC", "FAVOURITES"), LibraryChips.order(used, all))
    }

    @Test fun chipsThatNoLongerExistAreLeftOut() {
        // "ALBUM" was a chip before Music (issue #497).
        assertEquals(listOf("MUSIC", "PLAYLIST", "MIX", "FAVOURITES", "LISTENING"), LibraryChips.order(listOf("ALBUM", "MUSIC"), all))
    }

    @Test fun theOrderAndSortsAreStoredAndReadBack() {
        val order = listOf("MIX", "MUSIC")
        assertEquals(order, LibraryChips.parse(LibraryChips.format(order)))
        assertEquals(emptyList<String>(), LibraryChips.parse(null))
        val sorts = mapOf("ALL" to "RECENTS", "MUSIC" to "ALPHABETICAL", "PLAYLIST" to "ADDED")
        assertEquals(sorts, LibraryChips.parseSorts(LibraryChips.formatSorts(sorts)))
        assertEquals(mapOf("MUSIC" to "PLAYS"), LibraryChips.parseSorts("MUSIC=PLAYS;broken;=X;Y="))
    }

    @Test fun eachPageHasItsOwnSortKey() {
        assertEquals("ALL", LibraryChips.sortKey(null))
        assertEquals("MUSIC", LibraryChips.sortKey("MUSIC"))
    }
}
