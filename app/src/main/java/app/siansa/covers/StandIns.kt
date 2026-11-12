package app.siansa.covers

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The server's own stand-in pictures (the owner, 11 Nov): shown as the server sends them, never kept on the phone.
 *
 * Navidrome answers a cover it can't find **right now** (the library is still being scanned, the artist's photo isn't
 * fetched yet, the song has no picture) with its placeholder (a blue disc, an artist's star) as an ordinary picture:
 * HTTP 200, the same bytes at every size. It marks it `Cache-Control: no-store` and gives it no `ETag`; a real cover
 * has an `ETag` and may be kept (checked on a real Navidrome 0.64.2). The image cache kept every answer, so a
 * placeholder fetched once stayed for good at that size: lists (300 px) showed the blue disc while Now playing
 * (1000 px, fetched later) showed the cover.
 *
 * - A stand-in is shown but not written to the phone's cover cache ([isStandIn], asked by the image loader and by
 *   Download all covers).
 * - It stays in memory for [AGAIN_MS], so a list doesn't ask on every scroll; the next time the cover is needed
 *   after that, the server is asked again ([shown], [askAgain]).
 * - Stand-ins kept before this are found by what they are ([shipped]: every placeholder Navidrome has shipped since
 *   2021; [learn]: each one a server sends from now on) and removed, once after updating and on every Sync all
 *   (`CoverRefresh`).
 *
 * Emby and Jellyfin answer a missing picture with HTTP 404, which was never kept.
 */
object StandIns {
    /** How long a stand-in is shown from memory before its cover is asked for again. */
    const val AGAIN_MS = 2 * 60_000L

    /** How much of an answer is read to fingerprint a stand-in: Navidrome's biggest was 305 kB. */
    const val PEEK_BYTES = 1L shl 20

    /** A picture by its size and SHA-256. */
    data class Print(val bytes: Long, val sha256: String) {
        override fun toString() = "$bytes:$sha256"

        companion object {
            fun parse(text: String): Print? {
                val bytes = text.substringBefore(':').toLongOrNull() ?: return null
                val sha = text.substringAfter(':', "").takeIf { it.length == 64 } ?: return null
                return Print(bytes, sha)
            }
        }
    }

    /** Navidrome's placeholders, from its repository's history (`resources/`). */
    val shipped: Set<Print> = setOf(
        // album-placeholder.webp, since March 2025
        Print(69228, "273a4dbd61dfbb0a12d5d8ffe780eb7a3d4d000bc9771c5411cc70ae4dfa8a1f"),
        // album-placeholder.webp, December 2024
        Print(17464, "b502979c464ab7a61f79bfd820e23b8dc3eeb36034b0722f7588972fd9fa7392"),
        // artist-placeholder.webp, since January 2023
        Print(546, "521259c652af6d43cb124876106258d93b6b6f7fbe7ad2c73e8345875afe4dcf"),
        // placeholder.png, October 2022 and October 2021
        Print(300162, "6ee8f3c379050e52f8c5cdf7eb215f6d14c38cc7c4d8c59000d619d7a6a07888"),
        Print(304565, "292b20b877469b8030a08d1a262e84280a2b3334b9a254fba2fa3dd5600fd74c"),
    )

    @Volatile
    private var learned: Set<Print> = emptySet()

    /** Hears each stand-in met for the first time, to keep it for the next run. */
    @Volatile
    var onLearned: ((Set<Print>) -> Unit)? = null

    /** The stand-ins met on earlier runs. */
    fun restore(prints: Collection<String>) {
        learned = prints.mapNotNull(Print::parse).toSet()
    }

    /**
     * Whether an answer is the server's stand-in and not the item's own picture: a Subsonic cover ([path] ends in
     * `getCoverArt`) marked "don't keep" with no version. A proxy that marks everything "don't keep" leaves the
     * `ETag` of real covers in place, so those are still kept.
     */
    fun isStandIn(path: String, cacheControl: String?, etag: String?): Boolean =
        isCoverArt(path) && cacheControl.orEmpty().contains("no-store", ignoreCase = true) && etag.isNullOrBlank()

    /** Whether an address ([path], with or without what follows `?`) is a Subsonic cover's. */
    fun isCoverArt(path: String): Boolean =
        path.substringBefore('?').substringAfterLast('/').substringBefore('.') == "getCoverArt"

    fun isStandIn(response: okhttp3.Response): Boolean = response.isSuccessful &&
        response.header("Content-Type").orEmpty().startsWith("image/") &&
        isStandIn(response.request.url.encodedPath, response.header("Cache-Control"), response.header("ETag"))

    /** Keeps the fingerprint of a stand-in the server just sent ([bytes]: the whole picture). */
    fun learn(bytes: ByteArray) {
        val print = Print(bytes.size.toLong(), sha256(bytes))
        if (print in shipped || print in learned) return
        val all = synchronized(this) { (learned + print).also { learned = it } }
        onLearned?.invoke(all)
    }

    /** The sizes stand-ins have: only a kept picture of one of these is worth hashing. */
    fun sizes(): Set<Long> = (shipped + learned).mapTo(HashSet()) { it.bytes }

    /** Whether a picture of [bytes] bytes whose SHA-256 [sha256] gives is a known stand-in. */
    fun known(bytes: Long, sha256: () -> String): Boolean {
        val same = (shipped + learned).filter { it.bytes == bytes }
        if (same.isEmpty()) return false
        val sha = sha256()
        return same.any { it.sha256 == sha }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- Shown now, asked for again later ----

    private val shown = ConcurrentHashMap<String, Long>()

    /** The cover kept under [cacheId] just showed the server's stand-in. */
    fun shown(cacheId: String, now: Long = System.currentTimeMillis()) {
        shown[cacheId] = now
    }

    /**
     * True, once, when [cacheId]'s stand-in has been shown for [AGAIN_MS]: what's in memory for it is then dropped, so
     * the server is asked again.
     */
    fun askAgain(cacheId: String, now: Long = System.currentTimeMillis()): Boolean {
        val since = shown[cacheId] ?: return false
        return now - since >= AGAIN_MS && shown.remove(cacheId, since)
    }

    /** Every cover showing a stand-in now is asked for again at its next showing (after a sync). */
    fun askAllAgain(): Set<String> = shown.keys.toSet().also { shown.clear() }

    internal fun forget() {
        shown.clear()
        learned = emptySet()
    }
}

/** A cover's id and size, read from its address on any server; null for another address. */
data class CoverAddress(val id: String, val size: Int?) {
    companion object {
        private val SUBSONIC_ID = Regex("[?&]id=([^&]+)")
        private val SUBSONIC_SIZE = Regex("[?&]size=(\\d+)")
        private val ITEM = Regex("/Items/([^/?]+)/Images/Primary")
        private val ITEM_SIZE = Regex("[?&]maxWidth=(\\d+)", RegexOption.IGNORE_CASE)

        /**
         * Navidrome's `…/rest/getCoverArt?id=<id>&size=<px>`, and Emby's and Jellyfin's
         * `…/Items/<id>/Images/Primary?maxWidth=<px>`, which carries no `id=` (issue #903).
         */
        fun of(url: String): CoverAddress? {
            ITEM.find(url)?.let { m ->
                return CoverAddress(decode(m.groupValues[1]), ITEM_SIZE.find(url)?.groupValues?.get(1)?.toIntOrNull())
            }
            if (!url.substringBefore('?').substringAfterLast('/').startsWith("getCoverArt")) return null
            val id = SUBSONIC_ID.find(url)?.groupValues?.get(1) ?: return null
            return CoverAddress(decode(id), SUBSONIC_SIZE.find(url)?.groupValues?.get(1)?.toIntOrNull())
        }

        private fun decode(text: String) = runCatching { java.net.URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
    }
}
