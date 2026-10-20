package app.musix.mix

import app.musix.data.Album
import app.musix.data.AppJson
import app.musix.data.Song
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Cards of your own on Made for you (issue #274). */
class MadeForYouCardsTest {
    @Test fun newCardsGetASensibleName() {
        assertEquals("Not played in a while", CustomCard.new(CardKind.FORGOTTEN).title)
        assertEquals("Your Jazz", CustomCard.new(CardKind.GENRE, genre = "Jazz").title)
        assertEquals("Your 90s", CustomCard.new(CardKind.YEARS, fromYear = 1990, toYear = 1999).title)
        assertEquals("Your 00s", CustomCard.new(CardKind.YEARS, fromYear = 2000, toYear = 2009).title)
        assertEquals("Years", CustomCard.new(CardKind.YEARS, fromYear = 1985, toYear = 1995).title)
        assertEquals("1985–1995", CustomCard.new(CardKind.YEARS, fromYear = 1985, toYear = 1995).detail)
    }

    @Test fun aCardNeedsWhatItsKindNeeds() {
        assertNull(cardProblem(CustomCard.new(CardKind.RANDOM)))
        assertEquals("Give the card a name.", cardProblem(CustomCard.new(CardKind.RANDOM).copy(title = " ")))
        assertEquals("Pick a genre.", cardProblem(CustomCard.new(CardKind.GENRE)))
        assertEquals("Fill in both years.", cardProblem(CustomCard.new(CardKind.YEARS, fromYear = 1990)))
        assertEquals("The first year comes before the second.", cardProblem(CustomCard.new(CardKind.YEARS, fromYear = 2000, toYear = 1990), 2026))
        assertTrue(cardProblem(CustomCard.new(CardKind.YEARS, fromYear = 1800, toYear = 1990), 2026)!!.startsWith("Years run"))
    }

    @Test fun forgottenMeansPlayedBeforeButNotLately() {
        val cutoff = 1_760_000_000_000L // mid-October 2025
        val albums = listOf(
            Album("old", "Old favourite", played = "2025-01-01T10:00:00Z"),
            Album("new", "Played last week", played = "2026-10-12T10:00:00Z"),
            Album("never", "Never played"),
        )
        assertEquals(listOf("old"), forgottenAlbums(albums, cutoff).map { it.id })
    }

    @Test fun songsAreSpreadOverAlbums() {
        val byAlbum = (1..5).map { a -> (1..10).map { s -> Song("$a-$s", "Song $s", albumId = "$a") } }
        val picked = spreadOverAlbums(byAlbum, perAlbum = 4, size = 50, random = Random(1))
        assertEquals(20, picked.size)
        assertTrue(picked.groupBy { it.albumId }.values.all { it.size <= 4 })
        assertEquals(12, spreadOverAlbums(byAlbum, perAlbum = 4, size = 12, random = Random(1)).size)
    }

    @Test fun cardsAreSavedAndReadBack() {
        val cards = listOf(CustomCard.new(CardKind.GENRE, genre = "Rock"), CustomCard.new(CardKind.FORGOTTEN).copy(title = "Old gold"))
        val serializer = ListSerializer(CustomCard.serializer())
        assertEquals(cards, AppJson.decodeFromString(serializer, AppJson.encodeToString(serializer, cards)))
    }
}
