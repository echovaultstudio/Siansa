package app.musix.covers

import org.junit.Assert.assertEquals
import org.junit.Test

/** The cover download also fetches the cover ids Navidrome gives songs (issue #201). */
class SongCoverDownloadTest {
    private val albums = listOf("al-54xd1KWT9oO6pSAgFAvXBq_68f1a2b3", "ar-1MKHOmT1wKJxNgWWYZqzj6")

    @Test fun discAndSongCoversAsNavidromeSendsThem() {
        val songs = listOf("dc-54xd1KWT9oO6pSAgFAvXBq:1", "dc-4DgpLwJJp8eMyx6vZMBfdq:3", "mf-6u0xgGu8UpYMh4vYyxjdf9_68f1a2b3")
        assertEquals(songs, CoverDownload.songCoversToFetch(songs, albums))
    }

    @Test fun eachOnceAndNotWhatsAlreadyFetched() {
        val songs = listOf(
            "dc-4DgpLwJJp8eMyx6vZMBfdq:1",
            "dc-4DgpLwJJp8eMyx6vZMBfdq:1",
            // A song that shows its album's cover id: fetched with the albums already.
            "al-54xd1KWT9oO6pSAgFAvXBq_68f1a2b3",
            "al-54xd1KWT9oO6pSAgFAvXBq_70000000",
            "",
        )
        assertEquals(listOf("dc-4DgpLwJJp8eMyx6vZMBfdq:1"), CoverDownload.songCoversToFetch(songs, albums))
    }
}
