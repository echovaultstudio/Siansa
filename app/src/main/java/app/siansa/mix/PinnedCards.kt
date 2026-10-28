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

/** One custom card: its name, look and contents. Empty text fields and null years mean "no rule". */
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
) {
    /** The rules as one line: "Rock · 1990–1999 · favourites", or "Sound: rainy jazz piano". */
    val detail: String
        get() = when (source) {
            CardSource.SOUND -> "Sound: ${sound.trim()}"
            CardSource.LIBRARY -> buildList {
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
        get() = genre.isNotBlank() || fromYear != null || toYear != null || artist.isNotBlank() || album.isNotBlank() || words.isNotBlank() || favourites

    companion object {
        fun new(title: String = "") = PinnedCard(id = "pin-" + UUID.randomUUID().toString().take(8), title = title)

        /** At most this many songs on a card. */
        const val SIZE = 50
    }
}

/** What a card needs before it can be saved, or null when it's fine. */
fun pinnedCardProblem(card: PinnedCard, audioMuseReady: Boolean, thisYear: Int = LocalDate.now().year): String? = when {
    card.title.isBlank() -> "Give the card a name."
    card.source == CardSource.SOUND && !audioMuseReady -> "Set up AudioMuse-AI in Settings › Connections to make a card by sound."
    card.source == CardSource.SOUND && card.sound.isBlank() -> "Describe the sound."
    card.source == CardSource.LIBRARY && !card.hasRule -> "Set at least one rule, so the card isn't your whole library."
    card.fromYear != null && card.fromYear !in 1900..thisYear -> "Years run from 1900 to $thisYear."
    card.toYear != null && card.toYear !in 1900..thisYear -> "Years run from 1900 to $thisYear."
    card.fromYear != null && card.toYear != null && card.fromYear > card.toYear -> "The first year comes before the second."
    else -> null
}

/** Whether [song]'s tags match every rule of a library [card]. A rule that isn't set matches everything. */
fun matchesRules(song: Song, card: PinnedCard): Boolean {
    if (card.favourites && !song.starred) return false
    if (card.genre.isNotBlank() && !(song.genre ?: "").contains(card.genre.trim(), ignoreCase = true)) return false
    if (card.fromYear != null && (song.year == null || song.year < card.fromYear)) return false
    if (card.toYear != null && (song.year == null || song.year > card.toYear)) return false
    if (card.artist.isNotBlank()) {
        val wanted = card.artist.trim()
        val names = listOf(song.artist) + song.artists.map { it.name }
        if (names.none { it.contains(wanted, ignoreCase = true) }) return false
    }
    if (card.album.isNotBlank() && !song.album.contains(card.album.trim(), ignoreCase = true)) return false
    if (card.words.isNotBlank()) {
        val words = card.words.trim().split(Regex("\\s+"))
        if (words.any { !song.title.contains(it, ignoreCase = true) }) return false
    }
    return true
}

/**
 * The songs of a library card: those matching its rules, in a random order that limits each artist to a few
 * and avoids the same artist twice in a row, at most [size]. The same [random] seed gives the same list, so a
 * card looks the same all day.
 */
fun pickRuleSongs(all: List<Song>, card: PinnedCard, random: Random, size: Int = PinnedCard.SIZE, perArtist: Int = 5): List<Song> {
    val matching = all.filter { matchesRules(it, card) }.distinctBy { it.id }.shuffled(random)
    val counts = HashMap<String, Int>()
    val picked = matching.filter { s ->
        val n = counts.getOrDefault(s.artist, 0)
        if (n >= perArtist) false else { counts[s.artist] = n + 1; true }
    }.take(size)
    return spreadArtists(picked)
}

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
        val songs = when (card.source) {
            CardSource.SOUND -> graph.generator.soundSearch(card.sound.trim(), PinnedCard.SIZE)
            CardSource.LIBRARY -> {
                if (!graph.library.isSynced) throw IllegalStateException("Sync your library first (Settings › Users)")
                pickRuleSongs(graph.library.allSongs(), card, Random(daySeed(card.id, day, round)))
            }
        }
        val albums = if (card.look == CardLook.ALBUMS) albumIdsOf(songs).take(ALBUMS).mapNotNull { graph.library.album(it) } else emptyList()
        AppLog.i("Cards", "Custom card \"${card.title}\": ${songs.size} songs in ${System.currentTimeMillis() - started}ms")
        made.keys.removeAll { !it.startsWith(day.toString()) }
        val content = CardContent(songs, albums)
        made[key] = content
        return content
    }

    private fun PinnedCard.trimmed() = copy(title = title.trim(), sound = sound.trim(), genre = genre.trim(), artist = artist.trim(), album = album.trim(), words = words.trim())

    private companion object {
        const val ALBUMS = 20
    }
}
