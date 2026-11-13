package app.siansa.ui

import app.siansa.data.AppSettings
import app.siansa.data.Service
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Services (the owner, 13 Nov): a service switched off is as if it weren't set up; For the best Siansa says what to look at. */
class ServicesTest {
    private val all = AppSettings(
        navidromeUrl = "https://music.example", navidromeUser = "max", navidromePassword = "x",
        beatsUrl = "http://beats:8090", audioMuseUrl = "http://am:8000",
        audiobookshelfUrl = "http://abs", audiobookshelfToken = "t",
        lastFmEnabled = true, lastFmApiKey = "k", lastFmSecret = "s", lastFmUser = "max", lastFmSession = "sess",
        listenBrainzEnabled = true, listenBrainzToken = "lb",
        aiKey = "key",
    )

    @Test fun aServiceSwitchedOffIsNotReady() {
        assertTrue(all.beatsReady && all.audioMuseReady && all.booksReady && all.aiReady)
        val off = all.copy(servicesOff = setOf("beats", "audiomuse", "books", "ai"))
        assertFalse(off.beatsReady || off.audioMuseReady || off.booksReady || off.aiReady)
        // The logins stay: the service is set up, only off.
        assertTrue(Service.setUp(off, Service.BEATS) && Service.setUp(off, Service.AUDIOMUSE) && Service.setUp(off, Service.BOOKS) && Service.setUp(off, Service.AI))
        assertFalse(Service.on(off, Service.BEATS))
        // Last.fm and ListenBrainz keep their own switches.
        assertTrue(Service.on(all, Service.LASTFM) && Service.on(all, Service.LISTENBRAINZ))
        assertFalse(Service.on(all.copy(lastFmEnabled = false), Service.LASTFM))
    }

    @Test fun forTheBestSiansaSaysWhatIsNotNeededAndWhatIsBroken() {
        val lines = bestLines(all, mapOf(ServiceHealth.SERVER to ServiceCheck(true, "Connected to Navidrome 0.64")))
        assertEquals("ok", lines[0].mark)
        assertTrue(lines[0].words.startsWith("Music server · Connected"))
        // Siansa Beats on: AudioMuse-AI isn't needed, with the offer to turn it off.
        val audioMuse = lines.first { it.words.startsWith("AudioMuse-AI") }
        assertEquals("warn", audioMuse.mark)
        assertEquals(Service.AUDIOMUSE, audioMuse.turnOff)
        // Off: just a dash, no offer.
        val quiet = bestLines(all.copy(servicesOff = setOf("audiomuse")), emptyMap()).first { it.words.startsWith("AudioMuse-AI") }
        assertEquals("dash", quiet.mark)
        assertNull(quiet.turnOff)
        // Neither: red.
        val none = bestLines(all.copy(servicesOff = setOf("audiomuse", "beats")), emptyMap())
        assertEquals("bad", none.first { it.words.startsWith("Neither") }.mark)
        // A service that doesn't answer is red with what the check said.
        val broken = bestLines(all, mapOf("beats" to ServiceCheck(false, "Doesn't answer")))
        assertEquals("bad", broken.first { it.words.startsWith("Siansa Beats") }.mark)
        assertTrue(broken.first { it.words.startsWith("Siansa Beats") }.words.endsWith("Doesn't answer"))
        // Last.fm without a session: not signed in, with what's waiting.
        val lfm = ServiceHealth.lastFmCheck(all.copy(lastFmSession = ""), 12)
        assertEquals(false, lfm.ok)
        assertEquals("Not signed in · 12 listens waiting", lfm.words)
        assertEquals("Signed in as max", ServiceHealth.lastFmCheck(all, 0).words)
    }
}
