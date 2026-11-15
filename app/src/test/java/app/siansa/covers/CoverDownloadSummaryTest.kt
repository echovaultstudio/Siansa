package app.siansa.covers

import app.siansa.ui.LARGE_COVER
import app.siansa.ui.SMALL_COVER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Download all covers keeps and says (the owner, 14 Nov: song covers had only their small size). */
class CoverDownloadSummaryTest {
    @Test fun everyCoverIsKeptAtBothSizes() {
        assertEquals(listOf(SMALL_COVER, LARGE_COVER), CoverDownload.SIZES)
    }

    @Test fun coversWithNoPictureAreNotCalledAlreadyHere() {
        assertEquals(
            "34 downloaded, 12107 already here, 295 with no picture on the server in 9s",
            CoverDownload.summary(downloaded = 34, had = 12107, none = 295, failed = 0, seconds = 9),
        )
        assertEquals("0 downloaded, 10 already here, 2 failed in 1s", CoverDownload.summary(0, 10, 0, 2, 1))
    }

    @Test fun animationsDownloadedAreSaid() {
        assertEquals("14 downloaded (12 of them animations), 9 already here in 300s", CoverDownload.summary(14, 9, 0, 0, 300, animations = 12))
    }

    /** The owner, 15 Nov: with 6000 albums, some animated at over 100 MB, only stills of the big ones were kept. */
    @Test fun anAlbumsAnimationComesWhenOnlyItsStillIsHere() {
        assertTrue(CoverDownload.bringsAnimation("al-5AGucgFjtno5hRWWydbikV_67822602a9de827d", animations = true, onlyStill = true))
        // Already whole on the phone, or a plain picture: nothing to bring.
        assertFalse(CoverDownload.bringsAnimation("al-5AGucgFjtno5hRWWydbikV", animations = true, onlyStill = false))
        // Animated covers switched off: stills are all that's shown.
        assertFalse(CoverDownload.bringsAnimation("al-5AGucgFjtno5hRWWydbikV", animations = false, onlyStill = true))
        // A song's, a disc's or an artist's picture stays a still: the album's is what moves.
        for (id in listOf("mf-abc_1", "dc-5AGucgFjtno5hRWWydbikV:1", "ar-xyz", "pl-1")) {
            assertFalse(id, CoverDownload.bringsAnimation(id, animations = true, onlyStill = true))
        }
    }

    /** The owner, 15 Nov: "it should fetch ALL". Big Mountain is on one compilation and has no album of their own. */
    @Test fun artistsOnlyCreditedOnSongsAreFetchedToo() {
        fun song(id: String, artist: String, artistId: String?, vararg credits: Pair<String, String>) = app.siansa.data.Song(
            id = id, title = "T$id", artist = artist, artistId = artistId, artists = credits.map { app.siansa.data.ArtistRef(it.first, it.second) },
        )
        val songs = listOf(
            song("1", "Big Mountain", "big"),
            song("2", "Fred V & Grafix feat. Amy J Pryce", "fred", "fred" to "Fred V & Grafix", "amy" to "Amy J Pryce"),
            song("3", "Known", "known"),
            song("4", "Big Mountain", "big"),
            song("5", "A feat. B", "ab"),
            song("6", "Nobody", null),
        )
        val all = app.siansa.data.Credits.everyone(songs, known = setOf("known", "fred"))
        assertEquals(listOf("big", "amy", "ab"), all.map { it.id })
        assertEquals("Big Mountain", all[0].name)
        assertEquals("Amy J Pryce", all[1].name)
        // "A feat. B" under one id isn't one artist's name; the id is still looked up.
        assertEquals("", all[2].name)
        assertTrue(app.siansa.data.Credits.everyone(songs, known = setOf("known", "fred", "big", "amy", "ab")).isEmpty())
    }

    @Test fun artistsStillBeingFetchedAreAskedForAgainTwice() {
        assertEquals(2, CoverDownload.ARTISTS_AGAIN_MS.size)
        assertTrue(CoverDownload.ARTISTS_AGAIN_MS[0] < CoverDownload.ARTISTS_AGAIN_MS[1])
    }

    /**
     * On every server (the owner, 15 Nov): only Navidrome sends a stand-in while it fetches an artist's photo, so only
     * there is a photo waited for; Emby and Jellyfin answer a missing picture with 404, which is final. Artists only
     * credited on songs are found by their ids in each server's own style, and their pictures have an address on each.
     */
    @Test fun everyArtistIsFetchedOnEveryServer() {
        for (server in app.siansa.servers.EveryServer.all) {
            val artist = if (server.name == "Navidrome") "4pEiOs5wj3RTq6LVJaxSj0" else server.songId
            // The picture's id as the run asks for it, and its address on this server.
            val address = CoverAddress.of(server.source().coverUrl("ar-$artist", 300))
            assertEquals("$server", 300, address?.size)
            val path = okhttp3.HttpUrl.Companion.run { server.source().coverUrl("ar-$artist", 300).toHttpUrl() }.encodedPath
            // "Don't keep" with no version: Navidrome's stand-in. The same headers from Emby or Jellyfin aren't one.
            val standIn = StandIns.isStandIn(path, "no-store", null)
            assertEquals("$server sends a stand-in", server.name == "Navidrome", standIn)
            assertEquals("$server: the photo is waited for", server.name == "Navidrome", CoverDownload.waitsForPhoto("ar-$artist", standIn))
            // Never for an album's or a song's cover, on any server.
            assertFalse("$server", CoverDownload.waitsForPhoto("al-$artist", standIn))
            assertFalse("$server", CoverDownload.waitsForPhoto(server.songId, standIn && server.name != "Navidrome"))
            // An artist only credited on a song is found whatever the ids look like.
            val song = app.siansa.data.Song(id = server.songId, title = "T", artist = "Big Mountain", artistId = artist)
            assertEquals("$server", listOf(artist), app.siansa.data.Credits.everyone(listOf(song), emptySet()).map { it.id })
            // Animated originals are Navidrome's matter: only its album ids ("al-…") ask for one.
            assertEquals("$server", server.name == "Navidrome", CoverDownload.bringsAnimation(if (server.name == "Navidrome") "al-$artist" else artist, animations = true, onlyStill = true))
        }
    }
}
