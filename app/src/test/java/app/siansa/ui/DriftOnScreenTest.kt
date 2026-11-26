package app.siansa.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Moving cards only move while Siansa is shown (the owner's log, 25 Nov: Home drew its drifting cards for an hour in
 * the background, and Android stopped Siansa for using too much CPU).
 */
class DriftOnScreenTest {
    @Test fun onlyWhileSiansaIsShown() {
        assertEquals(
            listOf(false, false, false, true, true),
            listOf(Lifecycle.State.DESTROYED, Lifecycle.State.INITIALIZED, Lifecycle.State.CREATED, Lifecycle.State.STARTED, Lifecycle.State.RESUMED).map(::driftsWhen),
        )
    }
}
