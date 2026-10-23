package app.siansa.mix

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Carrying a mix on where it was left (issue #269). */
class MixResumeTest {
    private val songs = listOf(Song("a", "A", durationSec = 200), Song("b", "B", durationSec = 180), Song("c", "C", durationSec = 240))
    private fun at(id: String, pos: Long, dur: Long = 180_000) = ResumePoint(id, pos, dur, 0)

    @Test fun nothingSavedStartsFromTheTop() = assertNull(resumeStart(songs, null))

    @Test fun carriesOnAtTheExactSpot() = assertEquals(1 to 95_000L, resumeStart(songs, at("b", 95_000)))

    @Test fun aSongJustStartedStartsFromItsBeginning() = assertEquals(1 to 0L, resumeStart(songs, at("b", 3_000)))

    @Test fun aSongThatHadEndedCarriesOnWithTheNext() {
        assertEquals(2 to 0L, resumeStart(songs, at("b", 175_000)))
        assertEquals(2 to 0L, resumeStart(songs, at("b", 177_000)))
    }

    @Test fun afterTheLastSongItStartsOver() = assertNull(resumeStart(songs, at("c", 238_000, 240_000)))

    @Test fun aSongNoLongerInTheMixStartsOver() = assertNull(resumeStart(songs, at("gone", 50_000)))

    @Test fun withoutAKnownLengthHalfDecides() {
        val noLength = listOf(Song("x", "X"), Song("y", "Y"))
        assertEquals(0 to 0L, resumeStart(noLength, at("x", 10_000, 0)))
        assertEquals(1 to 0L, resumeStart(noLength, at("x", 60_000, 0)))
    }

    @Test fun theSongsOwnLengthIsUsedWhenThePlayerDidntSay() =
        assertEquals(0 to 120_000L, resumeStart(songs, at("a", 120_000, 0)))
}
