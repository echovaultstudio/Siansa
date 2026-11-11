package app.siansa.scrobble

import app.siansa.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saving and removing the listen server (ListenBrainz / Koito) in Settings › Scrobbling and history. */
class ListenServerTest {
    private val set = AppSettings(listenBrainzEnabled = true, listenBrainzUrl = "http://koito:4110", listenBrainzToken = "k3y")

    @Test fun theFirstSaveSwitchesSendingOn() {
        val saved = AppSettings().withListenServer(" http://koito:4110 ", " k3y ")
        assertEquals(set, saved)
        assertTrue(saved.listenBrainzReady)
    }

    /** The owner, 11 Nov: switched off, then saved, and listens were sent again. */
    @Test fun savingLeavesSendingOffOnceItWasSwitchedOff() {
        val off = set.copy(listenBrainzEnabled = false)
        val saved = off.withListenServer("http://koito:4110", "new-k3y")
        assertFalse(saved.listenBrainzEnabled)
        assertFalse(saved.listenBrainzReady)
        assertEquals("new-k3y", saved.listenBrainzToken)
        assertTrue(set.withListenServer("http://other:4110", "k3y").listenBrainzEnabled)  // on stays on
    }

    /** The owner, 11 Nov: emptying the fields answered "HTTP 400" and nothing was removed. */
    @Test fun anEmptyTokenTakesTheServerAway() {
        for (cleared in listOf(set.withListenServer("", ""), set.withListenServer("http://koito:4110", "  "))) {
            assertEquals("", cleared.listenBrainzUrl)
            assertEquals("", cleared.listenBrainzToken)
            assertFalse(cleared.listenBrainzEnabled)
            assertFalse(cleared.listenBrainzReady)
        }
    }

    @Test fun switchedOffOrWithoutATokenNothingIsReady() {
        assertFalse(set.copy(listenBrainzEnabled = false).listenBrainzReady)
        assertFalse(set.copy(listenBrainzToken = "").listenBrainzReady)
    }
}
