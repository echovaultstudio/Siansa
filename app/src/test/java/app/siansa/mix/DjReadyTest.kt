package app.siansa.mix

import app.siansa.data.AppSettings
import app.siansa.net.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A DJ mix only when its needs are met: AudioMuse answering and Siansa Beats behind its plugin (the owner, 10 Nov). */
class DjReadyTest {
    private val withAudioMuse = AppSettings(audioMuseUrl = "http://192.168.1.10:8000")
    private val answering = ConnectionState(network = true, server = true, audioMuse = true)

    @Test fun withoutAudioMuseThereIsNoDjMix() {
        assertEquals("Needs AudioMuse", DjReady.problem(AppSettings(), answering, true))
    }

    @Test fun audioMuseAwayOrOfflineMeansNotNow() {
        assertEquals("Not available now", DjReady.problem(withAudioMuse, answering.copy(audioMuse = false), true))
        assertEquals("Not available now", DjReady.problem(withAudioMuse, ConnectionState(network = false), true))
    }

    @Test fun withoutSiansaBeatsThereIsNoDjMix() {
        assertEquals("Needs Siansa Beats", DjReady.problem(withAudioMuse, answering, false))
        assertEquals("Checking…", DjReady.problem(withAudioMuse, answering, null))  // not yet: not offered until it's known
    }

    @Test fun everythingAnsweringMakesADjMixPossible() {
        assertNull(DjReady.problem(withAudioMuse, answering, true))
    }

    @Test fun checkedAgainSoonAfterANoAndRarelyAfterAYes() {
        var time = 1_000_000L
        val ready = DjReady { time }
        assertTrue(ready.due())
        ready.checked(false, "no plugin")
        assertFalse(ready.due())
        time += DjReady.AGAIN_FAILED_MS + 1
        assertTrue(ready.due())
        ready.checked(true, "ok")
        time += DjReady.AGAIN_FAILED_MS + 1
        assertFalse(ready.due())
        time += DjReady.AGAIN_OK_MS
        assertTrue(ready.due())
        ready.forget()
        assertNull(ready.beats.value)
    }

    @Test fun everyReasonIsExplained() {
        for (p in listOf("Needs AudioMuse", "Needs Siansa Beats", "Checking…", "Not available now")) {
            assertTrue(DjReady.explain(p).isNotBlank())
        }
    }
}
