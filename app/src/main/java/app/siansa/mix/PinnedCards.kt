package app.siansa.mix

import android.content.Context
import app.siansa.AppGraph
import app.siansa.data.Album
import app.siansa.data.JsonFile
import app.siansa.data.Song
import app.siansa.data.Users
import app.siansa.log.AppLog
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Custom cards (issue #622): cards of your own pinned to Home, in its "Your cards" section. Each has a name you
 * give it, a look (how it sits on Home) and contents (where its songs come from): a sound described to
 * AudioMuse, or songs from your library picked by what their tags say.
 */

/** How a custom card sits on Home. */
enum class CardLook(val label: String, val hint: String) {
    TILES("Song tiles", "A row of covers, one per song"),
    ALBUMS("Album row", "A row of the albums the songs come from"),
    BANNER("Banner", "One wide card with the first covers and a Play button"),
    LIST("Song list", "The first five songs listed, the rest a tap away"),
    BUTTON("Play button", "A slim card that plays the songs when tapped"),
}

/** Where a custom card's songs come from. */
enum class CardSource(val label: String, val hint: String) {
    LIBRARY("From your library", "Songs whose tags match: a genre, years, an artist, an album, words in the title, your favourites"),
    SOUND("By sound", "Describe a sound, like \"rainy jazz piano\", and AudioMuse finds songs that match"),
}

/**
 * One custom card: its name, look and contents. Its songs or albums ([contents]) come from a sound or from [rules]
 * on the library (issue #663). The single fields from genre to favourites are the rules of cards made before
 * that, read as rules ([allRules]) and turned into them when the card is changed ([migrated]); empty text fields
 * and null years mean "no rule".
 */
@Serializable
data class PinnedCard(
    val id: String,
    val title: String,
    val look: CardLook = CardLook.TILES,
    val source: CardSource = CardSource.LIBRARY,
    /** The sound described to AudioMuse ([CardSource.SOUND]). */
    val sound: String = "",
    val genre: String = "",
    val fromYear: Int? = null,
    val toYear: Int? = null,
    /** Part of an artist's name, ignoring case. */
    val artist: String = "",
    /** Part of an album's name, ignoring case. */
    val album: String = "",
    /** Words that must all be in the title, ignoring case. */
    val words: String = "",
    /** Only favourite (starred) songs. */
    val favourites: Boolean = false,
    /** Shown on Home; a card can be kept but taken off Home. */
    val shown: Boolean = true,
    /** Songs, or whole albums (issue #663). Albums come from the library only. */
    val contents: CardContents = CardContents.SONGS,
    /** What the songs or albums must be, or mustn't be (issue #663). */
    val rules: List<CardRule> = emptyList(),
    /** Keep what matches every rule; off, what matches any of them. */
    val matchAll: Boolean = true,
    /** The order they come in. */
    val sort: CardSort = CardSort.RANDOM,
    /** At most this many songs or albums. */
    val size: Int = SIZE,
) {
    /** Every rule: [rules], and the single fields of a card made before rules, as rules. */
    val allRules: List<CardRule>
        get() = rules + buildList {
            if (genre.isNotBlank()) add(CardRule(RuleField.GENRE, RuleOp.CONTAINS, genre.trim()))
            when {
                fromYear != null && toYear != null -> add(CardRule(RuleField.YEAR, RuleOp.BETWEEN, "$fromYear", "$toYear"))
                fromYear != null -> add(CardRule(RuleField.YEAR, RuleOp.AT_LEAST, "$fromYear"))
                toYear != null -> add(CardRule(RuleField.YEAR, RuleOp.AT_MOST, "$toYear"))
            }
            if (artist.isNotBlank()) add(CardRule(RuleField.ARTIST, RuleOp.CONTAINS, artist.trim()))
            if (album.isNotBlank()) add(CardRule(RuleField.ALBUM, RuleOp.CONTAINS, album.trim()))
            words.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { add(CardRule(RuleField.TITLE, RuleOp.CONTAINS, it)) }
            if (favourites) add(CardRule(RuleField.FAVOURITE, RuleOp.YES))
        }

    /** The card with its single fields turned into [rules], as the editor keeps it. */
    fun migrated(): PinnedCard = copy(rules = allRules, genre = "", fromYear = null, toYear = null, artist = "", album = "", words = "", favourites = false)

    /** The rules as one line: "Rock · 1990–1999 · favourites", "Albums · Record label contains Ninja Tune", or "Sound: rainy jazz piano". */
    val detail: String
        get() = when {
            source == CardSource.SOUND -> "Sound: ${sound.trim()}"
            rules.isNotEmpty() -> (if (contents == CardContents.ALBUMS) "Albums · " else "") +
                allRules.take(3).joinToString(if (matchAll) " · " else " or ") { it.text } + (if (allRules.size > 3) " …" else "")
            else -> (if (contents == CardContents.ALBUMS) "Albums · " else "") + buildList {
                if (genre.isNotBlank()) add(genre.trim())
                if (fromYear != null || toYear != null) add("${fromYear ?: "…"}–${toYear ?: "…"}")
                if (artist.isNotBlank()) add("artist \"${artist.trim()}\"")
                if (album.isNotBlank()) add("album \"${album.trim()}\"")
                if (words.isNotBlank()) add("title \"${words.trim()}\"")
                if (favourites) add("favourites")
            }.joinToString(" · ").ifEmpty { "Every song" }
        }

    /** Whether any library rule is set; a card from the library needs at least one. */
    val hasRule: Boolean
        get() = allRules.isNotEmpty()

    companion object {
        fun new(title: String = "") = PinnedCard(id = "pin-" + UUID.randomUUID().toString().take(8), title = title)

        /** At most this many songs on a card. */
        const val SIZE = 50
    }
}

/** What a card needs before it can be saved, or null when it's fine. */
fun pinnedCardProblem(card: PinnedCard, audioMuseReady: Boolean, thisYear: Int = LocalDate.now().year): String? = when {
    card.title.isBlank() -> "Give the card a name."
    card.contents == CardContents.ALBUMS && card.source == CardSource.SOUND -> "Albums come from your library: pick From your library."
    card.source == CardSource.SOUND && !audioMuseReady -> "Set up AudioMuse-AI in Settings › Connections to make a card by sound."
    card.source == CardSource.SOUND && card.sound.isBlank() -> "Describe the sound."
    card.source == CardSource.LIBRARY && !card.hasRule -> "Set at least one rule, so the card isn't your whole library."
    card.fromYear != null && card.fromYear !in 1900..thisYear -> "Years run from 1900 to $thisYear."
    card.toYear != null && card.toYear !in 1900..thisYear -> "Years run from 1900 to $thisYear."
    card.fromYear != null && card.toYear != null && card.fromYear > card.toYear -> "The first year comes before the second."
    card.source == CardSource.LIBRARY -> card.allRules.firstNotNullOfOrNull { rule ->
        rule.problem ?: if (card.contents == CardContents.ALBUMS && !rule.field.albums) "${rule.field.label} is for songs, not albums." else null
    } ?: if (card.size !in 1..MAX_SIZE) "A card holds 1 to $MAX_SIZE." else null
    else -> null
}

/** At most this many songs or albums on a card. */
const val MAX_SIZE = 500

/** Whether [song] matches a library [card]'s rules ([album]: the album it's on, for its label and the like). */
fun matchesRules(song: Song, card: PinnedCard, album: Album? = null): Boolean =
    matchesAll(card.allRules, card.matchAll, SongSubject(song, album))

/**
 * The songs of a library card: those matching its rules, at most [size]. In random order (the default) each artist
 * is limited to a few and the same artist twice in a row is avoided; the same [random] seed gives the same list, so
 * a card looks the same all day. [albums] by id gives each song its album's details.
 */
fun pickRuleSongs(
    all: List<Song>,
    card: PinnedCard,
    random: Random,
    size: Int = card.size,
    perArtist: Int = 5,
    albums: Map<String, Album> = emptyMap(),
): List<Song> {
    val matching = all.filter { matchesRules(it, card, it.albumId?.let(albums::get)) }.distinctBy { it.id }
    if (card.sort != CardSort.RANDOM) return sortSongs(matching, card.sort, albums).take(size)
    val counts = HashMap<String, Int>()
    val picked = matching.shuffled(random).filter { s ->
        val n = counts.getOrDefault(s.artist, 0)
        if (n >= perArtist) false else { counts[s.artist] = n + 1; true }
    }.take(size)
    return spreadArtists(picked)
}

private fun sortSongs(songs: List<Song>, sort: CardSort, albums: Map<String, Album>): List<Song> = when (sort) {
    CardSort.RANDOM -> songs
    CardSort.NEWEST -> songs.sortedByDescending { it.year ?: Int.MIN_VALUE }
    CardSort.OLDEST -> songs.sortedBy { it.year ?: Int.MAX_VALUE }
    CardSort.ADDED -> songs.sortedByDescending { it.albumId?.let(albums::get)?.created.orEmpty() }
    CardSort.PLAYED -> songs.sortedByDescending { it.albumId?.let(albums::get)?.playCount ?: 0 }
    CardSort.NAME -> songs.sortedBy { it.title.lowercase() }
}

/**
 * The albums of a library card (issue #663): those matching its rules, at most [size], in its order (random: the
 * same all day for one [random] seed). [favourites]: albums with a favourite song.
 */
fun pickRuleAlbums(all: List<Album>, card: PinnedCard, random: Random, size: Int = card.size, favourites: Set<String> = emptySet()): List<Album> {
    val matching = all.filter { a -> matchesAll(card.allRules, card.matchAll, AlbumSubject(a, a.id in favourites)) }.distinctBy { it.id }
    return when (card.sort) {
        CardSort.RANDOM -> matching.shuffled(random)
        CardSort.NEWEST -> matching.sortedByDescending { it.year ?: Int.MIN_VALUE }
        CardSort.OLDEST -> matching.sortedBy { it.year ?: Int.MAX_VALUE }
        CardSort.ADDED -> matching.sortedByDescending { it.created.orEmpty() }
        CardSort.PLAYED -> matching.sortedByDescending { it.playCount }
        CardSort.NAME -> matching.sortedBy { it.name.lowercase() }
    }.take(size)
}

/** Fields whose rules need each song's album (its label, release type, when it was added…). */
private val ALBUM_FIELDS = setOf(RuleField.LABEL, RuleField.RELEASE_TYPE, RuleField.COMPILATION, RuleField.ADDED, RuleField.PLAYS)

/** The albums the songs come from, in the order they first appear. */
fun albumIdsOf(songs: List<Song>): List<String> = songs.mapNotNull { it.albumId }.distinct()

/** A day's seed for [cardId], so the songs stay the same until tomorrow; [round] asks for a new set. */
fun daySeed(cardId: String, day: LocalDate, round: Int = 0): Long = (day.toString() + cardId).hashCode().toLong() * 31 + round

/** A custom card's songs and the albums they come from. */
data class CardContent(val songs: List<Song>, val albums: List<Album>)

/** Custom cards kept per user, and the songs of each (made once a day, kept while the app runs). */
class PinnedCards(private val graph: AppGraph, context: Context) {
    private val json = JsonFile(File(context.filesDir, Users.scoped("pinned_cards.json")), ListSerializer(PinnedCard.serializer())) { emptyList() }
    private val made = ConcurrentHashMap<String, CardContent>()

    val state: StateFlow<List<PinnedCard>?> = json.state

    suspend fun all(): List<PinnedCard> = json.read()

    suspend fun get(id: String): PinnedCard? = all().firstOrNull { it.id == id }

    suspend fun add(card: PinnedCard) {
        json.update { it + card.trimmed() }
        AppLog.i("Cards", "Added custom card \"${card.title}\" (${card.look.name.lowercase()}, ${card.source.name.lowercase()}: ${card.detail})")
    }

    /** Replaces the card with [card]'s id; its songs are made again next time it shows. */
    suspend fun update(card: PinnedCard) {
        json.update { list -> list.map { if (it.id == card.id) card.trimmed() else it } }
        made.keys.removeAll { it.contains("-${card.id}-") }
        AppLog.i("Cards", "Changed custom card \"${card.title}\" (${card.look.name.lowercase()}, ${card.detail})")
    }

    suspend fun setShown(id: String, shown: Boolean) {
        json.update { list -> list.map { if (it.id == id) it.copy(shown = shown) else it } }
    }

    suspend fun remove(id: String) {
        json.update { list -> list.filterNot { it.id == id } }
        AppLog.i("Cards", "Removed custom card $id")
    }

    suspend fun move(id: String, by: Int) {
        json.update { list ->
            val i = list.indexOfFirst { it.id == id }
            val j = i + by
            if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
        }
    }

    /** Today's songs for [card] and their albums; [round] above 0 makes a new set. */
    suspend fun content(card: PinnedCard, round: Int = 0): CardContent {
        val day = LocalDate.now()
        val key = "$day-${card.id}-${card.hashCode()}-$round"
        made[key]?.let { return it }
        val started = System.currentTimeMillis()
        val random = Random(daySeed(card.id, day, round))
        val (songs, albums) = when {
            card.source == CardSource.SOUND -> {
                val found = graph.generator.soundSearch(card.sound.trim(), card.size.coerceAtMost(PinnedCard.SIZE))
                found to if (card.look == CardLook.ALBUMS) albumIdsOf(found).take(ALBUMS).mapNotNull { graph.library.album(it) } else emptyList()
            }
            !graph.library.isSynced -> throw IllegalStateException("Sync your library first (Settings › Connections, under your server)")
            card.contents == CardContents.ALBUMS -> {
                val needsFavourites = card.allRules.any { it.field == RuleField.FAVOURITE }
                val favourites = if (needsFavourites) graph.library.starredSongs().mapNotNullTo(HashSet()) { it.albumId } else emptySet()
                val picked = pickRuleAlbums(graph.library.albums("name"), card, random, favourites = favourites)
                // Playing the card plays its first albums in turn.
                picked.take(PLAY_ALBUMS).flatMap { graph.library.songsOf(it) } to picked
            }
            else -> {
                val needsAlbums = card.allRules.any { it.field in ALBUM_FIELDS } || card.sort in setOf(CardSort.ADDED, CardSort.PLAYED)
                val albumsById = if (needsAlbums) graph.library.albumsById() else emptyMap()
                val picked = pickRuleSongs(graph.library.allSongs(), card, random, albums = albumsById)
                picked to if (card.look == CardLook.ALBUMS) albumIdsOf(picked).take(ALBUMS).mapNotNull { graph.library.album(it) } else emptyList()
            }
        }
        AppLog.i("Cards", "Custom card \"${card.title}\": ${songs.size} songs, ${albums.size} albums in ${System.currentTimeMillis() - started}ms" +
            (if (card.source == CardSource.LIBRARY) " · ${card.contents.label.lowercase()} where ${if (card.matchAll) "all" else "any"} of: ${card.allRules.joinToString("; ") { it.text }}" else ""))
        made.keys.removeAll { !it.startsWith(day.toString()) }
        val content = CardContent(songs, albums)
        made[key] = content
        return content
    }

    private fun PinnedCard.trimmed() = copy(
        title = title.trim(), sound = sound.trim(), genre = genre.trim(), artist = artist.trim(), album = album.trim(), words = words.trim(),
        rules = rules.map { it.copy(value = it.value.trim(), value2 = it.value2.trim()) },
    )

    private companion object {
        const val ALBUMS = 20
        /** An album card plays this many of its albums, in turn. */
        const val PLAY_ALBUMS = 10
    }
}
