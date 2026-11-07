package app.siansa.mix

import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An album's DJ mix in the order that blends best (issue #965). */
class AlbumDjMixTest {
    private fun t(id: String, bpm: Double?, key: String? = null) = DjTrack(id, "Artist", 1.0, bpm, key, if (key != null) "major" else null)

    @Test
    fun songsOfLikeTempoFollowEachOther() {
        val album = listOf(t("a", 90.0), t("b", 128.0), t("c", 92.0), t("d", 126.0), t("e", 91.0))
        val order = bestAlbumOrder(album)
        assertEquals(album.map { it.id }.sorted(), order.map { it.id }.sorted())
        // The slow ones together and the fast ones together: one jump in tempo, not four.
        val jumps = order.zipWithNext().count { (x, y) -> kotlin.math.abs(x.tempo!! - y.tempo!!) > 10 }
        assertEquals(1, jumps)
    }

    @Test
    fun keysOnTheWheelTogether() {
        val album = listOf(t("c", 120.0, "C"), t("fs", 120.0, "F#"), t("g", 120.0, "G"), t("cs", 120.0, "C#"))
        val order = bestAlbumOrder(album).map { it.id }
        // C and G are neighbours on the wheel, as are F# and C#: each pair follows on.
        val cg = kotlin.math.abs(order.indexOf("c") - order.indexOf("g")) == 1
        val fc = kotlin.math.abs(order.indexOf("fs") - order.indexOf("cs")) == 1
        assertTrue("$order", cg && fc)
    }

    @Test
    fun unmeasuredOrShortAlbumsKeepTheirOrder() {
        val unmeasured = listOf(t("1", null), t("2", null), t("3", null), t("4", null))
        assertEquals(unmeasured, bestAlbumOrder(unmeasured))
        val two = listOf(t("1", 140.0), t("2", 80.0))
        assertEquals(two, bestAlbumOrder(two))
    }

    @Test
    fun theMixStartsFromDifferentSongs() {
        val album = listOf(t("a", 90.0, "C"), t("b", 128.0, "G"), t("c", 92.0, "D"), t("d", 126.0, "A"), t("e", 91.0, "E"), t("f", 124.0, "B"))
        val starts = (1..60).map { seed -> pickAlbumOrder(album, kotlin.random.Random(seed)).first().id }.toSet()
        // Not always the same first song (the owner): at least three to pick from.
        assertTrue("$starts", starts.size >= 3)
        // Every pick is the whole album, each song once.
        (1..20).forEach { seed -> assertEquals(album.map { it.id }.sorted(), pickAlbumOrder(album, kotlin.random.Random(seed)).map { it.id }.sorted()) }
    }

    @Test
    fun theMixNeverStartsWhereItDidLastTime() {
        val album = listOf(t("a", 90.0), t("b", 128.0), t("c", 92.0), t("d", 126.0), t("e", 91.0))
        val last = pickAlbumOrder(album, kotlin.random.Random(1)).first().id
        (1..40).forEach { seed -> assertTrue(pickAlbumOrder(album, kotlin.random.Random(seed), avoidFirst = last).first().id != last) }
    }

    @Test
    fun theMixPicksOnlyAmongTheBestBlends() {
        // Two slow songs and two fast: the picks are among the orders with one tempo jump where there are enough.
        val album = listOf(t("a", 90.0), t("b", 128.0), t("c", 92.0), t("d", 126.0), t("e", 91.0))
        val bestScore = flowScore(bestAlbumOrder(album))
        (1..30).forEach { seed ->
            val pick = pickAlbumOrder(album, kotlin.random.Random(seed))
            val ranked = albumOrders(album).map { flowScore(it) }
            assertTrue(flowScore(pick) >= ranked.take(5).last() - 1e-9 && flowScore(pick) <= bestScore + 1e-9)
        }
    }

    private fun songs(vararg ids: String) = ids.map { Song(id = it, title = it) }

    @Test
    fun albumPagesPlayKeepsItsGroovesOnlyWhileItsMixPlays() {
        val album = songs("a", "b", "c")
        val mix = listOf("c", "a", "b")
        // The mix playing, with songs keep playing added after it.
        assertTrue(albumMixPlaying(songs("c", "a", "b", "x"), album, mix))
        // The album played in its own order, or shuffled another way: not the mix.
        assertFalse(albumMixPlaying(songs("a", "b", "c"), album, mix))
        // Something else, nothing, or another album's page.
        assertFalse(albumMixPlaying(songs("x", "y"), album, mix))
        assertFalse(albumMixPlaying(emptyList(), album, mix))
        assertFalse(albumMixPlaying(songs("c", "a", "b"), songs("d", "e", "f"), mix))
        assertFalse(albumMixPlaying(songs("c", "a", "b"), album, emptyList()))
    }
}
