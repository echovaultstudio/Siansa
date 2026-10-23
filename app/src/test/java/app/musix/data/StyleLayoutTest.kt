package app.musix.data

import app.musix.ui.dragTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings › Style: the mini player's buttons and the bottom bar's tabs (issue #346). */
class StyleLayoutTest {
    @Test fun miniPlayerStartsWithPlayCastNext() {
        assertEquals(listOf(MiniButton.PLAY, MiniButton.CAST, MiniButton.NEXT), MiniButton.parse(null))
    }

    @Test fun miniPlayerOrderRoundTripsAndDropsUnknowns() {
        val order = listOf(MiniButton.LIKE, MiniButton.PLAY, MiniButton.LYRICS)
        assertEquals(order, MiniButton.parse(MiniButton.format(order)))
        assertEquals(listOf(MiniButton.NEXT, MiniButton.PLAY), MiniButton.parse("next, gone,next,play"))
        // Every button hidden is a choice too.
        assertEquals(emptyList<MiniButton>(), MiniButton.parse(""))
        assertEquals(MiniButton.MAX, MiniButton.parse("play,cast,next,lyrics,add,like").size)
    }

    @Test fun miniPlayerExtrasAddUpToTheLimit() {
        val three = MiniButton.default
        val four = MiniButton.toggle(three, MiniButton.LIKE)
        assertEquals(three + MiniButton.LIKE, four)
        assertEquals(four, MiniButton.toggle(four, MiniButton.ADD))
        assertEquals(listOf(MiniButton.PLAY, MiniButton.NEXT, MiniButton.LIKE), MiniButton.toggle(four, MiniButton.CAST))
    }

    @Test fun navBarDefaultsWithDscvrBeforeSettings() {
        assertEquals(listOf(NavTab.HOME, NavTab.SEARCH, NavTab.LIBRARY, NavTab.DSCVR, NavTab.SETTINGS), NavTab.parse(null))
    }

    @Test fun navBarKeepsSettingsAndFitsFive() {
        assertEquals(listOf(NavTab.DSCVR, NavTab.HOME, NavTab.SETTINGS), NavTab.parse("dscvr,home"))
        assertEquals(listOf(NavTab.SETTINGS, NavTab.HOME), NavTab.parse("settings,home,home,nope"))
        val order = listOf(NavTab.SETTINGS, NavTab.DSCVR, NavTab.HOME, NavTab.LIBRARY, NavTab.SEARCH)
        assertEquals(order, NavTab.parse(NavTab.format(order)))
    }

    @Test fun navBarHidesAndPutsBackAfterItsNeighbour() {
        val withoutSearch = NavTab.toggle(NavTab.default, NavTab.SEARCH)
        assertEquals(listOf(NavTab.HOME, NavTab.LIBRARY, NavTab.DSCVR, NavTab.SETTINGS), withoutSearch)
        assertEquals(NavTab.default, NavTab.toggle(withoutSearch, NavTab.SEARCH))
        // Settings can't be hidden.
        assertEquals(NavTab.default, NavTab.toggle(NavTab.default, NavTab.SETTINGS))
        // Put back with nothing before it in the bar: first.
        assertEquals(listOf(NavTab.HOME, NavTab.SETTINGS), NavTab.toggle(listOf(NavTab.SETTINGS), NavTab.HOME))
    }

    @Test fun aDraggedItemTakesTheSlotItsMostlyOver() {
        assertEquals(2, dragTarget(2, 20f, 100f, 5))
        assertEquals(3, dragTarget(2, 60f, 100f, 5))
        assertEquals(0, dragTarget(2, -260f, 100f, 5))
        assertEquals(4, dragTarget(2, 900f, 100f, 5))
        assertEquals(1, dragTarget(1, 50f, 0f, 5))
    }

    @Test fun arrangeListShowsTheBarFirstThenTheHiddenOnes() {
        val shown = listOf(MiniButton.NEXT, MiniButton.PLAY)
        assertEquals(
            listOf(MiniButton.NEXT, MiniButton.PLAY, MiniButton.CAST, MiniButton.LYRICS, MiniButton.ADD, MiniButton.LIKE),
            arrangeOrder(shown, MiniButton.entries),
        )
    }

    @Test fun arrangeListSavesOnlyTheShownInTheirNewOrder() {
        // Like dragged above Play and shown: it goes on the bar in that place.
        val order = listOf(MiniButton.NEXT, MiniButton.LIKE, MiniButton.PLAY, MiniButton.CAST)
        assertEquals(listOf(MiniButton.NEXT, MiniButton.LIKE, MiniButton.PLAY), shownInOrder(order, setOf(MiniButton.PLAY, MiniButton.NEXT, MiniButton.LIKE)))
    }
}
