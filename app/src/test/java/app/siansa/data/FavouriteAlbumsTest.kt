package app.siansa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Favourite albums (issue #275). */
class FavouriteAlbumsTest {
    @Test fun anAlbumIsAFavouriteByItsId() {
        assertTrue(isFavouriteAlbum(Album("a1", "One"), setOf("a1")))
        assertFalse(isFavouriteAlbum(Album("a2", "Two"), setOf("a1")))
    }

    @Test fun anyMergedCopyCounts() {
        // The same album in two Navidrome libraries is one album in Siansa; starring either copy makes it a favourite.
        val merged = Album("a1", "One", ids = listOf("a1", "b7"))
        assertTrue(isFavouriteAlbum(merged, setOf("b7")))
        assertFalse(isFavouriteAlbum(merged, emptySet()))
    }
}
