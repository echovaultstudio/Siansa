package app.siansa.covers

/**
 * Which version of each album's cover the library knows (issue #639), so covers that carry no version of their own
 * are cached per version too.
 *
 * Navidrome's album cover ids end in `_<last change>` (`al-123_19a2f…`), so a cover replaced on the server gets a new
 * id and is downloaded again. A song's disc cover (`dc-123:1`) often has no such ending: cached once, it stayed the
 * old picture for good, in the mini player, now playing, the lock screen and the notification. Their cache keys now
 * carry their album's version instead, which changes when the album's cover does.
 *
 * An id with no version at all (`ar-<id>`, `al-<id>`, made by the app when the server gave none) is checked against
 * the server on Sync all (`CoverRefresh`). Navidrome's placeholder is never kept (`StandIns`), so the artist's star
 * no longer needs its key changed every week to go away.
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

    // ---- Emby and Jellyfin: a picture's tag ----

    private val tags = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var unsaved = false

    /** Gives the tags kept on an earlier run ([dump]'s text), read once, before the first item is seen. */
    @Volatile
    var kept: (() -> String?)? = null

    private var recalled = false

    @Synchronized
    private fun recall() {
        if (recalled) return
        recalled = true
        kept?.invoke()?.let(::restore)
    }

    /** Hears the id of an item whose picture changed on the server: what's kept of it is then removed. */
    @Volatile
    var onChanged: ((String) -> Unit)? = null

    /**
     * Emby and Jellyfin address a picture by its item alone (`/Items/<id>/Images/Primary`), so a cover replaced on the
     * server kept its address, and the phone kept the old picture for good (the owner, 11 Nov). Each item comes with
     * its picture's [tag], which changes when the picture does: the tags are remembered ([dump], [restore]), and an
     * item seen with another tag than before has its kept pictures removed ([onChanged]), to be fetched fresh.
     */
    fun seen(id: String?, tag: String?) {
        if (id.isNullOrEmpty() || tag.isNullOrEmpty()) return
        recall()
        val was = tags.put(id, tag)
        if (was == tag) return
        unsaved = true
        if (was != null) onChanged?.invoke(id)
    }

    /** The tags as text to keep (`<id> <tag>` a line), or null when nothing changed since the last [dump]. */
    fun dump(): String? {
        recall()
        if (!unsaved) return null
        unsaved = false
        return tags.entries.joinToString("\n") { "${it.key} ${it.value}" }
    }

    /** The tags kept by [dump] on an earlier run. */
    fun restore(text: String) {
        text.lineSequence().forEach { line ->
            val id = line.substringBefore(' ')
            val tag = line.substringAfter(' ', "")
            if (id.isNotEmpty() && tag.isNotEmpty()) tags.putIfAbsent(id, tag)
        }
    }

    internal fun forgetTags() {
        tags.clear()
        unsaved = false
        recalled = false
    }
}
