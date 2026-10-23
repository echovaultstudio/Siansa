package app.musix

import app.musix.data.PickUp
import app.musix.data.PickUps
import app.musix.data.PlayEvent
import app.musix.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PickUpTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L
    private val mix = (1..7).map { Song("m$it", "Track $it", durationSec = 300, track = it) }

    /** A play [hoursAgo] before now; heard in full unless [heardSec] says otherwise. */
    private fun play(id: String, hoursAgo: Double, heardSec: Int? = 300, skipped: Boolean = false) =
        PlayEvent(id, id, "", at = now - (hoursAgo * hour).toLong(), listenedMs = heardSec?.let { it * 1000L }, skipped = skipped)

    @Test fun carriesOnAfterTheLastSongHeardInFull() {
        val history = listOf(play("m4", 20.0), play("m3", 20.1), play("m2", 20.2), play("m1", 20.3))
        assertEquals(PickUp(lastIndex = 3, at = now - 20 * hour, continueIndex = 4), PickUps.find(mix, history, now))
    }

    @Test fun startsAgainAtASongStoppedPartWay() {
        val history = listOf(play("m5", 2.0, heardSec = 90), play("m4", 2.1))
        assertEquals(4, PickUps.find(mix, history, now)?.continueIndex)
        // Still playing or the app was closed: not known to be finished, so the same song.
        assertEquals(4, PickUps.find(mix, listOf(play("m5", 2.0, heardSec = null), play("m4", 2.1)), now)?.continueIndex)
    }

    @Test fun nothingForASingleSongFromElsewhere() {
        // One song of the album came up on a radio, between other songs.
        val history = listOf(play("x1", 1.0), play("m3", 1.1), play("x2", 1.2))
        assertNull(PickUps.find(mix, history, now))
        // Alone in the history.
        assertNull(PickUps.find(mix, listOf(play("m3", 1.0)), now))
    }

    @Test fun nothingOnceTheAlbumIsHeardToTheEnd() {
        assertNull(PickUps.find(mix, listOf(play("m7", 3.0), play("m6", 3.1)), now))
        // The last song stopped part way still offers to carry on with it.
        assertEquals(6, PickUps.find(mix, listOf(play("m7", 3.0, heardSec = 60), play("m6", 3.1)), now)?.continueIndex)
    }

    @Test fun goesAfterAWeek() {
        assertNull(PickUps.find(mix, listOf(play("m4", 7 * 24 + 1.0), play("m3", 7 * 24 + 1.1)), now))
        assertEquals(3, PickUps.find(mix, listOf(play("m4", 7 * 24 - 1.0), play("m3", 7 * 24 - 0.9)), now)?.lastIndex)
    }

    @Test fun twoPlaysFarApartAreNotOneListen() {
        assertNull(PickUps.find(mix, listOf(play("m4", 1.0), play("m3", 30.0)), now))
    }

    @Test fun laterPlaysOfOtherMusicDontHideIt() {
        val history = listOf(play("x1", 1.0), play("x2", 1.1), play("m2", 5.0), play("m1", 5.1))
        assertEquals(2, PickUps.find(mix, history, now)?.continueIndex)
    }

    @Test fun skippedSongIsNotHeardOut() {
        assertFalse(PickUps.heardOut(play("m1", 0.0, heardSec = 10, skipped = true), 300))
        assertTrue(PickUps.heardOut(play("m1", 0.0, heardSec = 280), 300))
        assertFalse(PickUps.heardOut(play("m1", 0.0, heardSec = 200), 300))
    }

    @Test fun daysReadNaturally() {
        val zone = ZoneId.of("Europe/Helsinki")
        val today = ZonedDateTime.of(2026, 10, 23, 19, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun at(d: Int, h: Int) = ZonedDateTime.of(2026, 10, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("today", PickUps.whenLabel(at(23, 1), today, zone))
        assertEquals("yesterday", PickUps.whenLabel(at(22, 23), today, zone))
        assertEquals("4 days ago", PickUps.whenLabel(at(19, 12), today, zone))
    }

    @Test fun trackNumberHasTheDiscOnlyWhenThereAreSeveral() {
        assertEquals("5", PickUps.trackLabel(mix, 4))
        val twoDiscs = listOf(Song("a", "A", track = 1, disc = 1), Song("b", "B", track = 1, disc = 2), Song("c", "C", track = 2, disc = 2))
        assertEquals("2-2", PickUps.trackLabel(twoDiscs, 2))
    }
}
