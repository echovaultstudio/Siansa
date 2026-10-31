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

    @Test fun yourCardsKeepTheirOwnLooks() {
        assertFalse(HomeSection.CARDS.lookable)
        assertTrue(HomeSection.entries.filter { it != HomeSection.CARDS }.all { it.lookable })
        assertTrue(HomeLook.parse("cards:banner").isEmpty())
    }

    @Test fun theLooksAreTheCustomCardsLooks() {
        assertEquals(listOf("As it is", "Tiles", "Banner", "List", "Play button"), HomeLook.entries.map { it.label })
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
}
