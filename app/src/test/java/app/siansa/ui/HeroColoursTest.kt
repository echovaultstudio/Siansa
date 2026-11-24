package app.siansa.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import app.siansa.data.CardLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #1351 (the owner, 23 Nov: "changing hero cards colours has no effect, it remains the same colour"). */
class HeroColoursTest {
    private val tint = Color(0xFF3A6FB0)
    private val own = Color(0xFFB0306A) to Color(0xFF30206A)

    @Test fun eachCardColourFillsTheHeroItsOwnWay() {
        val fills = listOf(CardLook.VIVID, CardLook.SOFT, CardLook.PLAIN, CardLook.ARTWORK).map { heroColours(it, tint, own) }
        assertEquals(4, fills.toSet().size)
        // Home's own choice of Vivid is Vivid.
        assertEquals(fills[0], heroColours(CardLook.AS_HOME, tint, own))
    }

    @Test fun vividFromItsPictureIsAsItAlwaysWas() {
        val was = lerp(lerp(tint, own.first, 0.35f), Color.Black, 0.25f) to lerp(lerp(tint, own.second, 0.5f), Color.Black, 0.5f)
        assertEquals(was, heroColours(CardLook.VIVID, tint, own))
        // A bright cover too: nothing about the look people have changes.
        val bright = Color(0xFFF2E04A)
        assertEquals(lerp(lerp(bright, own.first, 0.35f), Color.Black, 0.25f), heroColours(CardLook.VIVID, bright, own).first)
    }

    @Test fun plainHasNoColourAndSoftALittle() {
        val plain = heroColours(CardLook.PLAIN, tint, own)
        assertEquals(plain, heroColours(CardLook.PLAIN, Color.Red, Color.Green to Color.Yellow))
        assertEquals(plain.first.red, plain.first.blue, 0.03f)
        val soft = heroColours(CardLook.SOFT, tint, own)
        assertNotEquals(plain, soft)
        assertTrue(soft.first.blue > soft.first.red)  // the tint shows through
    }

    @Test fun fromTheArtworkIsTheTintItself() {
        val (top, foot) = heroColours(CardLook.ARTWORK, Color(0xFF204060), own)
        assertEquals(lerp(Color(0xFF204060), Color.Black, 0.1f), top)
        assertTrue(foot.luminance() < top.luminance())
    }

    /** The row's Colours (Siansa's frost, Mono's light greys) are pale: the white words must still read. */
    @Test fun aPaleColourIsMadeDarkEnoughForWhiteWords() {
        val frost = Color(0xFFE6EEF2) to Color(0xFFB8C8D0)
        for (look in listOf(CardLook.VIVID, CardLook.SOFT, CardLook.ARTWORK)) {
            val (top, foot) = heroColours(look, frost.first, frost, fromPicture = false)
            assertTrue("$look top", top.luminance() <= 0.2f)
            assertTrue("$look foot", foot.luminance() <= 0.2f)
        }
        assertEquals(Color(0xFF101418), readableUnderWhite(Color(0xFF101418)))
    }
}
