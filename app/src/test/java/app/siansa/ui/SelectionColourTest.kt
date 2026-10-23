package app.siansa.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The colour of selected items, chosen in Themes (issue #313). */
class SelectionColourTest {
    @Test fun themeUsesTheAccentButNotGrey() {
        assertEquals(Brand.Amber, selectionColourOf(SelectionColour.THEME, Brand.Amber))
        val ocean = Color(0xFF1F6FD1)
        assertEquals(ocean, selectionColourOf(SelectionColour.THEME, ocean))
        // A grey theme (Mono) would make selected items look like the rest: amber instead.
        assertEquals(Brand.Amber, selectionColourOf(SelectionColour.THEME, Color(0xFF777777)))
    }

    @Test fun aChosenColourIsUsedAsItIs() {
        assertEquals(Color(0xFF2BB5A8), selectionColourOf(SelectionColour.TEAL, Brand.Amber))
    }

    @Test fun textOnItStaysReadable() {
        assertEquals(Color(0xFF1A1400), onSelectionColourOf(Brand.Amber))
        assertEquals(Color.White, onSelectionColourOf(Color(0xFF5B3FD0)))
    }

    @Test fun greyIsTold() {
        assertTrue(isGreyish(Color(0xFF777777)))
        assertFalse(isGreyish(Brand.Amber))
    }

    @Test fun unknownSavedValueIsTheTheme() {
        assertEquals(SelectionColour.THEME, SelectionColour.parse(null))
        assertEquals(SelectionColour.THEME, SelectionColour.parse("gone"))
        assertEquals(SelectionColour.PINK, SelectionColour.parse("PINK"))
    }
}
