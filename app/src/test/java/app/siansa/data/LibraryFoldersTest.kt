package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every library kept on the phone, shown by choice, copies kept apart (issue #376). */
class LibraryFoldersTest {
    @Test fun copiesInDifferentLibrariesAreNotMerged() {
        assertNotEquals(albumGroupKey("Random Access Memories", "Daft Punk", "flac"), albumGroupKey("Random Access Memories", "Daft Punk", "atmos"))
        // In one library, or with no libraries, the same album is still one.
        assertEquals(albumGroupKey("RAM", "Daft Punk", "flac"), albumGroupKey("ram", "DAFT PUNK", "flac"))
        assertEquals(albumGroupKey("RAM", "Daft Punk", null), albumGroupKey("RAM", "Daft Punk", null))
    }

    @Test fun copiesInSeveralLibrariesGetTheLibraryName() {
        val names = mapOf("1" to "Music", "2" to "Dolby Atmos")
        val albums = listOf(
            Album(id = "a", name = "RAM", artist = "Daft Punk", folderId = "1"),
            Album(id = "b", name = "RAM", artist = "Daft Punk", folderId = "2"),
            Album(id = "c", name = "Discovery", artist = "Daft Punk", folderId = "1"),
        )
        assertEquals(mapOf("a" to "Music", "b" to "Dolby Atmos"), versionLabels(albums, names))
    }

    @Test fun noLabelWhenAnAlbumIsInOneLibrary() {
        val albums = listOf(Album(id = "a", name = "RAM", artist = "Daft Punk", folderId = "1"), Album(id = "b", name = "RAM", artist = "Daft Punk", folderId = "1"))
        assertTrue(versionLabels(albums, mapOf("1" to "Music")).isEmpty())
    }

    @Test fun theScopeNamesEveryLibraryWhateverTheChoice() {
        val all = listOf(MusicFolder("2", "Atmos"), MusicFolder("1", "Music"))
        assertEquals("all:1,2", syncScope(all))
    }
}
