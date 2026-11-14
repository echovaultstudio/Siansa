package app.siansa.covers

import app.siansa.servers.EveryServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server's stand-in for a cover it doesn't have right now is shown but never kept, and asked for again (the owner,
 * 11 Nov: the Kids library showed Navidrome's blue disc in lists while Now playing had the cover). The answers below
 * are what a real Navidrome 0.64.2 sent.
 */
class StandInsTest {
    private val cover = "http://192.168.32.129:4533/rest/getCoverArt?u=max&t=abc&s=def&id=al-05XDpZn1INikOzctTJt7DT&size=300"

    @After fun forget() {
        StandIns.forget()
        StandIns.onLearned = null
    }

    @Test fun navidromesPlaceholderIsAStandIn() {
        // An album without a picture: HTTP 200, image/webp, "don't keep", no version.
        assertTrue(StandIns.isStandIn(cover, "no-store", null))
        assertTrue(StandIns.isStandIn("/rest/getCoverArt.view", "No-Store", ""))
    }

    @Test fun aRealCoverIsNot() {
        // With its version in the id, and without: both carry an ETag.
        assertFalse(StandIns.isStandIn(cover, "public, max-age=31536000, immutable", "\"c35219b6e4fb56ff.300.false.q75\""))
        assertFalse(StandIns.isStandIn(cover, "public, no-cache", "\"c35219b6e4fb56ff\""))
        assertFalse(StandIns.isStandIn(cover, null, null))
    }

    @Test fun aProxyThatMarksEverythingDoesNotStopCoversBeingKept() {
        assertFalse(StandIns.isStandIn(cover, "no-store", "\"c35219b6e4fb56ff\""))
    }

    @Test fun onlySubsonicCoversHaveStandIns() {
        // Emby and Jellyfin answer a missing picture with 404; nothing of theirs is ever taken for a stand-in.
        assertFalse(StandIns.isStandIn("/Items/52131/Images/Primary", "no-store", null))
        assertFalse(StandIns.isStandIn("/rest/stream", "no-store", null))
    }

    @Test fun aStandInIsAskedForAgainSoonThenAfterAWhile() {
        // The first time: after 5 s (the owner, 12 Nov: a song's picture the server has, sent as a stand-in under load).
        StandIns.shown("al-1", now = 1_000)
        assertEquals(StandIns.SOON_MS[0], StandIns.dueIn("al-1", now = 1_000))
        assertFalse("still fresh", StandIns.askAgain("al-1", now = 1_000 + StandIns.SOON_MS[0] - 1))
        assertTrue(StandIns.askAgain("al-1", now = 1_000 + StandIns.SOON_MS[0]))
        assertFalse("only once: the new answer decides", StandIns.askAgain("al-1", now = 1_000 + 2 * StandIns.AGAIN_MS))
        assertEquals(null, StandIns.dueIn("al-1", now = 2_000))
        // Still a stand-in: 30 s, then two minutes each time.
        StandIns.shown("al-1", now = 10_000)
        assertFalse(StandIns.askAgain("al-1", now = 10_000 + StandIns.SOON_MS[1] - 1))
        assertTrue(StandIns.askAgain("al-1", now = 10_000 + StandIns.SOON_MS[1]))
        StandIns.shown("al-1", now = 50_000)
        assertFalse(StandIns.askAgain("al-1", now = 50_000 + StandIns.AGAIN_MS - 1))
        assertTrue(StandIns.askAgain("al-1", now = 50_000 + StandIns.AGAIN_MS))
        // The real picture came: the next stand-in (a sync later) starts over at 5 s.
        StandIns.real("al-1")
        StandIns.shown("al-1", now = 90_000)
        assertTrue(StandIns.askAgain("al-1", now = 90_000 + StandIns.SOON_MS[0]))
        assertFalse("a cover that never showed one", StandIns.askAgain("al-2", now = Long.MAX_VALUE))
    }

    @Test fun afterASyncEveryStandInShowingIsAskedForAgain() {
        StandIns.shown("al-1", now = 1_000)
        StandIns.shown("ar-9", now = 1_000)
        assertEquals(setOf("al-1", "ar-9"), StandIns.askAllAgain())
        assertFalse(StandIns.askAgain("al-1", now = Long.MAX_VALUE))
    }

    @Test fun everyPlaceholderNavidromeShippedIsKnown() {
        assertEquals(5, StandIns.shipped.size)
        // Navidrome 0.64.2's blue disc and artist's star, as it sent them.
        assertTrue(StandIns.known(69228) { "273a4dbd61dfbb0a12d5d8ffe780eb7a3d4d000bc9771c5411cc70ae4dfa8a1f" })
        assertTrue(StandIns.known(546) { "521259c652af6d43cb124876106258d93b6b6f7fbe7ad2c73e8345875afe4dcf" })
        assertFalse("a cover that happens to be as big", StandIns.known(69228) { "0".repeat(64) })
    }

    @Test fun aPictureOfAnotherSizeIsNeverHashed() {
        assertFalse(StandIns.known(11647) { error("not read") })
    }

    @Test fun aStandInMetIsLearnedOnceAndKeptForTheNextRun() {
        val heard = mutableListOf<Set<StandIns.Print>>()
        StandIns.onLearned = { heard += it }
        val picture = ByteArray(1234) { it.toByte() }
        StandIns.learn(picture)
        StandIns.learn(picture)
        assertEquals(1, heard.size)
        assertTrue(StandIns.known(1234) { StandIns.sha256(picture) })
        assertTrue(1234L in StandIns.sizes())

        val kept = heard.single().map { it.toString() }
        StandIns.forget()
        assertFalse(StandIns.known(1234) { StandIns.sha256(picture) })
        StandIns.restore(kept + "rubbish")
        assertTrue(StandIns.known(1234) { StandIns.sha256(picture) })
    }

    @Test fun aCoversIdAndSizeAreReadFromItsAddressOnEveryServer() {
        for (server in EveryServer.all) {
            val id = if (server.name == "Navidrome") "dc-4wvRmqilsb4YPaG2OBUd7X:1" else server.songId
            assertEquals("$server at 300", CoverAddress(id, 300), CoverAddress.of(server.source().coverUrl(id, 300)))
            assertEquals("$server, the original", CoverAddress(id, null), CoverAddress.of(server.source().coverUrl(id, null)))
            assertNull("$server: a song isn't a cover", CoverAddress.of(server.source().streamUrl(server.songId)))
        }
    }

    @Test fun aCoverIsKeptUnderTheSameKeyWhateverTheSignIn() {
        // The notification and the car read the id from the address: two sign-ins, one entry (issue #903).
        for (server in EveryServer.all) {
            val keys = listOf(1, 2).map { login ->
                val address = CoverAddress.of(server.source(login).coverUrl(server.songId, 300))!!
                CoverCache.diskKey(address.id, address.size)
            }
            assertEquals("$server", "cover:${server.songId}:300", keys[0])
            assertEquals("$server", keys[0], keys[1])
        }
    }

    @Test fun aDiskKeyNamesItsCover() {
        assertEquals("dc-1:2@7", CoverCache.cacheIdOf("cover:dc-1:2@7:300"))
        assertEquals("al-9_abc", CoverCache.cacheIdOf("cover:al-9_abc:full"))
    }

    @Test fun onlyIdsWithoutAVersionHaveTheirEtagKept() {
        CoverEtags.forget()
        CoverEtags.put("cover:al-9_abc:300", "\"x\"")
        CoverEtags.put("cover:dc-1:2@7:300", "\"x\"")
        CoverEtags.put("cover:ar-5:300", "\"y\"")
        assertNull(CoverEtags.get("cover:al-9_abc:300"))
        assertNull(CoverEtags.get("cover:dc-1:2@7:300"))
        assertEquals("\"y\"", CoverEtags.get("cover:ar-5:300"))
        CoverEtags.remove("cover:ar-5:300")
        assertNull(CoverEtags.get("cover:ar-5:300"))
    }
}
