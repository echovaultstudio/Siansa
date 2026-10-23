package app.musix.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #176: every lyrics line stays readable, whatever the cover's colour. */
class LyricsColorsTest {
    /** Cover colours as the player gets them: toned down by a third towards black (rememberCoverColor). */
    private fun cover(argb: Long) = lerp(Color(argb), Color.Black, 0.35f)

    private val covers = listOf(
        0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFFFFEB3B, 0xFFC8FF00, 0xFFE53935,
        0xFF141E50, 0xFF0E9AA7, 0xFFFF9800, 0xFFF8BBD0, 0xFF00E5FF, 0xFF6A1B9A,
    ).map(::cover)

    @Test fun linesToComeAreReadableOnEveryCover() {
        for (tint in covers) {
            val bg = lyricsBackground(tint)
            val upcoming = worstContrast(LyricsTextColors.upcoming, bg)
            // Big bold text: WCAG's 3:1 for large text (issue #362 made them darker, to stand apart from the words
            // being sung). Still clearer than the sung lines, which pass the same check.
            assertTrue("Lines to come on $tint: contrast $upcoming", upcoming >= 3.0)
            assertTrue("Lines to come clearer than sung ones on $tint", upcoming > worstContrast(LyricsTextColors.sung, bg))
        }
    }

    @Test fun lineNowAndSungLinesAreReadableOnEveryCover() {
        for (tint in covers) {
            val bg = lyricsBackground(tint)
            assertTrue("Line playing now on $tint", worstContrast(LyricsTextColors.current, bg) >= LYRICS_WHITE_CONTRAST)
            // Big bold text: 3:1 is WCAG's level for large text.
            assertTrue("Sung lines on $tint", worstContrast(LyricsTextColors.sung, bg) >= 3.0)
        }
    }

    @Test fun linesStepDownFromTheLineNow() {
        assertTrue(LyricsTextColors.current.alpha > LyricsTextColors.upcoming.alpha)
        assertTrue(LyricsTextColors.upcoming.alpha > LyricsTextColors.sung.alpha)
    }

    /** A cover colour that's already dark enough keeps the background it had before. */
    @Test fun darkCoverKeepsItsBackground() {
        val tint = cover(0xFF141E50)
        assertEquals(listOf(lerp(tint, Color.Black, 0.1f), lerp(tint, Color.Black, 0.4f)), lyricsBackground(tint))
    }

    /** A bright cover is darkened, keeping its hue, rather than turned grey. */
    @Test fun brightCoverIsDarkenedKeepingItsHue() {
        val tint = cover(0xFFFFEB3B)
        val top = lyricsBackground(tint).first()
        assertTrue("darker than before", top.red < lerp(tint, Color.Black, 0.1f).red)
        assertTrue("still yellow: $top", top.red > top.blue * 2 && top.green > top.blue * 2)
    }
}
