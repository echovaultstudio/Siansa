package app.musix

import app.musix.data.BackupRules
import app.musix.data.UserFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each Navidrome user's own settings, library and history files. */
class UserFilesTest {
    @Test fun theFirstUserKeepsTheOriginalNames() {
        assertEquals("settings", UserFiles.scoped("settings", 0))
        assertEquals("history.json", UserFiles.scoped("history.json", 0))
        assertEquals("library.db", UserFiles.scoped("library.db", 0))
    }

    @Test fun otherUsersGetTheirOwnCopy() {
        assertEquals("settings__u2", UserFiles.scoped("settings", 2))
        assertEquals("history__u2.json", UserFiles.scoped("history.json", 2))
        assertEquals("library__u12.db", UserFiles.scoped("library.db", 12))
    }

    @Test fun findsTheSharedNameAndTheUser() {
        assertEquals("history.json", UserFiles.base("history__u2.json"))
        assertEquals("settings", UserFiles.base("settings__u7"))
        assertEquals("settings", UserFiles.base("settings"))
        assertEquals(7, UserFiles.userOf("settings__u7"))
        assertEquals(0, UserFiles.userOf("history.json"))
    }

    @Test fun newUsersGetTheNextId() {
        assertEquals(1, UserFiles.nextId(listOf(0)))
        assertEquals(4, UserFiles.nextId(listOf(0, 3)))
    }

    @Test fun readsTheStoredList() {
        assertEquals(listOf(0), UserFiles.parseIds(null))
        assertEquals(listOf(0), UserFiles.parseIds(""))
        assertEquals(listOf(0, 2), UserFiles.parseIds("2,0,2"))
        // The first user can be removed too.
        assertEquals(listOf(2), UserFiles.parseIds("2"))
    }

    @Test fun labels() {
        assertEquals("anna on music.example.com", UserFiles.label("anna", "https://music.example.com"))
        assertEquals("anna", UserFiles.label("anna", ""))
        assertEquals("New user, not connected yet", UserFiles.label("", ""))
    }

    @Test fun backupsTreatEveryUserAlike() {
        assertTrue(BackupRules.isSecret("settings__u2", "nd_pass"))
        assertFalse(BackupRules.isSecret("settings__u2", "nd_user"))
        assertTrue(BackupRules.includesPrefs("settings__u2"))
        assertFalse(BackupRules.includesPrefs("artist_suggestions__u2"))
        assertTrue(BackupRules.includesFile("history__u2.json"))
        assertFalse(BackupRules.includesFile("pending_scrobbles__u2.json"))
    }
}
