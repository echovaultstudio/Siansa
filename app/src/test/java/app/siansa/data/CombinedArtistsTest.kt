package app.siansa.data

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

    @Test fun aNameGivenOneOfItsArtistsMusicBrainzIdIsStillSplit() {
        // 0.52.0-dev: "ID, Camo & Krooked" came from Navidrome with Camo & Krooked's ID, so it stayed one artist.
        val ck = Artist("ck", "Camo & Krooked", mbid = "5a5f0c3e-8d3c-4e5c-9b8f-2b8a6d0a0c01")
        val joined = Artist("j", "ID, Camo & Krooked", mbid = "5A5F0C3E-8D3C-4E5C-9B8F-2B8A6D0A0C01")
        val c = CombinedArtists.find(listOf(ck, joined)).getValue("j")
        assertEquals(listOf("ck"), c.parts.map { it.id })
        assertEquals(listOf("ID"), c.unknown)
    }

    @Test fun aBandWithItsOwnMusicBrainzIdStaysWhole() {
        val earth = Artist("e", "Earth, Wind & Fire", mbid = "535afeda-2538-435d-9dd1-5e10be586774")
        val wind = Artist("w2", "Wind", mbid = "11111111-2222-3333-4444-555555555555")
        assertTrue(CombinedArtists.find(listOf(earth, wind)).isEmpty())
    }

    @Test fun aSongsArtistOutsideTheArtistListIsSplitToo() {
        // 0.52.0-dev: "ID, Camo & Krooked" is only ever a song's artist, so Navidrome's artist list (album artists)
        // didn't have it and it was never checked.
        val ck = Artist("ck", "Camo & Krooked", mbid = "5a5f0c3e-8d3c-4e5c-9b8f-2b8a6d0a0c01")
        val fred = Artist("f", "Fred V")
        val index = CombinedArtists.Index(listOf(ck, fred))
        val c = CombinedArtists.of(Artist("j", "ID, Camo & Krooked"), index)!!
        assertEquals(listOf("ck"), c.parts.map { it.id })
        assertEquals(listOf("ID"), c.unknown)
    }

    private val ck = Artist("ck", "Camo & Krooked", mbid = "5a5f0c3e-8d3c-4e5c-9b8f-2b8a6d0a0c01")
    private val joinedId = Artist("j", "ID, Camo & Krooked")
    private fun joinedIdSplit() = CombinedArtists.of(joinedId, CombinedArtists.Index(listOf(ck)))!!

    @Test fun aPartTheServerHasBecomesThatArtist() {
        // "ID" is only on songs (DJ mixes), so the library's artist list (album artists) doesn't have it; the music
        // server does (MusicBrainz keeps "ID" as an artist for uncredited tracks), with its other ID songs.
        val id = Artist("srv-id", "ID", mbid = "11111111-2222-3333-4444-555555555555")
        val c = CombinedArtists.withServer(joinedIdSplit(), mapOf("id" to listOf(id)))
        assertTrue(c.complete)
        assertEquals(listOf("ck", "srv-id"), CombinedArtists.expand(listOf(joinedId), mapOf("j" to c)).map { it.id })
    }

    @Test fun aPlaceholderNobodyHasIsLeftOut() {
        val c = CombinedArtists.withServer(joinedIdSplit(), emptyMap())
        assertTrue(c.complete)
        assertEquals(listOf("ck"), CombinedArtists.expand(listOf(joinedId), mapOf("j" to c)).map { it.id })
    }

    @Test fun aRealNameNobodyHasKeepsTheJoinedName() {
        val rudimental = Artist("r", "Rudimental", mbid = "40cc8ea4-0f9b-4a1b-9b6c-6f0b1d3a1f11")
        val joined = Artist("c2", "Rudimental & Winny")
        val c = CombinedArtists.withServer(CombinedArtists.of(joined, CombinedArtists.Index(listOf(rudimental)))!!, emptyMap())
        assertEquals(listOf("Winny"), c.unknown)
        assertEquals(listOf("r", "c2"), CombinedArtists.expand(listOf(joined), mapOf("c2" to c)).map { it.id })
    }

    @Test fun twoServerArtistsByOneNameStayUnknown() {
        val c = CombinedArtists.withServer(joinedIdSplit(), mapOf("id" to listOf(Artist("a", "ID"), Artist("b", "ID"))))
        assertEquals(listOf("ID"), c.unknown)
    }
}
