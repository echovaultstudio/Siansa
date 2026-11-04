package app.siansa.ui

import app.siansa.data.AppSettings
import app.siansa.data.defaultSelectionFor
import app.siansa.mix.MixShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** A fresh install's look (the owner, 0.52.0-dev): Siansa's colours, grey pages and cards, the accent matching the theme. */
class DefaultThemeTest {
    @Test fun aFreshInstallIsSiansaWithGreyAndMatchTheme() {
        val choice = themeChoiceOf(AppSettings())
        assertEquals(ThemeColours.SIANSA, choice.colours)
        assertEquals(SelectionColour.THEME, choice.selection)
        assertEquals(ThemeMode.AUTO, choice.mode)
        assertEquals(ThemeChoice(), choice)
        assertEquals("GREY", AppSettings().surfaceColour)
    }

    @Test fun anAccentNeverSavedMatchesTheTheme() {
        assertEquals("THEME", defaultSelectionFor(savedColours = null))
        assertEquals("THEME", defaultSelectionFor(savedColours = "OCEAN"))
    }

    @Test fun theirPickedThemeStays() {
        val choice = themeChoiceOf(AppSettings(themeColours = "OCEAN", themeSelection = "AMBER", themeMode = "DARK"))
        assertEquals(ThemeColours.OCEAN, choice.colours)
        assertEquals(SelectionColour.AMBER, choice.selection)
        assertEquals(ThemeMode.DARK, choice.mode)
    }

    @Test fun aMixStartsAsSteadyVibesWithNoSurprises() {
        assertEquals(MixShape.STEADY, AppSettings().mixShape)
        assertEquals(0, AppSettings().mixSurprise)
        assertEquals(MixShape.STEADY, MixShape.parse(null))
    }
}
