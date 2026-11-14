package app.siansa.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One reading of a cover gives its page's header and its Play, kept on the phone (the owner, 14 Nov). */
class CoverTonesTest {
    @Test fun aColourfulCoversHeaderIsItsOwnColour() {
        val red = Color(0.7f, 0.2f, 0.2f)
        assertEquals(red, CoverTones(red, Color(0.3f, 0.3f, 0.3f)).main)
    }

    @Test fun aGreyCoversHeaderIsItsAverageGrey() {
        val average = Color(0.35f, 0.35f, 0.36f)
        assertEquals(average, CoverTones(Color(0.5f, 0.5f, 0.5f), average).main)
    }

    @Test fun anUnreadableCoverHasNoHeaderColourAndIsntKept() {
        assertEquals(Color.Unspecified, CoverTones.UNREADABLE.main)
        assertNull(CoverTones.line("al-1", CoverTones.UNREADABLE))
    }

    @Test fun keptLinesReadBack() {
        val t = CoverTones(Color(0xFF8899AA), Color(0xFF223344))
        val (id, back) = CoverTones.parse(CoverTones.line("al-1_abc", t)!!)!!
        assertEquals("al-1_abc", id)
        assertEquals(t, back)
        assertNull(CoverTones.parse("broken"))
    }

    @Test fun aGreyPhotoWithABlueTintGivesANearSilverPlayNotSteelBlue() {
        // A dark grey with a little blue, as a black and white photo with blue eyes gives.
        val tinted = Color(0.30f, 0.33f, 0.40f)
        val play = DynamicAccent.of(tinted, Color(0.08f, 0.08f, 0.09f))!!.controls
        assertTrue("chroma ${DynamicAccent.chroma(play)}", DynamicAccent.chroma(play) < 0.2f)
    }
}
