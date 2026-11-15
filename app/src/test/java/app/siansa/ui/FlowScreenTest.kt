package app.siansa.ui

import app.siansa.data.NavTab
import app.siansa.mix.FlowMood
import app.siansa.mix.SoundDial
import app.siansa.mix.VibeTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Flow tab (issue #369). */
class FlowScreenTest {
    @Test fun theTabIsCalledFlowAndKeepsItsPlace() {
        assertEquals("Flow", NavTab.DSCVR.label)
        // Saved nav bar orders name it by route, so the route stays.
        assertEquals("dscvr", NavTab.DSCVR.route)
    }

    @Test fun playWaitsForSomethingToMakeAVibeFrom() {
        assertFalse(flowReady("", VibeTuning()))
        assertFalse(flowReady("hi", VibeTuning()))
        assertTrue(flowReady("rainy Sunday", VibeTuning()))
        assertTrue(flowReady("", VibeTuning(tempo = 1)))
        assertTrue(flowReady("", VibeTuning(sounds = listOf(SoundDial("piano", FlowMood.MORE)))))
        // A pick taken off again is nothing set.
        assertFalse(flowReady("", VibeTuning(sounds = listOf(SoundDial("piano", 0)))))
    }

    @Test fun underPlayIsWhatsSetOrWhatToDo() {
        assertEquals("Drag the dot, add a sound, or say it", flowSummary("", VibeTuning()))
        assertEquals("Calmer and a bit faster · more piano", flowSummary("", VibeTuning(energy = -1, tempo = 1, sounds = listOf(SoundDial("piano", 3)))))
        assertEquals("“rainy Sunday”", flowSummary(" rainy Sunday ", VibeTuning()))
        assertEquals("“rainy Sunday” · Balanced · new to you", flowSummary("rainy Sunday", VibeTuning(familiar = -2)))
    }

    @Test fun thePadsDotKeepsClearOfTheEdgesAndLandsOnAStep() {
        assertEquals(0.5f, padAt(0), 0.0001f)
        assertEquals(1f - padAt(-2), padAt(2), 0.0001f)
        assertTrue(padAt(-2) > 0.1f)
        for (step in -2..2) assertEquals(step, padStep(padValue(padAt(step))))
        // Past the dot's last place the pad still says the end.
        assertEquals(2, padStep(padValue(1f)))
        assertEquals(-2, padStep(padValue(0f)))
        assertEquals("Piano", shownSound("piano"))
    }
}
