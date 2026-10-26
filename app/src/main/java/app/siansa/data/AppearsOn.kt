package app.siansa.data

/**
 * Between the names in a song's artist credit: "A feat. B", "A, B & C", "A ft B", "A vs. B", "A / B", "A; B", and
 * "A • B", how Navidrome joins several artists by default (issue #504).
 */
private val CREDIT_SEPARATORS = Regex(
    "\\s*(?:,|;|/|•|&|\\+|\\bfeat\\b\\.?|\\bft\\b\\.?|\\bfeaturing\\b|\\bvs\\b\\.?)\\s*",
    RegexOption.IGNORE_CASE,
)

/** The artists named in a song's artist text, normalised: the whole text and each name in it. */
fun creditedNames(artistText: String): Set<String> {
    val whole = normalizeKey(artistText)
    val parts = artistText.split(CREDIT_SEPARATORS).map { normalizeKey(it) }
    return (parts + whole).filter { it.isNotEmpty() }.toSet()
}

/** Whether the artist ([artistId], [name]) is credited on [song]: as its artist, one of its artists, or by name. */
fun isCredited(song: Song, artistId: String, name: String): Boolean {
    if (song.artistId == artistId || song.artists.any { it.id == artistId }) return true
    val key = normalizeKey(name)
    return key.isNotEmpty() && key in creditedNames(song.artist)
}

/**
 * The albums (ids, in the order first met) that the artist is credited on without being their album
 * artist: the "Appears on" of their page. [isOwnAlbum] says whether an album id is one of the artist's own.
 */
fun appearsOnAlbumIds(songs: List<Song>, artistId: String, name: String, isOwnAlbum: (String) -> Boolean): List<String> =
    songs.asSequence()
        .filter { isCredited(it, artistId, name) }
        .mapNotNull { it.albumId }
        .distinct()
        .filterNot(isOwnAlbum)
        .toList()

/** Escapes [value] for a SQL LIKE pattern used with ESCAPE '\'. */
fun likeEscape(value: String): String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
