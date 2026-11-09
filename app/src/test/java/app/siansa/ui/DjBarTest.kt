package app.siansa.ui

import androidx.compose.ui.unit.dp
import app.siansa.mix.MixShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DJ mix bar (issues #1033, #1035): which place a finger is on, the order kept under Style, and what's on the bar. */
class DjBarTest {
    @Test
    fun theDefaultIsTheMockupsOrderWithTheMixInTheMiddle() {
        assertEquals(
            listOf(DjBarSpot.STEADY, DjBarSpot.BUILD, DjBarSpot.MIX, DjBarSpot.EUPHORIC, DjBarSpot.WIND_DOWN),
            DjBarSpot.default,
        )
        assertEquals(DjBarSpot.MIX, DjBarSpot.default[DjBarSpot.default.size / 2])
    }

    @Test
    fun straightUpIsTheMiddlePlace() {
        assertEquals(2, djBarSpotAt(0f, 300f, 5))
        // Each place is an equal part of the bar, centred on the play button.
        assertEquals(1, djBarSpotAt(-60f, 300f, 5))
        assertEquals(0, djBarSpotAt(-121f, 300f, 5))
        assertEquals(3, djBarSpotAt(31f, 300f, 5))
        assertEquals(4, djBarSpotAt(149f, 300f, 5))
    }

    @Test
    fun pastTheEndsIsTheEndPlace() {
        assertEquals(0, djBarSpotAt(-500f, 300f, 5))
        assertEquals(4, djBarSpotAt(500f, 300f, 5))
    }

    @Test
    fun withOnePlaceItIsAlwaysThatOne() {
        // An album's DJ mix has only Stop; an unknown width can't tell places apart.
        assertEquals(0, djBarSpotAt(120f, 300f, 1))
        assertEquals(0, djBarSpotAt(120f, 0f, 5))
    }

    @Test
    fun theOrderIsKeptAndEveryPlaceIsAlwaysThere() {
        val mine = listOf(DjBarSpot.MIX, DjBarSpot.WIND_DOWN, DjBarSpot.STEADY, DjBarSpot.BUILD, DjBarSpot.EUPHORIC)
        assertEquals(mine, DjBarSpot.parse(DjBarSpot.format(mine)))
        // Unknown names go, repeats count once, missing places come back at the end in the default order.
        assertEquals(
            listOf(DjBarSpot.WIND_DOWN, DjBarSpot.STEADY, DjBarSpot.BUILD, DjBarSpot.MIX, DjBarSpot.EUPHORIC),
            DjBarSpot.parse("wind-down, rocket ,wind-down"),
        )
        assertEquals(DjBarSpot.default, DjBarSpot.parse(null))
        // An order saved before #1035 called the middle "stop": it still reads.
        assertEquals(DjBarSpot.MIX, DjBarSpot.parse("stop").first())
    }

    @Test
    fun theMiddleStartsOrStops() {
        val order = DjBarSpot.default
        // A DJ radio: shapes and Stop. A list's own mix: only Stop.
        assertEquals(DjBarPlaces(order, starting = false), djBarPlaces(order, djRadio = true, listMix = false, canStart = true))
        assertEquals(DjBarPlaces(listOf(DjBarSpot.MIX), starting = false), djBarPlaces(order, djRadio = false, listMix = true, canStart = true))
        // No DJ mix: the same bar with Start in the middle (issue #1035), when up starts one.
        assertEquals(DjBarPlaces(order, starting = true), djBarPlaces(order, djRadio = false, listMix = false, canStart = true))
        assertNull(djBarPlaces(order, djRadio = false, listMix = false, canStart = false))
        assertEquals("Start a DJ mix", DjBarSpot.MIX.title(starting = true))
        assertEquals("Stop the mix", DjBarSpot.MIX.title(starting = false))
    }

    @Test
    fun eachShapeHasItsPlace() {
        MixShape.entries.forEach { shape -> assertEquals(shape, DjBarSpot.of(shape).shape) }
        assertNull(DjBarSpot.MIX.shape)
    }

    @Test
    fun theBarLeavesRoomAbovePlay() {
        // Issue #1035: the bar was too close to Play. Centred on the seek bar's line, it ends at least Space.Page above
        // Play, which is the seek bar's 56 dp (its top padding, touch row and times) and the controls' Space.Small down.
        val top = DJ_BAR_CENTRE - DJ_BAR_HEIGHT / 2
        val bottom = DJ_BAR_CENTRE + DJ_BAR_HEIGHT / 2
        val playTop = 56.dp + Space.Small
        assertTrue("the bar starts inside the seek bar's room: $top", top >= 0.dp)
        assertTrue("room above Play: ${playTop - bottom}", playTop - bottom >= Space.Page)
    }
}
