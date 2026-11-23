package app.siansa.ui

import app.siansa.data.RecentPlay
import app.siansa.data.notedPlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Home's shortcuts: what was played last, as many as set, nothing pinned (issues #271, #934). */
class QuickShortcutsTest {
    private fun p(route: String) = RecentPlay(route, route)

    @Test fun lastPlayedComesFirstThenTheFill() {
        val played = listOf(p("album/1"), p("playlist/2"))
        val fill = listOf(p("mix/today"), p("album/1"), p("album/3"))
        assertEquals(listOf("album/1", "playlist/2", "mix/today", "album/3"), quickShortcuts(played, fill, 7).map { it.route })
    }

    @Test fun likedSongsIsOnlyThereWhenItWasPlayed() {
        val played = listOf(p("album/1"), p("liked"), p("album/2"))
        assertEquals(listOf("album/1", "liked", "album/2"), quickShortcuts(played, emptyList(), 8).map { it.route })
        assertEquals(listOf("album/1", "album/2"), quickShortcuts(listOf(p("album/1"), p("album/2")), emptyList(), 8).map { it.route })
    }

    /** The owner, 23 Nov: "no way to set number of shortcuts vertically. Its always 8… Minimum 1 maximum 4 should be doable." */
    @Test fun shortcutsAreSetInRowsFromOneToFour() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4), (1..8).map { shortcutRows(it) })
        assertEquals(4, shortcutRows(20))
        // Two a row (and big tiles, pill chips): the number itself, so a row's cog and Settings › Home agree.
        for (rows in SHORTCUT_ROWS) assertEquals(rows * 2, shortcutCount(rows * 2, null))
        assertEquals(5, shortcutCount(5, app.siansa.data.LookVariant.BIG))
        // Three a row fills each of its rows: the rows chosen are the rows shown.
        assertEquals(listOf(3, 6, 9, 12), SHORTCUT_ROWS.map { shortcutCount(it * 2, app.siansa.data.LookVariant.THREE) })
        assertEquals(QUICK_MOST, shortcutCount(8, app.siansa.data.LookVariant.THREE))
    }

    /** The owner, 23 Nov: "sliders for tiles shapes with perfect text spacing". */
    @Test fun aStopSlidersLabelsSitUnderTheirStops() {
        assertEquals(listOf(0, 0, 1, 2, 3, 3), listOf(-1f, 0.4f, 0.6f, 2.2f, 2.9f, 9f).map { stopFor(it, 4) })
        // Four stops across 900: at 0, 300, 600 and 900. The middle labels are centred on theirs, the outer two kept inside.
        assertEquals(0, stopLabelLeft(0, 4, 900, 120))
        assertEquals(240, stopLabelLeft(1, 4, 900, 120))
        assertEquals(540, stopLabelLeft(2, 4, 900, 120))
        assertEquals(780, stopLabelLeft(3, 4, 900, 120))
        assertEquals(0, stopLabelLeft(0, 1, 100, 400))  // wider than the row: at its start, not before it
    }

    @Test fun asManyAsSetFromTwoToEight() {
        val played = (1..12).map { p("album/$it") }
        assertEquals(4, quickShortcuts(played, emptyList(), 4).size)
        assertEquals("album/1", quickShortcuts(played, emptyList(), 4).first().route)
        assertEquals(QUICK_MOST, quickShortcuts(played, emptyList(), 20).size)
        assertEquals(QUICK_MIN, quickShortcuts(played, emptyList(), 0).size)
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
