package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRestTest {
    private val opensAt = 600

    @Test
    fun `a small slip goes back to the top`() {
        assertEquals(0, playerRest(1, opensAt, flicked = false))
        assertEquals(0, playerRest(40, opensAt, flicked = false))
        assertEquals(0, playerRest(599, opensAt, flicked = false))
    }

    @Test
    fun `a swipe up that ends short still opens the cards`() {
        assertEquals(opensAt, playerRest(40, opensAt, flicked = true))
        assertEquals(opensAt, playerRest(599, opensAt, flicked = true))
    }

    @Test
    fun `the top stays the top`() {
        assertEquals(0, playerRest(0, opensAt, flicked = false))
        assertEquals(0, playerRest(0, opensAt, flicked = true))
    }

    @Test
    fun `further down the page scrolls freely`() {
        assertEquals(600, playerRest(600, opensAt, flicked = false))
        assertEquals(1500, playerRest(1500, opensAt, flicked = false))
        assertEquals(1500, playerRest(1500, opensAt, flicked = true))
    }
}
