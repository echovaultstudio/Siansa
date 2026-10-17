package app.musix

import app.musix.playback.SeekTracker
import org.junit.Assert.assertEquals
import org.junit.Test

/** The now playing bar: no jumping back after a seek, no mixing of songs on a track change. */
class SeekTrackerTest {
    @Test fun staysAtSeekTargetUntilPlayerArrives() {
        val t = SeekTracker()
        t.update("a", 10_000, 200_000, playing = true, nowMs = 0)
        t.seekTo(120_000, nowMs = 100)
        // The player still reports the old spot for a moment: the bar must not fly back.
        assertEquals(120_000, t.update("a", 10_200, 200_000, playing = true, nowMs = 150).positionMs)
        assertEquals(120_300, t.update("a", 120_300, 200_000, playing = true, nowMs = 400).positionMs)
    }

    @Test fun givesUpWaitingForSeekAfterAWhile() {
        val t = SeekTracker(settleMs = 2_000)
        t.update("a", 10_000, 200_000, playing = true, nowMs = 0)
        t.seekTo(120_000, nowMs = 0)
        assertEquals(12_000, t.update("a", 12_000, 200_000, playing = true, nowMs = 2_500).positionMs)
    }

    @Test fun newSongStartsFromItsOwnPosition() {
        val t = SeekTracker()
        t.update("a", 190_000, 200_000, playing = true, nowMs = 0)
        t.seekTo(195_000, nowMs = 10)
        val shown = t.update("b", 0, 90_000, playing = true, nowMs = 50)
        assertEquals(0, shown.positionMs)
        assertEquals(90_000, shown.durationMs)
        assertEquals(0f, shown.fraction, 0f)
    }

    @Test fun ignoresSmallBackwardCorrectionsWhilePlaying() {
        val t = SeekTracker()
        t.update("a", 50_000, 200_000, playing = true, nowMs = 0)
        assertEquals(50_000, t.update("a", 49_800, 200_000, playing = true, nowMs = 16).positionMs)
        assertEquals(50_100, t.update("a", 50_100, 200_000, playing = true, nowMs = 32).positionMs)
    }

    @Test fun followsLargeBackwardJumps() {
        val t = SeekTracker()
        t.update("a", 50_000, 200_000, playing = true, nowMs = 0)
        // Previous restarts the song.
        assertEquals(0, t.update("a", 0, 200_000, playing = true, nowMs = 16).positionMs)
    }

    @Test fun followsSmallBackwardChangesWhilePaused() {
        val t = SeekTracker()
        t.update("a", 50_000, 200_000, playing = false, nowMs = 0)
        assertEquals(49_800, t.update("a", 49_800, 200_000, playing = false, nowMs = 200).positionMs)
    }

    @Test fun positionNeverPastTheEnd() {
        val t = SeekTracker()
        val shown = t.update("a", 210_000, 200_000, playing = true, nowMs = 0)
        assertEquals(200_000, shown.positionMs)
        assertEquals(1f, shown.fraction, 0f)
    }
}
