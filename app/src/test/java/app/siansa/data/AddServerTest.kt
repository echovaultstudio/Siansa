package app.siansa.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Adding a music server (issue #828): nothing is added until it's saved, and adding doesn't switch to it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AddServerTest {
    @Test fun aServerIsAddedOnlyOnceItsLoginIsKept() {
        val context = RuntimeEnvironment.getApplication()
        val before = Users.ids(context)
        // A login that can't be kept (a bad address): no user is added.
        val failed = runCatching { Users.add(context) { throw IllegalArgumentException("Not a server address") } }
        assertTrue(failed.isFailure)
        assertEquals(before, Users.ids(context))
        // Kept: it's added, after its settings were saved, and the user in use stays the same.
        var savedFor = -1
        val id = Users.add(context) { savedFor = it }
        assertEquals(id, savedFor)
        assertTrue(id in Users.ids(context))
        assertFalse(id in before)
        assertEquals(before.size + 1, Users.ids(context).size)
    }
}
