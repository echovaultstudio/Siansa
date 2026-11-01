package app.siansa

import app.siansa.data.BookSync
import app.siansa.data.SyncPhase
import app.siansa.data.SyncProgress
import app.siansa.ui.booksLine
import app.siansa.ui.booksStatus
import app.siansa.ui.syncAllPlan
import app.siansa.ui.syncHeadline
import app.siansa.ui.syncLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home › Source filter: Sync all, Audiobookshelf listed, and the sync on one small line (issue #693). */
class SourcesSyncTest {
    @Test fun syncAllTakesWhatsShownOnTheSameServer() {
        val plan = syncAllPlan(inUseShown = true, guests = mapOf(1 to true, 2 to false, 3 to null, 4 to true), shown = setOf(0, 1, 2, 3), booksReady = true)
        assertTrue(plan.inUse)
        // 2 is elsewhere, 3 is still being checked, 4 is hidden.
        assertEquals(listOf(1), plan.guests)
        assertTrue(plan.books)
        assertEquals("your music, 1 other user, Audiobookshelf", plan.describe())
    }

    @Test fun syncAllLeavesOutHiddenMusicAndBooksNotSetUp() {
        val plan = syncAllPlan(inUseShown = false, guests = emptyMap(), shown = setOf(0), booksReady = false)
        assertFalse(plan.inUse)
        assertFalse(plan.books)
        assertEquals("nothing shown", plan.describe())
    }

    @Test fun theSyncFitsOnOneLine() {
        val songs = SyncProgress(phase = SyncPhase.SONGS, libraryName = "compilations", libraryIndex = 2, libraryCount = 4, songs = 1200, songsExpected = 5000)
        assertEquals("compilations (2 of 4) · 1,200 of 5,000 songs", syncLine(songs))
        assertEquals("albums · 2,099 albums", syncLine(SyncProgress(phase = SyncPhase.ALBUMS, libraryName = "albums", libraryCount = 1, albums = 2099)))
        assertEquals("Up to date: no albums changed", syncLine(SyncProgress(phase = SyncPhase.DONE, changedAlbums = 0, albums = 10)))
        assertEquals("Sync failed: no answer", syncLine(SyncProgress(phase = SyncPhase.FAILED, error = "no answer")))
        assertEquals("Connecting to your server…", syncHeadline(SyncProgress(phase = SyncPhase.CONNECTING)))
    }

    @Test fun audiobookshelfSaysWhatItHas() {
        assertEquals("Not signed in. Set it up in Settings › Servers and services.", booksStatus(false, BookSync()))
        assertEquals("Audiobooks and podcasts · syncing…", booksStatus(true, BookSync(running = true)))
        assertEquals("Audiobooks and podcasts · 312 books · 4 podcasts", booksStatus(true, BookSync(books = 312, podcasts = 4, done = true)))
        assertEquals("Synced · 1 book", booksLine(BookSync(books = 1, done = true)))
        assertEquals("Sync failed: timeout", booksLine(BookSync(error = "timeout")))
    }
}
