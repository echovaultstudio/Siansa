package app.siansa.covers

import app.siansa.covers.CoverRefresh.Kept
import app.siansa.covers.CoverRefresh.Step
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sync all also refreshes the covers kept on the phone: stand-ins are removed, changed covers replaced (the owner, 11 Nov). */
class CoverRefreshTest {
    private val disc = "273a4dbd61dfbb0a12d5d8ffe780eb7a3d4d000bc9771c5411cc70ae4dfa8a1f"

    private fun kept(id: String, bytes: Long, size: String = "300") = Kept(id, size, "cover:$id:$size", bytes)

    @After fun forget() = StandIns.forget()

    @Test fun aKeptPlaceholderIsFoundAtEverySizeAndRealCoversAreLeft() {
        val blueSmall = kept("al-kids1", 69228)
        val blueBig = kept("al-kids1", 69228, "1000")
        val real = kept("al-7BAvv8V9hLxl0i28DN29fS_c35219b6e4fb56ff", 11647)
        val asBig = kept("al-other_1", 69228) // a real cover that happens to be as big
        val hashes = mapOf(blueSmall.key to disc, blueBig.key to disc, asBig.key to "f".repeat(64))
        val found = CoverRefresh.standInsAmong(listOf(blueSmall, real, blueBig, asBig), shared = true) { hashes[it.key] ?: error("${it.key} needn't be read") }
        assertEquals(listOf(blueSmall, blueBig), found)
    }

    @Test fun anUnknownStandInIsFoundByBeingManyAlbumsPicture() {
        // An older Navidrome's placeholder, shrunk to the size asked: not in the list, but the same for every album.
        val same = listOf("al-a_1", "al-b_2", "al-c_3").map { kept(it, 4321) }
        val real = listOf(kept("al-d_4", 4321), kept("al-e_5", 9000))
        val found = CoverRefresh.standInsAmong(same + real, shared = true) { if (it in same) "aa" else "bb-${it.id}" }
        assertEquals(same, found)
    }

    @Test fun oneAlbumsCoverAtTwoIdsOrOnItsSongsIsNotAStandIn() {
        val twoIds = listOf(kept("al-a", 500), kept("al-a_1", 500), kept("al-a_2", 500))
        val songs = listOf("mf-1", "mf-2", "mf-3", "mf-4").map { kept(it, 700) }
        assertEquals(emptyList<Kept>(), CoverRefresh.standInsAmong(twoIds + songs, shared = true) { "same" })
    }

    @Test fun embyAndJellyfinPicturesAreNeverGuessedAt() {
        val same = listOf("al-1", "al-2", "al-3").map { kept(it, 4321) }
        assertEquals(emptyList<Kept>(), CoverRefresh.standInsAmong(same, shared = false) { "aa" })
    }

    @Test fun whatTheServersAnswerAboutAKeptCoverMeans() {
        assertEquals("still this one", Step.KEEP, CoverRefresh.whatToDo(304, standIn = false))
        assertEquals("a picture: compared with the kept one", Step.COMPARE, CoverRefresh.whatToDo(200, standIn = false))
        assertEquals("the picture is gone", Step.REMOVE, CoverRefresh.whatToDo(200, standIn = true))
        assertEquals(Step.KEEP, CoverRefresh.whatToDo(404, standIn = false))
        assertEquals(Step.FAIL, CoverRefresh.whatToDo(502, standIn = false))
    }
}
