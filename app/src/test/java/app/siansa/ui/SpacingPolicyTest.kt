package app.siansa.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The spacing policy (issue #160): gaps and padding in the UI code come from the scale in `Spacing.kt`
 * (`Space.Tight` … `Space.Large`), never raw dp numbers, so every page shares one rhythm. A number on a line
 * marked `// spacing: exact` is allowed: drawing that must be pixel-exact rather than spacing.
 */
class SpacingPolicyTest {
    private val roots = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui"))

    @Test fun noRawDpInSpacing() {
        val root = roots.firstOrNull { it.isDirectory } ?: return fail("UI sources not found from ${File(".").absolutePath}")
        val files = root.walkTopDown().filter { it.extension == "kt" && it.name != "Spacing.kt" }.toList()
        assertTrue("found the UI files", files.size > 20)
        val problems = files.flatMap { f -> SpacingPolicy.violations(f.readText()).map { "${f.name}:${it.line}: ${it.text}" } }
        if (problems.isNotEmpty()) {
            fail(
                "Use the spacing scale (Space.Tight/Small/Medium/Page/Section/Large in ui/Spacing.kt) instead of raw dp, " +
                    "or mark pixel-exact drawing with // spacing: exact. ${problems.size} found:\n" + problems.joinToString("\n"),
            )
        }
    }

    /** Named gaps (CHIPS_GAP, PICKER_GAP, …) are steps of the scale too. */
    @Test fun namedGapsUseTheScale() {
        val root = roots.firstOrNull { it.isDirectory } ?: return fail("UI sources not found")
        val raw = Regex("""val\s+[A-Z_]*(GAP|PAD)[A-Z_]*\s*(:\s*Dp)?\s*=\s*\d+(\.\d+)?\.dp""")
        val problems = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (raw.containsMatchIn(line)) "${f.name}:${i + 1}: ${line.trim()}" else null }
        }.toList()
        assertTrue("Named gaps must be Space.* steps:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The big round Play button is drawn only by [BigPlayButton], inside a [PlayButtonRow] that leaves
     * Space.BelowPlayButton under it: a copy of the button elsewhere would miss that room (issue #240).
     */
    @Test fun bigPlayButtonOnlyFromItsRow() {
        val root = roots.firstOrNull { it.isDirectory } ?: return fail("UI sources not found")
        // The button's icon: ▶, or ⏸ while what the page shows plays (a book's page, issue #626).
        val button = Regex("""Icons\.Rounded\.PlayArrow,\s*(if \(playing\) "Pause" else )?"Play",\s*tint\s*=\s*OnAccent""")
        val found = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            button.findAll(f.readText()).map { f.name }
        }.toList()
        assertTrue("The big Play button must come from BigPlayButton in CollectionPage.kt, found in: $found", found == listOf("CollectionPage.kt"))
        val uses = root.walkTopDown().filter { it.extension == "kt" }.sumOf { f -> Regex("""BigPlayButton\(""").findAll(f.readText()).count() }
        assertTrue("BigPlayButton is used on album and artist pages", uses >= 3)
    }

    @Test fun theCheckerFindsRawNumbers() {
        val code = """
            Modifier.padding(16.dp)
            Modifier.padding(horizontal = Space.Page, vertical = 6.dp)
            Arrangement.spacedBy(8.dp)
            Spacer(Modifier.height(4.dp))
            PaddingValues(
                bottom = 12.dp,
            )
            Modifier.padding(Space.Page)
            Modifier.size(48.dp)
            Modifier.padding(3.dp) // spacing: exact
            Modifier.padding(0.dp)
            Modifier.statusBarsPadding()
        """.trimIndent()
        val found = SpacingPolicy.violations(code).map { it.line }
        assertTrue("found: $found", found == listOf(1, 2, 3, 4, 6))
    }
}

/** Finds raw dp numbers in spacing calls (see [SpacingPolicyTest]). */
object SpacingPolicy {
    data class Violation(val line: Int, val text: String)

    private val call = Regex("""(?<![A-Za-z0-9_])(padding|spacedBy|PaddingValues|Spacer|bottomBarPadding)\(""")
    private val number = Regex("""(?<![A-Za-z0-9_.])(\d+(?:\.\d+)?)\.dp\b""")

    fun violations(source: String): List<Violation> {
        val lines = source.lines()
        val lineStarts = IntArray(lines.size).also { var at = 0; lines.forEachIndexed { i, l -> it[i] = at; at += l.length + 1 } }
        fun lineOf(offset: Int) = lineStarts.indexOfLast { it <= offset } + 1
        val out = LinkedHashMap<Int, Violation>()
        for (m in call.findAll(source)) {
            // The call's arguments, up to its closing bracket.
            var depth = 1
            var i = m.range.last + 1
            while (i < source.length && depth > 0) {
                when (source[i]) { '(' -> depth++; ')' -> depth-- }
                i++
            }
            val args = source.substring(m.range.last + 1, (i - 1).coerceAtLeast(m.range.last + 1))
            for (n in number.findAll(args)) {
                if (n.groupValues[1].toDouble() == 0.0) continue
                val line = lineOf(m.range.last + 1 + n.range.first)
                val text = lines[line - 1]
                if ("spacing: exact" in text) continue
                out.putIfAbsent(line, Violation(line, text.trim()))
            }
        }
        return out.values.sortedBy { it.line }
    }
}
