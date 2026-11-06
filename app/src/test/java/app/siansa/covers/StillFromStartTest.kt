package app.siansa.covers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stills of big animated covers from the start of their file (issue #928). */
class StillFromStartTest {
    @Test
    fun bigOrUnsizedAnimationsAreReadOnlyFromTheStart() {
        // The TV's Library downloaded 61 MB to show one album's first frame.
        assertTrue(CoverStore.readsOnlyTheStart(61L * 1024 * 1024))
        // Navidrome often doesn't send a size: read the start, and keep it whole if it ends there.
        assertTrue(CoverStore.readsOnlyTheStart(-1))
        // A small one is downloaded whole, as before: the start is most of it anyway.
        assertFalse(CoverStore.readsOnlyTheStart(3L * 1024 * 1024))
        assertFalse(CoverStore.readsOnlyTheStart(CoverStore.STILL_FROM_BYTES))
    }
}
