package app.siansa

import app.siansa.data.Song
import app.siansa.mix.CountPoint
import app.siansa.mix.MixGenerator
import app.siansa.mix.MixRules
import app.siansa.mix.OnRepeat
import app.siansa.mix.SongPlays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** On repeat: the songs played most in the last 30 days (issue #495). */
class OnRepeatTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private fun plays(id: String, count: Long, daysAgo: Long?) = SongPlays(Song(id, id), count, daysAgo?.let { now - it * day })

    @Test fun onlyChangesInTheCountAreKept() {
        var points = OnRepeat.addPoint(emptyList(), 100, 5)
        points = OnRepeat.addPoint(points, 101, 5)
        points = OnRepeat.addPoint(points, 102, 7)
        // A second look the same day replaces that day's count.
        points = OnRepeat.addPoint(points, 102, 8)
        assertEquals(listOf(CountPoint(100, 5), CountPoint(102, 8)), points)
    }

    @Test fun oldPointsGoButTheLastBeforeTheWindowStays() {
        val points = listOf(CountPoint(1, 2), CountPoint(10, 4), CountPoint(60, 9))
        // Six weeks kept from day 70: day 10 is the last before them, day 1 goes.
        assertEquals(listOf(CountPoint(10, 4), CountPoint(60, 9), CountPoint(70, 12)), OnRepeat.addPoint(points, 70, 12, keepDays = 45))
    }

    @Test fun theCountOnADayIsTheLastSeenByThen() {
        val points = listOf(CountPoint(10, 4), CountPoint(40, 9))
        assertNull(OnRepeat.countOn(points, 5))
        assertEquals(4L, OnRepeat.countOn(points, 10))
        assertEquals(4L, OnRepeat.countOn(points, 39))
        assertEquals(9L, OnRepeat.countOn(points, 70))
    }

    @Test fun serverSongsCountTheirPlaysInTheWindow() {
        // Played 20 times now, 12 times 30 days ago: 8 plays in the window.
        assertEquals(8.0, OnRepeat.serverScore(plays("a", 20, 2), countBefore = 12, now = now)!!, 0.0)
        // Count from back then unknown: a little over one play, more for songs played a lot overall.
        val unknown = OnRepeat.serverScore(plays("b", 20, 2), countBefore = null, now = now)!!
        assertTrue(unknown > 1.0 && unknown < 3.0)
        // Not played in the window, or never.
        assertNull(OnRepeat.serverScore(plays("c", 20, 45), 12, now))
        assertNull(OnRepeat.serverScore(plays("d", 0, 1), null, now))
        assertNull(OnRepeat.serverScore(plays("e", 3, null), null, now))
    }

    @Test fun theHighestOfTheSourcesCountsAndSkippedSongsGo() {
        val ids = OnRepeat.rank(
            server = mapOf("a" to 8.0, "b" to 1.5),
            phone = mapOf("b" to 6, "c" to 2),
            ranked = listOf("d", "a"),
            skipped = setOf("c"),
        )
        // a: 8 (server); b: 6 (phone, not 6 + 1.5); d: first of the services, 4; c skipped.
        assertEquals(listOf("a", "b", "d"), ids)
    }

    @Test fun atMostFiftySongs() {
        val many = (1..80).associate { "s$it" to it.toDouble() }
        assertEquals(OnRepeat.MAX_SONGS, OnRepeat.rank(many, emptyMap(), emptyList(), emptySet()).size)
    }

    @Test fun onRepeatsTileGoesWhenThereIsntEnough() {
        val repeat = MixGenerator.Personal.REPEAT
        assertTrue(MixRules.showsPersonalTile(repeat, audioMuseReady = false, madeToday = false, notEnough = false))
        assertFalse(MixRules.showsPersonalTile(repeat, audioMuseReady = false, madeToday = false, notEnough = true))
        // Made today after all (more plays since): shown.
        assertTrue(MixRules.showsPersonalTile(repeat, audioMuseReady = false, madeToday = true, notEnough = true))
    }
}
