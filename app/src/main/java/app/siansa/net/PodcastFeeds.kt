package app.siansa.net

import app.siansa.data.Episode
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A podcast's RSS feed read into its title, author, picture and episodes (issue #353). Episodes are the items with an
 * audio enclosure; the rest (blog posts, video-only items) are left out. Newest first, as feeds list them, up to
 * [MAX_EPISODES].
 */
object PodcastFeeds {
    data class Parsed(
        val title: String,
        val author: String,
        val image: String?,
        val description: String?,
        val episodes: List<Episode>,
    )

    /** The feed in [xml], or null when it isn't an RSS feed. Relative addresses are made whole against [feedUrl]. */
    fun parse(xml: String, feedUrl: String): Parsed? {
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                // No outside files or addresses are ever read while parsing (XXE); not every parser knows each feature.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t'))))
        }.getOrNull() ?: return null
        val channel = doc.getElementsByTagNameNS("*", "channel").item(0) as? Element
            ?: doc.getElementsByTagName("channel").item(0) as? Element
            ?: return null
        val title = text(channel, "title")?.let(::plain) ?: return null
        val author = text(channel, "author")?.let(::plain) ?: text(channel, "managingEditor")?.let(::plain).orEmpty()
        val image = imageOf(channel, feedUrl)
        val description = (text(channel, "summary") ?: text(channel, "description"))?.let(::plain)?.take(MAX_DESCRIPTION)
        val episodes = children(channel, "item").mapNotNull { episodeOf(it, feedUrl) }.distinctBy { it.id }.take(MAX_EPISODES)
        return Parsed(title, author, image, description, episodes)
    }

    private fun episodeOf(item: Element, feedUrl: String): Episode? {
        val enclosure = children(item, "enclosure").firstOrNull { e ->
            val type = e.getAttribute("type").lowercase()
            type.isEmpty() || type.startsWith("audio/") || type == "application/octet-stream" || type == "video/mp4" && children(item, "enclosure").size == 1
        } ?: return null
        val url = absolute(enclosure.getAttribute("url"), feedUrl) ?: return null
        val title = text(item, "title")?.let(::plain)?.takeIf { it.isNotEmpty() } ?: "Episode"
        val guid = text(item, "guid")?.trim()?.takeIf { it.isNotEmpty() } ?: url
        return Episode(
            id = idOf(guid),
            title = title,
            url = url,
            publishedAt = date(text(item, "pubDate")),
            durationSec = duration(text(item, "duration")),
            image = imageOf(item, feedUrl),
            description = (text(item, "summary") ?: text(item, "description"))?.let(::plain)?.take(MAX_DESCRIPTION),
        )
    }

    /** `<itunes:image href>`, else `<image><url>`. */
    private fun imageOf(parent: Element, feedUrl: String): String? {
        children(parent, "image").forEach { img ->
            img.getAttribute("href").takeIf { it.isNotBlank() }?.let { href -> absolute(href, feedUrl)?.let { return it } }
            text(img, "url")?.let { u -> absolute(u, feedUrl)?.let { return it } }
        }
        return null
    }

    /**
     * An episode's length from `<itunes:duration>`: "1:02:03", "62:03" or seconds ("3723", "3723.5"). 0 when it's
     * missing or can't be read.
     */
    fun duration(text: String?): Int {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return 0
        if (':' !in t) return t.toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: 0
        val parts = t.split(':').map { it.trim().toDoubleOrNull() ?: return 0 }
        if (parts.size > 3) return 0
        return parts.fold(0.0) { acc, p -> acc * 60 + p }.toInt().coerceAtLeast(0)
    }

    /** An RSS date (RFC 822, "Tue, 05 Mar 2024 10:00:00 GMT") in ms, or 0 when it can't be read. */
    fun date(text: String?): Long {
        val t = text?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (t.isEmpty()) return 0
        for (format in DATE_FORMATS) {
            runCatching { return ZonedDateTime.parse(t, format).toInstant().toEpochMilli() }
        }
        // Zone names RFC 1123 doesn't take ("EST", "PDT"): read without the zone, as UTC.
        val noZone = t.substringBeforeLast(' ')
        return runCatching { java.time.LocalDateTime.parse(noZone, NO_ZONE).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrDefault(0L)
    }

    /** HTML and extra spaces taken out of a title or description. */
    fun plain(text: String): String = text
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** A short, stable id for an episode, from its guid. */
    fun idOf(guid: String): String =
        MessageDigest.getInstance("SHA-1").digest(guid.toByteArray(Charsets.UTF_8)).take(10).joinToString("") { "%02x".format(it) }

    private fun absolute(url: String?, base: String): String? {
        val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (StreamLinks.isWeb(u)) return u
        if (u.contains("://")) return null
        return runCatching { java.net.URI(base).resolve(u).toString() }.getOrNull()?.takeIf { StreamLinks.isWeb(it) }
    }

    /** Direct children of [parent] with this local name, whatever their namespace (`itunes:image`, `image`). */
    private fun children(parent: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && localName(n) == name) out += n as Element
        }
        return out
    }

    private fun localName(n: Node): String = n.localName ?: n.nodeName.substringAfter(':')

    /** The text of the first child called [name]; an iTunes one is tried first for author, summary and image. */
    private fun text(parent: Element, name: String): String? =
        children(parent, name).firstOrNull { it.namespaceURI == ITUNES }?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: children(parent, name).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    const val MAX_EPISODES = 300
    private const val MAX_DESCRIPTION = 600
    private val DATE_FORMATS = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )
    private val NO_ZONE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss", Locale.ENGLISH)
}
