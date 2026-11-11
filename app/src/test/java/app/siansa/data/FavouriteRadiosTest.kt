package app.siansa.data

import app.siansa.mix.CardLook
import app.siansa.mix.CardSource
import app.siansa.mix.PinnedCard
import app.siansa.mix.pinnedCardProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Favourite radios and cards of them, and the shortcut grid look (the owner, 10 Nov). */
class FavouriteRadiosTest {
    private fun radio(route: String, at: Long = 1) = FavouriteRadio(route, route.substringAfterLast('/'), 0xFF112233, 0xFF445566, at)

    @Test fun aHeartPutsTheRadioFirstAndAgainTakesItOut() {
        var list = FavouriteRadios.toggled(emptyList(), radio("radio/mood/happy"))
        list = FavouriteRadios.toggled(list, radio("blend/p1", 2))
        assertEquals(listOf("blend/p1", "radio/mood/happy"), list.map { it.route })
        list = FavouriteRadios.toggled(list, radio("radio/mood/happy", 3))
        assertEquals(listOf("blend/p1"), list.map { it.route })
    }

    @Test fun aCardOfAFavouriteRadio() {
        val card = PinnedCard.new("Happy").copy(source = CardSource.RADIO)
        assertEquals("Pick one of your favourite radios.", pinnedCardProblem(card, audioMuseReady = true))
        val picked = card.copy(radio = "radio/mood/happy", radioTitle = "Happy radio")
        assertNull(pinnedCardProblem(picked, audioMuseReady = true))
        assertEquals("Radio: Happy radio", picked.detail)
        assertTrue(pinnedCardProblem(picked.copy(contents = app.siansa.mix.CardContents.ALBUMS), true).orEmpty().contains("Albums"))
    }

    @Test fun aGridCardHasTwoToEightTiles() {
        val card = PinnedCard.new("Grid").copy(look = CardLook.GRID, genre = "Jazz")
        assertNull(pinnedCardProblem(card, audioMuseReady = false))
        assertEquals(8, card.gridSize)
        assertTrue(pinnedCardProblem(card.copy(gridSize = 9), false).orEmpty().contains("2 to 8"))
        assertTrue(pinnedCardProblem(card.copy(gridSize = 1), false).orEmpty().contains("2 to 8"))
    }

    @Test fun homeRowsKeepTheirGridSize() {
        val sizes = mapOf(HomeSection.RECENT to 4, HomeSection.GENRES to 8)
        assertEquals(sizes, HomeLook.parseGridSizes(HomeLook.formatGridSizes(sizes)))
        assertEquals(mapOf(HomeSection.RECENT to 6), HomeLook.parseGridSizes("recent:6,genres:12,nope:4,added:x"))
        assertEquals(mapOf(HomeSection.RECENT to HomeLook.GRID), HomeLook.parse("recent:grid"))
    }
}
