package app.siansa

import app.siansa.data.mixScopeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Beta report 9b7bed73: today's mix kept the music shown when it was made after the source filter changed. */
class MixScopeTest {
    @Test fun everythingOfYoursIsTheEmptyKey() {
        // So mixes made before this version stay in use until the filter changes.
        assertEquals("", mixScopeKey(ownHidden = false, guests = emptySet(), folders = emptySet()))
    }

    @Test fun anyChangeToWhatsShownIsAnotherKey() {
        val mine = mixScopeKey(false, emptySet(), emptySet())
        val kidsToo = mixScopeKey(false, setOf(2), emptySet())
        val onlyKids = mixScopeKey(true, setOf(2), emptySet())
        val oneLibrary = mixScopeKey(false, emptySet(), setOf("lib1"))
        listOf(kidsToo, onlyKids, oneLibrary).forEach { assertNotEquals(mine, it) }
        assertNotEquals(kidsToo, onlyKids)
        // The same choice in another order is the same key.
        assertEquals(mixScopeKey(false, setOf(2, 3), setOf("b", "a")), mixScopeKey(false, setOf(3, 2), setOf("a", "b")))
    }
}
