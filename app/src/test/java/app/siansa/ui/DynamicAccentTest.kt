package app.siansa.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Now playing's colours from the cover stand out on the cover behind them, dark or light (issue #970). */
class DynamicAccentTest {
    // The covers of the approved mockup: their liveliest colour and their average.
    private val covers = mapOf(
        "Clubfood (lime, light)" to (Color(0xFFB2CE05) to Color(0xFFB8C64F)),
        "HOSPITAL30 (blue on black)" to (Color(0xFF09A2E5) to Color(0xFF203440)),
        "Night mix (rust)" to (Color(0xFFAD6B41) to Color(0xFF6A4E31)),
        "NYE 2026 (mustard)" to (Color(0xFF8D7B11) to Color(0xFFA6996E)),
        "The Fork and Knife (white, a little brown)" to (Color(0xFF6A4744) to Color(0xFFCDC9C7)),
        "Very dark navy" to (Color(0xFF0F2347) to Color(0xFF0A1226)),
        "Almost white" to (Color(0xFFE9E1CF) to Color(0xFFF4F1EA)),
    )

    @Test
    fun alwaysCleanlyVisible() {
        covers.forEach { (name, tones) ->
            val (seed, average) = tones
            val backdrop = DynamicAccent.backdropOf(average)
            val c = DynamicAccent.of(seed, backdrop)
            assertNotNull(name, c)
            c!!
            assertTrue("$name: seek bar and Play ${DynamicAccent.contrast(c.controls, backdrop)}", DynamicAccent.contrast(c.controls, backdrop) >= DynamicAccent.CONTROLS_CONTRAST)
            assertTrue("$name: device name ${DynamicAccent.contrast(c.text, backdrop)}", DynamicAccent.contrast(c.text, backdrop) >= DynamicAccent.TEXT_CONTRAST)
            assertTrue("$name: play symbol ${DynamicAccent.contrast(c.onControls, c.controls)}", DynamicAccent.contrast(c.onControls, c.controls) >= DynamicAccent.TEXT_CONTRAST)
        }
    }

    @Test
    fun keepsTheCoversOwnHue() {
        // Lime stays green, blue stays blue: lighter if needed, never another colour.
        val lime = DynamicAccent.of(Color(0xFFB2CE05), DynamicAccent.backdropOf(Color(0xFFB8C64F)))!!.controls
        assertTrue("lime is green: $lime", lime.green > lime.red && lime.green > lime.blue)
        val blue = DynamicAccent.of(Color(0xFF09A2E5), DynamicAccent.backdropOf(Color(0xFF203440)))!!.controls
        assertTrue("blue is blue: $blue", blue.blue > blue.red && blue.blue > blue.green)
    }

    @Test
    fun aBlackAndWhiteCoverIsSilver() {
        // Issue #994: amber came from nowhere on a black and white cover; it's silver, from its own greys.
        listOf(Color(0xFF808080), Color(0xFF111111), Color(0xFFF0F0F0)).forEach { grey ->
            val c = DynamicAccent.of(grey, DynamicAccent.backdropOf(Color(0xFF202020)))!!.controls
            assertTrue("silver: $c", DynamicAccent.chroma(c) < 0.02f && c.red > 0.7f)
        }
    }

    // Issue #994: the owner's three covers, as Android's palette reads them (share of the cover, colour).
    private fun swatches(vararg pairs: Pair<Int, Long>) = pairs.map { (n, c) -> CoverSwatch(Color(c), n) }

    @Test
    fun aCoolGreyCoverIsABlueishSilver() {
        // Document One, Machine Learning: light grey with a cool tint.
        val seed = DynamicAccent.seedOf(swatches(1356 to 0xFFB8BCC4, 1059 to 0xFF6B6D75, 972 to 0xFFB0B0B9, 862 to 0xFF232323, 799 to 0xFF47484A))!!
        val c = DynamicAccent.of(seed, DynamicAccent.backdropOf(Color(0xFF9A9CA4)))!!.controls
        assertTrue("cool, not amber: $c", c.blue > c.red && DynamicAccent.chroma(c) < 0.15f)
    }

    @Test
    fun aSepiaCoverIsTanNotItsRedLights() {
        // UKF Drum and Bass 2026: sepia, with a little bright red from the police lights.
        val seed = DynamicAccent.seedOf(
            swatches(979 to 0xFF151413, 950 to 0xFF292421, 945 to 0xFFAD998D, 889 to 0xFF947D72, 872 to 0xFF6D5851, 120 to 0xFFD23A2A),
        )!!
        val c = DynamicAccent.of(seed, DynamicAccent.backdropOf(Color(0xFF4A3F3A)))!!.controls
        assertTrue("warm: $c", c.red > c.green && c.green > c.blue)
        assertTrue("muted tan, not red: $c", DynamicAccent.chroma(c) < 0.3f && c.green > 0.5f)
    }

    @Test
    fun aBlackAndWhiteCoverReadsAsGrey() {
        // The Chemical Brothers, Dig Your Own Hole: black, white and greys only.
        val seed = DynamicAccent.seedOf(swatches(9320 to 0xFF010101, 315 to 0xFFFEFEFE, 278 to 0xFF4E4E4E, 269 to 0xFFBFBFBF))!!
        assertTrue("grey: $seed", DynamicAccent.chroma(seed) < 0.04f)
    }

    @Test
    fun aBrightLogoOnABlackCoverStillCounts() {
        val seed = DynamicAccent.seedOf(swatches(9000 to 0xFF0A0A0C, 600 to 0xFF1A1A1E, 400 to 0xFFD0302A))!!
        assertTrue("red: $seed", seed.red > 0.7f && seed.green < 0.3f)
    }

    // Issue #1027: the owner's two covers, as their palettes read them.
    private val moreData = swatches(
        9119 to 0xFF1E1E1E, 626 to 0xFFA3938E, 621 to 0xFFBFBFBF, 575 to 0xFFDADADA, 560 to 0xFFD6D6D6, 496 to 0xFF24272D,
        428 to 0xFF1D1D1C, 388 to 0xFF536A94, 325 to 0xFF6B3F3B, 283 to 0xFFCBCBCB, 233 to 0xFF8EBC82, 230 to 0xFF191919,
    )
    private val pianoDnb = swatches(
        9740 to 0xFF000000, 481 to 0xFF37393A, 417 to 0xFF56595D, 407 to 0xFF131415, 383 to 0xFF949794, 364 to 0xFF4A4C4F,
        362 to 0xFF838792, 346 to 0xFF424448, 333 to 0xFF62666B, 300 to 0xFF6D7072, 268 to 0xFF262726, 181 to 0xFFC1C069,
    )

    /** A cover's pixels: so many of each colour, as ARGB. */
    private fun pixels(vararg runs: Pair<Int, Long>): IntArray = runs.flatMap { (n, c) -> List(n) { c.toInt() } }.toIntArray()

    @Test
    fun blackAndWhiteWithStripesOfThreeColoursIsSilver() {
        // MORE D4TA: black and white, with red, green and blue stripes of the same size. Blue was a guess.
        val seed = DynamicAccent.seedOf(moreData)!!
        assertTrue("grey: $seed", DynamicAccent.chroma(seed) < 0.04f)
        val c = DynamicAccent.of(seed, DynamicAccent.backdropOf(Color(0xFF3A3A3A)))!!.controls
        assertTrue("silver: $c", DynamicAccent.chroma(c) < 0.02f && c.red > 0.7f)
    }

    @Test
    fun aSmallYellowTitleIsYellowNotKhaki() {
        // Piano Drum & Bass: black and grey, a bright yellow title. The palette blends its letters with the grey: khaki.
        val cover = pixels(9800 to 0xFF000000, 3000 to 0xFF6E7174, 1200 to 0xFF3A3C3D, 220 to 0xFFE8E21E, 180 to 0xFFC1C069)
        val seed = DynamicAccent.pick(pianoDnb, cover)!!
        assertTrue("lively yellow, not khaki: $seed", DynamicAccent.chroma(seed) > 0.6f && seed.red > 0.8f && seed.green > 0.8f && seed.blue < 0.3f)
        val c = DynamicAccent.of(seed, DynamicAccent.backdropOf(Color(0xFF151615)))!!.controls
        assertTrue("a soft yellow on now playing: $c", c.red > c.blue + 0.2f && c.green > c.blue + 0.2f)
    }

    @Test
    fun aBrightCoverGetsACalmerColourOfItsOwnHue() {
        // A loud blue cover gives a steel blue that sits with it (the owner, 10 Nov), not the loud blue itself.
        for (backdrop in listOf(DynamicAccent.backdropOf(Color(0xFF1E3A8A)), pageBackdrop(Color(0xFFF7F7F7), Color(0xFF1E3A8A)))) {
            val c = DynamicAccent.of(Color(0xFF1E5BFF), backdrop)!!
            assertTrue("blue: ${c.controls}", c.controls.blue > c.controls.red && c.controls.blue > c.controls.green)
            assertTrue("calm: ${c.controls}", DynamicAccent.chroma(c.controls) <= 0.5f)
            assertTrue("stands out", DynamicAccent.contrast(c.controls, backdrop) >= DynamicAccent.CONTROLS_CONTRAST)
        }
    }

    @Test
    fun aBigColourIsTakenAsItIs() {
        // UKF's sepia fills the cover: its pixels aren't searched for a livelier tan, nor for the red lights near its hue.
        val sepia = swatches(979 to 0xFF151413, 950 to 0xFF292421, 945 to 0xFFAD998D, 889 to 0xFF947D72, 872 to 0xFF6D5851, 120 to 0xFFD23A2A)
        val cover = pixels(2000 to 0xFF151413, 1800 to 0xFFAD998D, 120 to 0xFFD23A2A)
        assertTrue(DynamicAccent.pick(sepia, cover) == DynamicAccent.seedOf(sepia))
    }

    @Test
    fun oneColourfulLogoOnBlackStillCounts() {
        // One colour, however small, is the cover's colour: only spots of different colours are silver.
        val seed = DynamicAccent.seedOf(swatches(9000 to 0xFF0A0A0C, 600 to 0xFFDADADA, 300 to 0xFF2050D0, 200 to 0xFF3060E0))!!
        assertTrue("blue: $seed", seed.blue > 0.7f && seed.red < 0.3f)
    }

    @Test
    fun tooFewPixelsKeepThePalettesColour() {
        val seed = Color(0xFFC1C069)
        assertTrue(DynamicAccent.vividOf(seed, pixels(5 to 0xFFE8E21E, 9000 to 0xFF000000)) == seed)
    }

    @Test
    fun onAPageItStandsOutLightOrDark() {
        // Issue #1028: album, artist and playlist pages, in the dark and the light theme, with a little of the cover's colour.
        val pages = mapOf(
            "dark" to pageBackdrop(Color(0xFF121212), Color(0xFF6A2E1E)),
            "light" to pageBackdrop(Color(0xFFF7F7F7), Color(0xFF6A2E1E)),
            "light, pale cover" to pageBackdrop(Color(0xFFFFFFFF), Color(0xFFE9E1CF)),
        )
        val seeds = covers.values.map { it.first } + Color(0xFF808080)
        pages.forEach { (name, page) ->
            seeds.forEach { seed ->
                val c = DynamicAccent.of(seed, page)!!
                assertTrue("$name, $seed: Play ${DynamicAccent.contrast(c.controls, page)}", DynamicAccent.contrast(c.controls, page) >= DynamicAccent.CONTROLS_CONTRAST)
                assertTrue("$name, $seed: play symbol", DynamicAccent.contrast(c.onControls, c.controls) >= DynamicAccent.TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun aLightPageGetsADarkerColourOfTheSameHue() {
        val page = pageBackdrop(Color(0xFFF7F7F7), Color(0xFF203440))
        val c = DynamicAccent.of(Color(0xFF09A2E5), page)!!.controls
        assertTrue("blue: $c", c.blue > c.red && c.blue > c.green)
        assertTrue("darker than the page: $c", c.luminance() < page.luminance())
    }

    @Test
    fun noSwatchesNoColour() {
        assertNull(DynamicAccent.seedOf(emptyList()))
    }

    /** The owner, 13 Nov: an artist page's header in khaki, its Play in steel blue (the picture's sky) — a clash. */
    @Test
    fun playKeepsToTheHeadersFamily() {
        val khaki = Color(0xFF8A7B55)
        val steelBlue = Color(0xFF4A7BB5)
        // Another family: Play takes the header's colour, and its colours are then of khaki's hue (yellowish).
        assertTrue(DynamicAccent.inFamily(steelBlue, khaki) == khaki)
        val c = DynamicAccent.of(steelBlue, pageBackdrop(Color(0xFF121212), khaki), tint = khaki)!!.controls
        assertTrue("of the header's family, not blue: $c", c.red > c.blue && c.green > c.blue)
        // The same family (an orange logo on a red cover) keeps the seed; grey on either side changes nothing.
        val red = Color(0xFFB02020)
        val orange = Color(0xFFE08020)
        assertTrue(DynamicAccent.inFamily(orange, red) == orange)
        assertTrue(DynamicAccent.inFamily(steelBlue, Color(0xFF808080)) == steelBlue)
        assertTrue(DynamicAccent.inFamily(Color(0xFF808080), khaki) == Color(0xFF808080))
        assertTrue(DynamicAccent.inFamily(steelBlue, null) == steelBlue)
        // Without a header tint (now playing), as before.
        assertTrue(DynamicAccent.of(steelBlue, Color(0xFF101418))!!.controls.blue > 0.5f)
    }
}
