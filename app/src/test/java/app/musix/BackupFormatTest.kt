package app.musix

import app.musix.data.BackupException
import app.musix.data.BackupRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What goes into a backup, and that it comes back exactly as it was. */
class BackupFormatTest {
    private val stores = mapOf(
        "settings" to mapOf<String, Any?>(
            "nd_url" to "http://server:4533",
            "nd_pass" to "sealed-password",
            "lb_token" to "sealed-token",
            "ai_titles" to false,
            "preamp" to -2.5f,
            "image_cache_mb" to 500,
            "sync_folders" to setOf("3", "1"),
        ),
        "library_pins" to mapOf<String, Any?>("pinned_at" to 1_700_000_000_000L),
        "cover_aliases" to mapOf<String, Any?>("a" to "b"),
        "androidx.work.util.preferences" to mapOf<String, Any?>("x" to 1),
    )
    private val files = mapOf(
        "selectors.json" to """[{"id":"morning","name":"Morning"}]""",
        "history.json" to "not json",
        "pending_scrobbles.json" to "[]",
        "notes.txt" to "{}",
    )

    private fun roundTrip() = BackupRules.unpack(BackupRules.pack(stores, files, 42L, "0.0.27beta"))

    @Test fun keepsEveryValueWithItsType() {
        val settings = roundTrip().prefs.getValue("settings").mapValues { BackupRules.decodeValue(it.value) }
        assertEquals("http://server:4533", settings["nd_url"])
        assertEquals(false, settings["ai_titles"])
        assertEquals(-2.5f, settings["preamp"])
        assertEquals(500, settings["image_cache_mb"])
        assertEquals(setOf("1", "3"), settings["sync_folders"])
        assertEquals(1_700_000_000_000L, BackupRules.decodeValue(roundTrip().prefs.getValue("library_pins").getValue("pinned_at")))
    }

    @Test fun neverSavesPasswordsOrTokens() {
        val text = BackupRules.pack(stores, files, 42L, "")
        assertFalse(text.contains("sealed-password"))
        assertFalse(text.contains("sealed-token"))
        assertNull(roundTrip().prefs.getValue("settings")["nd_pass"])
        assertTrue(BackupRules.isSecret("settings", "or_key"))
    }

    @Test fun leavesOutCachesAndLibraryStores() {
        val data = roundTrip()
        assertEquals(setOf("settings", "library_pins"), data.prefs.keys)
        // Not JSON, a queue, or not a JSON file: left out.
        assertEquals(setOf("selectors.json"), data.files.keys)
        assertEquals(42L, data.createdAt)
        assertEquals("0.0.27beta", data.appVersion)
    }

    @Test fun newStoresAreIncludedWithoutChanges() {
        assertTrue(BackupRules.includesPrefs("album_ratings"))
        assertTrue(BackupRules.includesFile("album_likes.json"))
        assertFalse(BackupRules.includesFile("../settings.json"))
    }

    @Test fun rejectsOtherFilesAndNewerFormats() {
        assertThrows(BackupException::class.java) { BackupRules.unpack("hello") }
        assertThrows(BackupException::class.java) { BackupRules.unpack("""{"app":"other"}""") }
        assertThrows(BackupException::class.java) { BackupRules.unpack("""{"app":"musix","format":99}""") }
    }

    @Test fun keepsOnlyTheNewestAutomaticBackups() {
        val names = (1..9).map { "musix-backup-2026-10-0$it-0300.json" } + "my-notes.json" + "musix-backup-manual.json"
        val gone = BackupRules.autoBackupsToDelete(names, keep = 7)
        assertEquals(listOf("musix-backup-2026-10-02-0300.json", "musix-backup-2026-10-01-0300.json"), gone)
    }
}
