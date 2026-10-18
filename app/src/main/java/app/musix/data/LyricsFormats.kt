package app.musix.data

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lyrics in the formats they come in, turned into lines (and words) with times. Navidrome hands over
 * lyrics from the file's tags and from .lrc / .txt files next to it; most arrive already split into synced
 * lines, but tags often hold raw text in another format. Supported:
 *  - LRC: `[01:23.45]line`, several times on one line, `[offset:…]`, and the `[ar:]`/`[ti:]` header lines;
 *  - Enhanced LRC (A2): word or syllable times inside a line, `<01:23.45>word`;
 *  - TTML (Apple Music and others): `<p begin=…>`, with `<span begin=…>` per word or syllable;
 *  - SRT and WebVTT subtitles: `00:01:23,450 --> 00:01:25,000`;
 *  - plain text.
 * [best] picks the most detailed lyrics when there are several (word timed, then synced, then plain).
 */
object LyricsFormats {
    private val TIME = """(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?"""
    private val LRC_LINE = Regex("""^\s*((?:\[$TIME\]\s*)+)(.*)$""")
    private val LRC_TAG = Regex("""\[$TIME\]""")
    private val LRC_OFFSET = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*\]\s*$""", RegexOption.IGNORE_CASE)
    private val LRC_HEADER = Regex("""^\s*\[[a-zA-Z#]+:[^\]]*\]\s*$""")
    private val WORD_TAG = Regex("""<$TIME>""")
    private val SUB_CUE = Regex("""^\s*(?:(\d+):)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*""")

    /** [lyrics] with any raw format inside it read properly; lyrics already in good shape come back as they are. */
    fun normalize(lyrics: Lyrics): Lyrics {
        val raw = lyrics.lines.joinToString("\n") { it.text }
        if (!lyrics.synced) {
            parse(raw)?.let { return it }
            return lyrics
        }
        // Synced lines may still carry word times inside them (enhanced LRC kept as text).
        if (lyrics.lines.none { WORD_TAG.containsMatchIn(it.text) }) return lyrics
        // Lines that already have their words (from the server's cues) keep them.
        return Lyrics(true, lyrics.lines.map { line -> if (line.words.isNotEmpty()) line else words(line.startMs, line.text) })
    }

    /** Lyrics from raw text in any supported format; null when it's just plain text. */
    fun parse(text: String): Lyrics? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("<") && (trimmed.contains("<tt") || trimmed.contains("<p "))) ttml(trimmed)?.let { return it }
        if (trimmed.startsWith("WEBVTT") || trimmed.lines().any { SUB_CUE.containsMatchIn(it) }) subtitles(trimmed)?.let { return it }
        if (trimmed.lines().count { LRC_LINE.matches(it) } >= 1) lrc(trimmed)?.let { return it }
        return null
    }

    /** The most detailed of several lyrics: timed by word, then by line, then plain; [preferLang] first among equals. */
    fun best(options: List<Pair<String?, Lyrics>>, preferLang: String? = null): Lyrics? {
        fun score(l: Lyrics) = when {
            l.wordTimed -> 3
            l.synced -> 2
            l.lines.any { it.text.isNotBlank() } -> 1
            else -> 0
        }
        return options
            .filter { score(it.second) > 0 }
            .maxWithOrNull(compareBy({ score(it.second) }, { if (preferLang != null && it.first.equals(preferLang, ignoreCase = true)) 1 else 0 }))
            ?.second
    }

    // ---- LRC and enhanced LRC ----

    private fun lrc(text: String): Lyrics? {
        var offset = 0L
        val lines = ArrayList<LyricLine>()
        for (raw in text.lines()) {
            LRC_OFFSET.find(raw)?.let { offset = it.groupValues[1].toLong(); continue }
            val m = LRC_LINE.find(raw)
            if (m == null) {
                if (LRC_HEADER.matches(raw)) continue
                continue
            }
            val times = LRC_TAG.findAll(m.groupValues[1]).map { time(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
            val body = m.groupValues.last()
            // A positive offset makes the lyrics come sooner.
            times.forEach { t -> lines += words((t - offset).coerceAtLeast(0), body, -offset) }
        }
        if (lines.isEmpty()) return null
        return Lyrics(true, lines.sortedBy { it.startMs })
    }

    /** A line with word times (`<01:02.30>word`) read out of [text]; just the clean text when there are none. */
    private fun words(start: Long?, text: String, shift: Long = 0): LyricLine {
        val tags = WORD_TAG.findAll(text).toList()
        if (tags.isEmpty()) return LyricLine(start, text.trim())
        val words = ArrayList<LyricWord>()
        // Text before the first tag belongs to the line's own start.
        val lead = text.substring(0, tags.first().range.first)
        if (lead.isNotBlank() && start != null) words += LyricWord(start, lead)
        tags.forEachIndexed { i, tag ->
            val end = if (i + 1 < tags.size) tags[i + 1].range.first else text.length
            val piece = text.substring(tag.range.last + 1, end)
            if (piece.isNotEmpty()) {
                val at = (time(tag.groupValues[1], tag.groupValues[2], tag.groupValues[3]) + shift).coerceAtLeast(0)
                words += LyricWord(at, piece)
            }
        }
        val clean = words.joinToString("") { it.text }.replace(Regex("\\s+"), " ").trim()
        return LyricLine(start ?: words.firstOrNull()?.startMs, clean, words.filter { it.text.isNotEmpty() })
    }

    private fun time(min: String, sec: String, frac: String): Long {
        val f = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + f
    }

    // ---- SRT and WebVTT ----

    private fun subtitles(text: String): Lyrics? {
        val lines = ArrayList<LyricLine>()
        val rows = text.lines()
        var i = 0
        while (i < rows.size) {
            val m = SUB_CUE.find(rows[i])
            if (m == null) { i++; continue }
            val (h, mm, ss, ms) = m.destructured
            val start = (h.toLongOrNull() ?: 0) * 3_600_000 + mm.toLong() * 60_000 + ss.toLong() * 1_000 + ms.padEnd(3, '0').take(3).toLong()
            i++
            val body = StringBuilder()
            while (i < rows.size && rows[i].isNotBlank()) {
                if (body.isNotEmpty()) body.append(' ')
                body.append(rows[i].replace(Regex("<[^>]+>"), "").trim())
                i++
            }
            lines += LyricLine(start, body.toString())
        }
        return if (lines.isEmpty()) null else Lyrics(true, lines)
    }

    // ---- TTML ----

    private fun ttml(text: String): Lyrics? {
        val doc = runCatching {
            DocumentBuilderFactory.newInstance().apply {
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                isExpandEntityReferences = false
                isNamespaceAware = false
            }.newDocumentBuilder().parse(InputSource(StringReader(text)))
        }.getOrNull() ?: return null
        val ps = doc.getElementsByTagName("p")
        val lines = ArrayList<LyricLine>()
        for (i in 0 until ps.length) {
            val p = ps.item(i) as? Element ?: continue
            val begin = clock(p.getAttribute("begin"))
            val spans = p.getElementsByTagName("span")
            val words = ArrayList<LyricWord>()
            for (j in 0 until spans.length) {
                val span = spans.item(j) as? Element ?: continue
                // A span holding other spans (background vocals around their words) isn't a word itself.
                if (span.getElementsByTagName("span").length > 0) continue
                // Background vocals and translations are spans with a role; only timed spans are words.
                val at = clock(span.getAttribute("begin")) ?: continue
                val word = span.textContent ?: continue
                // Whether a space follows the word is the text right after the span.
                val gap = span.nextSibling?.takeIf { it.nodeType == org.w3c.dom.Node.TEXT_NODE }?.textContent.orEmpty()
                words += LyricWord(at, word + if (gap.isNotEmpty() && gap.isBlank()) " " else "")
            }
            val lineText = (if (words.isNotEmpty()) words.joinToString("") { it.text } else p.textContent.orEmpty())
                .replace(Regex("\\s+"), " ").trim()
            if (lineText.isEmpty()) continue
            lines += LyricLine(begin ?: words.firstOrNull()?.startMs, lineText, words)
        }
        if (lines.isEmpty()) return null
        val synced = lines.all { it.startMs != null }
        return Lyrics(synced, if (synced) lines.sortedBy { it.startMs } else lines.map { it.copy(startMs = null, words = emptyList()) })
    }

    /** A TTML time: "1:02:03.45", "02:03.450", "12.3s", "500ms"; null when there's none. */
    fun clock(value: String?): Long? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        Regex("""^([\d.]+)(h|m|s|ms)$""").find(v)?.let { m ->
            val n = m.groupValues[1].toDoubleOrNull() ?: return null
            return when (m.groupValues[2]) {
                "h" -> (n * 3_600_000).toLong()
                "m" -> (n * 60_000).toLong()
                "s" -> (n * 1_000).toLong()
                else -> n.toLong()
            }
        }
        val parts = v.split(':')
        val seconds = parts.last().toDoubleOrNull() ?: return null
        val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0
        val hours = if (parts.size >= 3) parts[parts.size - 3].toLongOrNull() ?: 0 else 0
        return hours * 3_600_000 + minutes * 60_000 + (seconds * 1000).toLong()
    }
}
