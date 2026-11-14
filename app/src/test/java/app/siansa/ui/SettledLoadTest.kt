package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettledLoadTest {
    @Test
    fun `the first load shows as loading`() {
        assertEquals(Load.Loading, settledLoad<String>(Load.Loading, null))
    }

    @Test
    fun `while the next one loads the last one stays`() {
        val october = Load.Ok("October")
        assertEquals(october, settledLoad(Load.Loading, october))
    }

    @Test
    fun `the new one replaces the last when it arrives`() {
        assertEquals(Load.Ok("November"), settledLoad(Load.Ok("November"), Load.Ok("October")))
    }

    @Test
    fun `an error replaces the last one too`() {
        assertEquals(Load.Err("Out of reach"), settledLoad<String>(Load.Err("Out of reach"), Load.Ok("October")))
    }
}
