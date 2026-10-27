package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The open-source licences page (#576): every library the app is built with is on it, so adding one without listing
 * it fails here. About links to it, the docs and the privacy policy.
 */
class LicencesTest {
    private fun file(vararg paths: String): File = paths.map(::File).firstOrNull { it.isFile } ?: run { fail("None of ${paths.toList()} found from ${File(".").absolutePath}"); error("") }

    private val gradle get() = file("build.gradle.kts", "app/build.gradle.kts").readText()
    private val page get() = file("../deploy/site/licenses.html", "deploy/site/licenses.html").readText()

    /** What the page calls each library group, to find it there. */
    private val names = mapOf(
        "androidx.compose" to "Jetpack Compose",
        "androidx.media3" to "Media3",
        "androidx.room" to "Room",
        "androidx.core" to "Core",
        "androidx.activity" to "Activity",
        "androidx.lifecycle" to "Lifecycle",
        "androidx.navigation" to "Navigation",
        "androidx.work" to "WorkManager",
        "androidx.palette" to "Palette",
        "androidx.profileinstaller" to "Profile Installer",
        "androidx.mediarouter" to "MediaRouter",
        "org.jellyfin.media3" to "org.jellyfin.media3:media3-ffmpeg-decoder",
        "io.coil-kt.coil3" to "Coil",
        "com.squareup.okhttp3" to "OkHttp",
        "org.jetbrains.kotlinx" to "kotlinx",
    )

    @Test fun everyLibraryInTheAppIsOnTheLicencesPage() {
        val groups = Regex("""implementation\("([^:"]+):""").findAll(gradle).map { it.groupValues[1] }.toSet()
        assertTrue("No libraries found in build.gradle.kts", groups.isNotEmpty())
        // A group or one under it: androidx.compose covers androidx.compose.ui, androidx.compose.material3…
        fun nameOf(group: String) = names.entries.firstOrNull { (key, _) -> group == key || group.startsWith("$key.") }?.value
        val unknown = groups.filter { nameOf(it) == null }
        assertTrue("Add these to deploy/site/licenses.html and to this test: $unknown", unknown.isEmpty())
        val missing = groups.filter { nameOf(it)!! !in page }
        assertTrue("The licences page doesn't list: $missing", missing.isEmpty())
    }

    @Test fun thePageHasEachLicenceItUses() {
        assertTrue("Apache 2.0" in page)
        assertTrue("GPL 3.0" in page)
        assertTrue("Google Cast SDK" in page)
    }

    @Test fun aboutLinksToTheLicencesTheDocsAndThePrivacyPolicy() {
        assertEquals("https://siansa.app/licenses", AboutLinks.LICENCES)
        assertEquals("https://siansa.app/privacy", AboutLinks.PRIVACY)
        assertEquals(listOf("Open-source licences", "Docs", "Privacy policy"), AboutLinks.all.map { it.first })
        AboutLinks.all.forEach { (_, _, url) -> assertTrue(url, url.startsWith("https://")) }
    }
}
