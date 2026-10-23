package app.musix.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quick check before skipping a sync spots albums Navidrome added without a new scan time (issue #393). */
class NewAlbumsCheckTest {
    private val newest = listOf(Album("d1", "Cherry", "Daphni"), Album("a1", "Old", "Someone"))

    @Test fun anAlbumThePhoneDoesntHaveIsFound() = runBlocking {
        assertEquals(listOf("d1"), missingAlbums(newest) { it == "a1" }.map { it.id })
    }

    @Test fun nothingMissingWhenThePhoneHasThemAll() = runBlocking {
        assertTrue(missingAlbums(newest) { true }.isEmpty())
    }
}
