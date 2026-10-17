package app.musix

import app.musix.data.Playlist
import app.musix.data.PlaylistEditor
import app.musix.net.NavidromeNativeApi
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
}
