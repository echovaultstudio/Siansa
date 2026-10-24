package app.musix.net

import app.musix.data.AppSettings
import app.musix.ui.connectionsRoute
import app.musix.ui.extrasRoute
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

    @Test fun withEmbyTheAiComesFromAudioMuseAlone() {
        val emby = EmbyApi(okhttp3.OkHttpClient(), { AppSettings() })
        // Emby has no sonic plugin: sound-alikes go to AudioMuse when it's set up.
        kotlinx.coroutines.runBlocking { assertEquals(false, emby.hasSonicMatch()) }
        assertEquals(app.musix.mix.SonicRoute.AUDIOMUSE, app.musix.mix.sonicRoute(navidromeSonic = false, audioMuseReady = true))
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
