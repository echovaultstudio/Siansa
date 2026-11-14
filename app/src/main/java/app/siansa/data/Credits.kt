package app.siansa.data

import kotlinx.serialization.builtins.ListSerializer

/**
 * Every artist credited on a song, kept in the library (beta testers #34): a featured artist ("feat. DJ Fresh")
 * or one on a compilation (T95 on "Heavyweight D&B") is in no album artist list, yet should be found and have
 * their own page.
 */
object Credits {
    private val serializer = ListSerializer(ArtistRef.serializer())

    /** The song's artists for the library; empty when the song only has its main artist. */
    fun encode(song: Song): String {
        val refs = song.artists.filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id }
        if (refs.isEmpty() || (refs.size == 1 && refs[0].id == song.artistId)) return ""
        return AppJson.encodeToString(serializer, refs)
    }

    fun decode(value: String): List<ArtistRef> =
        if (value.isEmpty()) emptyList() else runCatching { AppJson.decodeFromString(serializer, value) }.getOrDefault(emptyList())

    /** The names in [value], for the song's search text. */
    fun names(value: String): String = decode(value).joinToString(" ") { it.name }

    /**
     * Artists credited on [songs] whose name has every one of [terms] (normalised), not among [known] (ids):
     * the artists search shows besides the album artists. Each once, with how many songs credit them.
     */
    fun matching(songs: List<Song>, terms: List<String>, known: Set<String>): List<Pair<ArtistRef, Int>> {
        val counts = LinkedHashMap<String, Pair<ArtistRef, Int>>()
        for (song in songs) {
            // The main artist by the song's artist text only when that names one artist, not "A feat. B".
            val main = song.artistId?.takeIf { song.artists.isEmpty() && creditedNames(song.artist).size == 1 }
            val refs = song.artists + listOfNotNull(main?.let { ArtistRef(it, song.artist) })
            for (ref in refs.distinctBy { it.id }) {
                if (ref.id in known) continue
                val key = normalizeKey(ref.name)
                if (key.isEmpty() || !terms.all { key.contains(it) }) continue
                counts[ref.id] = ref to ((counts[ref.id]?.second ?: 0) + 1)
            }
        }
        return counts.values.toList()
    }
}

/**
 * The most fitting names first for a search (beta testers #34): the exact name, then names starting with the
 * words, then a word of the name starting with them, then the rest; ties keep their order.
 */
fun <T> rankByName(query: String, items: List<T>, name: (T) -> String): List<T> {
    val q = normalizeKey(query)
    if (q.isEmpty()) return items
    fun rank(item: T): Int {
        val key = normalizeKey(name(item))
        return when {
            key == q -> 0
            key.startsWith(q) -> 1
            " $key".contains(" $q") -> 2
            else -> 3
        }
    }
    return items.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }
}

/**
 * How well [name] answers a search for [query], 0 the best (the owner, 12 Nov: "Gita" listed "Digital Bath" above the
 * song called Gita, and "The right" listed "Why the Bright Stars Glow" above "The Right"):
 *
 * 0. the name is exactly what was typed;
 * 1. the name starts with it;
 * 2. a word of the name starts with it;
 * 3. every word of the name was typed, with more besides ("dj seinfeld the right" for "The Right");
 * 4. every word typed starts a word of the name or of [also] (the artist and album: "moderat gita");
 * 5. the rest: a word typed is only inside another word ("gita" in "Digital").
 */
fun searchRank(query: String, name: String, also: String = ""): Int {
    val q = normalizeKey(query)
    val key = normalizeKey(name)
    if (q.isEmpty() || key.isEmpty()) return 5
    val terms = q.split(' ').filter { it.isNotEmpty() }
    val words = key.split(' ').filter { it.isNotEmpty() }
    return when {
        key == q -> 0
        key.startsWith(q) -> 1
        " $key".contains(" $q") -> 2
        terms.containsAll(words) -> 3
        (words + normalizeKey(also).split(' ')).let { all -> terms.all { term -> all.any { it.startsWith(term) } } } -> 4
        else -> 5
    }
}

/** [items] with the ones that answer [query] best first ([searchRank]); ties keep their order. */
fun <T> bestFirst(query: String, items: List<T>, name: (T) -> String, also: (T) -> String = { "" }): List<T> =
    items.withIndex().sortedWith(compareBy({ searchRank(query, name(it.value), also(it.value)) }, { it.index })).map { it.value }

/** Songs by their title (with their artist and album for the words that aren't in it), albums by their name (with their artist). */
fun SearchResults.bestFirst(query: String): SearchResults = copy(
    albums = bestFirst(query, albums, { it.name }) { it.artist },
    songs = bestFirst(query, songs, { it.title }) { "${it.artist} ${it.album}" },
)

/**
 * An artist's albums from the server split into their own and the ones they only appear on (beta testers #34):
 * Navidrome lists every album an artist takes part in, so "DJ Fresh" got a Flava D mixtape as a release.
 */
fun splitOwnAlbums(artist: Artist, albums: List<Album>): Pair<List<Album>, List<Album>> {
    val key = normalizeKey(artist.name)
    return albums.partition { it.artistId == artist.id || (key.isNotEmpty() && normalizeKey(it.artist) == key) }
}
