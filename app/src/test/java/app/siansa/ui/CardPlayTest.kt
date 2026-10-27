package app.siansa.ui

import app.siansa.data.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A card's play button knows its mix is the queue (#562). */
class CardPlayTest {
    private fun s(id: String) = Song(id = id, title = id)
    private val mix = listOf(s("a"), s("b"), s("c"))

    @Test fun theMixAsTheQueueIsLoadedInAnyOrder() {
        assertTrue(queueIsList(mix, emptySet(), mix))
        assertTrue(queueIsList(listOf(s("c"), s("a"), s("b")), emptySet(), mix))
    }

    @Test fun songsKeepPlayingAddedDontCount() {
        assertTrue(queueIsList(mix + s("x") + s("y"), setOf(3, 4), mix))
        assertFalse(queueIsList(mix + s("x"), emptySet(), mix))
    }

    @Test fun anotherListOrNothingIsNotLoaded() {
        assertFalse(queueIsList(listOf(s("a"), s("b"), s("z")), emptySet(), mix))
        assertFalse(queueIsList(emptyList(), emptySet(), mix))
        assertFalse(queueIsList(mix, emptySet(), emptyList()))
    }
}
