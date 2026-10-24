package app.siansa.playback

import app.siansa.playback.dlna.Upnp
import app.siansa.playback.dlna.rememberLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** TVs and speakers show at once (issue #361). */
class DlnaFastTest {
    @Test fun devicesAreAskedToAnswerWithinASecond() {
        assertTrue(Upnp.searchRequest(1).contains("MX: 1\r\n"))
    }

    @Test fun knownDevicesAreRememberedNewestKept() {
        val known = setOf("http://a/d.xml", "http://b/d.xml")
        assertSame(known, rememberLocation(known, "http://a/d.xml"))
        assertEquals(setOf("http://a/d.xml", "http://b/d.xml", "http://c/d.xml"), rememberLocation(known, "http://c/d.xml"))
        val full = (1..12).map { "http://$it/d.xml" }.toSet()
        val kept = rememberLocation(full, "http://new/d.xml")
        assertEquals(12, kept.size)
        assertTrue("http://new/d.xml" in kept)
    }
}
