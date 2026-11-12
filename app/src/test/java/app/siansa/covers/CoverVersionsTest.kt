package app.siansa.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Disc covers are cached per album cover version, so a cover replaced on the server shows everywhere (issue #639). */
class CoverVersionsTest {
    private val stamps = mapOf("4wvRmqilsb4YPaG2OBUd7X" to "19a2f3c")

    @Test fun aDiscCoverWithoutAStampTakesItsAlbumsVersion() {
        // The id from report 6db43bb4: the mini player's cover.
        assertEquals("dc-4wvRmqilsb4YPaG2OBUd7X:1@19a2f3c", CoverVersions.cacheIdFor("dc-4wvRmqilsb4YPaG2OBUd7X:1") { stamps[it] })
    }

    @Test fun replacingTheAlbumCoverChangesTheDiscCoversKey() {
        val before = CoverVersions.cacheIdFor("dc-4wvRmqilsb4YPaG2OBUd7X:1") { "19a2f3c" }
        val after = CoverVersions.cacheIdFor("dc-4wvRmqilsb4YPaG2OBUd7X:1") { "19b0001" }
        assertTrue(before != after)
    }

    @Test fun idsWithTheirOwnVersionOrNoAlbumStayAsTheyAre() {
        assertEquals("al-4wvRmqilsb4YPaG2OBUd7X_19a2f3c", CoverVersions.cacheIdFor("al-4wvRmqilsb4YPaG2OBUd7X_19a2f3c") { stamps[it] })
        assertEquals("dc-4wvRmqilsb4YPaG2OBUd7X:1_19a2f3c", CoverVersions.cacheIdFor("dc-4wvRmqilsb4YPaG2OBUd7X:1_19a2f3c") { stamps[it] })
        assertEquals("mf-abc", CoverVersions.cacheIdFor("mf-abc") { stamps[it] })
        assertEquals("dc-unknown:1", CoverVersions.cacheIdFor("dc-unknown:1") { stamps[it] })  // album not in the library
    }

    @Test fun stampsAreReadFromTheAlbumsCoverIds() {
        assertEquals("19a2f3c", CoverVersions.stampOf("al-123_19a2f3c"))
        assertNull(CoverVersions.stampOf("al-123"))
        assertNull(CoverVersions.stampOf(null))
    }

    @Test fun theLiveTableIsUsedOnceUpdated() {
        CoverVersions.update(mapOf("A1" to "al-A1_7", "A2" to null))
        assertEquals("dc-A1:2@7", CoverVersions.cacheId("dc-A1:2"))
        assertEquals("dc-A2:1", CoverVersions.cacheId("dc-A2:1"))
        CoverVersions.update(emptyMap())
    }

    @Test fun anArtistsIdWithoutAVersionIsKeptUnderItself() {
        // Its key no longer changes by the week: Navidrome's star is never kept, so there's nothing to wait out.
        assertEquals("ar-6mWOWUK18NNBIh4Nk9xNvC", CoverVersions.cacheId("ar-6mWOWUK18NNBIh4Nk9xNvC"))
    }

    // ---- Emby and Jellyfin: a picture's tag (the owner, 11 Nov) ----

    @Test fun anItemSeenWithANewTagHasItsCoversFetchedFresh() {
        CoverVersions.forgetTags()
        val changed = mutableListOf<String>()
        CoverVersions.onChanged = { changed += it }
        try {
            CoverVersions.seen("52131", "tag-1")   // the first time: nothing kept to doubt
            CoverVersions.seen("52131", "tag-1")
            CoverVersions.seen(null, "tag-1")
            CoverVersions.seen("52132", null)
            assertEquals(emptyList<String>(), changed)
            CoverVersions.seen("52131", "tag-2")   // the cover was replaced on the server
            assertEquals(listOf("52131"), changed)
        } finally {
            CoverVersions.onChanged = null
            CoverVersions.forgetTags()
        }
    }

    @Test fun tagsAreKeptFromOneRunToTheNext() {
        CoverVersions.forgetTags()
        CoverVersions.seen("52131", "tag-1")
        CoverVersions.seen("6f1df514408a4c898b01a0b305887968", "tag-9")
        val text = CoverVersions.dump()!!
        assertNull("nothing new to save", CoverVersions.dump())

        // The next run: the tags are read before the first item is seen, so a change while the app was closed is noticed.
        CoverVersions.forgetTags()
        val changed = mutableListOf<String>()
        CoverVersions.kept = { text }
        CoverVersions.onChanged = { changed += it }
        try {
            CoverVersions.seen("6f1df514408a4c898b01a0b305887968", "tag-9")
            CoverVersions.seen("52131", "tag-2")
            assertEquals(listOf("52131"), changed)
        } finally {
            CoverVersions.kept = null
            CoverVersions.onChanged = null
            CoverVersions.forgetTags()
        }
    }
}
