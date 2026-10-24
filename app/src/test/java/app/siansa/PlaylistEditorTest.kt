package app.siansa

import app.siansa.data.Playlist
import app.siansa.data.PlaylistEditor
import app.siansa.data.RemovedPlaylists
import app.siansa.net.NavidromeNativeApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which playlists you can change, and the names and pictures sent to Navidrome. */
class PlaylistEditorTest {
    @Test
    fun navidromesReadonlyDecides() {
        assertFalse(PlaylistEditor.canEdit(Playlist("1", "Smart", owner = "max", readonly = true), "max"))
        assertTrue(PlaylistEditor.canEdit(Playlist("2", "Shared with me", owner = "ratty", readonly = false), "max"))
    }

    @Test
    fun withoutReadonlyTheOwnerDecides() {
        assertTrue(PlaylistEditor.canEdit(Playlist("1", "Mine", owner = "Max"), "max"))
        assertTrue(PlaylistEditor.canEdit(Playlist("2", "No owner"), "max"))
        assertFalse(PlaylistEditor.canEdit(Playlist("3", "Ratty's", owner = "ratty"), "max"))
    }

    @Test
    fun namesAreTrimmedAndNeverEmpty() {
        assertEquals("Road trip", PlaylistEditor.cleanName("  Road trip "))
        assertNull(PlaylistEditor.cleanName("   "))
        assertEquals(PlaylistEditor.MAX_NAME, PlaylistEditor.cleanName("a".repeat(300))?.length)
    }

    @Test
    fun pictureFileNamesFollowTheirType() {
        assertEquals(".png", NavidromeNativeApi.extensionOf("image/png"))
        assertEquals(".webp", NavidromeNativeApi.extensionOf("IMAGE/WEBP"))
        assertEquals(".jpg", NavidromeNativeApi.extensionOf("image/jpeg"))
        assertEquals(".jpg", NavidromeNativeApi.extensionOf("image/heic"))
    }

    @Test
    fun removedPlaylistsAreHiddenButOthersStay() {
        val lists = listOf(Playlist("1", "Keep"), Playlist("2", "Gone"), Playlist("3", "New on the server"))
        assertEquals(listOf("Keep", "New on the server"), RemovedPlaylists.hide(lists, setOf("2")).map { it.name })
        assertEquals(lists, RemovedPlaylists.hide(lists, emptySet()))
    }
}
