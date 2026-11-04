package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** What Play on says under a Cast device's name (issue #114): never what the TV shows ("Casting: Run Away"). */
class CastKindTest {
    @Test fun tvsSpeakersAndGroups() {
        assertEquals("Chromecast", CastKind.of(group = false, speakerRoute = false, audioOnly = false).detail)
        assertEquals("Chromecast · speaker", CastKind.of(group = false, speakerRoute = true, audioOnly = false).detail)
        assertEquals("Chromecast · speaker", CastKind.of(group = false, speakerRoute = false, audioOnly = true).detail)
        assertEquals("Chromecast · speaker group", CastKind.of(group = true, speakerRoute = true, audioOnly = true).detail)
    }
}
