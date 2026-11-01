package app.siansa.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * A menu row's badge is read by the person ("ON"), not a test tag (issue #778): the shuffle row showed
 * "MENUSHUFFLE" because a test tag went into it. A badge written like a code name (camelCase) fails here.
 */
class MenuBadgeTest {
    private val roots = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui"))

    @Test fun badgesAreWordsNotCodeNames() {
        val root = roots.firstOrNull { it.isDirectory } ?: return fail("UI sources not found from ${File(".").absolutePath}")
        val problems = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> looksLikeCode(line) }
                .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertTrue("A badge is shown to the person: write a word, not a tag. ${problems.size} found:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun theCheckItself() {
        assertTrue(looksLikeCode("""MenuRow(Icons.Rounded.Shuffle, "Shuffle: off", badge = "menuShuffle") {"""))
        assertFalse(looksLikeCode("""MenuRow(Icons.Rounded.Bedtime, "Sleep", badge = if (on) "on" else null) {"""))
        assertFalse(looksLikeCode("""Modifier.testTag("menuShuffle")"""))
    }

    private fun looksLikeCode(line: String): Boolean = BADGE.findAll(line).any { CAMEL.matches(it.groupValues[1]) }

    private companion object {
        val BADGE = Regex("""badge\s*=\s*"([^"]*)"""")
        val CAMEL = Regex("""[a-z]+[A-Z]\w*""")
    }
}
