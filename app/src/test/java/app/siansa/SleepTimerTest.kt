package app.siansa

import app.siansa.mix.windDownSongs
import app.siansa.playback.SLEEP_FADE_MS
import app.siansa.playback.SleepTimer
import app.siansa.playback.SleepTimerState
import app.siansa.playback.isTimeUp
import app.siansa.playback.leftMs
import app.siansa.playback.sleepFade
import app.siansa.playback.sleepPauseNow
import app.siansa.playback.sleepTimeLabel
import app.siansa.playback.sleepTimeShort
import app.siansa.playback.windDownLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun timeUpFadesOutAtTheChosenTime() {
        timer.start(15, windDown = false)
        now += 15 * min
        timer.timeUp(songLeftMs = 90_000)
        val s = timer.state.value!!
        assertTrue(s.finishing)
        assertEquals("fades out now, not at the song's end (issue #153)", now + SLEEP_FADE_MS, s.cutAt)
        assertNull(s.leftMs(now))
        assertFalse("not counted as up twice", s.isTimeUp(now))
    }

    @Test fun songEndingWithinTheFadePlaysToItsEnd() {
        timer.start(15, windDown = false)
        now += 15 * min
        timer.timeUp(songLeftMs = 5_000)
        assertNull(timer.state.value!!.cutAt)
    }

    @Test fun unknownSongLengthFadesOutAtOnce() {
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
        assertFalse(s.windDown)
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

    @Test fun windDownSwitchWorksOnItsOwn() {
        timer.setWindDown(true)
        assertTrue("remembered without starting a timer (issue #153)", timer.windDownChosen)
        assertNull(timer.state.value)
        timer.start(30, windDown = false)
        timer.setWindDown(true)
        assertTrue("applied to the running timer", timer.state.value!!.windDown)
        assertEquals("the time is kept", now + 30 * min, timer.state.value!!.endsAt)
        timer.setWindDown(false)
        assertFalse(timer.state.value!!.windDown)
        assertFalse(timer.windDownChosen)
        timer.startEndOfSong()
        timer.setWindDown(true)
        assertFalse("not for the end of this song", timer.state.value!!.windDown)
    }

    @Test fun windDownPathFillsTheTimer() {
        assertEquals("an hour of 4-minute songs after this one", 16, windDownLength(60 * min))
        assertEquals("at least 3", 3, windDownLength(2 * min))
        assertEquals("at most 30", 30, windDownLength(180 * min))
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

    @Test
    fun pausesAtTheRightMomentOnPhoneAndTv() {
        val running = SleepTimerState(endsAt = 1_000, totalMs = 1_000)
        val finishing = running.copy(finishing = true)
        val cutShort = finishing.copy(cutAt = 5_000)
        // Not finishing yet: never pause.
        assertFalse(sleepPauseNow(onPhone = true, state = running, leftMs = 0))
        assertFalse(sleepPauseNow(onPhone = false, state = running, leftMs = 0))
        // Phone: the player pauses itself at the song's end; only a cut-short song is paused here.
        assertFalse(sleepPauseNow(onPhone = true, state = finishing, leftMs = 0))
        assertFalse(sleepPauseNow(onPhone = true, state = cutShort, leftMs = 100))
        assertTrue(sleepPauseNow(onPhone = true, state = cutShort, leftMs = 0))
        // TV or speaker: pause just before the song ends, and not while its length is unknown.
        assertFalse(sleepPauseNow(onPhone = false, state = finishing, leftMs = 5_000))
        assertTrue(sleepPauseNow(onPhone = false, state = finishing, leftMs = 400))
        assertFalse(sleepPauseNow(onPhone = false, state = finishing, leftMs = -1))
    }

    @Test fun windDownWorksWithoutATimer() {
        assertFalse(timer.windingDown())
        timer.setWindDown(true)
        assertTrue(timer.windDownAlone.value)
        assertTrue(timer.windingDown())
        timer.setWindDown(false)
        assertFalse(timer.windDownAlone.value)
        assertFalse(timer.windingDown())
    }

    @Test fun aTimerTakesOverAWindDownOnItsOwn() {
        timer.setWindDown(true)
        timer.start(30, windDown = true)
        assertFalse(timer.windDownAlone.value)
        assertTrue(timer.windingDown())
        timer.timeUp(songLeftMs = 90_000)
        assertFalse(timer.windingDown())
    }

    @Test fun windDownLengthFollowsTheTimerOrTheSetting() {
        assertEquals(windDownLength(30 * min), windDownSongs(30 * min, alone = false, setting = 20))
        assertEquals(21, windDownSongs(null, alone = true, setting = 20))
        assertNull(windDownSongs(null, alone = false, setting = 20))
    }

    @Test fun aWindDownOnItsOwnEndsWithItsLastSong() {
        timer.setWindDown(true)
        timer.queueEnded()
        assertFalse(timer.windDownAlone.value)
        assertFalse(timer.windingDown())
        assertFalse(timer.windDownChosen)
    }

    @Test fun theQueueEndingLeavesAWindDownWithATimerAlone() {
        timer.start(30, windDown = true)
        timer.queueEnded()
        assertTrue(timer.windingDown())
        assertTrue(timer.windDownChosen)
    }

    @Test fun switchingWindDownOffIsRememberedAfterTheTimerEnds() {
        timer.start(30, windDown = true)
        assertTrue(timer.windDownChosen)
        timer.setWindDown(false)
        timer.cancel()
        assertFalse(timer.windDownChosen)
    }
}
