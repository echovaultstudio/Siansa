package app.musix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The sound bars in the launcher icon sit in the middle of its 108x108 canvas (issue #54). */
class LauncherIconTest {
    private data class Bar(val left: Double, val top: Double, val right: Double, val bottom: Double)

    // Each bar is a rounded rectangle: "Mx,ya3,3 0 0,1 6,0vHa3,3 0 0,1 -6,0z" (3dp round caps above and below).
    private val barPath = Regex("""M([\d.]+),([\d.]+)a3,3 0 0,1 6,0v([\d.]+)a3,3 0 0,1 -6,0z""")

    private fun bars(name: String): List<Bar> {
        // Unit tests run from the module folder (app/).
        val xml = File("src/main/res/drawable/$name.xml").readText()
        return barPath.findAll(xml).map {
            val (x, y, h) = it.destructured
            Bar(x.toDouble(), y.toDouble() - 3, x.toDouble() + 6, y.toDouble() + h.toDouble() + 3)
        }.toList()
    }

    @Test fun barsAreCentredSideToSide() {
        val bars = bars("ic_launcher_foreground")
        assertEquals(5, bars.size)
        assertEquals(54.0, (bars.minOf { it.left } + bars.maxOf { it.right }) / 2, 0.01)
    }

    @Test fun barsDoNotSitLow() {
        val bars = bars("ic_launcher_foreground")
        val middle = (bars.minOf { it.top } + bars.maxOf { it.bottom }) / 2
        // Bars sharing a bottom line look heavy at the bottom, so the group sits slightly above the centre.
        assertTrue("bars' middle is $middle", middle in 50.0..54.0)
    }

    @Test fun barsStayInsideTheVisibleCircle() {
        // Launchers show at least the middle 66dp circle of the 108dp canvas.
        for (bar in bars("ic_launcher_foreground")) {
            for ((x, y) in listOf(bar.left to bar.top + 3, bar.right to bar.top + 3, bar.left to bar.bottom - 3, bar.right to bar.bottom - 3)) {
                val distance = Math.hypot(x - 54, y - 54)
                assertTrue("($x,$y) is $distance from the centre", distance <= 33)
            }
        }
    }

    @Test fun themedIconMatchesTheColouredOne() {
        assertEquals(bars("ic_launcher_foreground"), bars("ic_launcher_monochrome"))
    }
}
