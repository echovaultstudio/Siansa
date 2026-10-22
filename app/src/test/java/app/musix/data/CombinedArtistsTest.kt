package app.musix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Several artists tagged in one name become their own artists, when that's safe (issue #359). */
class CombinedArtistsTest {
    private val rudimental = Artist("r", "Rudimental", mbid = "40cc8ea4-0f9b-4a1b-9b6c-6f0b1d3a1f11")
    private val koffee = Artist("k", "Orginal Koffee")
    private val winny = Artist("w", "Winny")
    private val combined = Artist("c", "Rudimental, Orginal Koffee & Winny")

    @Test fun everyPartKnownSplitsIt() {
        val found = CombinedArtists.find(listOf(rudimental, koffee, winny, combined))
        val c = found.getValue("c")
        assertEquals(listOf("r", "k", "w"), c.parts.map { it.id })
        assertTrue(c.complete)
        assertEquals(setOf("c"), found.keys)
    }

    @Test fun anUnknownPartIsFineWhenAnotherHasAMusicBrainzId() {
        val c = CombinedArtists.find(listOf(rudimental, koffee, combined)).getValue("c")
        assertEquals(listOf("r", "k"), c.parts.map { it.id })
        assertEquals(listOf("Winny"), c.unknown)
        assertFalse(c.complete)
    }

    @Test fun anUnknownPartWithoutAnyMusicBrainzIdIsLeftAlone() {
        // "Earth, Wind & Fire" in a library that happens to have an artist called "Fire".
        val ewf = Artist("e", "Earth, Wind & Fire")
        val fire = Artist("f", "Fire")
        assertTrue(CombinedArtists.find(listOf(ewf, fire)).isEmpty())
    }

    @Test fun anArtistWithItsOwnMusicBrainzIdIsNeverSplit() {
        val ewf = Artist("e", "Earth, Wind & Fire", mbid = "535afeda-2538-435d-9dd1-5e10be586774")
        val parts = listOf(Artist("1", "Earth"), Artist("2", "Wind"), Artist("3", "Fire"))
        assertTrue(CombinedArtists.find(parts + ewf).isEmpty())
    }

    @Test fun twoArtistsWithTheSameNameLeaveItAsIs() {
        val otherWinny = Artist("w2", "Winny", mbid = "00000000-0000-0000-0000-000000000002")
        assertTrue(CombinedArtists.find(listOf(rudimental, koffee, winny, otherWinny, combined)).isEmpty())
    }

    @Test fun aKnownNameWithAJoinStaysWhole() {
        val chase = Artist("cs", "Chase & Status", mbid = "11111111-1111-1111-1111-111111111111")
        val bou = Artist("b", "Bou")
        val feat = Artist("x", "Chase & Status feat. Bou")
        assertEquals(listOf("cs", "b"), CombinedArtists.find(listOf(chase, bou, feat)).getValue("x").parts.map { it.id })
    }

    @Test fun aSingleArtistIsNotCombined() {
        assertTrue(CombinedArtists.find(listOf(rudimental, winny)).isEmpty())
    }

    @Test fun expandOffersEachArtistAndKeepsTheNameForUnknownParts() {
        val complete = CombinedArtists.find(listOf(rudimental, koffee, winny, combined))
        assertEquals(listOf("r", "k", "w"), CombinedArtists.expand(listOf(combined), complete).map { it.id })
        val partial = CombinedArtists.find(listOf(rudimental, koffee, combined))
        assertEquals(listOf("r", "k", "c"), CombinedArtists.expand(listOf(combined), partial).map { it.id })
        assertEquals(listOf("r", "k", "w"), CombinedArtists.expand(listOf(rudimental, combined), complete).map { it.id })
    }

    @Test fun listsDropOnlyFullyCoveredNames() {
        val all = listOf(rudimental, koffee, winny, combined)
        assertEquals(listOf("r", "k", "w"), CombinedArtists.withoutCombined(all, CombinedArtists.find(all)).map { it.id })
        val some = listOf(rudimental, koffee, combined)
        assertEquals(listOf("r", "k", "c"), CombinedArtists.withoutCombined(some, CombinedArtists.find(some)).map { it.id })
    }

    @Test fun eachPartsPageIncludesTheCombinedArtist() {
        val found = CombinedArtists.find(listOf(rudimental, koffee, winny, combined))
        assertEquals(listOf("c"), CombinedArtists.containing(koffee, found).map { it.id })
        assertTrue(CombinedArtists.containing(combined, found).isEmpty())
    }
}
