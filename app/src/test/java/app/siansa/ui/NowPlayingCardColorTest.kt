package app.siansa.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Now playing's cards take a deep shade of the cover's colour (#717). */
class NowPlayingCardColorTest {
    // The page tint as rememberCoverColor gives it: the cover's colour toned a third towards black.
    private fun page(r: Int, g: Int, b: Int) = androidx.compose.ui.graphics.lerp(Color(r, g, b), Color.Black, 0.35f)

    @Test fun aRedCoverGivesADarkRedCard() {
        val card = nowPlayingCardColor(page(156, 36, 36)) // Vaya Con Dios
        assertTrue("red stays the strongest channel: $card", card.red > card.green * 1.8f && card.red > card.blue * 1.8f)
        assertInRange(card)
    }

    @Test fun anOrangeCoverGivesAWarmBrownCard() {
        val card = nowPlayingCardColor(page(252, 156, 60)) // Rohaan
        assertTrue("warm: $card", card.red > card.green && card.green > card.blue)
        assertInRange(card)
    }

    @Test fun aBrightCoverIsTonedDownForWhiteText() {
        assertInRange(nowPlayingCardColor(page(255, 255, 255)))
        assertInRange(nowPlayingCardColor(page(255, 240, 0)))
    }

    @Test fun aBlackAndWhiteCoverGivesADarkGreyThatStillStandsOut() {
        val card = nowPlayingCardColor(page(12, 12, 12)) // gyrofield
        assertInRange(card)
        assertEquals(card.red, card.green, 0.01f)
        assertEquals(card.green, card.blue, 0.01f)
        assertInRange(nowPlayingCardColor(Color.Black))
    }

    @Test fun theCardsStandOutFromThePageBehindThem() {
        for (c in listOf(page(156, 36, 36), page(252, 156, 60), page(12, 12, 12), page(40, 90, 200))) {
            assertTrue(lightness(nowPlayingCardColor(c)) > lightness(pageBottomColor(c)))
        }
    }

    private fun assertInRange(c: Color) {
        val l = lightness(c)
        assertTrue("lightness $l of $c", l >= CARD_LIGHT_MIN - 0.005f && l <= CARD_LIGHT_MAX + 0.005f)
    }
}
