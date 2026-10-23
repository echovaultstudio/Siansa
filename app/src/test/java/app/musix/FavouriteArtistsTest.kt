package app.musix

import app.musix.data.Artist
import app.musix.data.favouriteArtistList
import org.junit.Assert.assertEquals
import org.junit.Test

class FavouriteArtistsTest {
    @Test fun listsFavouritesByNameWithTheSyncedCopy() {
        val fresh = listOf(Artist("b", "Burial"), Artist("a", "Ångström"), Artist("c", "caribou"), Artist("b", "Burial"))
        val local = mapOf("c" to Artist("c", "Caribou", albumCount = 7, coverArt = "ar-c"))
        val shown = favouriteArtistList(fresh, setOf("a", "b", "c"), local)
        assertEquals(listOf("a", "b", "c"), shown.map { it.id })
        // The synced library's copy wins: its picture and album count.
        assertEquals(7, shown.last().albumCount)
    }

    @Test fun aHeartJustTakenOffGoesAtOnce() {
        val fresh = listOf(Artist("a", "Air"), Artist("b", "Burial"))
        assertEquals(listOf("b"), favouriteArtistList(fresh, setOf("b"), emptyMap()).map { it.id })
    }
}
