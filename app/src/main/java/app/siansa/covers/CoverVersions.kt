package app.siansa.covers

/**
 * Which version of each album's cover the library knows (issue #639), so covers that carry no version of their own
 * are cached per version too.
 *
 * Navidrome's album cover ids end in `_<last change>` (`al-123_19a2f…`), so a cover replaced on the server gets a new
 * id and is downloaded again. A song's disc cover (`dc-123:1`) often has no such ending: cached once, it stayed the
 * old picture for good, in the mini player, now playing, the lock screen and the notification. Their cache keys now
 * carry their album's version instead, which changes when the album's cover does.
 */
object CoverVersions {
    @Volatile
    private var stamps: Map<String, String> = emptyMap()

    /** The albums' current cover ids (`al-<id>_<stamp>`), read from the library after it loads and after each sync. */
    fun update(albumCovers: Map<String, String?>) {
        stamps = albumCovers.mapNotNull { (id, cover) -> stampOf(cover)?.let { id to it } }.toMap()
    }

    /** The id to cache [id] under: itself, or with its album's cover version when it has none of its own. */
    fun cacheId(id: String): String = cacheIdFor(id) { stamps[it] }

    /** The `_<stamp>` ending of a cover id, or null when it has none. */
    internal fun stampOf(cover: String?): String? = cover?.substringAfter('_', "")?.takeIf { it.isNotEmpty() }

    /**
     * [cacheId] with the album's stamp looked up by [albumStamp]: a disc cover without a stamp of its own gets
     * `@<album stamp>` added; every other id stays as it is.
     */
    internal fun cacheIdFor(id: String, albumStamp: (String) -> String?): String {
        if (!id.startsWith("dc-") || id.contains('_')) return id
        val album = id.removePrefix("dc-").substringBefore(':').takeIf { it.isNotEmpty() } ?: return id
        return albumStamp(album)?.let { "$id@$it" } ?: id
    }
}
