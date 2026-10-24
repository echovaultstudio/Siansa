package app.siansa.ui

import app.siansa.data.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Issue #220: the user in use says what's on the phone and when it was synced. */
class SourceSyncStatusTest {
    @Test fun saysWhatsOnThePhone() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            val now = 1_760_000_000_000L
            val status = LibraryStatus(syncedAt = now - 60_000, artists = 878, albums = 2361, songs = 41942)
            val text = inUseStatus(syncing = false, status = status, now = now)
            assertTrue(text, text.startsWith("In use · 878 artists · 2,361 albums · 41,942 songs · synced "))
            assertEquals("In use · syncing…", inUseStatus(syncing = true, status = status, now = now))
            assertEquals("In use · not synced yet", inUseStatus(syncing = false, status = null, now = now))
            // Another day: the date instead of the time.
            val old = inUseStatus(false, status.copy(syncedAt = now - 3 * 86_400_000L), now)
            assertTrue(old, !old.endsWith(":00") && old.contains("synced "))
        } finally {
            Locale.setDefault(before)
        }
    }
}
