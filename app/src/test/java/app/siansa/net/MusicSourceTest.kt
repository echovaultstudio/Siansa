package app.siansa.net

import app.siansa.data.AppSettings
import app.siansa.ui.connectionsRoute
import app.siansa.ui.extrasRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The music source layer (issue #113). */
class MusicSourceTest {
    @Test fun onlyReadySourcesCanBeChosen() {
        assertEquals(listOf(SourceKind.OPENSUBSONIC, SourceKind.EMBY), SourceKind.entries.filter { it.ready })
        // The picker lists every kind, ready ones first.
        assertEquals(SourceKind.OPENSUBSONIC, SourceKind.entries.first())
    }

    @Test fun savedKindIsReadBack() {
        assertEquals(SourceKind.OPENSUBSONIC, SourceKind.parse("OPENSUBSONIC"))
        // Set up before #113, or something unknown: OpenSubsonic, the only source there was.
        assertEquals(SourceKind.OPENSUBSONIC, SourceKind.parse(null))
        assertEquals(SourceKind.OPENSUBSONIC, SourceKind.parse("PLEX"))
        // A kind that isn't ready yet can't be in use.
        assertEquals(SourceKind.OPENSUBSONIC, SourceKind.parse("JELLYFIN"))
        assertEquals(SourceKind.EMBY, SourceKind.parse("EMBY"))
        assertEquals(SourceKind.OPENSUBSONIC, AppSettings().sourceKind)
    }

    @Test fun subsonicIsTheOpenSubsonicSource() {
        val source: MusicSource = SubsonicApi(okhttp3.OkHttpClient()) { AppSettings() }
        assertEquals(SourceKind.OPENSUBSONIC, source.kind)
        assertTrue(source.streamUrl("s1").contains("/rest/stream"))
    }

    @Test fun theActiveSourceFollowsTheSettings() {
        var settings = AppSettings(navidromeUrl = "http://music.local")
        val subsonic = SubsonicApi(okhttp3.OkHttpClient()) { settings }
        val emby = EmbyApi(okhttp3.OkHttpClient(), { settings })
        val active = ActiveSource { if (settings.sourceKind == SourceKind.EMBY) emby else subsonic }
        assertEquals(SourceKind.OPENSUBSONIC, active.kind)
        assertTrue(active.streamUrl("s1").contains("/rest/stream"))
        settings = settings.copy(sourceKind = SourceKind.EMBY)
        assertEquals(SourceKind.EMBY, active.kind)
        assertTrue(active.streamUrl("s1").contains("/Audio/s1/stream"))
    }

    @Test fun anotherUsersMusicGoesToTheirOwnServer() {
        // Issue #427: the user in use is on Navidrome, another shown user is on Emby (and the other way round).
        val navidrome = AppSettings(navidromeUrl = "http://navidrome.local")
        val embyUser = AppSettings(navidromeUrl = "http://emby.local", sourceKind = SourceKind.EMBY)
        val subsonic = SubsonicApi(okhttp3.OkHttpClient()) { navidrome }
        val emby = EmbyApi(okhttp3.OkHttpClient(), { embyUser })
        val theirs = setOf("e1", "ealbum")
        val active = ActiveSource(route = { id -> if (id in theirs) emby else null }) { subsonic }
        assertTrue(active.streamUrl("n1").startsWith("http://navidrome.local/rest/stream"))
        assertTrue(active.streamUrl("e1").startsWith("http://emby.local/Audio/e1/stream"))
        assertTrue(active.coverUrl("ealbum", 300).startsWith("http://emby.local/Items/ealbum/Images/Primary"))
        assertTrue(active.coverUrl("al-n2", 300).startsWith("http://navidrome.local/rest/getCoverArt"))
        // Lists that aren't about one item stay with the user in use.
        assertEquals(SourceKind.OPENSUBSONIC, active.kind)

        val onEmby = ActiveSource(route = { id -> if (id == "n1") subsonic else null }) { emby }
        assertTrue(onEmby.streamUrl("n1").startsWith("http://navidrome.local/rest/stream"))
        assertTrue(onEmby.streamUrl("e1").startsWith("http://emby.local/Audio/e1/stream"))
    }

    @Test fun withEmbyTheAiComesFromAudioMuseAlone() {
        val emby = EmbyApi(okhttp3.OkHttpClient(), { AppSettings() })
        // Emby has no sonic plugin: sound-alikes go to AudioMuse when it's set up.
        kotlinx.coroutines.runBlocking { assertEquals(false, emby.hasSonicMatch()) }
        assertEquals(app.siansa.mix.SonicRoute.AUDIOMUSE, app.siansa.mix.sonicRoute(navidromeSonic = false, audioMuseReady = true))
        // "Your sound": no Navidrome login is sent to AudioMuse for an Emby setup.
        val navidrome = AppSettings(navidromeUser = "max", navidromePassword = "sesame")
        assertEquals("max", navidrome.fingerprintUser)
        val onEmby = navidrome.copy(sourceKind = SourceKind.EMBY)
        assertEquals("", onEmby.fingerprintUser)
        assertEquals("", onEmby.fingerprintPassword)
    }

    @Test fun setupStepsCarryTheChoice() {
        assertEquals("welcome/extras/OPENSUBSONIC", extrasRoute(SourceKind.OPENSUBSONIC))
        assertEquals("settings/connections?source=OPENSUBSONIC&extras=false", connectionsRoute(SourceKind.OPENSUBSONIC, extras = false))
    }
}
