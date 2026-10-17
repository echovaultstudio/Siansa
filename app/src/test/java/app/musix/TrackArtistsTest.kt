package app.musix

import app.musix.data.ArtistRef
import app.musix.data.Song
import app.musix.data.extraArtists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The line under a track on its album page: only artists beyond the album's own. */
class TrackArtistsTest {
    private fun song(artist: String, artists: List<ArtistRef> = emptyList()) = Song(id = "s", title = "t", artist = artist, artists = artists)

    @Test fun albumArtistAloneShowsNothing() {
        assertNull(extraArtists(song("Daft Punk"), "ar1", "Daft Punk"))
        assertNull(extraArtists(song("daft punk "), "ar1", "Daft Punk"))
    }

    @Test fun artistsTagListsOnlyTheOthers() {
        val s = song("Daft Punk feat. Pharrell Williams", listOf(ArtistRef("ar1", "Daft Punk"), ArtistRef("ar2", "Pharrell Williams")))
        assertEquals("Pharrell Williams", extraArtists(s, "ar1", "Daft Punk"))
    }

    @Test fun artistsTagMatchedByIdEvenIfNamedDifferently() {
        val s = song("x", listOf(ArtistRef("ar1", "Beyonce"), ArtistRef("ar2", "Jay-Z")))
        assertEquals("Jay-Z", extraArtists(s, "ar1", "Beyoncé Knowles"))
    }

    @Test fun artistsTagOnlyAlbumArtistShowsNothing() {
        val s = song("Daft Punk", listOf(ArtistRef("ar1", "Daft Punk")))
        assertNull(extraArtists(s, "ar1", "Daft Punk"))
    }

    @Test fun splitsTaggedNameWithoutArtistsList() {
        assertEquals("Pharrell Williams, Nile Rodgers", extraArtists(song("Daft Punk feat. Pharrell Williams & Nile Rodgers"), null, "Daft Punk"))
    }

    @Test fun albumWithTwoArtistsHidesBoth() {
        assertNull(extraArtists(song("Jay-Z & Kanye West"), null, "Jay-Z & Kanye West"))
        assertEquals("Frank Ocean", extraArtists(song("Jay-Z, Kanye West feat. Frank Ocean"), null, "Jay-Z & Kanye West"))
    }

    @Test fun compilationShowsTheWholeName() {
        assertEquals("Earth, Wind & Fire", extraArtists(song("Earth, Wind & Fire"), "va", "Various Artists"))
    }

    @Test fun emptyArtistShowsNothing() {
        assertNull(extraArtists(song(""), null, "Someone"))
    }
}

/** Splitting a tagged artist name without breaking artists whose own name has "&" or "and" in it. */
class SplitArtistsTest {
    private val known = setOf("chase status", "earth wind fire", "simon garfunkel").let { k -> { name: String -> app.musix.data.normalizeKey(name) in k } }

    @Test fun knownArtistWithAJoinStaysWhole() {
        assertEquals(listOf("Chase & Status"), app.musix.data.splitArtists("Chase & Status", known))
        assertEquals(listOf("Chase & Status", "Bou"), app.musix.data.splitArtists("Chase & Status feat. Bou", known))
        assertEquals(listOf("Sub Focus", "Chase & Status"), app.musix.data.splitArtists("Sub Focus, Chase & Status", known))
        assertEquals(listOf("Earth, Wind & Fire"), app.musix.data.splitArtists("Earth, Wind & Fire", known))
    }

    @Test fun unknownNamesAreSplitAtEveryJoin() {
        assertEquals(listOf("Chase", "Status"), app.musix.data.splitArtists("Chase & Status"))
        assertEquals(listOf("Jay-Z", "Kanye West", "Frank Ocean"), app.musix.data.splitArtists("Jay-Z, Kanye West feat. Frank Ocean"))
    }

    @Test fun albumLineKeepsAKnownArtistWhole() {
        val s = Song(id = "s", title = "t", artist = "Chase & Status feat. Bou")
        assertEquals("Chase & Status, Bou", extraArtists(s, null, "Sub Focus", known))
        assertEquals("Bou", extraArtists(s, null, "Chase & Status", known))
        val alone = Song(id = "s", title = "t", artist = "Chase & Status")
        assertEquals("Chase & Status", extraArtists(alone, null, "Sub Focus", known))
    }
}
