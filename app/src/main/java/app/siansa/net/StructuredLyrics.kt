package app.siansa.net

import app.siansa.data.LyricLine
import app.siansa.data.LyricWord
import app.siansa.data.Lyrics
import kotlinx.serialization.json.JsonObject

/**
 * One set of lyrics from OpenSubsonic's getLyricsBySongId, with its language. Navidrome 0.63+ sends word and
 * syllable timings when asked (`enhanced=true`, songLyrics v2): each `cueLine` belongs to a `line` (by
 * `index`) and its `cue` items are the words or syllables, with their start times (issue #155). The lines'
 * own text then carries no timing marks, so without the cues the lyrics only light up line by line.
 * Translations and pronunciations ([kind]) are left out: they're for showing alongside, not instead.
 */
fun structuredLyrics(set: JsonObject): Pair<String?, Lyrics>? {
    val kind = set.str("kind") ?: "main"
    if (kind != "main") return null
    val synced = set.bool("synced") == true
    val offset = set.long("offset") ?: 0L
    val lines = set.arr("line").map { line ->
        LyricLine(
            // A positive offset makes the lyrics come sooner.
            startMs = if (synced) line.long("start")?.let { (it - offset).coerceAtLeast(0) } else null,
            text = line.str("value").orEmpty(),
        )
    }.toMutableList()
    if (synced) {
        // The lead singer's cues for each line; background vocals (role "bg") only when there's nothing else.
        val roles = set.arr("agents").associate { (it.str("id") ?: "") to (it.str("role") ?: "main") }
        val byLine = set.arr("cueLine")
            .groupBy { it.long("index")?.toInt() ?: -1 }
            .mapValues { (_, cueLines) -> cueLines.firstOrNull { roles[it.str("agentId")] != "bg" } ?: cueLines.first() }
        byLine.forEach { (index, cueLine) ->
            val words = cueWords(cueLine, offset)
            if (words.isEmpty()) return@forEach
            if (index in lines.indices) {
                val line = lines[index]
                lines[index] = line.copy(startMs = line.startMs ?: words.first().startMs, words = words)
            } else if (index == lines.size) {
                // A cue line with no plain line to go with it.
                lines += LyricLine(cueLine.long("start")?.let { (it - offset).coerceAtLeast(0) } ?: words.first().startMs, cueLine.str("value").orEmpty(), words)
            }
        }
    }
    return set.str("lang") to Lyrics(synced, lines)
}

/**
 * A cue line's words or syllables. Each piece's text runs from the end of the one before it to its own end
 * (by the UTF-8 byte offsets into the line), so the spaces between words stay in and the pieces put
 * together give the whole line. Without usable offsets, the cues' own text is used.
 */
internal fun cueWords(cueLine: JsonObject, offset: Long): List<LyricWord> {
    val value = cueLine.str("value").orEmpty()
    val cues = cueLine.arr("cue").filter { it.long("start") != null }
    if (cues.isEmpty()) return emptyList()
    val bytes = value.toByteArray(Charsets.UTF_8)
    val sorted = cues.sortedBy { it.long("byteStart") ?: Long.MAX_VALUE }
    val usable = value.isNotEmpty() && sorted.all { c ->
        val s = c.long("byteStart"); val e = c.long("byteEnd")
        s != null && e != null && s in 0..e && e < bytes.size
    }
    if (!usable) {
        return cues.sortedBy { it.long("start") }.mapNotNull { c ->
            val text = c.str("value").orEmpty()
            if (text.isEmpty()) null else LyricWord((c.long("start")!! - offset).coerceAtLeast(0), text)
        }
    }
    var from = 0
    val words = ArrayList<LyricWord>()
    sorted.forEachIndexed { i, c ->
        // byteEnd is inclusive; the last piece also takes anything left at the end of the line.
        val end = if (i == sorted.lastIndex) bytes.size else c.long("byteEnd")!!.toInt() + 1
        if (end <= from) return@forEachIndexed
        val text = String(bytes, from, end - from, Charsets.UTF_8)
        from = end
        words += LyricWord((c.long("start")!! - offset).coerceAtLeast(0), text)
    }
    return words
}
