package app.siansa.data

import java.util.Locale

/**
 * What kind of release an album is, from its release types (MusicBrainz RELEASETYPE / albumtypes tags).
 * "album" alone is an Album; "album; live" is Live; a DJ-mix counts as DJ-mix even when it is also a
 * compilation. Entries are in display order.
 */
enum class AlbumCategory(val single: String, val plural: String) {
    ALBUM("Album", "Albums"),
    EP("EP", "EPs"),
    SINGLE("Single", "Singles"),
    LIVE("Live", "Live"),
    COMPILATION("Compilation", "Compilations"),
    DJMIX("DJ-mix", "DJ-mixes"),
    SOUNDTRACK("Soundtrack", "Soundtracks"),
    REMIX("Remix", "Remixes"),
    DEMO("Demo", "Demos"),
    MIXTAPE("Mixtape", "Mixtapes"),
    OTHER("Other", "Other releases"),
}

private val secondaryOrder = listOf(
    "dj-mix" to AlbumCategory.DJMIX,
    "live" to AlbumCategory.LIVE,
    "compilation" to AlbumCategory.COMPILATION,
    "soundtrack" to AlbumCategory.SOUNDTRACK,
    "remix" to AlbumCategory.REMIX,
    "demo" to AlbumCategory.DEMO,
    "mixtape/street" to AlbumCategory.MIXTAPE,
    "mixtape" to AlbumCategory.MIXTAPE,
)

private val primary = mapOf(
    "album" to AlbumCategory.ALBUM,
    "ep" to AlbumCategory.EP,
    "single" to AlbumCategory.SINGLE,
)

/** Release types as lowercase single words, also when a tag came through as one "album; live" string. */
fun Album.typeList(): List<String> = releaseTypes
    .flatMap { it.split(';', ',') }
    .map { it.trim().lowercase(Locale.ROOT).replace('_', '-').replace("djmix", "dj-mix") }
    .filter { it.isNotEmpty() }

val Album.category: AlbumCategory
    get() {
        val types = typeList()
        // A secondary type wins over the main one: album + live is Live.
        secondaryOrder.firstOrNull { (name, _) -> name in types }?.let { return it.second }
        types.firstNotNullOfOrNull { primary[it] }?.let { return it }
        if (isCompilation) return AlbumCategory.COMPILATION
        return when {
            types.isEmpty() -> AlbumCategory.ALBUM
            else -> AlbumCategory.OTHER
        }
    }
