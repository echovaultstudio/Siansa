package app.siansa.mix

import app.siansa.data.Song
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home's Time Machine card (issue #1341; the owner, 23 Nov: "a pinnable card from time machine, showing playlists like
 * this time last year, this day last year, your previous month"): which lists it can hold, in the order they're tried.
 */
enum class TimeListKind(val id: String) {
    YEAR_AGO("year_ago"),
    DAY("day"),
    LAST_MONTH("last_month"),
    LAST_YEAR("last_year"),
    TWO_YEARS("two_years"),
    FIVE_YEARS("five_years"),

    /** Not a look back, but a list all the same (issue #1347): the most played songs of one of the card's times. */
    RANGE("songs"),
}

/**
 * The times Your listening's card can be set to (issue #1347; the owner, 23 Nov: "past 30 days or last month, last
 * week, last year, all time"): the days back from today it covers, today among them, or everything.
 */
enum class ListenRange(val id: String, val label: String, val days: Int?) {
    WEEK("week", "Last 7 days", 7),
    MONTH("month", "Last 30 days", 30),
    YEAR("year", "Last year", 365),
    ALL("all", "All time", null);

    /** The kind its list of songs is kept under on the card, and opened by. */
    val listKind: String get() = "songs-$id"

    /** "in the last 7 days", "of all time": after "Nothing played". */
    val said: String get() = if (days == null) "yet" else "in the ${label.replaceFirstChar { it.lowercase() }}"

    companion object {
        val default = MONTH
        fun of(id: String?): ListenRange = entries.firstOrNull { it.id == id } ?: default
    }
}

/** The songs of [range] up to [today]. Asked again every day, so never kept for good. */
fun rangePlan(range: ListenRange, today: LocalDate): TimeListPlan {
    val from = range.days?.let { today.minusDays(it - 1L) } ?: LocalDate.of(TIME_MACHINE_FIRST_YEAR, 1, 1)
    return TimeListPlan(TimeListKind.RANGE, "Top songs", "", range.label, TimeWindow(from, today.plusDays(1), range.label.replaceFirstChar { it.lowercase() }),
        "${range.id}-$today", today.year, null, 1)
}

/** One of the card's times as kept on the phone: its songs (none played: null), artists and genres. */
@Serializable
data class RangeData(val range: String, val songs: TimeCardList? = null, val artists: List<CardArtist> = emptyList(), val genres: List<CardGenre> = emptyList()) {
    val empty: Boolean get() = songs == null && artists.isEmpty() && genres.isEmpty()
}

/** A song with how often it was played in a list's days. */
data class PlayedSong(val song: Song, val plays: Int)

/**
 * One list the card may show: its [title], the small date [tag] on its picture ("NOV 2025", "23 NOV"), the [line] that
 * says when it's from ("November 2025"), the days it covers and the [key] its plays are kept under on the phone.
 * [year] and [month] (null: the whole year) are where it opens in the Time Machine. [fewest]: with fewer songs in the
 * library than this it's left out.
 */
data class TimeListPlan(
    val kind: TimeListKind,
    val title: String,
    val tag: String,
    val line: String,
    val window: TimeWindow,
    val key: String,
    val year: Int,
    val month: Int?,
    val fewest: Int = TIME_LIST_FEWEST,
)

/** A list needs this many songs in the library to be on the card; a single day needs [TIME_DAY_FEWEST]. */
const val TIME_LIST_FEWEST = 5
const val TIME_DAY_FEWEST = 3

/** How many lists the card shows: one big, three at its foot. */
const val TIME_CARD_LISTS = 4

/** How many songs a list holds. */
const val TIME_LIST_SONGS = 50

/**
 * The lists to try on [today], in order: this month a year ago, this date a year ago, last month, last year, and two
 * to fall back on (this month two and five years ago). A month's and a year's [TimeListPlan.key] are the Time
 * Machine's own, so the card and the page share what's kept on the phone.
 */
fun timeCardPlans(today: LocalDate, locale: Locale = Locale.getDefault()): List<TimeListPlan> {
    fun short(date: LocalDate) = date.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').uppercase(locale)
    fun month(kind: TimeListKind, title: String, first: LocalDate, tag: String = "${short(first)} ${first.year}"): TimeListPlan {
        val window = timeWindow(first.year, first.monthValue, locale)
        return TimeListPlan(kind, title, tag, window.label, window, "${first.year}-${first.monthValue}", first.year, first.monthValue)
    }
    val thisMonth = today.withDayOfMonth(1)
    val day = today.minusYears(1)
    val lastYear = today.year - 1
    return listOf(
        month(TimeListKind.YEAR_AGO, "This time last year", thisMonth.minusYears(1)),
        TimeListPlan(
            TimeListKind.DAY, "This day last year", "${day.dayOfMonth} ${short(day)}",
            "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, locale)} ${day.year}",
            TimeWindow(day, day.plusDays(1), day.toString()), day.toString(), day.year, day.monthValue, TIME_DAY_FEWEST,
        ),
        month(TimeListKind.LAST_MONTH, "Last month", thisMonth.minusMonths(1), short(thisMonth.minusMonths(1))),
        TimeListPlan(TimeListKind.LAST_YEAR, "Your $lastYear", "$lastYear", "$lastYear", timeWindow(lastYear, null, locale), "$lastYear-all", lastYear, null),
        month(TimeListKind.TWO_YEARS, "Two years ago", thisMonth.minusYears(2)),
        month(TimeListKind.FIVE_YEARS, "Five years ago", thisMonth.minusYears(5)),
    )
}

/** An artist on the card: its name and plays, and where its page and picture are when it's in the library. */
@Serializable
data class CardArtist(val name: String, val plays: Int, val id: String? = null, val coverArt: String? = null, val imageUrl: String? = null)

/** A genre on the card, with its share of the year's plays in percent. */
@Serializable
data class CardGenre(val name: String, val share: Int)

/** How many artists and genres the card shows, and how many of the year's songs its genres are counted from. */
const val CARD_ARTISTS = 5
const val CARD_GENRES = 5
const val GENRE_SONGS = 200

/**
 * The genres of [songs] by their plays, most played first, each with its share in percent of the plays that have a
 * genre: [genresOf] says which genres a song is in (Siansa Beats' by its sound, else its tag), the first counted.
 */
fun topGenres(songs: List<PlayedSong>, most: Int = CARD_GENRES, genresOf: (Song) -> List<String>): List<CardGenre> {
    val plays = LinkedHashMap<String, Int>()
    for (p in songs) {
        val name = genresOf(p.song).firstOrNull { it.isNotBlank() }?.trim() ?: continue
        plays[name] = (plays[name] ?: 0) + p.plays.coerceAtLeast(1)
    }
    val all = plays.values.sum()
    if (all == 0) return emptyList()
    return plays.entries.sortedByDescending { it.value }.take(most).map { CardGenre(it.key, Math.round(it.value * 100f / all)) }
}

/** A song's genres from its tag: "Rock; Metal" is two. */
fun tagGenres(song: Song): List<String> = song.genre.orEmpty().split(';').map { it.trim() }.filter { it.isNotEmpty() }

/** One list as it's kept on the phone: what the card draws and plays, with no need to ask anyone again. */
@Serializable
data class TimeCardList(
    val kind: String,
    val title: String,
    val tag: String,
    val line: String,
    val songIds: List<String>,
    /** How often each of [songIds] was played in its days, in their order; empty from a card made before they were kept. */
    val plays: List<Int> = emptyList(),
    /** Its first few covers, for its picture. */
    val covers: List<String> = emptyList(),
    val year: Int,
    val month: Int? = null,
)

/** The card as kept on the phone: the [day] it was made for (ISO), and its lists, the big one first. */
@Serializable
data class TimeCard(
    val day: String,
    val lists: List<TimeCardList>,
    /** Which make of card this is: one of another make is made again ([TIME_CARD_MAKE]). */
    val make: Int = 0,
    /** Your listening's times (issues #1343, #1347): the songs, artists and genres of each [ListenRange]. */
    val ranges: List<RangeData> = emptyList(),
) {
    fun range(range: ListenRange): RangeData? = ranges.firstOrNull { it.range == range.id }

    /** The list of [kind] (a look back's [TimeListKind.id], or a [ListenRange.listKind]), wherever on the card it is. */
    fun list(kind: String): TimeCardList? = (ranges.mapNotNull { it.songs } + lists).firstOrNull { it.kind == kind }

    /** Nothing to show: no history yet. */
    val empty: Boolean get() = lists.isEmpty() && ranges.all { it.empty }
}

/**
 * The make of card this version keeps: 2 had Your listening's pages and each song's plays, 3 has them for each time,
 * 4 has five artists and five genres (the owner, 23 Nov: "only shows top 4 and not top 5… the same for genres and songs").
 */
const val TIME_CARD_MAKE = 4

/** What's in the card's file: nothing until it was first made. */
@Serializable
data class TimeCardKept(val card: TimeCard? = null)

/** Whether the card must be made again: there's none, or it was made for another day ("this day last year" moved on). */
fun timeCardStale(card: TimeCard?, today: LocalDate): Boolean = card?.day != today.toString() || card.make != TIME_CARD_MAKE

/**
 * The card for [today] from [plans], asking [songsOf] for each list's songs in the library (null: its plays couldn't
 * be fetched) until [TIME_CARD_LISTS] have enough. Null when a list couldn't be fetched and the card would be short
 * of lists because of it: then the one already on the phone stays, and it's tried again later. A card with no lists
 * (no history yet) is a card: Home shows nothing for it.
 */
suspend fun buildTimeCard(today: LocalDate, plans: List<TimeListPlan>, songsOf: suspend (TimeListPlan) -> List<PlayedSong>?): TimeCard? {
    val lists = ArrayList<TimeCardList>()
    var failed = false
    for (plan in plans) {
        if (lists.size >= TIME_CARD_LISTS) break
        val songs = songsOf(plan)
        if (songs == null) { failed = true; continue }
        if (songs.size < plan.fewest) continue
        lists += keptList(plan, songs)
    }
    return if (failed && lists.size < TIME_CARD_LISTS) null else TimeCard(today.toString(), lists, TIME_CARD_MAKE)
}

private fun keptList(plan: TimeListPlan, songs: List<PlayedSong>, kind: String = plan.kind.id): TimeCardList {
    val kept = songs.take(TIME_LIST_SONGS)
    return TimeCardList(kind, plan.title, plan.tag, plan.line, kept.map { it.song.id }, kept.map { it.plays },
        kept.mapNotNull { it.song.coverArt }.distinct().take(TIME_CARD_COVERS), plan.year, plan.month)
}

/**
 * Your listening's whole card for [today] (issues #1343, #1347): the look back lists ([buildTimeCard]), and for each
 * [ListenRange] its songs, artists ([artistsOf]) and genres (counted from its [GENRE_SONGS] most played, by
 * [genresOf]). [songsOf] is asked with how many songs are wanted. Null when any of it couldn't be fetched: the card on
 * the phone stays, and it's tried again later.
 */
suspend fun buildListeningCard(
    today: LocalDate,
    plans: List<TimeListPlan>,
    songsOf: suspend (TimeListPlan, Int) -> List<PlayedSong>?,
    artistsOf: suspend (TimeWindow) -> List<CardArtist>?,
    genresOf: suspend (List<Song>) -> ((Song) -> List<String>),
): TimeCard? {
    val back = buildTimeCard(today, plans) { songsOf(it, TIME_LIST_SONGS) } ?: return null
    val ranges = ListenRange.entries.map { range ->
        val plan = rangePlan(range, today)
        val songs = songsOf(plan, GENRE_SONGS) ?: return null
        val artists = artistsOf(plan.window) ?: return null
        RangeData(range.id, songs.takeIf { it.isNotEmpty() }?.let { keptList(plan, it, range.listKind) }, artists.take(CARD_ARTISTS),
            topGenres(songs, genresOf = genresOf(songs.map { it.song })))
    }
    return back.copy(ranges = ranges)
}

/** How many covers a list keeps for its picture. */
const val TIME_CARD_COVERS = 6

/** The page of one of the card's lists ([TimeListKind.id]): its songs, Play and Create memory. */
fun timeListRoute(kind: String): String = "timelist/$kind"

/** The Time Machine's page at a list's month or year: month 0 is the whole year. */
fun timeMachineRoute(year: Int, month: Int?): String = "timemachine?year=$year&month=${month ?: 0}"
