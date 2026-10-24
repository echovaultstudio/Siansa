package app.siansa

import app.siansa.mix.SuggestedArtist
import app.siansa.mix.SuggestionRanking
import app.siansa.net.ApiException
import app.siansa.net.DeezerApi
import app.siansa.ui.artistFacts
import app.siansa.ui.shortCount
import app.siansa.ui.similarToText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Artists you might like": ranking similar artists and reading Deezer's answers. */
class ArtistSuggestionsTest {
    private val none: (String) -> Boolean = { false }

    @Test fun anArtistSimilarToSeveralYouLikeComesFirst() {
        val ranked = SuggestionRanking.rank(
            listOf("Rammstein" to listOf("Oomph!", "Eisbrecher"), "Megaherz" to listOf("Eisbrecher")),
            seeds = listOf("Rammstein", "Megaherz"),
            inLibrary = none,
            limit = 10,
        )
        assertEquals(listOf("Eisbrecher", "Oomph!"), ranked.map { it.name })
        assertEquals(listOf("Rammstein", "Megaherz"), ranked[0].because)
    }

    @Test fun moreLikedAndMoreSimilarRankHigher() {
        val ranked = SuggestionRanking.rank(
            listOf("A" to (1..10).map { "A$it" }, "B" to listOf("B1")),
            seeds = listOf("A", "B"),
            inLibrary = none,
            limit = 20,
        ).map { it.name }
        // Most like the artist you play most comes first; a close match to your second artist
        // beats a distant match to your first.
        assertEquals("A1", ranked.first())
        assertTrue(ranked.indexOf("B1") < ranked.indexOf("A10"))
    }

    @Test fun artistsYouHaveAndYouLikeAreLeftOut() {
        val ranked = SuggestionRanking.rank(
            listOf("Björk" to listOf("Sigur Rós", "bjork", "Múm", "Portishead")),
            seeds = listOf("Björk", "Portishead"),
            inLibrary = { it == "mum" },
            limit = 10,
        )
        assertEquals(listOf("Sigur Rós"), ranked.map { it.name })
    }

    @Test fun theSameSeedListingAnArtistTwiceCountsOnce() {
        // Navidrome and Deezer both say Oomph! is like Rammstein: still one reason, not two.
        val ranked = SuggestionRanking.rank(
            listOf("Rammstein" to listOf("Oomph!"), "Rammstein" to listOf("OOMPH!", "Eisbrecher"), "Megaherz" to listOf("Eisbrecher")),
            seeds = listOf("Rammstein", "Megaherz"),
            inLibrary = none,
            limit = 10,
        )
        assertEquals(listOf("Eisbrecher", "Oomph!"), ranked.map { it.name })
        assertEquals(listOf("Rammstein"), ranked[1].because)
    }

    @Test fun rankingStopsAtTheLimit() {
        val ranked = SuggestionRanking.rank(listOf("A" to (1..30).map { "Artist $it" }), listOf("A"), none, limit = 5)
        assertEquals(5, ranked.size)
        assertEquals("Artist 1", ranked.first().name)
    }

    @Test fun deezerArtistsAreRead() {
        val json = """{"data":[
            {"id":464,"name":"Rammstein","link":"https://www.deezer.com/artist/464",
             "picture_medium":"https://e-cdns-images.dzcdn.net/images/artist/abc/250x250-000000-80-0-0.jpg",
             "picture_big":"https://e-cdns-images.dzcdn.net/images/artist/abc/500x500-000000-80-0-0.jpg",
             "nb_album":59,"nb_fan":5123456,"type":"artist"},
            {"id":7,"name":"No Photo","picture_big":"https://e-cdns-images.dzcdn.net/images/artist//500x500-000000-80-0-0.jpg","nb_album":1,"nb_fan":3},
            {"name":"No id"}
        ],"total":3}"""
        val artists = DeezerApi.parseArtists(json)
        assertEquals(2, artists.size)
        val r = artists[0]
        assertEquals(464L, r.id)
        assertEquals("https://e-cdns-images.dzcdn.net/images/artist/abc/500x500-000000-80-0-0.jpg", r.photo)
        assertEquals(59, r.albums)
        assertEquals(5123456, r.fans)
        assertEquals("https://www.deezer.com/artist/464", r.link)
        assertNull(artists[1].photo)
    }

    @Test fun deezerErrorsAndNoResults() {
        assertThrows(ApiException::class.java) { DeezerApi.parseArtists("""{"error":{"type":"DataException","message":"no data","code":800}}""") }
        assertThrows(ApiException::class.java) { DeezerApi.parseArtists("<html>") }
        assertTrue(DeezerApi.parseArtists("""{"data":[],"total":0}""").isEmpty())
    }

    @Test fun placeholderPhotosAreNotPhotos() {
        assertTrue(DeezerApi.isRealPhoto("https://e-cdns-images.dzcdn.net/images/artist/9f2/500x500-000000-80-0-0.jpg"))
        assertFalse(DeezerApi.isRealPhoto("https://e-cdns-images.dzcdn.net/images/artist//500x500-000000-80-0-0.jpg"))
        assertFalse(DeezerApi.isRealPhoto("http://example.org/a.jpg"))
    }

    @Test fun searchOnlyAcceptsTheSameName() {
        val found = DeezerApi.parseArtists(
            """{"data":[{"id":1,"name":"Sigur Ros Tribute"},{"id":2,"name":"Sigur Rós"}]}""",
        )
        assertEquals(2L, DeezerApi.bestMatch("sigur ros", found)?.id)
        assertNull(DeezerApi.bestMatch("Sigur", found))
    }

    @Test fun factsAndReasonsReadPlainly() {
        assertEquals("12 albums · 1.2M fans on Deezer", artistFacts(SuggestedArtist("X", "p", albums = 12, fans = 1_234_567)))
        assertEquals("1 album on Deezer", artistFacts(SuggestedArtist("X", "p", albums = 1)))
        assertNull(artistFacts(SuggestedArtist("X", "p")))
        assertNull(similarToText(emptyList()))
        assertEquals("Similar to A", similarToText(listOf("A")))
        assertEquals("Similar to A and B", similarToText(listOf("A", "B")))
        assertEquals("Similar to A, B and C", similarToText(listOf("A", "B", "C", "D")))
        assertEquals("950", shortCount(950))
        assertEquals("1.2K", shortCount(1_234))
        assertEquals("35K", shortCount(35_400))
        assertEquals("2M", shortCount(2_000_000))
    }
}
