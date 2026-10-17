package app.musix

import app.musix.playback.SLEEP_FADE_MS
import app.musix.playback.SleepTimer
import app.musix.playback.SleepTimerState
import app.musix.playback.isTimeUp
import app.musix.playback.leftMs
import app.musix.playback.sleepFade
import app.musix.playback.sleepTimeLabel
import app.musix.playback.sleepTimeShort
import app.musix.playback.windDownFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sleep timer (issue #59). */
class SleepTimerTest {
    private var now = 1_000_000L
    private val timer = SleepTimer { now }
    private val min = 60_000L

    @Test fun runsForTheChosenTime() {
        timer.start(30, windDown = false)
        val s = timer.state.value!!
        assertEquals(30 * min, s.leftMs(now))
        assertFalse(s.isTimeUp(now))
        now += 29 * min
        assertEquals(min, s.leftMs(now))
        now += min
        assertTrue(s.isTimeUp(now))
    }

    @Test fun timeUpFinishesTheSong() {
        timer.start(15, windDown = false)
        now += 15 * min
        timer.timeUp(songLeftMs = 90_000)
        val s = timer.state.value!!
        assertTrue(s.finishing)
        assertNull("plays to the song's own end", s.cutAt)
        assertNull(s.leftMs(now))
        assertFalse("not counted as up twice", s.isTimeUp(now))
    }

    @Test fun veryLongOrUnknownSongFadesOutAtOnce() {
        timer.start(15, windDown = false)
        now += 15 * min
        timer.timeUp(songLeftMs = 25 * min)
        assertEquals(now + SLEEP_FADE_MS, timer.state.value!!.cutAt)

        timer.start(15, windDown = false)
        now += 15 * min
        timer.timeUp(songLeftMs = -1)
        assertEquals(now + SLEEP_FADE_MS, timer.state.value!!.cutAt)
    }

    @Test fun endOfThisSongFinishesStraightAway() {
        timer.startEndOfSong()
        val s = timer.state.value!!
        assertTrue(s.finishing)
        assertNull(s.cutAt)
        assertNull(s.windDownFrom())
    }

    @Test fun turningOffAndFinishingClearIt() {
        timer.start(45, windDown = true)
        timer.cancel()
        assertNull(timer.state.value)
        timer.start(45, windDown = true)
        timer.done()
        assertNull(timer.state.value)
        assertTrue("the wind-down choice is remembered", timer.windDownChosen)
    }

    @Test fun fadesOverTheLastSeconds() {
        assertEquals(1f, sleepFade(60_000), 0f)
        assertEquals(1f, sleepFade(SLEEP_FADE_MS), 0f)
        assertEquals(0.5f, sleepFade(SLEEP_FADE_MS / 2), 0.001f)
        assertEquals(0f, sleepFade(0), 0f)
        assertEquals("unknown time left keeps full volume", 1f, sleepFade(-1), 0f)
    }

    @Test fun windDownCoversTheLastPart() {
        fun from(minutes: Long, windDown: Boolean = true): Long? =
            SleepTimerState(endsAt = minutes * min, totalMs = minutes * min, windDown = windDown).windDownFrom()
        assertNull(from(60, windDown = false))
        assertEquals("last third of an hour", 40 * min, from(60))
        assertEquals("at most 20 minutes", 160 * min, from(180))
        assertEquals("at least 5 minutes", 10 * min, from(15))
        assertEquals("all of a short timer", 0L, from(3))
        assertNotNull(from(5))
    }

    @Test fun timeLeftInWords() {
        assertEquals("45 min", sleepTimeLabel(45 * min))
        assertEquals("45 min", sleepTimeLabel(44 * min + 1))
        assertEquals("1 h", sleepTimeLabel(60 * min))
        assertEquals("1 h 5 min", sleepTimeLabel(65 * min))
        assertEquals("40 s", sleepTimeLabel(40_000))
        assertEquals("0 s", sleepTimeLabel(-5))
        assertEquals("45m", sleepTimeShort(45 * min))
        assertEquals("1h05", sleepTimeShort(65 * min))
        assertEquals("<1m", sleepTimeShort(30_000))
    }
}
