package app.siansa.ui

import app.siansa.mix.MixShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The DJ mix bar (issue #1033): which place a finger is on, the order kept under Style, and what's on the bar. */
class DjBarTest {
    @Test
    fun theDefaultIsTheMockupsOrderWithStopInTheMiddle() {
        assertEquals(
            listOf(DjBarSpot.STEADY, DjBarSpot.BUILD, DjBarSpot.STOP, DjBarSpot.EUPHORIC, DjBarSpot.WIND_DOWN),
            DjBarSpot.default,
        )
        assertEquals(DjBarSpot.STOP, DjBarSpot.default[DjBarSpot.default.size / 2])
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
        val mine = listOf(DjBarSpot.STOP, DjBarSpot.WIND_DOWN, DjBarSpot.STEADY, DjBarSpot.BUILD, DjBarSpot.EUPHORIC)
        assertEquals(mine, DjBarSpot.parse(DjBarSpot.format(mine)))
        // Unknown names go, repeats count once, missing places come back at the end in the default order.
        assertEquals(
            listOf(DjBarSpot.WIND_DOWN, DjBarSpot.STEADY, DjBarSpot.BUILD, DjBarSpot.STOP, DjBarSpot.EUPHORIC),
            DjBarSpot.parse("wind-down, rocket ,wind-down"),
        )
        assertEquals(DjBarSpot.default, DjBarSpot.parse(null))
    }

    @Test
    fun aRadioHasShapesAndStopAListsOwnMixOnlyStop() {
        val order = DjBarSpot.default
        assertEquals(order, djBarSpots(order, djRadio = true, listMix = false))
        assertEquals(listOf(DjBarSpot.STOP), djBarSpots(order, djRadio = false, listMix = true))
        assertNull(djBarSpots(order, djRadio = false, listMix = false))
    }

    @Test
    fun eachShapeHasItsPlace() {
        MixShape.entries.forEach { shape -> assertEquals(shape, DjBarSpot.of(shape).shape) }
        assertNull(DjBarSpot.STOP.shape)
    }
}
