package app.siansa

import app.siansa.data.moved
import app.siansa.data.movesFirstVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arrange Home: moving a row to the top keeps it on screen (#614). */
class ArrangeHomeTest {
    @Test
    fun movingARowOntoTheFirstRowChangesWhatIsFirst() {
        assertTrue(movesFirstVisible(from = 1, to = 0, firstVisible = 0))
        assertTrue(movesFirstVisible(from = 4, to = 3, firstVisible = 3))  // scrolled down a little
    }

    @Test
    fun movingTheFirstRowDownChangesWhatIsFirst() {
        assertTrue(movesFirstVisible(from = 0, to = 1, firstVisible = 0))
    }

    @Test
    fun movesBelowTheFirstRowLeaveTheListAlone() {
        assertFalse(movesFirstVisible(from = 3, to = 2, firstVisible = 0))
        assertFalse(movesFirstVisible(from = 2, to = 5, firstVisible = 0))
        assertFalse(movesFirstVisible(from = 0, to = 0, firstVisible = 0))
    }

    @Test
    fun theRowMovedToTheTopIsFirst() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(1, 0))
    }
}
