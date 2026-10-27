package app.siansa.ui

import app.siansa.data.RecentPlay
import app.siansa.data.notedPlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's shortcuts: Liked songs first, then what was played last (issue #271). */
class QuickShortcutsTest {
    private fun p(route: String) = RecentPlay(route, route)

    @Test fun lastPlayedComesFirstThenTheFill() {
        val played = listOf(p("album/1"), p("playlist/2"))
        val fill = listOf(p("mix/today"), p("album/1"), p("album/3"))
        assertEquals(listOf("album/1", "playlist/2", "mix/today", "album/3"), quickShortcuts(played, fill, 7).map { it.route })
    }

    @Test fun likedSongsIsNeverRepeatedAndThereAreAtMostSeven() {
        val played = listOf(p("liked")) + (1..10).map { p("album/$it") }
        val shown = quickShortcuts(played, emptyList(), QUICK_COUNT - 1)
        assertEquals(7, shown.size)
        assertEquals("album/1", shown.first().route)
    }

    @Test fun aBookPlayedIsAShortcutToItsPage() {
        val book = app.siansa.data.Book(id = "b1", title = "The Tide Keeper", subtitle = "A novel")
        assertEquals(RecentPlay("book/b1", "The Tide Keeper", "bk-b1"), app.siansa.data.bookShortcut(book, episode = false))
        // An episode plays as a book named after it, with the podcast's name as the subtitle: the shortcut is the podcast.
        val episode = app.siansa.data.Book(id = "p1", title = "Episode 12", subtitle = "Tide Talk", podcast = true)
        assertEquals("Tide Talk", app.siansa.data.bookShortcut(episode, episode = true).title)
    }

    @Test fun playingSomethingAgainMovesItToTheTop() {
        val list = listOf(p("album/1"), p("album/2"), p("album/3"))
        assertEquals(listOf("album/3", "album/1", "album/2"), notedPlay(list, p("album/3")).map { it.route })
        assertEquals(2, notedPlay(list, p("album/9"), max = 2).size)
    }

    @Test fun pagesAreFoundAgainByTheirRoute() {
        assertEquals("album/42", fillRoute("album/{id}") { if (it == "id") "42" else null })
        assertEquals("radio/genre/Drum%20%26%20Bass", fillRoute("radio/genre/{name}") { "Drum & Bass" })
        assertEquals("liked", fillRoute("liked") { null })
        assertNull(fillRoute("album/{id}") { null })
        assertNull(fillRoute(null) { "x" })
    }
}
