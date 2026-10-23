package app.musix.ui

import app.musix.data.NavTab
import app.musix.mix.VibeTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Flow tab (issue #369). */
class FlowScreenTest {
    @Test fun theTabIsCalledFlowAndKeepsItsPlace() {
        assertEquals("Flow", NavTab.DSCVR.label)
        // Saved nav bar orders name it by route, so the route stays.
        assertEquals("dscvr", NavTab.DSCVR.route)
    }

    @Test fun shapeYourSoundSaysItsOptionalUntilSomethingIsSet() {
        assertTrue(shapeHint(VibeTuning()).startsWith("Optional"))
        assertEquals("Using: faster", shapeHint(VibeTuning(tempo = 1)))
    }
}
