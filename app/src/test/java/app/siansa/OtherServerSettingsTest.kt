package app.siansa

import app.siansa.data.AiProvider
import app.siansa.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Changing another login's server on the Connections page (issue #687). */
class OtherServerSettingsTest {
    private val saved = AppSettings(
        navidromeUrl = "http://192.168.1.10:4533", navidromeUser = "kids", navidromePassword = "a",
        audioMuseUrl = "http://am", aiProvider = AiProvider.OPENROUTER, aiModel = "m", preampDb = -2f,
    )

    @Test fun onlyTheServerAndLoginAreTaken() {
        val page = saved.copy(
            navidromeUrl = "http://192.168.1.11:4533", navidromeUser = "kid", navidromePassword = "b",
            proxyUser = "p", proxyPassword = "q", audioMuseServer = "Kids",
            // Not the server's: these stay as they are saved.
            audioMuseUrl = "http://other", aiModel = "x", preampDb = 3f,
        )
        val next = saved.withServerFrom(page)
        assertEquals("http://192.168.1.11:4533", next.navidromeUrl)
        assertEquals("kid", next.navidromeUser)
        assertEquals("b", next.navidromePassword)
        assertEquals("p", next.proxyUser)
        assertEquals("q", next.proxyPassword)
        assertEquals("Kids", next.audioMuseServer)
        assertEquals("http://am", next.audioMuseUrl)
        assertEquals("m", next.aiModel)
        assertEquals(-2f, next.preampDb)
    }

    @Test fun anUnchangedCardIsntSaved() {
        assertFalse(saved.serverDiffers(saved))
        assertFalse(saved.serverDiffers(saved.copy(aiModel = "other")))  // not the server's
        assertTrue(saved.serverDiffers(saved.copy(navidromePassword = "new")))
    }
}
