package app.musix.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Liked songs list right after a heart is tapped (issue #250). */
class LikedMergeTest {
    private fun song(id: String) = Song(id, "Song $id")

    @Test fun aSongLikedJustNowShowsFirstBeforeNavidromeListsIt() {
        val server = listOf(song("a"), song("b"))
        val merged = mergeLikedSongs(server, likedHere = listOf(song("new")), overrides = mapOf("new" to true))
        assertEquals(listOf("new", "a", "b"), merged.map { it.id })
    }

    @Test fun onceNavidromeListsItItIsntThereTwice() {
        val server = listOf(song("new"), song("a"))
        val merged = mergeLikedSongs(server, likedHere = listOf(song("new")), overrides = mapOf("new" to true))
        assertEquals(listOf("new", "a"), merged.map { it.id })
    }

    @Test fun unlikedInTheAppIsLeftOut() {
        val server = listOf(song("a"), song("b"))
        val merged = mergeLikedSongs(server, likedHere = emptyList(), overrides = mapOf("a" to false))
        assertEquals(listOf("b"), merged.map { it.id })
    }

    @Test fun likedThenUnlikedDoesntShow() {
        val merged = mergeLikedSongs(emptyList(), likedHere = listOf(song("x")), overrides = mapOf("x" to false))
        assertEquals(emptyList<String>(), merged.map { it.id })
    }
}
