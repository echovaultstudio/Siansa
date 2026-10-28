package app.siansa

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #584: the repository is public, so the GPL text is in and the signing key and its password are out. */
class PublicRepoTest {
    private val root = listOf(File(".."), File(".")).first { File(it, "LICENSE").isFile }

    @Test fun theLicenceIsTheGpl3() {
        val licence = File(root, "LICENSE").readText()
        assertTrue("LICENSE isn't the GPL 3", licence.contains("GNU GENERAL PUBLIC LICENSE") && licence.contains("Version 3, 29 June 2007"))
        val plugin = File(root, "audiomuse-plugin/LICENSE").readText()
        assertTrue("The plugin's licence isn't the AGPL 3", plugin.contains("GNU AFFERO GENERAL PUBLIC LICENSE"))
    }

    @Test fun theSigningKeyIsOut() {
        assertTrue("The signing key is in the tree", !File(root, "app/siansa.keystore").exists())
        val gradle = File(root, "app/build.gradle.kts").readText()
        val literalPassword = Regex("""(store|key)Password\s*=\s*"[^"]+"""")
        assertTrue("A password is written in build.gradle.kts", !literalPassword.containsMatchIn(gradle))
        assertTrue("The key isn't read from SIANSA_KEYSTORE", gradle.contains("SIANSA_KEYSTORE"))
        assertTrue(".gitignore doesn't keep keys out", File(root, ".gitignore").readText().contains("*.keystore"))
    }
}
