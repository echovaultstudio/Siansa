package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Home row's cover grid (#1263): its sizes, how it's saved, and what fills it. */
class CoverGridTest {
    @Test fun theGridsTheOwnerAskedFor() {
        // The owner, 19 Nov: "2x2 or 3x3 or 2x4 3x6".
        assertEquals(listOf("2x2", "3x3", "2x4", "3x6"), CoverGrid.entries.map { it.id })
        assertEquals(listOf(4, 9, 8, 18), CoverGrid.entries.map { it.count })
        // 2×4 and 3×6 make the card twice as wide, so each cover stays square.
        assertEquals(listOf(1f, 1f, 2f, 2f), CoverGrid.entries.map { it.aspect })
        assertEquals("3×6", CoverGrid.THREE_WIDE.label)
    }

    @Test fun savedPerRowUnknownOnesDropped() {
        val grids = mapOf(HomeSection.RECENT to CoverGrid.THREE, HomeSection.ARTIST_RADIOS to CoverGrid.TWO_WIDE, HomeSection.CARDS to CoverGrid.TWO)
        val saved = CoverGrid.format(grids)
        assertEquals(grids, CoverGrid.parse(saved))
        assertEquals(mapOf(HomeSection.QUICK to CoverGrid.THREE_WIDE), CoverGrid.parse("quick:3x6,nowhere:2x2,recent:5x5,broken"))
        assertTrue(CoverGrid.parse(null).isEmpty())
    }

    @Test fun eachCoverOnceThenRoundAgainToFillIt() {
        assertEquals(listOf("a", "b", "c", "d"), CoverGrid.cells(listOf("a", "b", null, "a", "c", "d", "e"), 4))
        assertEquals(listOf("a", "b", "a", "b"), CoverGrid.cells(listOf("a", "b"), 4))
        // One picture or none is no grid: the card shows its own.
        assertTrue(CoverGrid.cells(listOf("a", "a", null), 4).isEmpty())
        assertTrue(CoverGrid.cells(emptyList(), 9).isEmpty())
    }

    @Test fun anAlbumsGridIsItsCoverThenItsArtistsOtherAlbums() {
        val album = Album("1", "First", "Artist", coverArt = "al-1")
        val others = listOf(Album("2", "Second", coverArt = "al-2"), album, Album("3", "Third", coverArt = null), Album("4", "Fourth", coverArt = "al-4"))
        assertEquals(listOf("al-1", "al-2", "al-4"), app.siansa.ui.albumGridCovers(album, others))
    }
}
