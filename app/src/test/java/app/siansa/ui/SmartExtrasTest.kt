package app.siansa.ui

import app.siansa.net.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** First-run setup's Smart extras step offers Siansa Beats where this build has a page for it, else AudioMuse-AI. */
class SmartExtrasTest {
    @Test fun setupOffersTheServiceThisBuildHas() {
        assertEquals("Siansa Beats", smartExtrasName(beats = true))
        assertEquals("AudioMuse-AI", smartExtrasName(beats = false))
        assertTrue(smartExtrasWords(true, SourceKind.JELLYFIN).contains("Siansa Beats"))
        assertFalse(smartExtrasWords(false, SourceKind.JELLYFIN).contains("Siansa Beats"))
        assertTrue(smartExtrasWords(false, SourceKind.JELLYFIN).contains("Jellyfin"))
    }
}
