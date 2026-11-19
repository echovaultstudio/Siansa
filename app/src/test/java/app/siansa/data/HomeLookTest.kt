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
        assertEquals(listOf("As it is", "Tiles", "Banner", "List", "Play button", "Cover rows", "Shortcut grid", "Carousel"), HomeLook.entries.map { it.label })
    }

    @Test fun moreOfYouIsARowOfItsOwnAfterMadeForYou() {
        // The owner, 17 Nov: Made for you and More of you apart. A saved order from before gets it after Made for you.
        val order = HomeSection.parse("quick,made,radios")
        assertEquals(order.indexOf(HomeSection.MADE_FOR_YOU) + 1, order.indexOf(HomeSection.MORE_OF_YOU))
        assertTrue(HomeSection.MORE_OF_YOU.lookable)
        // Rearranged while it isn't listed (Siansa Beats' mixes off): it keeps its place.
        val before = listOf(HomeSection.QUICK, HomeSection.RADIOS, HomeSection.MORE_OF_YOU, HomeSection.MADE_FOR_YOU)
        assertEquals(listOf(HomeSection.RADIOS, HomeSection.MORE_OF_YOU, HomeSection.QUICK, HomeSection.MADE_FOR_YOU),
            app.siansa.ui.keepUnlisted(listOf(HomeSection.RADIOS, HomeSection.QUICK, HomeSection.MADE_FOR_YOU), before))
    }

    @Test fun siansaBeatsRowsTakeEveryLookTheCarouselBeingTheirOwn() {
        // #1261 (the owner, 19 Nov: every row gets every option): it was the carousel and Tiles only.
        listOf(null, HomeLook.AS_IS, HomeLook.CAROUSEL).forEach { assertEquals("$it", HomeLook.CAROUSEL, app.siansa.ui.beatsLook(it)) }
        (HomeLook.entries - HomeLook.AS_IS).forEach { assertEquals("$it", it, app.siansa.ui.beatsLook(it)) }
        assertEquals(setOf(HomeSection.CAROUSEL, HomeSection.MADE_FOR_YOU, HomeSection.MORE_OF_YOU), app.siansa.ui.beatsRows(true).keys)
    }

    @Test fun shortcutTilesArePlainAsHomeEveryOtherRowTakesHomesColour() {
        // #1261: Shortcuts has a colour now; As Home keeps its tiles as they were.
        assertEquals(CardLook.PLAIN, app.siansa.ui.asHomeCardLook(HomeSection.QUICK, CardLook.VIVID))
        assertEquals(CardLook.SOFT, app.siansa.ui.asHomeCardLook(HomeSection.RECENT, CardLook.SOFT))
        assertEquals(mapOf(HomeSection.CARDS to CardLook.SOFT, HomeSection.QUICK to CardLook.ARTWORK), CardLook.parse("cards:soft,quick:artwork"))
    }

    @Test fun anyRowCanBeACarousel() {
        // The owner, 17 Nov: every look for every row, the carousel too.
        val looks = mapOf(HomeSection.RECENT to HomeLook.CAROUSEL, HomeSection.CAROUSEL to HomeLook.CAROUSEL)
        assertEquals("carousel:carousel,recent:carousel", HomeLook.format(looks))
        assertEquals(looks, HomeLook.parse(HomeLook.format(looks)))
        val item = app.siansa.ui.HomeItem("al-1", "Hell Is Just a Halfway House", subtitle = "", coverArt = "al-1", meta = "11 songs · 2024", open = {})
        val sleeve = app.siansa.ui.crateRecordOf(item)
        assertEquals(listOf("al-1"), sleeve.covers)
        assertEquals("11 songs · 2024", sleeve.caption)  // no small line: the third
        assertEquals("Radio", app.siansa.ui.crateRecordOf(item.copy(subtitle = "Radio")).caption)
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
