package app.siansa.data

import app.siansa.covers.CoverVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Artists' pictures (the owner, 10 Nov): Navidrome sends a placeholder star for an artist until it has found their
 * photo when they're looked up, then a new, versioned picture id. Lists kept showing the star.
 */
class ArtistCoverIdsTest {
    private val fresh = mapOf("0OujfvOslCvfctvNIGOXFN" to "ar-0OujfvOslCvfctvNIGOXFN_1c3cdcecf2acf122")

    @Test fun anArtistLookedUpShowsTheirNewPictureEverywhere() {
        assertEquals(fresh.values.single(), freshArtistCover("ar-0OujfvOslCvfctvNIGOXFN", fresh))
        assertEquals(fresh.values.single(), freshArtistCover("ar-0OujfvOslCvfctvNIGOXFN_0", fresh))
        assertEquals("ar-other", freshArtistCover("ar-other", fresh))
        assertEquals("al-0OujfvOslCvfctvNIGOXFN", freshArtistCover("al-0OujfvOslCvfctvNIGOXFN", fresh))  // albums untouched
    }

    @Test fun aSyncKeepsThePictureFoundByLookingTheArtistUp() {
        val kept = "ar-abc_19a2f3c"
        assertEquals(kept, keptArtistCover(null, kept))
        assertEquals(kept, keptArtistCover("ar-abc", kept))
        // The server's own new version wins.
        assertEquals("ar-abc_19b0001", keptArtistCover("ar-abc_19b0001", kept))
        // Nothing kept, or nothing versioned kept: the server's.
        assertEquals("ar-abc", keptArtistCover("ar-abc", null))
        assertEquals("ar-abc", keptArtistCover("ar-abc", "ar-abc"))
    }

    @Test fun anArtistPictureIsKeptUnderItsOwnId() {
        // No weekly key any more: Navidrome's star is never kept (StandIns), so there's nothing to wait out.
        assertEquals("ar-abc", CoverVersions.cacheIdFor("ar-abc") { null })
        assertEquals("ar-abc_19a2f3c", CoverVersions.cacheIdFor("ar-abc_19a2f3c") { null })
    }
}
