package app.siansa.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Radio links as people paste them (issue #353): a plain stream, or a playlist that points at one. `.m3u` and
 * `.m3u8` list addresses one per line (with `#EXTINF` titles); `.pls` is an INI file with `File1=`, `Title1=`. An
 * `.m3u8` with `#EXT-X-` lines is an HLS stream, played as it is. Only http and https addresses are taken.
 */
object StreamLinks {
    /** What a fetched link turned out to be. */
    enum class Type { STREAM, M3U, PLS, HLS, FEED, UNKNOWN }

    /**
     * The link as typed, cleaned: spaces trimmed, `https://` added when there's no scheme. Null when it isn't a web
     * address at all.
     */
    fun clean(text: String): String? {
        var t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        if (!t.contains("://")) t = "https://$t"
        val url = t.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank() || !url.host.contains('.') && url.host != "localhost") return null
        return t
    }

    /** Whether [url] is an http or https address. */
    fun isWeb(url: String?): Boolean = url?.toHttpUrlOrNull() != null

    /** Addresses in an M3U playlist, in order, relative ones made whole against [base]. */
    fun parseM3u(text: String, base: String? = null): List<String> =
        lines(text).filter { !it.startsWith("#") }.mapNotNull { resolve(it, base) }.distinct()

    /** The first `#EXTINF:-1,Title` title in an M3U playlist. */
    fun m3uTitle(text: String): String? =
        lines(text).firstOrNull { it.startsWith("#EXTINF", ignoreCase = true) }
            ?.substringAfter(',', "")?.trim()?.takeIf { it.isNotEmpty() }

    /** Addresses in a PLS playlist (`File1=`, `File2=`…), by their number. */
    fun parsePls(text: String, base: String? = null): List<String> =
        plsEntries(text, "file").mapNotNull { resolve(it, base) }.distinct()

    /** The first title in a PLS playlist (`Title1=`). */
    fun plsTitle(text: String): String? = plsEntries(text, "title").firstOrNull { it.isNotBlank() }?.trim()

    /** Whether an M3U8 is an HLS stream (segments and variants) rather than a list of stream addresses. */
    fun isHls(text: String): Boolean = text.contains("#EXT-X-", ignoreCase = true)

    /** Whether [text] looks like an RSS or Atom feed. */
    fun isFeed(text: String): Boolean {
        val head = text.take(FEED_SNIFF).lowercase()
        return head.contains("<rss") || head.contains("<feed") || head.contains("<channel")
    }

    /**
     * What a link is, from its server's content type, its address, and the start of its body ([head], empty when
     * the body wasn't read). The body wins over the type: servers often send playlists as text/plain.
     */
    fun typeOf(url: String, contentType: String?, head: String): Type {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase() ?: url.lowercase().substringBefore('?')
        val start = head.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return when {
            start.isNotEmpty() && isFeed(start) -> Type.FEED
            start.isNotEmpty() && start.startsWith("[playlist]", ignoreCase = true) -> Type.PLS
            start.isNotEmpty() && isHls(start) -> Type.HLS
            start.isNotEmpty() && (start.startsWith("#EXTM3U", ignoreCase = true) || parseM3u(start).isNotEmpty() && looksLikeText(start)) -> Type.M3U
            type in PLS_TYPES || path.endsWith(".pls") -> Type.PLS
            type in M3U_TYPES || path.endsWith(".m3u") || path.endsWith(".m3u8") -> Type.M3U
            type.endsWith("xml") || type.contains("rss") -> Type.FEED
            type.startsWith("audio/") || type.startsWith("video/") || type == "application/ogg" || type == "application/octet-stream" -> Type.STREAM
            // No type and a body that isn't text: audio from a server that doesn't say so.
            type.isEmpty() && start.isNotEmpty() && !looksLikeText(start) -> Type.STREAM
            else -> Type.UNKNOWN
        }
    }

    /** Whether a response of this type should have its body read to find out what it is (a playlist or a feed). */
    fun worthReading(url: String, contentType: String?): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase().orEmpty()
        if (type in M3U_TYPES || type in PLS_TYPES) return true
        if (path.endsWith(".m3u") || path.endsWith(".m3u8") || path.endsWith(".pls") || path.endsWith(".xml") || path.endsWith(".rss")) return true
        return type.startsWith("text/") || type.contains("xml") || type.contains("rss") || type.contains("json") || type.isEmpty()
    }

    private fun looksLikeText(text: String): Boolean = text.none { it == '\u0000' }

    private fun lines(text: String): List<String> =
        text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

    private fun plsEntries(text: String, key: String): List<String> =
        lines(text).mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val name = line.substring(0, eq).trim().lowercase()
            if (!name.startsWith(key)) return@mapNotNull null
            val n = name.removePrefix(key).toIntOrNull() ?: return@mapNotNull null
            n to line.substring(eq + 1).trim()
        }.sortedBy { it.first }.map { it.second }

    /** [line] as a whole http(s) address, relative ones against [base]; null for anything else. */
    private fun resolve(line: String, base: String?): String? {
        val direct = line.toHttpUrlOrNull()
        if (direct != null) return line
        if (line.contains("://") || line.any { it.isWhitespace() || it == '<' || it == '>' || it == '"' }) return null
        return base?.toHttpUrlOrNull()?.resolve(line)?.toString()
    }

    private const val FEED_SNIFF = 2048
    private val M3U_TYPES = setOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/m3u", "audio/x-m3u")
    private val PLS_TYPES = setOf("audio/x-scpls", "audio/scpls", "application/pls+xml", "audio/x-pls")
}
