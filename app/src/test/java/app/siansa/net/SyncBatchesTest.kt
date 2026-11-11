package app.siansa.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

/** AudioMuse's /api/sync asked in batches short enough for its web server (the owner's log, 8 Nov). */
class SyncBatchesTest {
    private fun sent(batch: List<String>) = batch.joinToString("%2C") { URLEncoder.encode(it, "UTF-8") }

    @Test fun aBigPlaylistIsAskedInBatchesWithinTheBudget() {
        // 251 Navidrome ids (22 characters): one request was 6 KB and AudioMuse answered HTTP 400.
        val ids = (1..251).map { "%022d".format(it) }
        val batches = AudioMuseApi.syncBatches(ids)
        assertTrue(batches.size > 1)
        assertEquals(ids, batches.flatten())
        batches.forEach { assertTrue(sent(it).length <= AudioMuseApi.SYNC_IDS_BUDGET) }
    }

    @Test fun anAlbumIsOneRequest() {
        val ids = (1..14).map { "tr-$it" }
        assertEquals(listOf(ids), AudioMuseApi.syncBatches(ids))
        assertEquals(emptyList<List<String>>(), AudioMuseApi.syncBatches(emptyList()))
    }

    @Test fun neverMoreThanTheBatchSize() {
        val ids = (1..1200).map { "$it" }
        val batches = AudioMuseApi.syncBatches(ids, budget = 1_000_000)
        assertEquals(listOf(500, 500, 200), batches.map { it.size })
    }

    @Test fun everyServersIdsFit() {
        for (server in app.siansa.servers.EveryServer.all) {
            val ids = (1..300).map { "${server.songId}$it" }
            AudioMuseApi.syncBatches(ids).forEach { assertTrue(server.name, sent(it).length <= AudioMuseApi.SYNC_IDS_BUDGET) }
        }
    }
}
