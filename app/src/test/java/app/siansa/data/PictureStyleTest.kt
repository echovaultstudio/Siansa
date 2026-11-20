package app.siansa.data

import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.siansa.ui.hueRange
import app.siansa.ui.mosaicSitsWell
import app.siansa.ui.pictureShown
import app.siansa.ui.topCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How a row's card pictures show (the owner, 20 Nov: "some do not look good with the cover mosaic"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PictureStyleTest {
    @Test fun keptAndReadBack() {
        val saved = mapOf(HomeSection.QUICK to PictureStyle.COLOUR, HomeSection.MADE_FOR_YOU to PictureStyle.GLOW)
        assertEquals(saved, PictureStyle.parse(PictureStyle.format(saved)))
        assertEquals(mapOf(HomeSection.QUICK to PictureStyle.STACK), PictureStyle.parse("quick:stack,nothing:glow,made_for_you:sideways"))
        // Auto is how it is without a choice: not written.
        assertEquals("", PictureStyle.format(mapOf(HomeSection.QUICK to PictureStyle.AUTO)))
    }

    @Test fun keptPerRowAndClearedByReset() {
        fun settings() = SettingsRepository(ApplicationProvider.getApplicationContext())
        settings().setHomePicture(HomeSection.MADE_FOR_YOU, PictureStyle.DUOTONE)
        settings().setHomePicture(HomeSection.QUICK, PictureStyle.COVER)
        settings().setHomePicture(HomeSection.QUICK, PictureStyle.AUTO)
        assertEquals(mapOf(HomeSection.MADE_FOR_YOU to PictureStyle.DUOTONE), settings().state.value.homePictures)
        settings().resetHomeLooks()
        assertEquals(emptyMap<HomeSection, PictureStyle>(), settings().state.value.homePictures)
    }

    @Test fun aStyleWithoutTheCoversForItFallsBack() {
        val none = { emptyList<Color?>() }
        assertEquals(PictureStyle.MOSAIC, pictureShown(PictureStyle.MOSAIC, 4, true, none))
        assertEquals(PictureStyle.COVER, pictureShown(PictureStyle.MOSAIC, 3, true, none))
        assertEquals(PictureStyle.STACK, pictureShown(PictureStyle.STACK, 2, true, none))
        assertEquals(PictureStyle.COVER, pictureShown(PictureStyle.STACK, 1, true, none))
        assertEquals(PictureStyle.COLOUR, pictureShown(PictureStyle.GLOW, 0, false, none))
        assertEquals(PictureStyle.COLOUR, pictureShown(PictureStyle.COLOUR, 4, true, none))
    }

    @Test fun autoTakesTheMosaicOnlyWhenItsCoversSitWell() {
        val blues = { listOf<Color?>(Color(0xFF1F4F78), Color(0xFF2A6F9A), Color(0xFF1F6F78), Color(0xFF203A60)) }
        val clash = { listOf<Color?>(Color(0xFFE8D44D), Color(0xFF1F6F78), Color(0xFFF2F2F3), Color(0xFFC2185B)) }
        assertEquals(PictureStyle.MOSAIC, pictureShown(PictureStyle.AUTO, 4, true, blues))
        assertEquals(PictureStyle.COVER, pictureShown(PictureStyle.AUTO, 4, true, clash))
        assertEquals(PictureStyle.COVER, pictureShown(PictureStyle.AUTO, 2, true, blues))
        assertEquals(PictureStyle.COLOUR, pictureShown(PictureStyle.AUTO, 0, false, blues))
    }

    @Test fun whatSitsWell() {
        // Not known yet: no reason against.
        assertTrue(mosaicSitsWell(listOf(null, null, Color.Unspecified, null)))
        // Black, grey and a colour go together.
        assertTrue(mosaicSitsWell(listOf(Color(0xFF111111), Color(0xFF555555), Color(0xFF7A2E3A), Color(0xFF333333))))
        // Very light beside very dark.
        assertFalse(mosaicSitsWell(listOf(Color(0xFFF5F5F5), Color(0xFF101010))))
        // Red and green: far apart on the wheel.
        assertFalse(mosaicSitsWell(listOf(Color(0xFF8A2020), Color(0xFF208A20))))
    }

    @Test fun huesWrapRoundTheWheel() {
        assertEquals(20f, hueRange(listOf(350f, 10f)), 0.01f)
        assertEquals(120f, hueRange(listOf(0f, 120f)), 0.01f)
    }

    @Test fun theTopAlbumIsTheCoverMostSongsHave() {
        assertEquals("b", topCover(listOf("a", "b", null, "b", "c"), "x"))
        assertEquals("a", topCover(listOf("a", "b"), "x"))
        assertEquals("x", topCover(listOf(null, null), "x"))
    }
}
