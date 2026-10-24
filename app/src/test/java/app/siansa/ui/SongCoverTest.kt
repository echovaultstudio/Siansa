package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Now playing shows a song's own cover when it differs from its album's (issue #358). */
class SongCoverTest {
    private val still = AnimatedCovers.Kind.Still
    private val animated = AnimatedCovers.Kind.Animated(300)
    private val local = AnimatedCovers.Kind.Local

    @Test fun aSongOrDiscWithItsOwnPictureIsCheckedFirst() {
        assertEquals("mf-abc_123", bigCoverKey("mf-abc_123", "alb1"))
        assertEquals("dc-alb1:2_9", bigCoverKey("dc-alb1:2_9", "alb1"))
    }

    @Test fun otherwiseTheAlbumsCover() {
        assertEquals("al-alb1", bigCoverKey("al-alb1_5", "alb1"))
        assertEquals("al-alb1", bigCoverKey(null, "alb1"))
        assertEquals("al-alb1", bigCoverKey(null, "al-alb1"))
        assertEquals("pl-1", bigCoverKey("pl-1", null))
        assertEquals(null, bigCoverKey(null, null))
    }

    @Test fun aDifferentPictureShowsTheSongsOwn() {
        assertEquals("mf-1" to still, pickBigCover("mf-1", still, "al-a", animated, same = false))
        assertEquals("mf-1" to still, pickBigCover("mf-1", still, "al-a", still, same = null))
    }

    @Test fun theAlbumsPictureAgainKeepsItsAnimation() {
        assertEquals("al-a" to animated, pickBigCover("mf-1", still, "al-a", animated, same = true))
        assertEquals("al-a" to local, pickBigCover("dc-a:2", still, "al-a", local, same = true))
    }

    @Test fun notComparedYetShowsTheSongsOwnMeanwhile() {
        assertEquals("mf-1" to still, pickBigCover("mf-1", still, "al-a", animated, same = null))
    }

    @Test fun anAnimatedSongCoverAnimatesItself() {
        assertEquals("dc-a:1" to local, pickBigCover("dc-a:1", local, "al-a", animated, same = true))
    }

    @Test fun samePictureAfterResizingDifferentPictureNot() {
        val gradient = IntArray(72) { i -> (i % 9) * 28 }
        val brighter = IntArray(72) { i -> ((i % 9) * 28 + 6).coerceAtMost(255) }
        val reversed = IntArray(72) { i -> (8 - i % 9) * 28 }
        val a = CoverMatch.differenceHash(gradient)
        assertTrue(CoverMatch.samePicture(a, CoverMatch.differenceHash(brighter)))
        assertFalse(CoverMatch.samePicture(a, CoverMatch.differenceHash(reversed)))
    }

    @Test fun greyWeighsGreenMost() {
        val (red, green, blue) = CoverMatch.grey(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())).toList()
        assertTrue(green > red && red > blue)
        assertEquals(255, CoverMatch.grey(intArrayOf(0xFFFFFFFF.toInt()))[0])
    }
}
