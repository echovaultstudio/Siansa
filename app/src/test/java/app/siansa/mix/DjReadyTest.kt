package app.siansa.mix

import app.siansa.data.AppSettings
import app.siansa.net.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A DJ mix only when its needs are met (the owner, 10 Nov), and those are Siansa Beats alone (11 Nov): set up,
 * answering, and with the songs' sound, so the tempos are Beat This!'s. AudioMuse-AI neither makes one possible nor
 * is needed for one.
 */
class DjReadyTest {
    private val withBeats = AppSettings(beatsUrl = "http://192.168.1.10:8090")
    private val withBoth = withBeats.copy(audioMuseUrl = "http://192.168.1.10:8000")
    private val answering = ConnectionState(network = true, server = true, audioMuse = null)

    @Test fun withoutSiansaBeatsThereIsNoDjMix() {
        assertEquals("Needs Siansa Beats", DjReady.problem(AppSettings(), answering, true, beatsSound = true))
        // AudioMuse-AI alone, even with the Siansa plugin answering, doesn't make one possible any more.
        assertEquals("Needs Siansa Beats", DjReady.problem(AppSettings(audioMuseUrl = "http://192.168.1.10:8000"), answering.copy(audioMuse = true), true, beatsSound = true))
        assertEquals("Needs Siansa Beats", DjReady.problem(withBeats, answering, false, beatsSound = true))
        assertEquals("Checking…", DjReady.problem(withBeats, answering, null, beatsSound = true))  // not yet: not offered until it's known
    }

    @Test fun siansaBeatsNeedsTheSongsSound() {
        // Before 0.25, or with nothing taken over yet: the order would have to go by AudioMuse-AI's tempos.
        assertEquals("Needs Siansa Beats 0.25", DjReady.problem(withBoth, answering.copy(audioMuse = true), true, beatsSound = false))
        assertTrue(DjReady.explain(DjReady.NEEDS_SOUND).contains("analyses your library"))
    }

    @Test fun offlineMeansNotNow() {
        assertEquals("Not available now", DjReady.problem(withBeats, ConnectionState(network = false), true, beatsSound = true))
    }

    @Test fun audioMuseIsNotNeededAndItsBeingAwayDoesntMatter() {
        assertNull(DjReady.problem(withBeats, answering, true, beatsSound = true))
        assertNull(DjReady.problem(withBoth, answering.copy(audioMuse = false), true, beatsSound = true))
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
        for (p in listOf("Needs AudioMuse-AI", "Needs Siansa Beats", "Checking…", "Not available now")) {
            assertTrue(DjReady.explain(p).isNotBlank())
        }
    }
}
