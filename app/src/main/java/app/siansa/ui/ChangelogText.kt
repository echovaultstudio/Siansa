package app.siansa.ui

/**
 * The small part of Markdown that CHANGELOG.md uses, read into blocks for the What's new page:
 * `## version — date` headings, `### Fixed/Added/Changed` sections, `-` bullets (indented ones nested,
 * lines that continue a bullet joined to it) and plain paragraphs. Inline, `**bold**` is kept as bold,
 * links and `code` become their plain text. Everything before the first version (the file's title and
 * introduction) and HTML comments are left out.
 */
sealed interface ChangelogBlock {
    data class Version(val version: String, val date: String) : ChangelogBlock
    data class Section(val title: String) : ChangelogBlock
    data class Bullet(val text: String, val level: Int) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
}

/** A piece of a line of text, bold or not. */
data class ChangelogSpan(val text: String, val bold: Boolean)

private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val LINK = Regex("""\[([^\]]+)]\([^)\s]*\)""")
private val CODE = Regex("""`([^`]+)`""")
private val VERSION_DATE = Regex("""^(\S+)\s+[—–-]+\s+(.*)$""")

fun parseChangelog(markdown: String): List<ChangelogBlock> {
    val blocks = mutableListOf<ChangelogBlock>()
    var started = false
    var inComment = false
    // The bullet or paragraph being read, so lines that continue it can be joined to it.
    var pending: ChangelogBlock? = null

    fun flush() {
        pending?.let { blocks += it }
        pending = null
    }

    for (raw in markdown.lineSequence()) {
        val line = raw.trimEnd()
        if (inComment) {
            if (line.contains("-->")) inComment = false
            continue
        }
        if (line.trimStart().startsWith("<!--")) {
            if (!line.contains("-->")) inComment = true
            continue
        }
        if (line.isBlank()) {
            flush()
            continue
        }
        val heading = HEADING.find(line)
        if (heading != null) {
            flush()
            val level = heading.groupValues[1].length
            val text = heading.groupValues[2]
            when {
                level == 2 -> {
                    started = true
                    val m = VERSION_DATE.find(text)
                    blocks += if (m != null) ChangelogBlock.Version(m.groupValues[1], m.groupValues[2].trim())
                    else ChangelogBlock.Version(text.trim(), "")
                }
                level >= 3 && started -> blocks += ChangelogBlock.Section(plainInline(text))
            }
            continue
        }
        if (!started) continue
        val bullet = BULLET.find(line)
        if (bullet != null) {
            flush()
            val indent = bullet.groupValues[1].replace("\t", "    ").length
            pending = ChangelogBlock.Bullet(bullet.groupValues[2].trim(), (indent / 2).coerceAtMost(2))
            continue
        }
        val text = line.trim()
        pending = when (val current = pending) {
            is ChangelogBlock.Bullet -> current.copy(text = current.text + " " + text)
            is ChangelogBlock.Paragraph -> current.copy(text = current.text + " " + text)
            else -> ChangelogBlock.Paragraph(text)
        }
    }
    flush()
    return blocks
}

/** Links and `code` as their plain text (bold markers are kept for [changelogSpans]). */
private fun plainInline(text: String): String =
    CODE.replace(LINK.replace(text) { it.groupValues[1] }) { it.groupValues[1] }

/** A line of changelog text as plain and bold pieces; `**` without a partner stays as it is. */
fun changelogSpans(text: String): List<ChangelogSpan> {
    val parts = plainInline(text).split("**").toMutableList()
    if (parts.size % 2 == 0) {
        // An odd number of ** markers: the last one has no partner, so it is ordinary text.
        val last = parts.removeAt(parts.lastIndex)
        parts[parts.lastIndex] = parts[parts.lastIndex] + "**" + last
    }
    return parts.mapIndexedNotNull { i, part ->
        if (part.isEmpty()) null else ChangelogSpan(part, bold = i % 2 == 1)
    }
}

/**
 * Every version's changes: the changelog in the Siansa repository (issue #435), which each release keeps up to date.
 */
const val FULL_CHANGELOG_URL = "https://github.com/echovaultstudio/Siansa/blob/main/CHANGELOG.md"

/** What's new shows only the newest version (issue #435): its heading and what follows, up to the next version. */
fun latestOnly(blocks: List<ChangelogBlock>): List<ChangelogBlock> {
    val first = blocks.indexOfFirst { it is ChangelogBlock.Version }
    if (first < 0) return blocks
    val next = blocks.drop(first + 1).indexOfFirst { it is ChangelogBlock.Version }
    return if (next < 0) blocks.drop(first) else blocks.subList(first, first + 1 + next)
}
