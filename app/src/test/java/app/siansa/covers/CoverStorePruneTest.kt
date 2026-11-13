package app.siansa.covers

import android.app.Application
import org.robolectric.RuntimeEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The TV app keeps a limited amount of animated covers: it kept every one it had played, 17 to 163 MB each, and the
 * owner's SHIELD ran low on storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CoverStorePruneTest {
    private val context = RuntimeEnvironment.getApplication()
    private val dir = File(context.filesDir, "covers")

    private fun cover(name: String, bytes: Int, playedAt: Long): File {
        File(dir, "$name.jpg").writeBytes(ByteArray(10))
        return File(dir, "$name.anim").apply {
            writeBytes(ByteArray(bytes))
            setLastModified(playedAt)
        }
    }

    @Before fun setUp() {
        CoverStore.init(context)
        dir.listFiles()?.forEach { it.delete() }
    }

    @After fun tearDown() {
        CoverStore.maxOriginalBytes = Long.MAX_VALUE
        dir.listFiles()?.forEach { it.delete() }
    }

    @Test fun aPhoneKeepsThemAll() {
        cover("al-1", 4_000, 1_000_000)
        cover("al-2", 4_000, 2_000_000)
        assertEquals(0L, CoverStore.prune())
        assertEquals(4, dir.listFiles()!!.size)
    }

    @Test fun theAnimationsPlayedLongestAgoGoAndTheirStillsStay() {
        val old = cover("al-old", 4_000, 1_000_000)
        val middle = cover("al-middle", 3_000, 2_000_000)
        val new = cover("al-new", 2_000, 3_000_000)
        CoverStore.maxOriginalBytes = 5_000
        assertEquals(4_000L, CoverStore.prune())
        assertTrue(!old.exists() && middle.exists() && new.exists())
        // Lists still have every still, so nothing is downloaded again until an animation plays.
        assertEquals(setOf("al-old.jpg", "al-middle.jpg", "al-new.jpg"), dir.listFiles()!!.map { it.name }.filter { it.endsWith(".jpg") }.toSet())
        assertEquals(0L, CoverStore.prune())
    }

    @Test fun theOneJustDownloadedStaysThoughItAloneIsTooBig() {
        val old = cover("al-old", 1_000, 1_000_000)
        val big = cover("al-big", 9_000, 2_000_000)
        CoverStore.maxOriginalBytes = 5_000
        assertEquals(1_000L, CoverStore.prune(keep = big))
        assertTrue(big.exists() && !old.exists())
    }

    @Test fun noRoomForAnimationsKeepsNone() {
        cover("al-1", 1_000, 1_000_000)
        cover("al-2", 1_000, 2_000_000)
        CoverStore.maxOriginalBytes = 0
        assertEquals(2_000L, CoverStore.prune())
        assertEquals(listOf("al-1.jpg", "al-2.jpg"), dir.listFiles()!!.map { it.name }.sorted())
    }
}
