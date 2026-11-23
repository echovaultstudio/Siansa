package app.siansa.mix

import app.siansa.data.HomeSection
import app.siansa.data.Song
import app.siansa.ui.ListenPage
import app.siansa.ui.shareBar
import app.siansa.ui.listenPages
import app.siansa.ui.timeListAsTimeMachine
import app.siansa.ui.timeMachineStart
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Home's Time Machine card (issue #1341): its lists, what it keeps, and when it's made again. */
class TimeCardTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val plans = timeCardPlans(today, Locale.ENGLISH)
    private fun songs(n: Int, tag: String = "s") = (1..n).map { PlayedSong(Song(id = "$tag$it", title = "Song $it", coverArt = "c${it % 3}"), n - it + 1) }

    @Test fun theListsInTheOrderTheyAreTried() {
        assertEquals(listOf("This time last year", "This day last year", "Last month", "Your 2025", "Two years ago", "Five years ago"), plans.map { it.title })
        assertEquals(listOf("NOV 2025", "23 NOV", "OCT", "2025", "NOV 2024", "NOV 2021"), plans.map { it.tag })
        assertEquals(listOf("November 2025", "23 November 2025", "October 2026", "2025", "November 2024", "November 2021"), plans.map { it.line })
    }

    @Test fun eachListCoversItsDays() {
        val (yearAgo, day, lastMonth, lastYear) = plans
        assertEquals(LocalDate.of(2025, 11, 1) to LocalDate.of(2025, 12, 1), yearAgo.window.from to yearAgo.window.to)
        assertEquals(LocalDate.of(2025, 11, 23) to LocalDate.of(2025, 11, 24), day.window.from to day.window.to)
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 11, 1), lastMonth.window.from to lastMonth.window.to)
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2026, 1, 1), lastYear.window.from to lastYear.window.to)
    }

    /** A month's and a year's plays are kept under the Time Machine's own keys, so the page and the card share them. */
    @Test fun monthsAndYearsShareTheTimeMachinesKeys() {
        assertEquals(listOf("2025-11", "2025-11-23", "2026-10", "2025-all", "2024-11", "2021-11"), plans.map { it.key })
        assertEquals(listOf(11, 11, 10, null, 11, 11), plans.map { it.month })
        assertEquals("timemachine?year=2025&month=11", timeMachineRoute(2025, 11))
        assertEquals("timemachine?year=2025&month=0", timeMachineRoute(2025, null))
    }

    @Test fun januaryLooksBackOverTheYearsEnd() {
        val january = timeCardPlans(LocalDate.of(2027, 1, 5), Locale.ENGLISH)
        assertEquals(listOf("JAN 2026", "5 JAN", "DEC", "2026"), january.take(4).map { it.tag })
        assertEquals("2026-12", january[2].key)
        // The 29th of February a year on is the 28th.
        assertEquals("2027-02-28", timeCardPlans(LocalDate.of(2028, 2, 29), Locale.ENGLISH)[1].key)
    }

    @Test fun theFirstFourWithEnoughSongsMakeTheCard() = runBlocking {
        val asked = ArrayList<TimeListKind>()
        val card = buildTimeCard(today, plans) { plan ->
            asked += plan.kind
            when (plan.kind) {
                TimeListKind.YEAR_AGO -> songs(60)
                TimeListKind.DAY -> songs(2)   // too few for a day: left out
                TimeListKind.LAST_MONTH -> songs(4)  // too few for a month
                else -> songs(8, plan.kind.id)
            }
        }!!
        assertEquals("2026-11-23", card.day)
        assertEquals(listOf("year_ago", "last_year", "two_years", "five_years"), card.lists.map { it.kind })
        assertEquals(TIME_LIST_SONGS, card.lists.first().songIds.size)
        assertEquals(listOf("c1", "c2", "c0"), card.lists.first().covers)
        assertEquals(2025 to null, card.lists[1].year to card.lists[1].month)
        assertEquals(listOf(60, 59, 58), card.lists.first().plays.take(3))
        assertEquals(TimeListKind.entries.toList() - TimeListKind.RANGE, asked)
    }

    @Test fun itStopsAskingOnceItHasFour() = runBlocking {
        val asked = ArrayList<TimeListKind>()
        val card = buildTimeCard(today, plans) { asked += it.kind; songs(10) }!!
        assertEquals(4, card.lists.size)
        assertEquals(listOf(TimeListKind.YEAR_AGO, TimeListKind.DAY, TimeListKind.LAST_MONTH, TimeListKind.LAST_YEAR), asked)
        // A day with three songs is enough.
        assertEquals(3, buildTimeCard(today, plans) { if (it.kind == TimeListKind.DAY) songs(3) else emptyList() }!!.lists.single().songIds.size)
    }

    /** A list that couldn't be fetched isn't taken for an empty one: the card on the phone stays, and it's tried again. */
    @Test fun aListThatCouldNotBeFetchedKeepsTheOldCard() = runBlocking {
        assertNull(buildTimeCard(today, plans) { if (it.kind == TimeListKind.DAY) null else songs(10) }.takeIf { it?.lists?.size != 4 })
        assertNull(buildTimeCard(today, plans) { if (it.kind == TimeListKind.LAST_YEAR) null else if (it.kind == TimeListKind.DAY) songs(10) else emptyList() })
        // Four lists all the same: nothing is missing from the card, so it's kept.
        assertEquals(4, buildTimeCard(today, plans) { if (it.kind == TimeListKind.DAY) null else songs(10) }!!.lists.size)
        // No history at all is a card with no lists, not a failure: Home shows nothing for it.
        assertTrue(buildTimeCard(today, plans) { emptyList() }!!.lists.isEmpty())
    }

    @Test fun itIsMadeAgainOnAnotherDay() {
        val card = TimeCard("2026-11-23", emptyList(), TIME_CARD_MAKE)
        assertTrue(timeCardStale(null, today))
        // A card from before Your listening's pages is made again the same day.
        assertTrue(timeCardStale(card.copy(make = 0), today))
        assertFalse(timeCardStale(card, today))
        assertTrue(timeCardStale(card, today.plusDays(1)))
    }

    /** Your listening's times (issue #1347): the days back from today each covers, today among them. */
    @Test fun theTimesTheCardCanBeSetTo() {
        assertEquals(listOf("Last 7 days", "Last 30 days", "Last year", "All time"), ListenRange.entries.map { it.label })
        fun days(r: ListenRange) = rangePlan(r, today).window.let { it.from to it.to }
        assertEquals(LocalDate.of(2026, 11, 17) to LocalDate.of(2026, 11, 24), days(ListenRange.WEEK))
        assertEquals(LocalDate.of(2026, 10, 25) to LocalDate.of(2026, 11, 24), days(ListenRange.MONTH))
        assertEquals(LocalDate.of(2025, 11, 24) to LocalDate.of(2026, 11, 24), days(ListenRange.YEAR))
        assertEquals(LocalDate.of(TIME_MACHINE_FIRST_YEAR, 1, 1) to LocalDate.of(2026, 11, 24), days(ListenRange.ALL))
        assertEquals("Last 30 days", rangePlan(ListenRange.MONTH, today).line)
        assertEquals("songs-week", ListenRange.WEEK.listKind)
        // What's saved picks the time; anything else is the last 30 days.
        assertEquals(ListenRange.YEAR, ListenRange.of("year"))
        assertEquals(ListenRange.MONTH, ListenRange.of(null))
        assertEquals(ListenRange.MONTH, ListenRange.of("fortnight"))
        assertEquals("in the last 7 days", ListenRange.WEEK.said)
        assertEquals("yet", ListenRange.ALL.said)
    }

    @Test fun theCardsColours() {
        assertEquals(listOf("Teal", "Plum", "Ink", "Ember", "Moss", "From its cover"), app.siansa.data.ListenColour.entries.map { it.label })
        assertEquals(app.siansa.data.ListenColour.PLUM, app.siansa.data.ListenColour.of("plum"))
        assertEquals(app.siansa.data.ListenColour.TEAL, app.siansa.data.ListenColour.of(null))
        assertEquals(app.siansa.data.ListenColour.TEAL, app.siansa.data.ListenColour.of("pink"))
    }

    @Test fun genresByTheirShareOfThePlays() {
        fun played(id: String, genre: String?, plays: Int) = PlayedSong(Song(id = id, title = id, genre = genre), plays)
        val songs = listOf(played("a", "Metal", 50), played("b", "Folk; Irish", 30), played("c", "Metal", 10), played("d", null, 99), played("e", "Ambient", 10))
        val genres = topGenres(songs, genresOf = ::tagGenres)
        assertEquals(listOf(CardGenre("Metal", 60), CardGenre("Folk", 30), CardGenre("Ambient", 10)), genres)
        assertEquals(listOf("Metal"), topGenres(songs, most = 1, genresOf = ::tagGenres).map { it.name })
        // Siansa Beats' genre for a song, where it has one, comes before the tag's.
        assertEquals("Progressive metal", topGenres(songs) { if (it.id == "a") listOf("Progressive metal") else tagGenres(it) }.first().name)
        assertTrue(topGenres(listOf(played("d", null, 9)), genresOf = ::tagGenres).isEmpty())
        // The most played's bar is full; the others are their share of it, and never nothing.
        assertEquals(1f, shareBar(60, 60), 0f)
        assertEquals(0.5f, shareBar(30, 60), 0f)
        assertEquals(0.04f, shareBar(0, 60), 0f)
    }

    @Test fun theWholeCard() = runBlocking {
        val artists = listOf("One", "Two", "Three", "Four", "Five").mapIndexed { i, n -> CardArtist(n, 50 - i, "id$i") }
        val asked = ArrayList<Pair<TimeListKind, Int>>()
        val card = buildListeningCard(today, plans,
            songsOf = { plan, limit -> asked += plan.kind to limit; songs(if (limit == GENRE_SONGS) 120 else 60, plan.key) },
            artistsOf = { artists }, genresOf = { { listOf("Metal") } })!!
        assertEquals(4, card.lists.size)
        assertEquals(ListenRange.entries.map { it.id }, card.ranges.map { it.range })
        val month = card.range(ListenRange.MONTH)!!
        assertEquals("songs-month" to "Last 30 days", month.songs!!.kind to month.songs!!.line)
        assertEquals(TIME_LIST_SONGS, month.songs!!.songIds.size)
        assertEquals(listOf("One", "Two", "Three", "Four"), month.artists.map { it.name })
        assertEquals(listOf(CardGenre("Metal", 100)), month.genres)
        assertEquals(TIME_CARD_MAKE, card.make)
        // Each time's songs are asked for in full, for its genres.
        assertEquals(List(4) { TimeListKind.RANGE to GENRE_SONGS }, asked.takeLast(4))
        // Any list of the card by its kind, for its page of songs.
        assertEquals("All time", card.list("songs-all")!!.line)
        assertEquals("This day last year", card.list("day")!!.title)
        assertNull(card.list("nothing"))
        assertEquals(ListenPage.entries.toList(), listenPages(card))
    }

    /** Anything that couldn't be fetched keeps the card on the phone; nothing played yet is a card with no pages. */
    @Test fun theWholeCardOrTheOldOne() = runBlocking {
        assertNull(buildListeningCard(today, plans, { plan, _ -> if (plan.key.startsWith("week")) null else songs(10) }, { emptyList() }, { { emptyList() } }))
        assertNull(buildListeningCard(today, plans, { _, _ -> songs(10) }, { null }, { { emptyList() } }))
        val none = buildListeningCard(today, plans, { _, _ -> emptyList() }, { emptyList() }, { { emptyList() } })!!
        assertTrue(none.empty)
        assertTrue(listenPages(none).isEmpty())
        // A quiet week: the pages are still there, as another time has something.
        val quiet = buildListeningCard(today, plans, { plan, _ -> if (plan.key.startsWith("all")) songs(10) else emptyList() }, { emptyList() }, { { listOf("Folk") } })!!
        assertEquals(listOf(ListenPage.SONGS, ListenPage.GENRES), listenPages(quiet))
        assertNull(quiet.range(ListenRange.WEEK)!!.songs)
        assertTrue(quiet.range(ListenRange.WEEK)!!.empty)
        assertFalse(quiet.empty)
    }

    /** A list's page of songs: the ones still in the library, each with its plays, named by when it's from. */
    @Test fun aListAsAPageOfSongs() {
        val list = TimeCardList("day", "This day last year", "23 NOV", "23 November 2025", listOf("a", "b", "c"), listOf(9, 6, 2), year = 2025, month = 11)
        val tm = timeListAsTimeMachine(list, listOf(Song(id = "a", title = "A", artist = "X"), Song(id = "c", title = "C", artist = "Y")))
        assertEquals("23 November 2025", tm.label)
        assertEquals(listOf("A" to 9L, "C" to 2L), tm.tracks.map { it.title to it.plays })
        assertEquals("Time Machine - 23 November 2025", memoryName(tm.label))
        assertEquals("timelist/day", timeListRoute("day"))
    }

    @Test fun theTimeMachineOpensWhereAsked() {
        assertEquals(2025 to 11, timeMachineStart(null, null, today))
        assertEquals(2026 to 10, timeMachineStart(2026, 10, today))
        assertEquals(2025 to null, timeMachineStart(2025, 0, today))
        // A month that hasn't come yet is the whole year; a year out of reach is the usual start.
        assertEquals(2026 to null, timeMachineStart(2026, 12, today))
        assertEquals(2025 to 11, timeMachineStart(2030, 3, today))
    }

    /** In Arrange Home it's a row like any other, after More of you, named Your listening; its look is fixed, so its cog has its colour alone. */
    @Test fun itIsARowOfHomeWithAFixedLook() {
        assertTrue(HomeSection.TIME_MACHINE.fixed)
        assertEquals("Your listening", HomeSection.TIME_MACHINE.label)
        assertEquals("time_machine", HomeSection.TIME_MACHINE.id)
        assertFalse(HomeSection.TIME_MACHINE.lookable)
        assertEquals(listOf(HomeSection.TIME_MACHINE), HomeSection.entries.filter { it.fixed })
        // A saved order from before it existed gets it after More of you.
        val order = HomeSection.parse("quick,more_of_you,recent")
        assertEquals(HomeSection.TIME_MACHINE, order[order.indexOf(HomeSection.MORE_OF_YOU) + 1])
    }
}
