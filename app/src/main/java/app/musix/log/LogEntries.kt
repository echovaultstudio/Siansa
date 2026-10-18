package app.musix.log

/** One log entry: a timestamped line plus the lines that continue it (a stack trace). */
class LogEntry(val level: String, val lines: List<String>) {
    val problem get() = level == "E" || level == "W"
    val error get() = level == "E"
}

private val entryStart = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} ([A-Z])/""")

/** Groups log lines into entries, oldest first, keeping an error and its stack trace together. */
fun logEntries(lines: List<String>): List<LogEntry> {
    val out = ArrayList<LogEntry>()
    var level = "I"
    var current = ArrayList<String>()
    for (line in lines) {
        val m = entryStart.find(line)
        if (m != null) {
            if (current.isNotEmpty()) out += LogEntry(level, current)
            current = arrayListOf(line)
            level = m.groupValues[1]
        } else {
            current += line
        }
    }
    if (current.isNotEmpty()) out += LogEntry(level, current)
    return out
}

/**
 * Every problem in [entries] (errors and warnings, as under "Problems") as one text for the clipboard,
 * oldest first with their stack traces, under [header] (e.g. the app version). Kept under [maxChars] by
 * leaving out the oldest ones, since the clipboard can't take a huge text. Null when there are none.
 */
fun problemsForClipboard(entries: List<LogEntry>, header: String, maxChars: Int = 200_000): String? {
    val errors = entries.filter { it.problem }
    if (errors.isEmpty()) return null
    val kept = ArrayDeque<String>()
    var size = header.length
    for (e in errors.asReversed()) {
        val text = e.lines.joinToString("\n")
        if (kept.isNotEmpty() && size + text.length + 1 > maxChars) break
        kept.addFirst(text)
        size += text.length + 1
    }
    val left = errors.size - kept.size
    val errorCount = errors.count { it.error }
    val warningCount = errors.size - errorCount
    val counts = listOfNotNull(
        errorCount.takeIf { it > 0 }?.let { if (it == 1) "1 error" else "$it errors" },
        warningCount.takeIf { it > 0 }?.let { if (it == 1) "1 warning" else "$it warnings" },
    ).joinToString(", ")
    val title = header + " · " + counts +
        (if (left > 0) " (oldest $left left out)" else "")
    return (listOf(title) + kept).joinToString("\n") + "\n"
}

/**
 * The whole log as one text for the clipboard, oldest first, under [header] (e.g. the app version).
 * Android's clipboard can't take a huge text (about 1 MB, and the log can be 30 MB with every request in it),
 * so when it doesn't fit in [maxChars] the newest whole entries that do are kept and the top line says how
 * many older ones were left out. Null when the log is empty.
 */
fun logForClipboard(entries: List<LogEntry>, header: String, maxChars: Int = 400_000): String? {
    if (entries.isEmpty()) return null
    val kept = ArrayDeque<String>()
    var size = header.length + 60
    for (e in entries.asReversed()) {
        val text = e.lines.joinToString("\n")
        if (kept.isNotEmpty() && size + text.length + 1 > maxChars) break
        kept.addFirst(text)
        size += text.length + 1
    }
    val left = entries.size - kept.size
    val title = header + " · " + (if (left > 0) "newest ${kept.size} of ${entries.size} entries (Save logs has them all)" else "${entries.size} entries")
    return (listOf(title) + kept).joinToString("\n") + "\n"
}
