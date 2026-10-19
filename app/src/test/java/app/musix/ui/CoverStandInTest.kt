package app.musix.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverStandInTest {
    @Test fun aDiscCoverShowsItsAlbumsWhileItLoads() {
        assertTrue(albumCanStandIn("dc-2Fx2A0PG3R5jyL1lcv0uey:1", "al-2Fx2A0PG3R5jyL1lcv0uey_68f1a2b3"))
    }

    @Test fun aSongsOwnCoverToo() {
        assertTrue(albumCanStandIn("mf-6u0xgGu8UpYMh4vYyxjdf9_68f1a2b3", "al-54xd1KWT9oO6pSAgFAvXBq_68f1a2b3"))
    }

    /** A song without a cover id shows "al-<album>", saved under the album's id with its last change. */
    @Test fun theAlbumIdWithoutItsVersion() {
        assertTrue(albumCanStandIn("al-54xd1KWT9oO6pSAgFAvXBq", "al-54xd1KWT9oO6pSAgFAvXBq_68f1a2b3"))
    }

    @Test fun notTheSameCoverOrNone() {
        assertFalse(albumCanStandIn("al-54xd1KWT9oO6pSAgFAvXBq_68f1a2b3", "al-54xd1KWT9oO6pSAgFAvXBq_68f1a2b3"))
        assertFalse(albumCanStandIn("dc-54xd1KWT9oO6pSAgFAvXBq:1", null))
    }
}
