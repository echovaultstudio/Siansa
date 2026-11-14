package app.siansa.tvapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The TV logo's half circles turn round the bars like a record while a DJ mix plays (the owner, 14 Nov). They're drawn
 * in code (LOGO_RINGS), so they must match the launcher icon's own arcs, and the bars' drawing has none of them.
 */
class TvBrandRecordTest {
    private val icon = File("../app/src/main/res/drawable/ic_launcher_foreground.xml").readText()
    private val arc = Regex("""M([\d.]+),([\d.]+)A([\d.]+),[\d.]+ 0 0,[01] ([\d.]+),([\d.]+)""")

    @Test
    fun theDrawnRingsMatchTheIconsArcs() {
        val arcs = arc.findAll(icon).map { m -> m.groupValues.drop(1).map { it.toFloat() } }.toList()
        assertEquals("four half circles in the icon", 4, arcs.size)
        // The left ones, inner then outer: where their ends and their middle are, seen from the icon's middle (54, 54).
        val left = arcs.filter { it[0] < 54f }.sortedBy { -it[0] }
        left.zip(LOGO_RINGS).forEach { (a, ring) ->
            val (x1, y1, r) = a
            val endDistance = hypot(x1 - 54f, y1 - 54f)
            val halfChord = (54f - y1)
            val middleX = x1 + sqrt(r * r - halfChord * halfChord) - r
            val middleDistance = 54f - middleX
            assertTrue("ring ${ring.radius} between the arc's middle $middleDistance and ends $endDistance",
                ring.radius >= middleDistance - 0.1f && ring.radius <= endDistance + 0.1f)
            val sweep = 2 * Math.toDegrees(atan2((54f - y1).toDouble(), (54f - x1).toDouble())).toFloat()
            assertEquals("ring ${ring.radius} as long as the icon's", sweep, ring.sweep, 1.5f)
        }
    }

    @Test
    fun theBarsDrawingHasNoHalfCircles() {
        val bars = File("src/main/res/drawable/tv_brand_bars.xml").readText()
        assertFalse(bars.contains("strokeWidth"))
        assertEquals(icon.split("<path").size - 1 - 4, bars.split("<path").size - 1)
    }

    @Test
    fun aStoppingRecordComesToRestOnAHalfTurn() {
        assertEquals(0f, restingTurn(0f), 0.001f)
        assertEquals(180f, restingTurn(10f), 0.001f)
        assertEquals(360f, restingTurn(190f), 0.001f)
        assertEquals(720f, restingTurn(700f), 0.001f)
    }
}
