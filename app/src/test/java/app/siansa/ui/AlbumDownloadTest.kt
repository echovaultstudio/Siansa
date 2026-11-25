package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An album's download as its ⊕ and its page say it (issue #1331: Download moved into the album's sheet). */
class AlbumDownloadTest {
    private val songs = listOf("s1", "s2", "s3")

    @Test fun notChosenSaysNothingUnderTheTitle() {
        val d = AlbumDownload.of(chosen = false, songIds = songs, done = setOf("s1"))
        assertFalse(d.all)
        assertNull(downloadNote(d))
    }

    @Test fun onItsWaySaysHowFar() {
        val d = AlbumDownload.of(chosen = true, songIds = songs, done = setOf("s1", "other"))
        assertEquals(1, d.done)
        assertFalse(d.all)
        assertEquals("Downloading 1 of 3", downloadNote(d))
    }

    @Test fun everySongOnThePhoneIsDownloaded() {
        val d = AlbumDownload.of(chosen = true, songIds = songs, done = songs.toSet())
        assertTrue(d.all)
        assertEquals("Downloaded", downloadNote(d))
        assertFalse("An empty album isn't done", AlbumDownload.of(true, emptyList(), emptySet()).all)
    }
}
