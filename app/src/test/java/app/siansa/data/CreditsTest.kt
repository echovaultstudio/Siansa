package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Featured and compilation artists are found and their pages split own releases from "Appears on" (beta testers #34). */
class CreditsTest {
    private val pendulum = ArtistRef("p", "Pendulum")
    private val djFresh = ArtistRef("f", "DJ Fresh")
    private val tarantula = Song(
        "s1", "Tarantula", "Pendulum feat. DJ Fresh", "p", "Hold Your Colour", "a1",
        artists = listOf(pendulum, djFresh),
    )
    private val dropDat = Song("s2", "Drop Dat", "T95", "t95", "Heavyweight D&B", "va")

    @Test fun creditsAreKeptAndReadBack() {
        val stored = Credits.encode(tarantula)
        assertEquals(listOf(pendulum, djFresh), Credits.decode(stored))
        assertEquals("Pendulum DJ Fresh", Credits.names(stored))
        // A song with only its main artist keeps nothing extra.
        assertEquals("", Credits.encode(dropDat.copy(artists = listOf(ArtistRef("t95", "T95")))))
        assertTrue(Credits.decode("not json").isEmpty())
        assertTrue(Credits.decode("").isEmpty())
    }

    @Test fun aFeaturedArtistIsFound() {
        val found = Credits.matching(listOf(tarantula), listOf("dj", "fresh"), known = emptySet())
        assertEquals(listOf(djFresh to 1), found)
    }

    @Test fun aCompilationArtistIsFound() {
        val found = Credits.matching(listOf(dropDat), listOf("t95"), known = emptySet())
        assertEquals(listOf(ArtistRef("t95", "T95") to 1), found)
    }

    @Test fun artistsAlreadyListedAndCombinedNamesAreLeftOut() {
        assertTrue(Credits.matching(listOf(tarantula), listOf("fresh"), known = setOf("f")).isEmpty())
        // "Pendulum feat. DJ Fresh" is no artist of its own.
        val noCredits = tarantula.copy(artists = emptyList())
        assertTrue(Credits.matching(listOf(noCredits), listOf("fresh"), known = emptySet()).isEmpty())
    }

    @Test fun theExactNameComesFirst() {
        val names = listOf("Fresh Prince", "Freshlyground", "DJ Fresh", "Fresh")
        assertEquals(listOf("Fresh", "Fresh Prince", "Freshlyground", "DJ Fresh"), rankByName("fresh", names) { it })
        assertEquals(listOf("DJ Fresh", "Fresh Prince", "Freshlyground", "Fresh"), rankByName("dj fresh", names) { it })
    }

    @Test fun onlyTheArtistsOwnAlbumsAreReleases() {
        val artist = Artist("f", "DJ Fresh")
        val own = Album("al1", "Kryptonite", "DJ Fresh", "f")
        val byName = Album("al2", "Gold Dust", "DJ FRESH", null)
        val mixtape = Album("al3", "Flava D Mixtape", "Flava D", "fd")
        val (mine, others) = splitOwnAlbums(artist, listOf(own, mixtape, byName))
        assertEquals(listOf(own, byName), mine)
        assertEquals(listOf(mixtape), others)
    }

    @Test fun anArtistFromSongCreditsStillHasAPicture() {
        // Found through "feat. DJ Fresh" only: no cover from the server, so the artist's own picture address.
        assertEquals("ar-f", Artist("f", "DJ Fresh").picture)
        assertEquals("ar-f_abc", Artist("f", "DJ Fresh", coverArt = "ar-f_abc").picture)
    }
}
