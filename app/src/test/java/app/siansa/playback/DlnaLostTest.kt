package app.siansa.playback

import app.siansa.playback.dlna.DlnaQueue
import app.siansa.playback.dlna.LocalNetworks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A DLNA TV or speaker that's out of reach is let go of quickly, and the music comes back to the phone (issue #624). */
class DlnaLostTest {
    @Test fun aFewMissedAnswersOnBusyWifiAreFine() {
        assertFalse(DlnaQueue.gone(failures = 1, sinceAnswerMs = 20_000))
        assertFalse(DlnaQueue.gone(failures = 2, sinceAnswerMs = 20_000))
        assertFalse(DlnaQueue.gone(failures = 5, sinceAnswerMs = 6_000))  // quick misses, answered a moment ago
    }

    @Test fun noAnswerForFifteenSecondsMeansGone() {
        assertTrue(DlnaQueue.gone(failures = 3, sinceAnswerMs = 15_000))
        assertTrue(DlnaQueue.gone(failures = 4, sinceAnswerMs = 40_000))
    }

    @Test fun manyMissesInARowMeanGoneEvenWhenQuick() {
        assertTrue(DlnaQueue.gone(failures = DlnaQueue.MAX_FAILURES, sinceAnswerMs = 9_000))
    }

    @Test fun leavingTheLastWifiCountsOnlyOnceOneWasSeen() {
        val n = LocalNetworks()
        assertFalse(n.left)  // on mobile data or the phone's own hotspot from the start: never "left"
        n.available("wifi-1")
        assertFalse(n.left)
        n.lost("wifi-1")
        assertTrue(n.left)
        n.available("wifi-2")  // back on Wi-Fi within the grace time
        assertFalse(n.left)
    }

    @Test fun anotherLocalNetworkStillUpIsNotLeaving() {
        val n = LocalNetworks()
        n.available("wifi")
        n.available("ethernet")
        n.lost("wifi")
        assertFalse(n.left)
        n.lost("ethernet")
        assertTrue(n.left)
    }
}
