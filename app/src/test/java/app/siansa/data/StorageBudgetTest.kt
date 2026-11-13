package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the TV app keeps on the TV: the owner's SHIELD warned that its storage was low. The TV app had the phone's
 * 2 GB of songs and 500 MB of covers, and every animated cover it had played, for good.
 */
class StorageBudgetTest {
    @Test fun aTvWithRoomGetsTheWholeBudgetAboutOneGigabyte() {
        val budget = StorageBudget.forTv(freeMb = 5_000, heldMb = 0)
        assertEquals(StorageBudget(songsMb = 512, picturesMb = 200, animatedMb = 300), budget)
        assertEquals(1_012, budget.totalMb)
    }

    // What Siansa already keeps would be free without it, so it counts as room: a SHIELD it filled still gets the
    // budget, and the 2.6 GB it held comes down to it.
    @Test fun whatSiansaHoldsCountsAsRoom() {
        assertEquals(StorageBudget(512, 200, 300), StorageBudget.forTv(freeMb = 300, heldMb = 2_600))
    }

    @Test fun aTvShortOfRoomGetsTheSameShareOfEach() {
        // 700 free and 830 held: 1,530 without Siansa, 506 after the gigabyte left free, half the budget.
        assertEquals(StorageBudget(songsMb = 256, picturesMb = 100, animatedMb = 150), StorageBudget.forTv(freeMb = 700, heldMb = 830))
    }

    @Test fun aFullTvGetsTheLeastThePlayerAndTheCoversWorkWith() {
        assertEquals(StorageBudget(songsMb = 100, picturesMb = 50, animatedMb = 0), StorageBudget.forTv(freeMb = 200, heldMb = 100))
        assertEquals(StorageBudget(100, 50, 0), StorageBudget.forTv(freeMb = 0, heldMb = 0))
    }

    @Test fun theBudgetNeverTakesTheRoomLeftFree() {
        for (free in 0L..4_000L step 137) for (held in 0L..3_000L step 211) {
            val budget = StorageBudget.forTv(free, held)
            val least = StorageBudget.SONGS_LEAST_MB + StorageBudget.PICTURES_LEAST_MB
            // Once Siansa is down to its budget, the TV has the gigabyte free, unless there never was that much.
            val freeAfter = free + held - budget.totalMb
            assertTrue("free $free, held $held: $budget", freeAfter >= StorageBudget.KEEP_FREE_MB || budget.totalMb == least)
            assertTrue(budget.totalMb <= 1_012)
        }
    }

    private data class Kept(val name: String, val usedAt: Long, val bytes: Long)

    private fun drop(entries: List<Kept>, max: Long, keep: Kept? = null) =
        oldestToDrop(entries, max, { it.usedAt }, { it.bytes }, keep).map { it.name }

    @Test fun theOnesUsedLongestAgoGoUntilTheRestFits() {
        val kept = listOf(Kept("new", 30, 50), Kept("old", 10, 100), Kept("middle", 20, 80))
        assertEquals(emptyList<String>(), drop(kept, 230))
        assertEquals(listOf("old"), drop(kept, 229))
        assertEquals(listOf("old"), drop(kept, 130))
        assertEquals(listOf("old", "middle"), drop(kept, 129))
        assertEquals(listOf("old", "middle", "new"), drop(kept, 0))
    }

    @Test fun theOneJustWrittenStaysEvenWhenItAloneIsTooMuch() {
        val big = Kept("just downloaded", 5, 163)
        val kept = listOf(big, Kept("older", 1, 40), Kept("newer", 9, 40))
        assertEquals(listOf("older", "newer"), drop(kept, 100, keep = big))
        assertEquals(listOf("older"), drop(kept, 203, keep = big))
    }

    @Test fun theLogSaysWhatIsKeptAgainstWhatMayBe() {
        val mb = 1024L * 1024L
        val line = TvStorage.line(TvStorage.Held(songs = 1_900 * mb, pictures = 480 * mb, animated = 1_720 * mb, library = 60 * mb, logs = 31 * mb), StorageBudget(512, 200, 300), freeMb = 340, totalMb = 5_800)
        assertEquals("Storage: songs 1900 of 512 MB, covers 480 of 200 MB, animated covers 1720 of 300 MB, library 60 MB, logs 31 MB · TV free 340 of 5800 MB", line)
    }
}
