package app.siansa.ui

import app.siansa.net.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Someone on Emby doesn't read "Navidrome" (beta testers #31): screens say "your server", or ask [ServerWords] for
 * the server's own name. Logs may name Navidrome; a line that names it on purpose says `// server-name: ok`.
 */
class ServerWordingTest {
    private val roots = listOf(File("src/main/java/app/siansa/ui"), File("app/src/main/java/app/siansa/ui"))
    /** Files whose "Navidrome" strings are only written to the log, or that pick the name by server ([ServerWords]). */
    private val allowed = setOf("ServerWords.kt", "AnimatedCovers.kt")

    @Test fun screensDontNameNavidrome() {
        val root = roots.firstOrNull { it.isDirectory } ?: return fail("UI sources not found from ${File(".").absolutePath}")
        val problems = root.walkTopDown().filter { it.extension == "kt" && it.name !in allowed }.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) -> ServerWording.namesNavidrome(line) }
                .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertTrue(
            "Say \"your server\", or use ServerWords for the server's name. ${problems.size} found:\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test fun theCheckFindsOnlyTextShownToPeople() {
        assertTrue(ServerWording.namesNavidrome("""Text("Import from Navidrome")"""))
        assertFalse(ServerWording.namesNavidrome("""// Navidrome sends "" for artists without one"""))
        assertFalse(ServerWording.namesNavidrome("""AppLog.i("Sync", "Navidrome answered")"""))
        assertFalse(ServerWording.namesNavidrome("""Hint("Navidrome can't")  // server-name: ok"""))
        assertFalse(ServerWording.namesNavidrome("""val scrobble = settings.scrobbleNavidrome"""))
    }

    /** #573: an OpenSubsonic server's errors say "your server", as it may not be Navidrome. */
    @Test fun subsonicErrorsDontNameNavidrome() {
        val codes = listOf(null, 0, 10, 20, 30, 40, 41, 42, 43, 44, 50, 60, 70, 99)
        codes.forEach { code ->
            val text = app.siansa.net.SubsonicErrors.message(code, null)
            assertFalse("Error $code names Navidrome: $text", text.contains("Navidrome"))
        }
    }

    @Test fun embyIsCalledEmbyAndToldItsQualityDoesntApply() {
        assertEquals("Emby", ServerWords.name(SourceKind.EMBY))
        assertEquals("OpenSubsonic", ServerWords.name(SourceKind.OPENSUBSONIC))
        assertFalse(ServerWords.converts(SourceKind.EMBY))
        assertTrue("Emby" in ServerWords.streamingHint(SourceKind.EMBY))
        assertFalse(ServerWords.streamingHint(SourceKind.EMBY).contains("converted by"))
        assertTrue(ServerWords.streamingHint(SourceKind.OPENSUBSONIC).contains("opus"))
        assertTrue(ServerWords.downloadHint(SourceKind.EMBY).startsWith("Emby"))
    }
}

/** Finds "Navidrome" inside a string literal on a line that isn't a comment or a log call. */
object ServerWording {
    private val literal = Regex(""""[^"]*Navidrome[^"]*"""")

    fun namesNavidrome(line: String): Boolean {
        val t = line.trim()
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return false
        if ("AppLog." in t || "server-name: ok" in t) return false
        return literal.containsMatchIn(t)
    }
}
