package app.siansa

import app.siansa.data.BackupRules
import app.siansa.data.UserFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun aNamedServerShowsItsName() {
        assertEquals("max on Home Navidrome", UserFiles.label("max", "http://192.168.1.106:4533", "Home Navidrome"))
        assertEquals("max on 192.168.1.106", UserFiles.label("max", "http://192.168.1.106:4533", " "))
        assertEquals("max on 192.168.1.106", UserFiles.label("max", "http://192.168.1.106:4533", null))
    }

    @Test fun serverNamesAreKeptByAddressAndCleaned() {
        // Emby and Navidrome on one IP are two servers: their ports tell them apart.
        val navidrome = UserFiles.serverKey("http://192.168.1.106:4533/")!!
        val emby = UserFiles.serverKey("http://192.168.1.106:8096")!!
        val names = mapOf(navidrome to "  Home\n Navidrome ", emby to "Emby", "x" to "   ")
        val stored = UserFiles.formatServerNames(names)
        assertEquals(mapOf(navidrome to "Home Navidrome", emby to "Emby"), UserFiles.parseServerNames(stored))
        assertEquals(navidrome, UserFiles.serverKey("http://192.168.1.106:4533"))
        // Odd entries are skipped; long names are cut.
        assertEquals(emptyMap<String, String>(), UserFiles.parseServerNames(setOf("no name", "\nonly a name")))
        assertEquals(UserFiles.SERVER_NAME_MAX, UserFiles.cleanServerName("a".repeat(100)).length)
    }

    @Test fun eachUserNamesTheirOwnServer() {
        val server = UserFiles.serverKey("http://192.168.1.106:4533")!!
        val servers = mapOf(0 to server, 3 to server)
        // max names it "Kids": kids, on the same server, keeps the address.
        val (byUser, byServer) = UserFiles.withUserName(0, "Kids", servers, emptyMap(), emptyMap())
        assertEquals("Kids", UserFiles.nameFor(0, server, byUser, byServer))
        assertNull(UserFiles.nameFor(3, server, byUser, byServer))
        // Then kids names it "My server": max keeps "Kids".
        val (byUser2, byServer2) = UserFiles.withUserName(3, "My server", servers, byUser, byServer)
        assertEquals("Kids", UserFiles.nameFor(0, server, byUser2, byServer2))
        assertEquals("My server", UserFiles.nameFor(3, server, byUser2, byServer2))
        // A blank name shows the address again, for that user only.
        val (byUser3, _) = UserFiles.withUserName(0, "  ", servers, byUser2, byServer2)
        assertNull(UserFiles.nameFor(0, server, byUser3, byServer2))
        assertEquals("My server", UserFiles.nameFor(3, server, byUser3, byServer2))
    }

    @Test fun aNameGivenToTheWholeServerBeforeStaysWithEachUser() {
        val server = UserFiles.serverKey("http://192.168.1.106:4533")!!
        val other = UserFiles.serverKey("http://192.168.1.106:8096")!!
        val servers = mapOf(0 to server, 3 to server, 5 to other)
        val before = mapOf(server to "Kids", other to "Emby")
        // Read as before until someone renames.
        assertEquals("Kids", UserFiles.nameFor(3, server, emptyMap(), before))
        val (byUser, byServer) = UserFiles.withUserName(0, "Home", servers, emptyMap(), before)
        assertEquals("Home", UserFiles.nameFor(0, server, byUser, byServer))
        assertEquals("Kids", UserFiles.nameFor(3, server, byUser, byServer))
        // Another server's name is left alone.
        assertEquals(mapOf(other to "Emby"), byServer)
        assertEquals("Emby", UserFiles.nameFor(5, other, byUser, byServer))
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
