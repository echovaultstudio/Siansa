package app.siansa.data

import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.siansa.net.TrackFeatures
import app.siansa.ui.hsl
import app.siansa.ui.lift
import app.siansa.ui.moodPaint
import app.siansa.ui.setPaint
import app.siansa.ui.stickerSpots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A card's picture colours (the owner, 20 Nov: "dull colours with bad fades are my biggest gripe"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PicturePaintTest {
    @Test fun aDullColourIsMadeBright() {
        val (_, s, l) = hsl(lift(Color(0xFF6B5E4E)))
        assertTrue("colourful enough: $s", s >= 0.57f)
        assertTrue("bright enough: $l", l in 0.49f..0.69f)
    }

    @Test fun aGreyCoverTurnsSilverNotGrey() {
        val (h, s, l) = hsl(lift(Color(0xFF777777)))
        assertEquals(220f, h, 1f)
        assertTrue(s > 0.2f && l >= 0.69f)
    }

    @Test fun aSetGivesACardTheSamePairEveryTime() {
        assertEquals(setPaint(PictureColours.OCEAN, "Night Drive"), setPaint(PictureColours.OCEAN, "Night Drive"))
        assertNull(setPaint(PictureColours.COVERS, "Night Drive"))
        assertNull(setPaint(PictureColours.MOOD, "Night Drive"))
    }

    private fun song(id: String, arousal: Double?, vararg traits: Pair<String, Double>) =
        TrackFeatures(id, "", null, null, null, emptyMap(), emptyMap(), arousal = arousal, traits = traits.toMap())

    @Test fun aMixTakesItsStrongestMoodsColours() {
        val calm = moodPaint(listOf(song("1", 2.0, "relaxed" to 0.9, "happy" to 0.3), song("2", 2.5, "relaxed" to 0.7, "happy" to 0.4)))!!
        val (h, _, _) = hsl(calm.first)
        // Relaxed is teal.
        assertTrue("teal: $h", h in 150f..185f)
        val loud = moodPaint(listOf(song("1", 8.5, "party" to 0.9, "happy" to 0.6)))!!
        // A loud mix is brighter than a calm one.
        assertTrue(hsl(loud.first).third > hsl(calm.first).third)
        assertNotEquals(calm.first, loud.first)
    }

    @Test fun noMoodsNoColour() {
        assertNull(moodPaint(listOf(song("1", null))))
        assertNull(moodPaint(emptyList()))
    }

    @Test fun keptPerRowAndClearedByReset() {
        fun settings() = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings().setHomePictureColours(HomeSection.MADE_FOR_YOU, PictureColours.MOOD)
        settings().setHomePictureColours(HomeSection.QUICK, PictureColours.NEON)
        settings().setHomePictureColours(HomeSection.QUICK, PictureColours.COVERS)
        assertEquals(mapOf(HomeSection.MADE_FOR_YOU to PictureColours.MOOD), settings().state.value.homePictureColours)
        assertEquals(mapOf(HomeSection.QUICK to PictureColours.PASTEL), PictureColours.parse("quick:pastel,nothing:neon"))
        settings().resetHomeLooks()
        assertEquals(emptyMap<HomeSection, PictureColours>(), settings().state.value.homePictureColours)
    }

    @Test fun stickersSitTheSameForOneCardAndDifferentlyForAnother() {
        assertEquals(stickerSpots(PictureStyle.PILE, 7).map { it.x to it.angle }, stickerSpots(PictureStyle.PILE, 7).map { it.x to it.angle })
        assertNotEquals(stickerSpots(PictureStyle.PILE, 7).map { it.angle }, stickerSpots(PictureStyle.PILE, 8).map { it.angle })
        assertEquals(4, stickerSpots(PictureStyle.CUTOUTS, 1).size)
        assertTrue(stickerSpots(PictureStyle.CUTOUTS, 1).first().round)
    }
}
