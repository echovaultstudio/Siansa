package app.siansa.mix

import app.siansa.ui.continuousHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keep playing (issue #337): when the queue is topped up, and DJ mode's order. */
class ContinuousPlayTest {
    private fun due(
        mode: ContinuousMode = ContinuousMode.STEADY,
        left: Int = 1,
        hasMedia: Boolean = true,
        repeatOff: Boolean = true,
        radioOn: Boolean = false,
        windingDown: Boolean = false,
        sleepFinishing: Boolean = false,
    ) = continuousTopUpDue(mode, left, hasMedia, repeatOff, radioOn, windingDown, sleepFinishing)

    @Test fun topsUpWhenAboutOneSongIsLeft() {
        assertTrue(due(left = 1))
        assertTrue(due(left = 0))
        assertFalse(due(left = 2))
        assertTrue(due(mode = ContinuousMode.DJ, left = 0))
    }

    @Test fun neverWhenOffOrNothingIsPlaying() {
        assertFalse(due(mode = ContinuousMode.OFF))
        assertFalse(due(hasMedia = false))
    }

    @Test fun leavesItToRepeatRadiosAndTheSleepTimer() {
        assertFalse("repeat plays the queue again", due(repeatOff = false))
        assertFalse("a song radio tops itself up", due(radioOn = true))
        assertFalse("the wind down ends the music", due(windingDown = true))
        assertFalse("the sleep timer is finishing", due(sleepFinishing = true))
    }

    @Test fun unknownSettingIsOff() {
        assertEquals(ContinuousMode.OFF, ContinuousMode.parse(null))
        assertEquals(ContinuousMode.OFF, ContinuousMode.parse("nope"))
        assertEquals(ContinuousMode.DJ, ContinuousMode.parse("DJ"))
    }

    @Test fun keysLandOnTheCamelotWheel() {
        assertEquals(Camelot(8, minor = false), camelot("C", "major"))
        assertEquals(Camelot(8, minor = true), camelot("A", "minor"))
        assertEquals(Camelot(5, minor = true), camelot("C", "minor"))
        assertEquals(Camelot(1, minor = false), camelot("B", "major"))
        assertEquals(Camelot(2, minor = false), camelot("F#", "major"))
        assertEquals(Camelot(2, minor = false), camelot("Gb", "major"))
        assertEquals(Camelot(6, minor = false), camelot("Bb", "major"))
        assertEquals(Camelot(11, minor = true), camelot("F#", "minor"))
        assertEquals("minor written in the key", Camelot(8, minor = true), camelot("Am", null))
        assertNull(camelot(null, "major"))
        assertNull(camelot("H", "major"))
        assertNull(camelot("", null))
    }

    @Test fun neighboursOnTheWheelMixBetterThanFarKeys() {
        val c = camelot("C", "major")
        val g = camelot("G", "major")
        val am = camelot("A", "minor")
        val fSharp = camelot("F#", "major")
        assertEquals(1.0, keyMatch(c, c), 0.0)
        assertTrue(keyMatch(c, g) > keyMatch(c, fSharp))
        assertTrue(keyMatch(c, am) > keyMatch(c, fSharp))
        // 12 and 1 are neighbours too.
        assertEquals(0.9, keyMatch(Camelot(12, false), Camelot(1, false)), 0.0)
        assertEquals(0.5, keyMatch(null, c), 0.0)
    }

    @Test fun halfAndDoubleTimeCountAsTheSameTempo() {
        assertEquals(1.0, tempoMatch(120.0, 120.0), 1e-9)
        assertEquals(1.0, tempoMatch(140.0, 70.0), 1e-9)
        assertEquals(1.0, tempoMatch(70.0, 140.0), 1e-9)
        assertTrue(tempoMatch(120.0, 124.0) > tempoMatch(120.0, 130.0))
        assertEquals(0.0, tempoMatch(120.0, 100.0), 0.0)
        assertEquals(0.5, tempoMatch(null, 120.0), 0.0)
    }

    @Test fun djPicksTheSmoothestChangeEachTime() {
        val seed = DjTrack("seed", "A", 1.0, 120.0, "C", "major")
        val pool = listOf(
            DjTrack("far", "B", 1.0, 90.0, "F#", "major"),
            DjTrack("near", "C", 0.9, 121.0, "G", "major"),
            DjTrack("next", "D", 0.8, 122.0, "D", "major"),
        )
        assertEquals(listOf("near", "next", "far"), djOrder(seed, pool, 3).map { it.id })
        assertEquals(listOf("near"), djOrder(seed, pool, 1).map { it.id })
    }

    @Test fun djNeverPicksTheSeedOrTheSameSongTwice() {
        val seed = DjTrack("seed", "A", 1.0, 120.0)
        val pool = listOf(DjTrack("seed", "A"), DjTrack("x", "B"), DjTrack("x", "B"), DjTrack("y", "C"))
        assertEquals(listOf("x", "y"), djOrder(seed, pool, 10).map { it.id }.sorted())
    }

    @Test fun djAvoidsTheSameArtistTwiceInARow() {
        val seed = DjTrack("seed", "Artist", 1.0, 120.0, "C", "major")
        val pool = listOf(
            DjTrack("same", "Artist", 1.0, 120.0, "C", "major"),
            DjTrack("other", "Someone", 1.0, 120.0, "C", "major"),
        )
        assertEquals("other", djOrder(seed, pool, 1).single().id)
    }

    @Test fun likenessFollowsAudioMusesOrder() {
        assertEquals(1.0, likenessByRank(0, 10), 0.0)
        assertTrue(likenessByRank(1, 10) > likenessByRank(9, 10))
        assertEquals(1.0, likenessByRank(0, 1), 0.0)
    }

    @Test fun djSaysWhenItCantOrderWithoutAudioMuse() {
        assertEquals(ContinuousMode.DJ.hint, continuousHint(ContinuousMode.DJ, audioMuseReady = true))
        assertTrue(continuousHint(ContinuousMode.DJ, audioMuseReady = false).contains("AudioMuse isn't set up"))
        assertEquals(ContinuousMode.STEADY.hint, continuousHint(ContinuousMode.STEADY, audioMuseReady = false))
    }
}
