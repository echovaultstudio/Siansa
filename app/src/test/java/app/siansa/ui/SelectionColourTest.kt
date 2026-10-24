package app.siansa.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The accent colour, chosen in Themes (issues #313, #404). */
class SelectionColourTest {
    @Test fun matchThemeUsesTheThemesAccent() {
        assertEquals(Brand.Amber, selectionColourOf(SelectionColour.THEME, Brand.Amber, dark = true))
        val ocean = Color(0xFF1F6FD1)
        assertEquals(ocean, selectionColourOf(SelectionColour.THEME, ocean, dark = false))
    }

    @Test fun monoIsBlackOnLightAndWhiteOnDark() {
        // Issue #404: a mid grey (or amber) was hard to see on a white artist page.
        assertEquals(MonoOnLight, selectionColourOf(SelectionColour.THEME, Color(0xFF777777), dark = false))
        assertEquals(MonoOnDark, selectionColourOf(SelectionColour.THEME, Color(0xFF777777), dark = true))
    }

    @Test fun aChosenColourIsUsedAsItIs() {
        assertEquals(Color(0xFF2BB5A8), selectionColourOf(SelectionColour.TEAL, Brand.Amber, dark = true))
    }

    @Test fun aFaintAccentGetsAShadeThatStandsOut() {
        val white = Color.White
        val deeper = readableOn(Brand.Amber, white)
        assertNotEquals(Brand.Amber, deeper)
        assertTrue(contrastOf(deeper, white) >= ACCENT_MIN_CONTRAST)
        // Already readable: left as it is.
        assertEquals(Brand.Amber, readableOn(Brand.Amber, Color.Black))
        // A dark blue on black gets lighter.
        val navy = Color(0xFF1F3F8F)
        assertTrue(luminanceOf(readableOn(navy, Color.Black)) > luminanceOf(navy))
    }

    /** Every theme × every accent × light, dark and pure black: the accent can be seen on the page. */
    @Test fun everyAccentCanBeSeenOnEveryTheme() {
        data class Page(val name: String, val themeAccent: Color, val surface: Color, val dark: Boolean)
        val pages = buildList {
            add(Page("Siansa light", Brand.Amber, Color(0xFFFBF8FF), false))
            add(Page("Siansa dark", Brand.Amber, Brand.Plum, true))
            add(Page("Siansa black", Brand.Amber, Color.Black, true))
            ThemeColours.entries.filter { it.seed != null }.forEach { t ->
                for ((label, dark, black) in listOf(Triple("light", false, false), Triple("dark", true, false), Triple("black", true, true))) {
                    val r = ThemeTones.scheme(t.seed!!.toInt(), dark, mono = t == ThemeColours.MONO, black = black)
                    add(Page("${t.label} $label", Color(r.primary), Color(r.surface), dark))
                }
            }
        }
        for (page in pages) for (choice in SelectionColour.entries) {
            val accent = accentFor(selectionColourOf(choice, page.themeAccent, page.dark), page.surface)
            val ratio = contrastOf(accent, page.surface)
            assertTrue("${choice.label} on ${page.name}: $ratio", ratio >= ACCENT_MIN_CONTRAST)
        }
    }

    @Test fun textOnItStaysReadable() {
        assertEquals(Color(0xFF1A1400), onSelectionColourOf(Brand.Amber))
        assertEquals(Color.White, onSelectionColourOf(Color(0xFF5B3FD0)))
        assertEquals(Color.White, onSelectionColourOf(MonoOnLight))
        assertEquals(Color(0xFF1A1400), onSelectionColourOf(MonoOnDark))
    }

    @Test fun greyIsTold() {
        assertTrue(isGreyish(Color(0xFF777777)))
        assertFalse(isGreyish(Brand.Amber))
    }

    @Test fun unknownSavedValueIsTheTheme() {
        assertEquals(SelectionColour.THEME, SelectionColour.parse(null))
        assertEquals(SelectionColour.THEME, SelectionColour.parse("gone"))
        assertEquals(SelectionColour.PINK, SelectionColour.parse("PINK"))
        assertEquals("Match theme", SelectionColour.THEME.label)
    }
}
