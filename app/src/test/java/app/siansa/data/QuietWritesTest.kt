package app.siansa.data

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** Saves made as music plays go to Siansa's own thread, in order (issue #860: a song's start waited for them). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QuietWritesTest {
    @Test fun savesArriveInTheOrderMade() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("quiet_test", Context.MODE_PRIVATE)
        QuietWrites.save(prefs) { putString("a", "1") }
        QuietWrites.save(prefs) { putString("a", "2"); putBoolean("b", true) }
        QuietWrites.save(prefs) { remove("b") }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline && (prefs.getString("a", null) != "2" || prefs.contains("b"))) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertEquals("2", prefs.getString("a", null))
        assertEquals(false, prefs.contains("b"))
    }
}
