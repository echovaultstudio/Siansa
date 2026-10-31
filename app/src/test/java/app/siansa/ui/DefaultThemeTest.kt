package app.siansa.ui

import androidx.compose.ui.graphics.Color
import app.siansa.data.AppSettings
import app.siansa.data.defaultSelectionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fresh install's theme (issue #728): Mono colours with the Amber accent; a theme someone picked stays. */
class DefaultThemeTest {
    @Test fun aFreshInstallIsMonoWithAmber() {
        val choice = themeChoiceOf(AppSettings())
        assertEquals(ThemeColours.MONO, choice.colours)
        assertEquals(SelectionColour.AMBER, choice.selection)
        assertEquals(ThemeMode.AUTO, choice.mode)
        assertEquals(ThemeChoice(), choice)
    }

    @Test fun nothingSavedGetsAmber() {
        assertEquals("AMBER", defaultSelectionFor(savedColours = null))
    }

    @Test fun someoneWhoPickedColoursKeepsMatchTheme() {
        // Their accent has followed their colours until now; it doesn't turn amber on them.
        assertEquals("THEME", defaultSelectionFor(savedColours = "OCEAN"))
        assertEquals("THEME", defaultSelectionFor(savedColours = "SIANSA"))
    }

    @Test fun theirPickedThemeStays() {
        val choice = themeChoiceOf(AppSettings(themeColours = "SIANSA", themeSelection = "THEME", themeMode = "DARK"))
        assertEquals(ThemeColours.SIANSA, choice.colours)
        assertEquals(SelectionColour.THEME, choice.selection)
        assertEquals(ThemeMode.DARK, choice.mode)
    }

    @Test fun amberCanBeSeenOnMonoInLightAndDark() {
        val amber = Color(SelectionColour.AMBER.argb!!)
        for (dark in listOf(false, true)) {
            val page = Color(ThemeTones.scheme(ThemeColours.MONO.seed!!.toInt(), dark, mono = true).surface)
            val accent = accentFor(amber, page)
            assertTrue("amber on ${if (dark) "dark" else "light"} Mono: ${contrastOf(accent, page)}", contrastOf(accent, page) >= ACCENT_MIN_CONTRAST)
        }
    }
}
