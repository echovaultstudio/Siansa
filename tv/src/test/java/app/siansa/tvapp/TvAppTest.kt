package app.siansa.tvapp

import app.siansa.pairing.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TV app's own logic (issue #860). */
class TvAppTest {
    @Test
    fun homeSaysTheLibrarySyncsOnlyWhileItDoes() {
        assertTrue(showsSyncLine(app.siansa.data.SyncProgress(phase = app.siansa.data.SyncPhase.ALBUMS)))
        assertTrue(showsSyncLine(app.siansa.data.SyncProgress(phase = app.siansa.data.SyncPhase.CONNECTING)))
        // Done or failed, the line goes; it said "so far" until the app was opened again.
        assertFalse(showsSyncLine(app.siansa.data.SyncProgress(phase = app.siansa.data.SyncPhase.DONE)))
        assertFalse(showsSyncLine(app.siansa.data.SyncProgress(phase = app.siansa.data.SyncPhase.FAILED)))
        assertFalse(showsSyncLine(app.siansa.data.SyncProgress(phase = app.siansa.data.SyncPhase.IDLE)))
        // The quiet check at start never shows it.
        assertFalse(showsSyncLine(app.siansa.data.SyncProgress(phase = app.siansa.data.SyncPhase.ALBUMS, quiet = true)))
    }

    @Test
    fun theSignInCodeHoldsThePagesAddress() {
        val url = Pairing.pageUrl("192.168.1.20", 41234, Pairing.newKey())
        val grid = QrGrid.of(url)
        // A QR code is square, with its quiet zone, and has dark squares in its three corners' finder patterns.
        assertTrue(grid.size >= 21 + 4)
        assertTrue(grid[2, 2] && grid[grid.size - 3, 2] && grid[2, grid.size - 3])
        assertFalse(grid[0, 0])
    }

    @Test
    fun thePageSendsNoKeyAndOpensSiansa() {
        val page = PairingServer.page("192.168.1.20", 41234)
        assertTrue("siansa://pair?tv=" in page)
        assertTrue("192.168.1.20:41234" in page)
        // The key comes from the address's # part in the phone's browser, never from the TV's answer.
        assertTrue("location.hash" in page)
    }

    @Test
    fun timesReadAsMinutesAndSeconds() {
        assertEquals("3:07", minutes(187_000))
        assertEquals("0:00", minutes(-5))
        assertEquals("61:01", minutes(3_661_000))
    }

    @Test
    fun jumpBackInShowsWhatTheTvCanOpen() {
        assertTrue(shortcutOpens("album/123"))
        assertTrue(shortcutOpens("mix/2026-11-05-daily"))
        assertTrue(shortcutOpens("radio/mood/calm"))
        assertFalse(shortcutOpens("book/9"))
        assertFalse(shortcutOpens("pinned/3"))
    }

    @Test
    fun theTvsOwnPagesStandInForThePhones() {
        // The phone's pages for these aren't added to the TV's NavHost: the TV's are, under the same routes.
        assertTrue(TvRoutes.own.containsAll(listOf("home", "library", "search", "settings", "album/{id}", "artist/{id}", "playlist/{id}", "mix/{id}")))
    }

    @Test
    fun streamDetails() {
        assertEquals("Opus 160 kbit/s", details("OPUS", 160))
        assertEquals("FLAC", details("FLAC", 0))
        assertEquals("", details(null, 0))
    }
}
