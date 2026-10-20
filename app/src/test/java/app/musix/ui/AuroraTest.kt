package app.musix.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Your listening header made from the top artists' colours (issue #327). */
class AuroraTest {
    @Test fun missingColoursComeFromTheDefaults() {
        val c = auroraColours(listOf(null, Color(0xFF2E7880)))
        assertEquals(3, c.size)
        assertEquals(readableTone(AURORA_DEFAULTS[0]), c[0])
        assertEquals(readableTone(Color(0xFF2E7880)), c[1])
        assertEquals(readableTone(AURORA_DEFAULTS[2]), c[2])
    }

    @Test fun lightAndNeonColoursAreToned() {
        // A white cover: no lighter than 50 %, so white text stays readable on it.
        assertTrue(toHsl(readableTone(Color.White)).third <= 0.5f + 1e-3f)
        // A near-black cover: still a visible colour.
        assertTrue(toHsl(readableTone(Color(0xFF050505))).third >= 0.3f - 1e-3f)
        // A neon green: saturation at most 80 %.
        assertTrue(toHsl(readableTone(Color(0xFF00FF00))).second <= 0.8f + 1e-3f)
    }

    @Test fun hslRoundTrips() {
        val teal = Color(0xFF2E7880)
        val (h, s, l) = toHsl(teal)
        val back = fromHsl(h, s, l)
        assertEquals(teal.red, back.red, 0.01f)
        assertEquals(teal.green, back.green, 0.01f)
        assertEquals(teal.blue, back.blue, 0.01f)
    }
}
