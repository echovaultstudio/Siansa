package app.siansa.mix

import app.siansa.data.HomeSection
import app.siansa.data.NoticeStore
import app.siansa.ui.noticesLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The chips row at the top of Home (issue #784): which notices show, in what order, and the settings for them. */
class NoticesTest {
    private val sync = Notice(NoticeKind.NEEDS_YOU, "needs:sync", "Library sync failed", "settings/connections")
    private val book = Notices.carryOn("b1", "Dune")
    private val albums = Notices.newAlbums(3, "a9")!!
    private val month = Notices.yourMonth(LocalDate.of(2026, 11, 2))!!
    private val artists = Notices.newArtists(4, LocalDate.of(2026, 11, 2))!!
    private val all = listOf(month, artists, albums, book, sync)

    @Test fun automaticPutsWhatNeedsYouFirstThenWhatYoureInThenWhatsNew() {
        val order = Notices.order(all, NoticePrefs(), emptyMap(), emptySet())
        assertEquals(listOf(sync, book, month, artists, albums), order)
    }

    @Test fun withinATierTheKindsYouTapMostComeFirst() {
        val order = Notices.order(all, NoticePrefs(), mapOf(NoticeKind.NEW_ALBUMS to 5, NoticeKind.NEW_ARTISTS to 2), emptySet())
        assertEquals(listOf(sync, book, albums, artists, month), order)
    }

    @Test fun tapsDontLiftAKindAboveAHigherTier() {
        val order = Notices.order(all, NoticePrefs(), mapOf(NoticeKind.YOUR_MONTH to 99), emptySet())
        assertEquals(listOf(sync, book), order.take(2))
    }

    @Test fun pinnedKindsComeFirstInTheOrderPinned() {
        val prefs = NoticePrefs().with(NoticeKind.YOUR_MONTH, NoticeMode.PINNED).with(NoticeKind.NEW_ALBUMS, NoticeMode.PINNED)
        val order = Notices.order(all, prefs, emptyMap(), emptySet())
        assertEquals(listOf(month, albums, sync, book, artists), order)
    }

    @Test fun offKindsAndHiddenNoticesAreLeftOut() {
        val prefs = NoticePrefs().with(NoticeKind.CARRY_ON, NoticeMode.OFF)
        val order = Notices.order(all, prefs, emptyMap(), setOf(month.key))
        assertFalse(book in order)
        assertFalse(month in order)
        assertEquals(3, order.size)
    }

    @Test fun atMostFiveShow() {
        val many = all + Notices.carryOn("b2", "Emma")
        assertEquals(Notices.MAX, Notices.order(many, NoticePrefs(), emptyMap(), emptySet()).size)
    }

    @Test fun nothingToSayShowsNothing() {
        assertTrue(Notices.order(emptyList(), NoticePrefs(), emptyMap(), emptySet()).isEmpty())
    }

    @Test fun modesRoundTripKeepingThePinnedOrder() {
        val prefs = NoticePrefs()
            .with(NoticeKind.NEW_ARTISTS, NoticeMode.PINNED)
            .with(NoticeKind.YOUR_MONTH, NoticeMode.OFF)
            .with(NoticeKind.CARRY_ON, NoticeMode.PINNED)
        assertEquals("artists:pinned,carry_on:pinned,month:off", prefs.format())
        val back = NoticePrefs.parse(prefs.format())
        assertEquals(prefs, back)
        assertEquals(listOf(NoticeKind.NEW_ARTISTS, NoticeKind.CARRY_ON), back.pinned)
    }

    @Test fun backToAutomaticForgetsTheKind() {
        val prefs = NoticePrefs().with(NoticeKind.CARRY_ON, NoticeMode.PINNED).with(NoticeKind.CARRY_ON, NoticeMode.AUTO)
        assertEquals(NoticePrefs(), prefs)
        assertEquals("", prefs.format())
        assertEquals(NoticeMode.AUTO, prefs.modeOf(NoticeKind.CARRY_ON))
    }

    @Test fun unknownSavedPartsAreDropped() {
        assertEquals(NoticePrefs(), NoticePrefs.parse(null))
        assertEquals(NoticePrefs(), NoticePrefs.parse(""))
        val prefs = NoticePrefs.parse("gone:pinned,month:loud,month:off,month:pinned")
        assertEquals(NoticeMode.OFF, prefs.modeOf(NoticeKind.YOUR_MONTH))
        assertTrue(prefs.pinned.isEmpty())
    }

    @Test fun yourMonthShowsInTheFirstWeekOnly() {
        val notice = Notices.yourMonth(LocalDate.of(2026, 1, 3))
        assertNotNull(notice)
        assertEquals("month:2025-12", notice!!.key)
        assertEquals("report/last-month", notice.route)
        assertNotNull(Notices.yourMonth(LocalDate.of(2026, 3, Notices.MONTH_DAYS)))
        assertNull(Notices.yourMonth(LocalDate.of(2026, 3, Notices.MONTH_DAYS + 1)))
    }

    @Test fun newAlbumsCountsOnlyTheLastFewDays() {
        val today = LocalDate.of(2026, 11, 1)
        val created = listOf("2026-11-01T08:00:00Z", "2026-10-29T10:00:00.000Z", "2026-10-28T23:00:00Z", null, "junk", "2026-11-05")
        assertEquals(2, Notices.newAlbumCount(created, today))
        assertNull(Notices.newAlbums(0, "a"))
        assertEquals("1 new album", Notices.newAlbums(1, "a")!!.text)
        assertEquals("home/newest", albums.route)
    }

    @Test fun carryOnIsOnlyForBooksPlayedThisWeek() {
        val now = 100L * 24 * 60 * 60 * 1000
        assertTrue(Notices.recentlyPlayed(now - 60_000, now))
        assertFalse(Notices.recentlyPlayed(now - (Notices.CARRY_ON_DAYS + 1L) * 24 * 60 * 60 * 1000, now))
        assertFalse(Notices.recentlyPlayed(0, now))
        assertEquals("book/b1", book.route)
        assertEquals("Carry on: Dune", book.text)
    }

    @Test fun hiddenNoticesKeepTheNewest() {
        var hidden = emptyList<String>()
        repeat(Notices.MAX_HIDDEN + 10) { hidden = Notices.hide(hidden, "k$it") }
        assertEquals(Notices.MAX_HIDDEN, hidden.size)
        assertEquals("k${Notices.MAX_HIDDEN + 9}", hidden.last())
        assertEquals(listOf("a", "b"), Notices.hide(listOf("b", "a"), "b"))
    }

    @Test fun tapsRoundTrip() {
        val taps = mapOf(NoticeKind.YOUR_MONTH to 2, NoticeKind.CARRY_ON to 5)
        assertEquals("carry_on:5,month:2", NoticeStore.formatTaps(taps))
        assertEquals(taps, NoticeStore.parseTaps(NoticeStore.formatTaps(taps)))
        assertEquals(emptyMap<NoticeKind, Int>(), NoticeStore.parseTaps("gone:3,month:x,artists:-1"))
    }

    @Test fun noticesAreAtTheTopOfHomeAndHaveNoLook() {
        assertEquals(HomeSection.NOTICES, HomeSection.default.first())
        assertFalse(HomeSection.NOTICES.lookable)
        // An order saved before notices existed gets them back at the top.
        assertEquals(HomeSection.NOTICES, HomeSection.parse("quick,today").first())
    }

    @Test fun settingsLineSaysWhatsSet() {
        assertEquals("Hidden", noticesLine(false, NoticePrefs()))
        assertEquals("Shown", noticesLine(true, NoticePrefs()))
        val prefs = NoticePrefs().with(NoticeKind.CARRY_ON, NoticeMode.PINNED).with(NoticeKind.YOUR_MONTH, NoticeMode.OFF).with(NoticeKind.NEW_ARTISTS, NoticeMode.OFF)
        assertEquals("Shown · 1 pinned · 2 off", noticesLine(true, prefs))
    }
}
