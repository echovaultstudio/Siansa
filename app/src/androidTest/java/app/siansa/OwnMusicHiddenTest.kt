package app.siansa

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.siansa.data.db.AlbumEntity
import app.siansa.data.db.LibraryDatabase
import app.siansa.data.db.SourceEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Issue #427: hiding the user in use's own music hides only theirs, at once, and showing it again brings it back. */
@RunWith(AndroidJUnit4::class)
class OwnMusicHiddenTest {
    private fun album(id: String) = AlbumEntity(
        id = id, name = id, artist = "a", artistId = null, coverArt = null, songCount = 1, durationSec = 1, year = null,
        genre = null, created = null, playCount = 0, releaseTypes = "", isCompilation = false, sortKey = id, artistKey = "a",
        groupKey = id, signature = id,
    )

    @Test fun onlyTheUserInUsesAlbumsAreHidden() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val dao = db.dao()
            dao.putAlbums(listOf(album("own1"), album("own2"), album("theirs")))
            dao.putSources(listOf(SourceEntity("theirs", 1)))
            dao.hideOwnAlbums()
            assertEquals(false, dao.album("own1")!!.visible)
            assertEquals(false, dao.album("own2")!!.visible)
            assertEquals(true, dao.album("theirs")!!.visible)
            dao.showAllFolders()
            assertEquals(true, dao.album("own1")!!.visible)
        } finally {
            db.close()
        }
    }
}
