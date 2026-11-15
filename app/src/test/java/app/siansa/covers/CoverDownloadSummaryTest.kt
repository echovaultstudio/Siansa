package app.siansa.covers

import app.siansa.ui.LARGE_COVER
import app.siansa.ui.SMALL_COVER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun animationsDownloadedAreSaid() {
        assertEquals("14 downloaded (12 of them animations), 9 already here in 300s", CoverDownload.summary(14, 9, 0, 0, 300, animations = 12))
    }

    /** The owner, 15 Nov: with 6000 albums, some animated at over 100 MB, only stills of the big ones were kept. */
    @Test fun anAlbumsAnimationComesWhenOnlyItsStillIsHere() {
        assertTrue(CoverDownload.bringsAnimation("al-5AGucgFjtno5hRWWydbikV_67822602a9de827d", animations = true, onlyStill = true))
        // Already whole on the phone, or a plain picture: nothing to bring.
        assertFalse(CoverDownload.bringsAnimation("al-5AGucgFjtno5hRWWydbikV", animations = true, onlyStill = false))
        // Animated covers switched off: stills are all that's shown.
        assertFalse(CoverDownload.bringsAnimation("al-5AGucgFjtno5hRWWydbikV", animations = false, onlyStill = true))
        // A song's, a disc's or an artist's picture stays a still: the album's is what moves.
        for (id in listOf("mf-abc_1", "dc-5AGucgFjtno5hRWWydbikV:1", "ar-xyz", "pl-1")) {
            assertFalse(id, CoverDownload.bringsAnimation(id, animations = true, onlyStill = true))
        }
    }
}
