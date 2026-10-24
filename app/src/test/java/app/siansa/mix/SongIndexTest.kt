package app.siansa.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The time machine matches Koito and Last.fm songs to the library through one index (issue #186). */
class SongIndexTest {
    private val index = SongIndex(
        listOf(
            SongIndex.Entry("1", "Left Hand Free", "alt-J"),
            SongIndex.Entry("2", "Ghost", "Mystery Skulls"),
            SongIndex.Entry("3", "Ghost", "Justin Bieber"),
            SongIndex.Entry("4", "Déjà Vu", "Beyoncé feat. Jay-Z"),
            SongIndex.Entry("5", "Intro", ""),
        )
    )

    @Test fun sameTitleAndArtistInAnyCase() = assertEquals("1", index.find("ALT-J", "left hand free"))

    @Test fun theRightArtistAmongTheSameTitles() {
        assertEquals("3", index.find("Justin Bieber", "Ghost"))
        assertEquals("2", index.find("Mystery Skulls", "Ghost"))
    }

    @Test fun accentsAndFeaturedArtists() = assertEquals("4", index.find("Beyonce", "Deja vu"))

    @Test fun noArtistTakesTheTitle() = assertEquals("2", index.find("", "Ghost"))

    @Test fun wrongArtistIsNoMatch() = assertNull(index.find("Radiohead", "Ghost"))

    @Test fun aSongWithoutArtistOnlyMatchesWithoutArtist() {
        // An empty artist in the library is "contained" in every name; it mustn't match any artist.
        assertNull(index.find("Bonobo", "Intro"))
        assertEquals("5", index.find("", "Intro"))
    }

    @Test fun unknownTitle() = assertNull(index.find("alt-J", "Breezeblocks"))

    @Test fun exactArtistIsPreferred() {
        val i = SongIndex(listOf(SongIndex.Entry("a", "Kerala", "Bonobo feat. Someone"), SongIndex.Entry("b", "Kerala", "Bonobo")))
        assertEquals("b", i.find("Bonobo", "Kerala"))
    }
}
