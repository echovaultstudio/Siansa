package app.siansa.pairing

import app.siansa.data.AppSettings
import app.siansa.net.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Signing in the TV app from the phone (issue #860). */
class PairingTest {
    private val login = Pairing.Login(url = "https://music.example.org", user = "ana", password = "s3cret pass", proxyUser = "gate")

    @Test
    fun aSealedLoginOpensWithItsKey() {
        val key = Pairing.newKey()
        assertEquals(login, Pairing.open(Pairing.seal(login, key), key))
    }

    @Test
    fun anotherKeyCantOpenIt() {
        val sealed = Pairing.seal(login, Pairing.newKey())
        assertNull(Pairing.open(sealed, Pairing.newKey()))
    }

    @Test
    fun aChangedMessageIsTurnedAway() {
        val key = Pairing.newKey()
        val sealed = Pairing.seal(login, key)
        val changed = sealed.dropLast(2) + if (sealed.endsWith("AA")) "AB" else "AA"
        assertNull(Pairing.open(changed, key))
        assertNull(Pairing.open("not even base64 !", key))
        assertNull(Pairing.open("", key))
    }

    @Test
    fun thePasswordNeverTravelsInTheClear() {
        val sealed = Pairing.seal(login, Pairing.newKey())
        assertTrue("s3cret" !in sealed)
    }

    @Test
    fun theAppLinkReadsBack() {
        val key = Pairing.newKey()
        val link = Pairing.parse(Pairing.appLink("192.168.1.20:41234", key))
        assertNotNull(link)
        assertEquals("192.168.1.20", link!!.host)
        assertEquals(41234, link.port)
        assertTrue(link.key.contentEquals(key))
    }

    @Test
    fun onlyHomeNetworkAddressesAreTrusted() {
        val key = Pairing.encodeKey(Pairing.newKey())
        // Anywhere outside the home network could collect the login: refused.
        assertNull(Pairing.parse("siansa://pair?tv=8.8.8.8:80&k=$key"))
        assertNull(Pairing.parse("siansa://pair?tv=127.0.0.1:80&k=$key"))
        assertNull(Pairing.parse("siansa://pair?tv=music.example.org:80&k=$key"))
        assertNull(Pairing.parse("siansa://pair?tv=10.0.0.5&k=$key"))
        assertNull(Pairing.parse("siansa://pair?tv=10.0.0.5:99999&k=$key"))
        assertNotNull(Pairing.parse("siansa://pair?tv=10.0.0.5:8123&k=$key"))
        assertNotNull(Pairing.parse("siansa://pair?tv=172.20.1.2:8123&k=$key"))
    }

    @Test
    fun aShortOrMissingKeyIsRefused() {
        assertNull(Pairing.parse("siansa://pair?tv=192.168.1.20:8123"))
        assertNull(Pairing.parse("siansa://pair?tv=192.168.1.20:8123&k=abcd"))
        assertNull(Pairing.parse("https://pair?tv=192.168.1.20:8123&k=abcd"))
        assertNull(Pairing.parse(null))
    }

    @Test
    fun theKeyStaysOutOfWhatTheBrowserSends() {
        val key = Pairing.newKey()
        val page = Pairing.pageUrl("192.168.1.20", 8123, key)
        assertEquals("http://192.168.1.20:8123/", page.substringBefore('#'))
        assertEquals(Pairing.encodeKey(key), page.substringAfter('#'))
    }

    @Test
    fun theLoginGoesIntoTheTvsSettingsAndNothingElse() {
        val before = AppSettings(transcodeWifiKbps = 160, sourceKind = SourceKind.EMBY)
        val after = login.into(before)
        assertEquals(SourceKind.OPENSUBSONIC, after.sourceKind)
        assertEquals("https://music.example.org", after.navidromeUrl)
        assertEquals("ana", after.navidromeUser)
        assertEquals("s3cret pass", after.navidromePassword)
        assertEquals("gate", after.proxyUser)
        assertEquals(160, after.transcodeWifiKbps)
        assertEquals(login, Pairing.Login.of(after))
    }

    @Test
    fun theTickedServicesTravelAndAreSetUpOnTheTv() {
        val phone = AppSettings(
            navidromeUrl = "https://music.example.org", navidromeUser = "ana", navidromePassword = "pw",
            audioMuseUrl = "http://192.168.1.10:8000", audioMuseToken = "am-token",
            lastFmEnabled = true, lastFmApiKey = "k", lastFmSecret = "s", lastFmUser = "ana", lastFmSession = "sess",
            listenBrainzEnabled = false, listenBrainzToken = "lb-off",
            scrobblePercent = 75,
        )
        val sent = Pairing.Login.of(phone, withServices = true)
        val key = Pairing.newKey()
        val arrived = Pairing.open(Pairing.seal(sent, key), key)
        assertEquals(sent, arrived)
        assertEquals(listOf("AudioMuse-AI", "Last.fm"), arrived!!.services!!.names)
        // A TV with its own ListenBrainz keeps it: the phone's is switched off, so it isn't sent.
        val tv = arrived.into(AppSettings(listenBrainzEnabled = true, listenBrainzToken = "tv-lb"))
        assertEquals("http://192.168.1.10:8000", tv.audioMuseUrl)
        assertEquals("am-token", tv.audioMuseToken)
        assertTrue(tv.lastFmReady)
        assertEquals("sess", tv.lastFmSession)
        assertEquals("tv-lb", tv.listenBrainzToken)
        assertEquals(75, tv.scrobblePercent)
        assertTrue("am-token" !in Pairing.seal(sent, key))
    }

    @Test
    fun withoutTheTickOnlyTheLoginTravels() {
        val phone = AppSettings(navidromeUrl = "https://music.example.org", navidromeUser = "ana", navidromePassword = "pw", audioMuseUrl = "http://am")
        val sent = Pairing.Login.of(phone)
        assertNull(sent.services)
        assertEquals("", sent.into(AppSettings()).audioMuseUrl)
    }

    @Test
    fun anOlderPhonesLoginStillOpens() {
        // A phone without the tick sends no services at all; the TV reads it as before.
        val key = Pairing.newKey()
        assertNull(Pairing.open(Pairing.seal(login, key), key)!!.services)
    }
}
