package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Siansa is GPL-3.0-or-later: the license ships in the APK and each version's source can be had without the key. */
class LicenseTest {
    private val root = listOf(File("."), File("..")).first { File(it, "settings.gradle.kts").isFile }

    @Test fun licenseIsTheGplVersion3() {
        val text = File(root, "LICENSE").readText()
        assertTrue(text.startsWith("                    GNU GENERAL PUBLIC LICENSE\n                       Version 3, 29 June 2007"))
        assertTrue(text.contains("END OF TERMS AND CONDITIONS"))
    }

    @Test fun licenseGoesIntoTheApk() {
        val build = File(root, "app/build.gradle.kts").readText()
        assertTrue(build.contains("""license.set(rootProject.layout.projectDirectory.file("LICENSE"))"""))
        assertTrue(build.contains(""""LICENSE.txt""""))
    }

    @Test fun signingKeyIsLeftOutOfTheSourceArchive() {
        val attributes = File(root, ".gitattributes").readLines().map { it.trim() }
        assertTrue(attributes.contains("app/siansa.keystore export-ignore"))
    }

    @Test fun sourceNameMatchesTheServer() {
        assertEquals("app.siansa_1234_src.tar.gz", sourceFileName(1234))
        val server = File(root, "deploy/scripts/siansa-source").readText()
        assertTrue(server.contains(""""app.siansa_%s_src.tar.gz""""))
    }

    @Test fun sourceRequestNamesTheVersion() {
        assertEquals("Siansa source code for 0.32.0beta (812)", sourceRequestSubject("0.32.0beta", 812))
        assertEquals("Siansa source code for ? (0)", sourceRequestSubject("", 0))
    }
}
