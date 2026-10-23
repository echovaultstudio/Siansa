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
        assertEquals(listOf(SourceKind.OPENSUBSONIC), SourceKind.entries.filter { it.ready })
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
        assertEquals(SourceKind.OPENSUBSONIC, AppSettings().sourceKind)
    }

    @Test fun subsonicIsTheOpenSubsonicSource() {
        val source: MusicSource = SubsonicApi(okhttp3.OkHttpClient()) { AppSettings() }
        assertEquals(SourceKind.OPENSUBSONIC, source.kind)
        assertTrue(source.streamUrl("s1").contains("/rest/stream"))
    }

    @Test fun setupStepsCarryTheChoice() {
        assertEquals("welcome/extras/OPENSUBSONIC", extrasRoute(SourceKind.OPENSUBSONIC))
        assertEquals("settings/connections?source=OPENSUBSONIC&extras=false", connectionsRoute(SourceKind.OPENSUBSONIC, extras = false))
    }
}
