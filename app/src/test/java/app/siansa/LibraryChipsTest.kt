package app.siansa

import app.siansa.ui.LibraryChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun theMainChipsShrinkJustEnoughToFit() {
        // Five chips, 314 px of text at full size, 16 px padding each side, 8 px gaps: 506 px.
        val texts = listOf(62f, 42f, 40f, 72f, 98f)
        fun width(f: LibraryChips.Fit) = 314f * f.text + 10 * f.padding + 4 * f.gap
        assertEquals(LibraryChips.Fit(1f, 16f, 8f), LibraryChips.fit(texts, 16f, 8f, 8f, 4f, 600f))
        // A bit too wide: only the room around and between shrinks, the text stays.
        val roomy = LibraryChips.fit(texts, 16f, 8f, 8f, 4f, 450f)
        assertEquals(1f, roomy.text, 0.001f)
        assertEquals(450f, width(roomy), 0.5f)
        // A 360 dp phone (328 between the edges): then the text, still readable.
        val small = LibraryChips.fit(texts, 16f, 8f, 8f, 4f, 328f)
        assertEquals(328f, width(small), 0.5f)
        assertTrue(small.text >= LibraryChips.MIN_SCALE)
        // Never smaller than that: a row still too wide scrolls instead.
        assertEquals(LibraryChips.MIN_SCALE, LibraryChips.fit(texts, 16f, 8f, 8f, 4f, 100f).text, 0.001f)
    }
}
