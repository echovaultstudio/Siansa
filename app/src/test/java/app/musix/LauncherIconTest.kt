package app.musix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot
import kotlin.math.sqrt

/** The launcher icon (issues #54, #407): sound bars with echo rings, centred and inside the circle every launcher shows. */
class LauncherIconTest {
    private data class Bar(val left: Double, val top: Double, val right: Double, val bottom: Double)
    private data class Ring(val x1: Double, val y1: Double, val r: Double, val x2: Double, val y2: Double, val stroke: Double)

    // A bar: "Mx,ya r,r 0 0,1 w,0v h a r,r 0 0,1 -w,0z", round caps above and below.
    private val barPath = Regex("""M([\d.]+),([\d.]+)a([\d.]+),[\d.]+ 0 0,1 ([\d.]+),0v([\d.]+)a[\d.]+,[\d.]+ 0 0,1 -[\d.]+,0z""")
    // An echo ring: "Mx,yA r,r 0 0,s x,y" with its stroke width.
    private val ringPath = Regex("""M([\d.]+),([\d.]+)A([\d.]+),[\d.]+ 0 0,[01] ([\d.]+),([\d.]+)"[^/]*?strokeWidth="([\d.]+)"""")

    // Unit tests run from the module folder (app/).
    private fun xml(name: String) = File("src/main/res/drawable/$name.xml").readText()

    private fun bars(name: String): List<Bar> = barPath.findAll(xml(name)).map {
        val (x, y, r, w, h) = it.destructured
        Bar(x.toDouble(), y.toDouble() - r.toDouble(), x.toDouble() + w.toDouble(), y.toDouble() + h.toDouble() + r.toDouble())
    }.toList()

    private fun rings(name: String): List<Ring> = ringPath.findAll(xml(name)).map {
        val v = it.groupValues.drop(1).map(String::toDouble)
        Ring(v[0], v[1], v[2], v[3], v[4], v[5])
    }.toList()

    @Test fun barsAreCentredSideToSide() {
        val bars = bars("ic_launcher_foreground")
        assertEquals(5, bars.size)
        assertEquals(54.0, (bars.minOf { it.left } + bars.maxOf { it.right }) / 2, 0.05)
    }

    @Test fun barsDoNotSitLow() {
        val bars = bars("ic_launcher_foreground")
        val middle = (bars.minOf { it.top } + bars.maxOf { it.bottom }) / 2
        // Bars sharing a bottom line look heavy at the bottom, so the group sits slightly above the centre.
        assertTrue("bars' middle is $middle", middle in 50.0..54.0)
    }

    @Test fun echoRingsSitEvenlyOnBothSides() {
        val rings = rings("ic_launcher_foreground")
        assertEquals(4, rings.size)
        for (ring in rings) {
            val twin = rings.single { it !== ring && it.r == ring.r }
            assertEquals("${ring.x1} mirrors ${twin.x1}", 108.0, ring.x1 + twin.x1, 0.05)
        }
    }

    @Test fun everythingStaysInsideTheVisibleCircle() {
        // Launchers show at least the middle 66dp circle of the 108dp canvas.
        for (bar in bars("ic_launcher_foreground")) {
            for ((x, y) in listOf(bar.left to bar.top, bar.right to bar.top, bar.left to bar.bottom, bar.right to bar.bottom)) {
                assertTrue("bar corner ($x,$y)", hypot(x - 54, y - 54) <= 33)
            }
        }
        for (ring in rings("ic_launcher_foreground")) {
            // The arc bulges out from its chord: its furthest point is the chord's middle pushed out by the sagitta.
            val half = hypot(ring.x2 - ring.x1, ring.y2 - ring.y1) / 2
            val sagitta = ring.r - sqrt(ring.r * ring.r - half * half)
            val outer = hypot(ring.x1 - 54, 0.0) + sagitta + ring.stroke / 2
            val ends = hypot(ring.x1 - 54, ring.y1 - 54) + ring.stroke / 2
            assertTrue("ring reaches $outer from the centre", outer <= 33)
            assertTrue("ring ends reach $ends from the centre", ends <= 33)
        }
    }

    @Test fun themedIconMatchesTheColouredOne() {
        assertEquals(bars("ic_launcher_foreground"), bars("ic_launcher_monochrome"))
        assertEquals(rings("ic_launcher_foreground"), rings("ic_launcher_monochrome"))
    }
}
