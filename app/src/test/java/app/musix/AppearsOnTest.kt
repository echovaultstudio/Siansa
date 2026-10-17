package app.musix

import app.musix.data.ArtistRef
import app.musix.data.Song
import app.musix.data.appearsOnAlbumIds
import app.musix.data.creditedNames
import app.musix.data.isCredited
import app.musix.data.likeEscape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finding the albums an artist appears on without being the album artist. */
class AppearsOnTest {
    private fun song(artist: String, albumId: String, artistId: String? = null, artists: List<ArtistRef> = emptyList()) =
        Song(id = "$albumId-$artist", title = "T", artist = artist, artistId = artistId, albumId = albumId, artists = artists)

    @Test fun splitsCommonCredits() {
        val names = creditedNames("Daft Punk feat. Pharrell Williams & Nile Rodgers")
        assertTrue("daft punk" in names)
        assertTrue("pharrell williams" in names)
        assertTrue("nile rodgers" in names)
        assertTrue(creditedNames("A ft B").containsAll(listOf("a", "b")))
        assertTrue(creditedNames("A, B; C / D vs. E").containsAll(listOf("a", "b", "c", "d", "e")))
        assertTrue(creditedNames("A Featuring B").contains("b"))
    }

    @Test fun keepsTheWholeCreditToo() {
        assertTrue("ac dc" in creditedNames("AC/DC"))
        assertTrue("simon garfunkel" in creditedNames("Simon & Garfunkel"))
    }

    @Test fun doesNotSplitInsideWords() {
        // "ft" and "vs" only count as whole words.
        assertEquals(setOf("daft punk"), creditedNames("Daft Punk"))
        assertEquals(setOf("soft cell"), creditedNames("Soft Cell"))
        assertEquals(setOf("the vselka"), creditedNames("The Vselka"))
    }

    @Test fun creditedByIdOrName() {
        assertTrue(isCredited(song("Someone", "x", artistId = "ar1"), "ar1", "Other Name"))
        assertTrue(isCredited(song("Someone feat. Guest", "x", artists = listOf(ArtistRef("ar2", "Guest"))), "ar2", "Guest"))
        assertTrue(isCredited(song("Someone feat. guest", "x"), "ar9", "Guest"))
        assertFalse(isCredited(song("Guesthouse", "x"), "ar9", "Guest"))
        assertFalse(isCredited(song("Someone", "x"), "ar9", ""))
    }

    @Test fun leavesOutOwnAlbumsAndDuplicates() {
        val songs = listOf(
            song("Guest", "own"),
            song("Host feat. Guest", "feature"),
            song("Host feat. Guest", "feature"),
            song("Various", "comp", artistId = "guest"),
            song("Host", "unrelated"),
        )
        assertEquals(listOf("feature", "comp"), appearsOnAlbumIds(songs, "guest", "Guest") { it == "own" })
    }

    @Test fun escapesLikePatterns() {
        assertEquals("100\\% Pure", likeEscape("100% Pure"))
        assertEquals("a\\_b\\\\c", likeEscape("a_b\\c"))
    }
}
