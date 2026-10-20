package app.musix.mix

import android.content.Context
import app.musix.AppGraph
import app.musix.data.Album
import app.musix.data.JsonFile
import app.musix.data.Song
import app.musix.data.Users
import app.musix.data.isoMillis
import app.musix.log.AppLog
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What a card of your own on Home's Made for you shows (issue #274), from what Navidrome can sort and group by. */
enum class CardKind(val label: String, val hint: String) {
    FORGOTTEN("Not played in a while", "Songs from albums you played a lot, but not in the last two months"),
    RANDOM("Random songs", "A different handful from your whole library each day"),
    GENRE("A genre", "Random songs of one genre"),
    YEARS("Years", "Random songs from a stretch of years, like the 90s"),
    NEWEST("Recently added", "Songs from the albums added last"),
    TOP_RATED("Top rated", "Songs from the albums you rated highest"),
    MOST_PLAYED("Most played", "Songs from the albums you play most"),
    FAVOURITE_ALBUMS("From favourite albums", "Songs from your favourite (starred) albums");
}

/** One card of your own: what it shows and the name you gave it. */
@Serializable
data class CustomCard(
    val id: String,
    val kind: CardKind,
    val title: String,
    val genre: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
) {
    /** "Rock", "1990–1999", or the kind's own description. */
    val detail: String
        get() = when (kind) {
            CardKind.GENRE -> genre ?: kind.hint
            CardKind.YEARS -> "${fromYear ?: "?"}–${toYear ?: "?"}"
            else -> kind.hint
        }

    companion object {
        fun new(kind: CardKind, genre: String? = null, fromYear: Int? = null, toYear: Int? = null) = CustomCard(
            id = "card-" + UUID.randomUUID().toString().take(8),
            kind = kind,
            title = when (kind) {
                CardKind.GENRE -> genre?.let { "Your $it" } ?: kind.label
                CardKind.YEARS -> if (fromYear != null && toYear != null && fromYear % 10 == 0 && toYear == fromYear + 9) "Your ${fromYear % 100}s" else kind.label
                else -> kind.label
            },
            genre = genre,
            fromYear = fromYear,
            toYear = toYear,
        )
    }
}

/** What a card needs before it can be added, or null when it's fine. */
fun cardProblem(card: CustomCard, thisYear: Int = LocalDate.now().year): String? = when {
    card.title.isBlank() -> "Give the card a name."
    card.kind == CardKind.GENRE && card.genre.isNullOrBlank() -> "Pick a genre."
    card.kind == CardKind.YEARS && (card.fromYear == null || card.toYear == null) -> "Fill in both years."
    card.kind == CardKind.YEARS && (card.fromYear!! !in 1900..thisYear || card.toYear!! !in 1900..thisYear) -> "Years run from 1900 to $thisYear."
    card.kind == CardKind.YEARS && card.fromYear!! > card.toYear!! -> "The first year comes before the second."
    else -> null
}

/** Albums played a lot but not since [cutoffMs] (ISO "played" dates from Navidrome); never-played ones don't count. */
fun forgottenAlbums(frequent: List<Album>, cutoffMs: Long): List<Album> =
    frequent.filter { a -> isoMillis(a.played)?.let { it < cutoffMs } ?: false }

/** At most [perAlbum] songs from each album, mixed together, at most [size] in all. */
fun spreadOverAlbums(byAlbum: List<List<Song>>, perAlbum: Int, size: Int, random: kotlin.random.Random = kotlin.random.Random.Default): List<Song> =
    byAlbum.flatMap { it.shuffled(random).take(perAlbum) }.distinctBy { it.id }.shuffled(random).take(size)

/**
 * Your own cards on Home's Made for you (issue #274), kept per user, and the songs for each. A card's songs
 * are made once a day (kept while the app runs), so opening it twice shows the same list.
 */
class MadeForYouCards(private val graph: AppGraph, context: Context) {
    private val json = JsonFile(File(context.filesDir, Users.scoped("made_for_you_cards.json")), ListSerializer(CustomCard.serializer())) { emptyList() }
    private val made = ConcurrentHashMap<String, List<Song>>()

    val state: StateFlow<List<CustomCard>?> = json.state

    suspend fun all(): List<CustomCard> = json.read()

    suspend fun get(id: String): CustomCard? = all().firstOrNull { it.id == id }

    suspend fun add(card: CustomCard) {
        json.update { it + card }
        AppLog.i("Cards", "Added card \"${card.title}\" (${card.kind.name.lowercase()})")
    }

    suspend fun rename(id: String, title: String) {
        json.update { list -> list.map { if (it.id == id) it.copy(title = title.trim()) else it } }
    }

    suspend fun remove(id: String) {
        json.update { list -> list.filterNot { it.id == id } }
    }

    suspend fun move(id: String, by: Int) {
        json.update { list ->
            val i = list.indexOfFirst { it.id == id }
            val j = i + by
            if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
        }
    }

    /** Today's songs for [card]; [fresh] makes them again. */
    suspend fun songs(card: CustomCard, fresh: Boolean = false): List<Song> {
        val key = "${LocalDate.now()}-${card.id}-${card.kind}-${card.genre}-${card.fromYear}-${card.toYear}"
        if (!fresh) made[key]?.let { return it }
        val started = System.currentTimeMillis()
        val songs = when (card.kind) {
            CardKind.RANDOM -> graph.subsonic.randomSongs(SIZE)
            CardKind.GENRE -> graph.subsonic.randomSongs(SIZE, genre = card.genre)
            CardKind.YEARS -> graph.subsonic.randomSongs(SIZE, fromYear = card.fromYear, toYear = card.toYear)
            CardKind.NEWEST -> fromAlbums(graph.subsonic.albums("newest", 20, 0))
            CardKind.TOP_RATED -> fromAlbums(graph.subsonic.albums("highest", 20, 0))
            CardKind.MOST_PLAYED -> fromAlbums(graph.subsonic.albums("frequent", 20, 0))
            CardKind.FAVOURITE_ALBUMS -> fromAlbums(graph.subsonic.albums("starred", 40, 0).shuffled().take(20))
            CardKind.FORGOTTEN -> {
                val cutoff = System.currentTimeMillis() - FORGOTTEN_DAYS * 24L * 60 * 60 * 1000
                fromAlbums(forgottenAlbums(graph.subsonic.albums("frequent", 100, 0), cutoff).shuffled().take(20))
            }
        }
        AppLog.i("Cards", "\"${card.title}\": ${songs.size} songs in ${System.currentTimeMillis() - started}ms")
        made.keys.removeAll { !it.startsWith(LocalDate.now().toString()) }
        made[key] = songs
        return songs
    }

    /** A few songs from each album, from the synced library when it has the album, otherwise from Navidrome. */
    private suspend fun fromAlbums(albums: List<Album>): List<Song> {
        val byAlbum = albums.map { a ->
            (graph.library.album(a.id)?.let { graph.library.songsOf(it) }).orEmpty()
                .ifEmpty { runCatching { graph.subsonic.album(a.id).songs }.getOrDefault(emptyList()) }
        }
        return spreadOverAlbums(byAlbum, perAlbum = 4, size = SIZE)
    }

    private companion object {
        const val SIZE = 50
        const val FORGOTTEN_DAYS = 60
    }
}
