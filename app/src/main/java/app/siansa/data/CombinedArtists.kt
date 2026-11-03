package app.siansa.data

/**
 * Artists that are really several artists in one name (issue #359).
 *
 * When a song's artists are tagged in one field without the server's separator ("Rudimental, Orginal Koffee &
 * Winny" where the server splits on ";"), Navidrome makes one artist of the whole name. Siansa splits such a
 * name only when it's safe:
 *
 * - The combined artist has no MusicBrainz ID of its own: one that has is a real artist ("Earth, Wind & Fire",
 *   "Crosby, Stills, Nash & Young") and is never split. Unless that ID is another library artist's: Navidrome gives
 *   a name tagged with several artists' IDs the first of them ("ID, Camo & Krooked" got Camo & Krooked's), so the
 *   ID says which artist it is, not that the whole name is one.
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
    /** Names that stand for no artist at all: an unreleased "ID", an unknown or various artists. */
    private val PLACEHOLDERS = setOf("id", "ids", "unknown", "unknown artist", "various", "various artists", "va", "n a", "tba", "tbc")
        .map { normalizeKey(it) }.toSet()

    /** The combined artists among [artists], by the combined artist's id. */
    fun find(artists: List<Artist>): Map<String, CombinedArtist> {
        val index = Index(artists)
        val out = LinkedHashMap<String, CombinedArtist>()
        for (artist in artists) {
            check(artist, index.byKey, index.byMbid)?.let { out[artist.id] = it }
        }
        return out
    }

    /** The library's artists by name and by MusicBrainz ID, to check names against. */
    class Index(artists: List<Artist>) {
        val byKey: Map<String, List<Artist>> = artists.groupBy { normalizeKey(it.name) }
        val byMbid: Map<String, List<Artist>> = artists.filter { !it.mbid.isNullOrBlank() }.groupBy { it.mbid!!.lowercase() }
    }

    /**
     * [artist] as a [CombinedArtist] when it's safe to split, checked against the library [index]: for a song's
     * artist that isn't in the library's artist list (Navidrome lists album artists there, so "ID, Camo & Krooked",
     * only ever a song's artist, was never checked: 0.52.0-dev).
     */
    fun of(artist: Artist, index: Index): CombinedArtist? = check(artist, index.byKey, index.byMbid)

    /** Whether [artist]'s MusicBrainz ID is its own: no other library artist (by another name) has it. */
    private fun ownMbid(artist: Artist, byMbid: Map<String, List<Artist>>): Boolean {
        val id = artist.mbid?.takeIf { it.isNotBlank() }?.lowercase() ?: return false
        val key = normalizeKey(artist.name)
        return byMbid[id].orEmpty().none { it.id != artist.id && normalizeKey(it.name) != key }
    }

    /** [artist] as a [CombinedArtist] when it's safe to split, else null. */
    internal fun check(artist: Artist, byKey: Map<String, List<Artist>>, byMbid: Map<String, List<Artist>> = emptyMap()): CombinedArtist? {
        if (ownMbid(artist, byMbid)) return null
        val ownKey = normalizeKey(artist.name)
        val names = splitArtists(artist.name) { name -> normalizeKey(name).let { it != ownKey && byKey.containsKey(it) } }
        if (names.size < 2) return null
        val parts = ArrayList<Artist>()
        val unknown = ArrayList<String>()
        for (name in names) {
            // "ID" (a DJ's unreleased track), "Unknown"…: not an artist, so it needs no page and isn't offered.
            if (normalizeKey(name) in PLACEHOLDERS) continue
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
     * For the log: why [artist], whose name looks like several artists, is kept as one ([find] didn't split it), or
     * null when its name doesn't split at all.
     */
    fun whyKept(artist: Artist, artists: List<Artist>): String? {
        val byKey = artists.groupBy { normalizeKey(it.name) }
        val byMbid = artists.filter { !it.mbid.isNullOrBlank() }.groupBy { it.mbid!!.lowercase() }
        val ownKey = normalizeKey(artist.name)
        val names = splitArtists(artist.name) { name -> normalizeKey(name).let { it != ownKey && byKey.containsKey(it) } }
        if (names.size < 2) return null
        if (ownMbid(artist, byMbid)) return "it has its own MusicBrainz ID"
        val matches = names.map { n -> byKey[normalizeKey(n)].orEmpty().filter { it.id != artist.id } }
        if (matches.any { it.size > 1 }) return "a name in it belongs to more than one artist"
        val known = matches.filter { it.size == 1 }.map { it.first() }
        if (known.isEmpty()) return "none of ${names.joinToString(" | ")} is an artist in the library"
        if (known.none { !it.mbid.isNullOrBlank() }) {
            val unknown = names.filterIndexed { i, _ -> matches[i].isEmpty() }
            return "${unknown.joinToString(" | ")} isn't in the library, and none of ${known.joinToString(" | ") { it.name }} has a MusicBrainz ID"
        }
        return null
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
