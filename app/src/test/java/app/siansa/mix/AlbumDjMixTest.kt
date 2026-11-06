package app.siansa.mix

import org.junit.Assert.assertEquals
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
}
