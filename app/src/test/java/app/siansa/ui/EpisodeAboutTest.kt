package app.siansa.ui

import app.siansa.net.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A podcast episode's own description, shown under it (issue #630). */
class EpisodeAboutTest {
    private fun ep(description: String?) = Episode("e", "p", "T", description, 0L, 1800, null, null)

    @Test fun showNotesKeepTheirParagraphsAndListItems() {
        val html = "<p>Liam sits down with <b>Naomi</b>&#39;s bodyguard.</p><p>Guests:</p><ul><li>Jo Smith</li><li>Sam Lee</li></ul>"
        assertEquals("Liam sits down with Naomi's bodyguard.\n\nGuests:\n\n• Jo Smith\n• Sam Lee", episodeAbout(ep(html)))
    }

    @Test fun blankRunsAreSqueezed() {
        assertEquals("One\n\nTwo", episodeAbout(ep("One<br><br><br><br>Two")))
    }

    @Test fun noDescriptionMeansNoArrow() {
        assertNull(episodeAbout(ep(null)))
        assertNull(episodeAbout(ep("  <p> </p> ")))
    }
}
