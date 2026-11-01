package app.siansa.data

import app.siansa.ui.homeButtonCaption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's rows in other looks (issue #655): what's saved, what can change, what the slim card says. */
class HomeLookTest {
    @Test fun looksSaveAndLoadAsTheyWere() {
        val looks = mapOf(HomeSection.RECENT to HomeLook.LIST, HomeSection.ADDED to HomeLook.BANNER, HomeSection.TODAY to HomeLook.BUTTON)
        val saved = HomeLook.format(looks)
        assertEquals("today:button,recent:list,added:banner", saved)
        assertEquals(looks, HomeLook.parse(saved))
    }

    @Test fun unknownSectionsLooksAndAsItIsAreDropped() {
        assertEquals(mapOf(HomeSection.GENRES to HomeLook.TILES), HomeLook.parse("genres:tiles,nonsense:list,radios:sparkly,added:as_is,broken"))
        assertTrue(HomeLook.parse(null).isEmpty())
        assertTrue(HomeLook.parse("").isEmpty())
        assertEquals("", HomeLook.format(mapOf(HomeSection.RECENT to HomeLook.AS_IS)))
    }

    @Test fun yourCardsAndNoticesKeepTheirOwnLooks() {
        assertFalse(HomeSection.CARDS.lookable)
        // Notices are always a row of chips (issue #784).
        assertFalse(HomeSection.NOTICES.lookable)
        assertTrue(HomeSection.entries.filter { it != HomeSection.CARDS && it != HomeSection.NOTICES }.all { it.lookable })
        assertTrue(HomeLook.parse("cards:banner").isEmpty())
        assertTrue(HomeLook.parse("notices:list").isEmpty())
    }

    @Test fun theLooksAreTheCustomCardsLooks() {
        assertEquals(listOf("As it is", "Tiles", "Banner", "List", "Play button", "Cover rows"), HomeLook.entries.map { it.label })
    }

    @Test fun theSlimCardSaysWhatATapDoes() {
        // The card opens; only its ▶ plays (issue #730).
        assertEquals("Press play, or tap to open", homeButtonCaption(plays = true, playing = false, count = 12))
        assertEquals("Playing", homeButtonCaption(plays = true, playing = true, count = 12))
        assertEquals("Tap to open · 12", homeButtonCaption(plays = false, playing = false, count = 12))
    }

    @Test fun aCustomPlayButtonCardSaysItsSongs() {
        assertEquals("24 songs", app.siansa.ui.pinnedButtonCaption(playing = false, count = "24 songs"))
        assertEquals("Playing · 24 songs", app.siansa.ui.pinnedButtonCaption(playing = true, count = "24 songs"))
        assertEquals("Tap to open", app.siansa.ui.pinnedButtonCaption(playing = false, count = null))
    }

    /** Cover rows on Home (issue #789): a Home item becomes a row, colours standing in where there's no cover. */
    @Test fun aHomeItemBecomesACoverRow() {
        val album = app.siansa.ui.HomeItem("a", "Romance", "Fontaines D.C.", "cover-1", open = {}, play = {}, meta = "11 songs · 2024")
        val row = app.siansa.ui.coverRowOf(album)
        assertEquals("Fontaines D.C.", row.small)
        assertEquals("Romance", row.title)
        assertEquals("11 songs · 2024", row.meta)
        assertEquals("cover-1", row.cover)
        assertEquals(null, row.colors)
        val radio = app.siansa.ui.HomeItem("r", "Melancholy radio", colors = androidx.compose.ui.graphics.Color(0xFF4B2BD6) to androidx.compose.ui.graphics.Color(0xFF8FD3C8), open = {})
        val block = app.siansa.ui.coverRowOf(radio)
        assertEquals(null, block.cover)
        assertEquals(0xFF4B2BD6L to 0xFF8FD3C8L, block.colors)
        val artist = app.siansa.ui.HomeItem("x", "Someone radio", round = true, coverArt = "c", open = {}, progress = 0.4f)
        assertEquals(true, app.siansa.ui.coverRowOf(artist).round)
        assertEquals(0.4f, app.siansa.ui.coverRowOf(artist).progress)
    }
}
