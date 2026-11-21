package app.siansa.covers

import app.siansa.data.Album
import app.siansa.data.Song
import app.siansa.ui.albumCoverIds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Holding an album's cover clears every picture kept of it (the owner, 21 Nov). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ClearAlbumCoversTest {
    private fun song(id: String, cover: String?) = Song(id = id, title = id, coverArt = cover)

    @Test fun anAlbumsPicturesAreItsOwnTheAppsAndItsSongs() {
        val album = Album(id = "a1", name = "Night Drive", coverArt = "al-a1_19a2f")
        val songs = listOf(song("s1", "dc-a1:1"), song("s2", "dc-a1:1"), song("s3", "mf-s3_77"), song("s4", null))
        assertEquals(listOf("al-a1_19a2f", "al-a1", "dc-a1:1", "mf-s3_77"), albumCoverIds(album, songs))
    }

    @Test fun anAlbumWithoutACoverClearsItsStandIn() {
        // Emby and Jellyfin: the album's id is its picture.
        val album = Album(id = "f00d", name = "Old Film", coverArt = null)
        assertEquals(listOf("f00d", "al-f00d"), albumCoverIds(album, emptyList(), standIn = "f00d"))
    }

    @Test fun clearingCountsEachCoverOnceAndTellsCoversOnScreen() {
        val loader = coil3.ImageLoader.Builder(androidx.test.core.app.ApplicationProvider.getApplicationContext()).build()
        val before = CoverCache.cleared.value["dc-a1:1"] ?: 0
        assertEquals(2, CoverCache.clearAll(loader, listOf("dc-a1:1", "dc-a1:1", "", "al-a1")))
        assertEquals(before + 1, CoverCache.cleared.value["dc-a1:1"])
    }
}
