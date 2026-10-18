package app.musix.mix

import app.musix.data.normalizeKey

/**
 * Every song in the library by its title, for matching many songs from Koito or Last.fm at once (the time
 * machine checks up to 150). A library search per song took 10–100 ms each, about 12 s for one month on a
 * 40,000-song library; this is built once and then each match is a lookup.
 *
 * A song matches when its title is the same (in any case, accents and punctuation aside) and its artist
 * is the same, or one contains the other ("alt-J" and "alt-J feat. Conrad Sewell"). The same artist
 * exactly is preferred.
 */
class SongIndex(songs: List<Entry>) {
    /** A song as the index needs it. */
    data class Entry(val id: String, val title: String, val artist: String)

    private class Keyed(val id: String, val artist: String)

    private val byTitle: Map<String, List<Keyed>> = songs
        .groupBy({ normalizeKey(it.title) }, { Keyed(it.id, normalizeKey(it.artist)) })
        .filterKeys { it.isNotEmpty() }

    val size: Int = songs.size

    /** The id of the library's song with this [title] by this [artist], or null when there's none. */
    fun find(artist: String, title: String): String? {
        val candidates = byTitle[normalizeKey(title)] ?: return null
        val a = normalizeKey(artist)
        if (a.isEmpty()) return candidates.first().id
        candidates.firstOrNull { it.artist == a }?.let { return it.id }
        return candidates.firstOrNull { it.artist.isNotEmpty() && (it.artist.contains(a) || a.contains(it.artist)) }?.id
    }
}
