package app.siansa

import app.siansa.data.Album
import app.siansa.data.AlbumCategory
import app.siansa.ui.chosenReleaseType
import app.siansa.ui.onlyReleaseType
import app.siansa.ui.releaseTypeChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The release-type chips on an artist's page: which ones show and what they keep. */
class ReleaseTypeFilterTest {
    private fun album(id: String, vararg types: String) = Album(id = id, name = id, releaseTypes = types.toList())

    @Test fun choicesAreTheArtistsTypesInDisplayOrder() {
        val albums = listOf(
            album("a", "single"),
            album("b", "album", "dj-mix"),
            album("c", "album"),
            album("d", "ep"),
            album("e", "single"),
        )
        assertEquals(
            listOf(AlbumCategory.ALBUM, AlbumCategory.EP, AlbumCategory.SINGLE, AlbumCategory.DJMIX),
            releaseTypeChoices(albums),
        )
    }

    @Test fun noChipsForASingleTypeOrNoReleases() {
        assertTrue(releaseTypeChoices(listOf(album("a", "album"), album("b"))).isEmpty())
        assertTrue(releaseTypeChoices(emptyList()).isEmpty())
    }

    @Test fun chosenTypeOnlyWhenTheArtistHasIt() {
        val choices = listOf(AlbumCategory.ALBUM, AlbumCategory.SINGLE)
        assertEquals(AlbumCategory.SINGLE, chosenReleaseType("SINGLE", choices))
        assertNull(chosenReleaseType(null, choices))
        assertNull(chosenReleaseType("DJMIX", choices))
        assertNull(chosenReleaseType("nonsense", choices))
    }

    @Test fun filterKeepsOnlyTheChosenGroup() {
        val groups = listOf(
            AlbumCategory.ALBUM to listOf("a"),
            AlbumCategory.SINGLE to listOf("b", "c"),
            AlbumCategory.DJMIX to listOf("d"),
        )
        assertEquals(groups, onlyReleaseType(groups, null))
        assertEquals(listOf(AlbumCategory.SINGLE to listOf("b", "c")), onlyReleaseType(groups, AlbumCategory.SINGLE))
        assertTrue(onlyReleaseType(groups, AlbumCategory.LIVE).isEmpty())
    }
}
