package app.musix.data

/** A heading shown above the first song of a disc on an album page. */
data class DiscHeader(val disc: Int, val label: String)

/**
 * The disc headings of an album page, keyed by the index of the song each one goes above. Like
 * Navidrome, a disc is named by its disc title ("disctitle" tag, from [titles]) and otherwise
 * "Disc N". Albums with a single disc get no headings, unless that disc has a title of its own.
 * Songs without a disc number count as disc 1, as in [Library.songsOf].
 */
fun discHeaders(songs: List<Song>, titles: Map<Int, String>): Map<Int, DiscHeader> {
    val discs = songs.map { it.disc ?: 1 }
    val distinct = discs.distinct()
    val named = titles.filterValues { it.isNotBlank() }
    if (distinct.size < 2 && distinct.none { it in named }) return emptyMap()
    val headers = LinkedHashMap<Int, DiscHeader>()
    discs.forEachIndexed { index, disc ->
        if (index == 0 || discs[index - 1] != disc) {
            headers[index] = DiscHeader(disc, named[disc]?.trim() ?: "Disc $disc")
        }
    }
    return headers
}
