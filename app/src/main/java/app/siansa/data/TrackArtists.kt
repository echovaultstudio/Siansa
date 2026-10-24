package app.siansa.data

// How artist names are joined in tags: "A feat. B", "A & B", "A, B", "A x B", "A vs. B"…
private val JOINS = Regex("""\s*(?:,|;|/|\s&\s|\sx\s|\sand\s|\bfeat\.?|\bft\.?|\bfeaturing\b|\bvs\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE)

/**
 * The artists in a tagged name, split where names are joined, but keeping names that are one artist in
 * the library together even when they contain a join: "Chase & Status feat. Bou" is Chase & Status and
 * Bou when [isKnown] knows "Chase & Status", not Chase, Status and Bou.
 */
fun splitArtists(name: String, isKnown: (String) -> Boolean = { false }): List<String> {
    val text = name.trim()
    if (text.isEmpty()) return emptyList()
    // Parts with where each starts and ends in the text, so neighbours can be put back together as written.
    val parts = ArrayList<IntRange>()
    var start = 0
    for (m in JOINS.findAll(text)) {
        if (m.range.first > start) parts += start until m.range.first
        start = m.range.last + 1
    }
    if (start < text.length) parts += start until text.length
    val out = ArrayList<String>()
    var i = 0
    while (i < parts.size) {
        // The longest run of parts from here that's one known artist; otherwise just this part.
        var end = i
        for (j in parts.size - 1 downTo i + 1) {
            if (isKnown(text.substring(parts[i].first, parts[j].last + 1))) { end = j; break }
        }
        out += text.substring(parts[i].first, parts[end].last + 1).trim()
        i = end + 1
    }
    return out.filter { it.isNotEmpty() }
}

/**
 * What to show under a track on its album page: only the artists beyond the album's own, or null when
 * the song is just by the album artist. Uses the song's artist tags as Navidrome reports them (its
 * "artists" list, one entry per artist) when present, otherwise the song's artist name, split where
 * names are joined ("feat.", "&", ",") except inside a name [isKnown] knows as one artist, and with the
 * album artist taken out.
 */
fun extraArtists(song: Song, albumArtistId: String?, albumArtist: String, isKnown: (String) -> Boolean = { false }): String? {
    val albumNames = (listOf(albumArtist) + splitArtists(albumArtist, isKnown))
        .map(::normalizeKey).filter { it.isNotEmpty() }.toSet()
    if (song.artists.isNotEmpty()) {
        val extra = song.artists
            .filterNot { (albumArtistId != null && it.id == albumArtistId) || normalizeKey(it.name) in albumNames }
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy(::normalizeKey)
        return extra.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
    val name = song.artist.trim()
    if (name.isEmpty() || normalizeKey(name) in albumNames) return null
    val parts = splitArtists(name, isKnown)
    val extra = parts.filterNot { normalizeKey(it) in albumNames }
    return when {
        extra.isEmpty() -> null
        extra.size == parts.size -> name // none of it is the album artist: show the name as tagged
        else -> extra.distinctBy(::normalizeKey).joinToString(", ")
    }
}
