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

    @Test fun underPlayIsWhatsSet() {
        // Nothing set still plays: the middle.
        assertEquals("Balanced, a mix of artists", flowSummary("", VibeTuning()))
        assertEquals("Calmer and a bit faster · more piano", flowSummary("", VibeTuning(energy = -1, tempo = 1, sounds = listOf(SoundDial("piano", 3)))))
        assertEquals("“rainy Sunday”", flowSummary(" rainy Sunday ", VibeTuning()))
        assertEquals("“rainy Sunday” · Balanced · new to you", flowSummary("rainy Sunday", VibeTuning(familiar = -2)))
        // A pick taken off again is nothing set.
        assertEquals("Balanced, a mix of artists", flowSummary("", VibeTuning(sounds = listOf(SoundDial("piano", 0)))))
    }

    /** The owner, 15 Nov: one bar. The pill's round ends fold into the mini player's corners, and it's gone before they settle. */
    @Test fun thePillFoldsIntoTheMiniPlayer() {
        assertEquals(FlowDock.ROUND.value, FlowDock.corner(0f), 0.001f)
        assertEquals(FlowDock.FOLDED.value, FlowDock.corner(1f), 0.001f)
        assertTrue(FlowDock.corner(0.5f) in FlowDock.FOLDED.value..FlowDock.ROUND.value)
        // Half the pill's height: its ends are round.
        assertEquals(FlowDock.HEIGHT.value / 2, FlowDock.ROUND.value, 0.001f)
        assertEquals(1f, FlowDock.pill(0f), 0.001f)
        assertEquals(0f, FlowDock.pill(0.8f), 0.001f)
        assertEquals(0f, FlowDock.pill(1f), 0.001f)
        // Past either end stays the end.
        assertEquals(FlowDock.corner(1f), FlowDock.corner(3f), 0.001f)
        assertEquals(1f, FlowDock.pill(-1f), 0.001f)
    }

    @Test fun thePageSaysWhichMixItSteers() {
        assertEquals("Playing “Easy groove with jazz”. Move anything here to change what comes next.", flowLiveLine("Easy groove with jazz", steering = false))
        assertEquals("Playing “Evening Chill”. Changing what comes next…", flowLiveLine("Evening Chill", steering = true))
    }

    @Test fun nowPlayingNamesAFlowMixAndTheQueueHidesShapeYourMix() {
        assertEquals("NOW PLAYING FROM", playingFromLabel("Evening Chill"))
        assertEquals("PLAYING FROM ALBUM", playingFromLabel(null))
        // Shape your mix: as ever without a Flow mix; in one, only while a DJ mix runs.
        assertTrue(shapeYourMixShown(flow = false, djRadio = false, listMix = false))
        assertFalse(shapeYourMixShown(flow = true, djRadio = false, listMix = false))
        assertTrue(shapeYourMixShown(flow = true, djRadio = true, listMix = false))
        assertTrue(shapeYourMixShown(flow = true, djRadio = false, listMix = true))
    }

    @Test fun genreWordsAreNotListedUnderSounds() {
        assertEquals(listOf("piano", "dreamy"), soundsNotGenres(listOf("piano", "Progressive Rock", "dreamy", "jazz"), listOf("Jazz", " progressive rock", "Ambient")))
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
