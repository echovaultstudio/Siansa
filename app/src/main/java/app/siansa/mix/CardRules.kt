package app.siansa.mix

import app.siansa.data.Album
import app.siansa.data.PlayEvent
import app.siansa.data.Song
import app.siansa.data.normalizeKey
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Rules for a custom card's contents (issue #663): each says what a song or an album must be, or mustn't be,
 * like "Record label contains Ninja Tune" or "Year is 2024". A card keeps the songs or albums that match all its
 * rules, or any of them.
 */

/** What a custom card lists: songs, or whole albums. */
enum class CardContents(val label: String, val hint: String, val fromBooks: Boolean = false) {
    SONGS("Songs", "Single songs that match the rules"),
    ALBUMS("Albums", "Whole albums that match the rules; playing the card plays them in turn"),
    // From Audiobookshelf (issue #782): no rules, just the order.
    BOOKS("Audiobooks", "Your audiobooks from Audiobookshelf", fromBooks = true),
    EPISODES("Podcast episodes", "The newest episodes of your podcasts", fromBooks = true),
    LISTENING("Keep listening", "Books and episodes you're partway through, the most recent first", fromBooks = true),
}

/** The kind of value a rule checks, which decides the ways it can compare. */
enum class RuleKind { TEXT, NUMBER, FLAG, AGE }

/**
 * What a rule looks at. [songs] and [albums] say what it can be used for: a song's album details (its label, its
 * release type, when it was added) come from the album it's on.
 */
enum class RuleField(val label: String, val kind: RuleKind, val songs: Boolean, val albums: Boolean, val unit: String = "") {
    TITLE("Song title", RuleKind.TEXT, songs = true, albums = false),
    ALBUM("Album", RuleKind.TEXT, songs = true, albums = true),
    ARTIST("Artist", RuleKind.TEXT, songs = true, albums = true),
    GENRE("Genre", RuleKind.TEXT, songs = true, albums = true),
    LABEL("Record label", RuleKind.TEXT, songs = true, albums = true),
    RELEASE_TYPE("Release type", RuleKind.TEXT, songs = true, albums = true),
    YEAR("Year", RuleKind.NUMBER, songs = true, albums = true),
    LENGTH("Length", RuleKind.NUMBER, songs = true, albums = true, unit = "minutes"),
    SONG_COUNT("Songs on it", RuleKind.NUMBER, songs = false, albums = true),
    /** A song's own plays (issue #678), an album's as the server counts them. */
    PLAYS("Play count", RuleKind.NUMBER, songs = true, albums = true),
    /** How often this phone saw the song skipped, over its history (about two months). */
    SKIPS("Times skipped", RuleKind.NUMBER, songs = true, albums = false),
    PLAYED("Played", RuleKind.AGE, songs = true, albums = true, unit = "days"),
    ADDED("Added", RuleKind.AGE, songs = true, albums = true, unit = "days"),
    FAVOURITE("Favourite", RuleKind.FLAG, songs = true, albums = true),
    COMPILATION("Compilation", RuleKind.FLAG, songs = true, albums = true);

    /** The rule fields for [contents], in the order the editor offers them. */
    companion object {
        fun forContents(contents: CardContents): List<RuleField> =
            entries.filter { if (contents == CardContents.ALBUMS) it.albums else it.songs }
    }
}

/** How a rule compares. [kinds] says which kinds of field it's for; [negative] ones keep what doesn't match. */
enum class RuleOp(val label: String, val kinds: Set<RuleKind>, val negative: Boolean = false) {
    CONTAINS("contains", setOf(RuleKind.TEXT)),
    NOT_CONTAINS("doesn't contain", setOf(RuleKind.TEXT), negative = true),
    IS("is", setOf(RuleKind.TEXT, RuleKind.NUMBER)),
    IS_NOT("isn't", setOf(RuleKind.TEXT, RuleKind.NUMBER), negative = true),
    AT_LEAST("at least", setOf(RuleKind.NUMBER)),
    AT_MOST("at most", setOf(RuleKind.NUMBER)),
    BETWEEN("between", setOf(RuleKind.NUMBER)),
    YES("yes", setOf(RuleKind.FLAG)),
    NO("no", setOf(RuleKind.FLAG), negative = true),
    WITHIN("in the last", setOf(RuleKind.AGE)),
    NOT_WITHIN("not in the last", setOf(RuleKind.AGE), negative = true);

    companion object {
        fun forKind(kind: RuleKind): List<RuleOp> = entries.filter { kind in it.kinds }
    }
}

/**
 * One rule. [value] is the text or number to compare with; for text, several values separated by commas are
 * alternatives ("contains Pop, Rock": either one; "doesn't contain Live, Remix": neither). [value2] is the upper end
 * of [RuleOp.BETWEEN].
 */
@Serializable
data class CardRule(val field: RuleField, val op: RuleOp, val value: String = "", val value2: String = "") {
    /** "Record label contains Ninja Tune", "Year between 1990 and 1999", "Favourite: yes". */
    val text: String
        get() = when {
            this.field.kind == RuleKind.FLAG -> "${this.field.label}: ${op.label}"
            op == RuleOp.BETWEEN -> "${this.field.label} between ${value.trim()} and ${value2.trim()}${unitText()}"
            else -> "${this.field.label} ${op.label} ${value.trim()}${unitText()}"
        }

    private fun unitText() = if (this.field.unit.isNotEmpty()) " ${this.field.unit}" else ""

    /** What's wrong with it, or null when it's fine. */
    val problem: String?
        get() = when {
            this.field.kind !in op.kinds -> "${this.field.label} can't be compared with \"${op.label}\"."
            this.field.kind == RuleKind.FLAG -> null
            value.isBlank() -> "Give ${this.field.label.lowercase()} a value."
            this.field.kind == RuleKind.TEXT -> null
            value.trim().toDoubleOrNull() == null -> "${this.field.label} takes a number."
            op == RuleOp.BETWEEN && value2.trim().toDoubleOrNull() == null -> "Give ${this.field.label.lowercase()} the second number too."
            else -> null
        }

    companion object {
        /** A new rule for [field], with its first way of comparing. */
        fun of(field: RuleField): CardRule = CardRule(field, RuleOp.forKind(field.kind).first())
    }
}

/** What the rules can ask of a song or an album. */
interface RuleSubject {
    fun texts(field: RuleField): List<String>
    fun number(field: RuleField): Double?
    fun flag(field: RuleField): Boolean
    /** Days since it was added or last played ([field]), or null when that isn't known (never played). */
    fun ageDays(field: RuleField): Long?
}

/**
 * What's known of a song's plays (issue #678): [plays] from the server's count or this phone's history, whichever is
 * higher; [skips] on this phone; [lastPlayed] as an epoch day.
 */
data class PlayStats(val plays: Long = 0, val skips: Int = 0, val lastPlayed: Long? = null)

/**
 * A song's play count on the server as Siansa last saw it, and the day it was played when that's known (issue #678):
 * only a day the count went up says so. The first count Siansa notes is just when it first saw the song, which may be
 * years after it was last played.
 */
data class ServerPlays(val count: Long, val playedDay: Long?) {
    companion object {
        /** From a song's noted counts ([PlayCountLog]), oldest first; null when there are none. */
        fun of(points: List<CountPoint>): ServerPlays? {
            val last = points.lastOrNull() ?: return null
            val before = points.getOrNull(points.size - 2)
            return ServerPlays(last.count, last.day.takeIf { before != null && last.count > before.count })
        }
    }
}

/**
 * Each song's [PlayStats], from this phone's [history] and the server's counts as Siansa last saw them ([server]).
 */
fun playStats(history: List<PlayEvent>, server: Map<String, ServerPlays>, zone: ZoneId = ZoneId.systemDefault()): Map<String, PlayStats> {
    val out = HashMap<String, PlayStats>()
    history.groupBy { it.id }.forEach { (id, events) ->
        val heard = events.filter { !it.skipped }
        out[id] = PlayStats(
            plays = heard.size.toLong(),
            skips = events.size - heard.size,
            lastPlayed = heard.maxOfOrNull { it.at }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() },
        )
    }
    server.forEach { (id, plays) ->
        val have = out[id] ?: PlayStats()
        out[id] = have.copy(plays = maxOf(have.plays, plays.count), lastPlayed = listOfNotNull(have.lastPlayed, plays.playedDay).maxOrNull())
    }
    return out
}

/** An album's [PlayStats] from its songs' ([songs]: song id → album id): their plays added up, the latest play. */
fun albumPlayStats(songStats: Map<String, PlayStats>, songs: Map<String, String>): Map<String, PlayStats> =
    songStats.entries.mapNotNull { (id, s) -> songs[id]?.let { it to s } }.groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> PlayStats(list.sumOf { it.plays }, list.sumOf { it.skips }, list.mapNotNull { it.lastPlayed }.maxOrNull()) }

/** [song] for the rules, with the [album] it's on (when known) for the album's details, and its [stats]. */
class SongSubject(
    private val song: Song,
    private val album: Album?,
    private val stats: PlayStats? = null,
    private val today: LocalDate = LocalDate.now(),
) : RuleSubject {
    override fun texts(field: RuleField): List<String> = when (field) {
        RuleField.TITLE -> listOf(song.title)
        RuleField.ALBUM -> listOf(song.album)
        RuleField.ARTIST -> (listOf(song.artist) + song.artists.map { it.name }).filter { it.isNotBlank() }
        RuleField.GENRE -> listOfNotNull(song.genre)
        RuleField.LABEL -> album?.labels.orEmpty()
        RuleField.RELEASE_TYPE -> album?.let(::releaseTypesOf).orEmpty()
        else -> emptyList()
    }

    override fun number(field: RuleField): Double? = when (field) {
        RuleField.YEAR -> song.year?.toDouble()
        RuleField.LENGTH -> song.durationSec / 60.0
        RuleField.PLAYS -> (stats?.plays ?: 0).toDouble()
        RuleField.SKIPS -> (stats?.skips ?: 0).toDouble()
        else -> null
    }

    override fun flag(field: RuleField): Boolean = when (field) {
        RuleField.FAVOURITE -> song.starred
        RuleField.COMPILATION -> album?.isCompilation == true
        else -> false
    }

    override fun ageDays(field: RuleField): Long? = when (field) {
        RuleField.PLAYED -> stats?.lastPlayed?.let { (today.toEpochDay() - it).coerceAtLeast(0) }
        else -> album?.created?.let { addedDaysAgo(it, today) }
    }
}

/** [album] for the rules; [favourite] when any of its songs is a favourite; [stats] from its songs' plays. */
class AlbumSubject(
    private val album: Album,
    private val favourite: Boolean = false,
    private val stats: PlayStats? = null,
    private val today: LocalDate = LocalDate.now(),
) : RuleSubject {
    override fun texts(field: RuleField): List<String> = when (field) {
        RuleField.ALBUM -> listOf(album.name)
        RuleField.ARTIST -> listOf(album.artist).filter { it.isNotBlank() }
        RuleField.GENRE -> listOfNotNull(album.genre)
        RuleField.LABEL -> album.labels
        RuleField.RELEASE_TYPE -> releaseTypesOf(album)
        else -> emptyList()
    }

    override fun number(field: RuleField): Double? = when (field) {
        RuleField.YEAR -> album.year?.toDouble()
        RuleField.LENGTH -> album.durationSec / 60.0
        RuleField.SONG_COUNT -> album.songCount.toDouble()
        RuleField.PLAYS -> maxOf(album.playCount, stats?.plays ?: 0).toDouble()
        else -> null
    }

    override fun flag(field: RuleField): Boolean = when (field) {
        RuleField.FAVOURITE -> favourite
        RuleField.COMPILATION -> album.isCompilation
        else -> false
    }

    override fun ageDays(field: RuleField): Long? = when (field) {
        RuleField.PLAYED -> listOfNotNull(stats?.lastPlayed, album.played?.let { addedDaysAgo(it, today) }?.let { today.toEpochDay() - it })
            .maxOrNull()?.let { (today.toEpochDay() - it).coerceAtLeast(0) }
        else -> album.created?.let { addedDaysAgo(it, today) }
    }
}

/** An album's release types as words people use: "Album", "EP", "Single", "Compilation"… */
internal fun releaseTypesOf(album: Album): List<String> =
    (album.releaseTypes + if (album.isCompilation) listOf("compilation") else emptyList()).distinct()

/** Days from [created] (an ISO date or time from the server) to [today]; null when it can't be read. */
internal fun addedDaysAgo(created: String, today: LocalDate): Long? {
    val day = runCatching { OffsetDateTime.parse(created).toLocalDate() }.getOrNull()
        ?: runCatching { Instant.parse(created).atOffset(ZoneOffset.UTC).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(created.take(10)) }.getOrNull()
        ?: return null
    return ChronoUnit.DAYS.between(day, today).coerceAtLeast(0)
}

/** Whether [subject] passes [rule]. A rule with a missing value (a song without a year) passes only when negative. */
fun CardRule.matches(subject: RuleSubject): Boolean = when (field.kind) {
    RuleKind.TEXT -> {
        val wanted = value.split(',').map { normalizeKey(it) }.filter { it.isNotEmpty() }
        val have = subject.texts(field).map { normalizeKey(it) }
        val hit = when (op) {
            RuleOp.CONTAINS, RuleOp.NOT_CONTAINS -> wanted.any { w -> have.any { it.contains(w) || it.replace(" ", "").contains(w.replace(" ", "")) } }
            else -> wanted.any { w -> have.any { it == w } }
        }
        if (op.negative) !hit else hit
    }
    RuleKind.NUMBER -> {
        val n = subject.number(field)
        val a = value.trim().toDoubleOrNull()
        val b = value2.trim().toDoubleOrNull()
        when {
            n == null || a == null -> op.negative
            op == RuleOp.IS -> n.roundTo() == a.roundTo()
            op == RuleOp.IS_NOT -> n.roundTo() != a.roundTo()
            op == RuleOp.AT_LEAST -> n >= a
            op == RuleOp.AT_MOST -> n <= a
            op == RuleOp.BETWEEN -> b != null && n >= minOf(a, b) && n <= maxOf(a, b)
            else -> false
        }
    }
    RuleKind.FLAG -> if (op == RuleOp.NO) !subject.flag(field) else subject.flag(field)
    RuleKind.AGE -> {
        val days = subject.ageDays(field)
        val limit = value.trim().toDoubleOrNull()
        if (days == null || limit == null) op.negative else (days <= limit) != (op == RuleOp.NOT_WITHIN)
    }
}

/** Numbers compare as whole numbers for "is" (a 3:40 song is 4 minutes, not 3.67). */
private fun Double.roundTo(): Long = Math.round(this)

/** Whether [subject] passes [rules]: all of them, or with [matchAll] off any of them. No rules: everything. */
fun matchesAll(rules: List<CardRule>, matchAll: Boolean, subject: RuleSubject): Boolean =
    rules.isEmpty() || if (matchAll) rules.all { it.matches(subject) } else rules.any { it.matches(subject) }

/** The order a card's songs or albums come in. */
enum class CardSort(val label: String) {
    RANDOM("Random, new each day"),
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    ADDED("Recently added"),
    PLAYED("Most played"),
    NAME("A to Z"),
}
