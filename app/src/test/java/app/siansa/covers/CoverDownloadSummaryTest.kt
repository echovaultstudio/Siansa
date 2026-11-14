package app.siansa.covers

import app.siansa.ui.LARGE_COVER
import app.siansa.ui.SMALL_COVER
import org.junit.Assert.assertEquals
import org.junit.Test

/** What Download all covers keeps and says (the owner, 14 Nov: song covers had only their small size). */
class CoverDownloadSummaryTest {
    @Test fun everyCoverIsKeptAtBothSizes() {
        assertEquals(listOf(SMALL_COVER, LARGE_COVER), CoverDownload.SIZES)
    }

    @Test fun coversWithNoPictureAreNotCalledAlreadyHere() {
        assertEquals(
            "34 downloaded, 12107 already here, 295 with no picture on the server in 9s",
            CoverDownload.summary(downloaded = 34, had = 12107, none = 295, failed = 0, seconds = 9),
        )
        assertEquals("0 downloaded, 10 already here, 2 failed in 1s", CoverDownload.summary(0, 10, 0, 2, 1))
    }
}
