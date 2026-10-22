package app.musix.data

/**
 * Artists that are really several artists in one name (issue #359).
 *
 * When a song's artists are tagged in one field without the server's separator ("Rudimental, Orginal Koffee &
 * Winny" where the server splits on ";"), Navidrome makes one artist of the whole name. Musix splits such a
 * name only when it's safe:
 *
 * - The combined artist has no MusicBrainz ID of its own: one that has is a real artist ("Earth, Wind & Fire",
 *   "Crosby, Stills, Nash & Young") and is never split.
 * - Its name splits where names are joined (",", "&", "feat."…; see [splitArtists]) into at least two parts,
 *   keeping a part whole when it's one library artist ("Chase & Status").
 * - Every part found in the library is exactly one artist. Two library artists with the same name are two
 *   different people, and which one was meant can't be told, so the name is left as it is.
 * - Either every part is an artist in the library, or at least one of them has a MusicBrainz ID: proof that
 *   the tagged name lists known artists rather than being a band whose name happens to contain a join.
 *
 * Parts the library doesn't know (an artist only ever tagged together with others) have no page of their
 * own, so the combined artist is kept for them: it stays in the artist list and is offered with the others.
 */
data class CombinedArtist(
    val combined: Artist,
    /** The library artists it names, in the order they're written. */
    val parts: List<Artist>,
    /** Names in it the library has no artist for. */
    val unknown: List<String>,
) {
    /** Every part is a library artist, so the combined artist itself isn't needed anywhere. */
    val complete: Boolean get() = unknown.isEmpty()
}

object CombinedArtists {
    /** The combined artists among [artists], by the combined artist's id. */
    fun find(artists: List<Artist>): Map<String, CombinedArtist> {
        val byKey = artists.groupBy { normalizeKey(it.name) }
        val out = LinkedHashMap<String, CombinedArtist>()
        for (artist in artists) {
            check(artist, byKey)?.let { out[artist.id] = it }
        }
        return out
    }

    /** [artist] as a [CombinedArtist] when it's safe to split, else null. */
    internal fun check(artist: Artist, byKey: Map<String, List<Artist>>): CombinedArtist? {
        if (!artist.mbid.isNullOrBlank()) return null
        val ownKey = normalizeKey(artist.name)
        val names = splitArtists(artist.name) { name -> normalizeKey(name).let { it != ownKey && byKey.containsKey(it) } }
        if (names.size < 2) return null
        val parts = ArrayList<Artist>()
        val unknown = ArrayList<String>()
        for (name in names) {
            val matches = byKey[normalizeKey(name)].orEmpty().filter { it.id != artist.id }
            when (matches.size) {
                0 -> unknown += name
                1 -> parts += matches.first()
                else -> return null // the same name for different artists: can't tell which one is meant
            }
        }
        if (parts.isEmpty()) return null
        val safe = unknown.isEmpty() || parts.any { !it.mbid.isNullOrBlank() }
        if (!safe) return null
        return CombinedArtist(artist, parts.distinctBy { it.id }, unknown)
    }

    /**
     * The artists to offer for [refs] (a song's artists): a combined artist is replaced by the artists it
     * names, and kept after them only when some of its names have no library artist.
     */
    fun expand(refs: List<Artist>, combined: Map<String, CombinedArtist>): List<Artist> = refs.flatMap { ref ->
        val c = combined[ref.id] ?: return@flatMap listOf(ref)
        if (c.complete) c.parts else c.parts + ref
    }.distinctBy { it.id }

    /** The combined artists that name [artist], whose albums and songs also belong on its page. */
    fun containing(artist: Artist, combined: Map<String, CombinedArtist>): List<Artist> =
        combined.values.filter { c -> c.parts.any { it.id == artist.id } }.map { it.combined }

    /** [artists] without the combined artists that are fully covered by their parts (for artist lists). */
    fun withoutCombined(artists: List<Artist>, combined: Map<String, CombinedArtist>): List<Artist> =
        artists.filterNot { combined[it.id]?.complete == true }
}
