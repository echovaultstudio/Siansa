package app.siansa.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.siansa.data.CardLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #1351 (the owner, 23 Nov: "changing hero cards colours has no effect"); 25 Nov: Your listening's colours. */
class HeroColoursTest {
    private val tint = Color(0xFF3A6FB0)
    private val own = Color(0xFFB0306A) to Color(0xFF30206A)

    @Test fun eachCardColourFillsTheHeroItsOwnWay() {
        val fills = CardLook.forHome.map { heroColours(it, tint, own) }
        assertEquals(CardLook.forHome.size, fills.toSet().size)
        // Nothing narrower than As Home: the default colour.
        assertEquals(heroColours(CardLook.DEFAULT, tint, own), heroColours(CardLook.AS_HOME, tint, own))
    }

    @Test fun aColourIsItsOwnFadeWhateverThePicture() {
        val plum = app.siansa.data.ListenColour.PLUM
        assertEquals(Color(plum.top) to Color(plum.foot), heroColours(CardLook.PLUM, tint, own))
        assertEquals(heroColours(CardLook.PLUM, tint, own), heroColours(CardLook.PLUM, Color.Red, Color.Green to Color.Yellow))
    }

    @Test fun fromItsCoverIsThePicturesColourDarkened() {
        val (top, foot) = heroColours(CardLook.COVER, Color(0xFF204060), own)
        assertEquals(lerp(Color(0xFF204060), Color.Black, COVER_TOP), top)
        assertTrue(foot.luminance() < top.luminance())
    }

    /** The row's Colours (Siansa's frost, Mono's light greys) are pale: the white words must still read. */
    @Test fun aPaleColourIsMadeDarkEnoughForWhiteWords() {
        val frost = Color(0xFFE6EEF2) to Color(0xFFB8C8D0)
        for (look in listOf(CardLook.AS_HOME, CardLook.COVER)) {
            val (top, foot) = heroColours(look, frost.first, frost, fromPicture = false)
            assertTrue("$look top", top.luminance() <= 0.2f)
            assertTrue("$look foot", foot.luminance() <= 0.2f)
        }
        assertEquals(Color(0xFF101418), readableUnderWhite(Color(0xFF101418)))
    }
}
