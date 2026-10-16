package app.musix.data

// How artist names are joined in tags: "A feat. B", "A & B", "A, B", "A x B", "A vs. B"…
private val JOINS = Regex("""\s*(?:,|;|/|\s&\s|\sx\s|\sand\s|\bfeat\.?|\bft\.?|\bfeaturing\b|\bvs\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE)

/**
 * What to show under a track on its album page: only the artists beyond the album's own, or null when
 * the song is just by the album artist. Uses the song's artist tags as Navidrome reports them (its
 * "artists" list, one entry per artist) when present, otherwise the song's artist name, split where
 * names are joined ("feat.", "&", ",") and with the album artist taken out.
 */
fun extraArtists(song: Song, albumArtistId: String?, albumArtist: String): String? {
    val albumNames = (listOf(albumArtist) + albumArtist.split(JOINS))
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
    val parts = name.split(JOINS).map { it.trim() }.filter { it.isNotEmpty() }
    val extra = parts.filterNot { normalizeKey(it) in albumNames }
    return when {
        extra.isEmpty() -> null
        extra.size == parts.size -> name // none of it is the album artist: show the name as tagged
        else -> extra.distinctBy(::normalizeKey).joinToString(", ")
    }
}
