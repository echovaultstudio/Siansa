package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The artist's name rides up with the page, then glides into the title bar without a jolt (the owner, 10 Nov). */
class HeroNameFrameTest {
    private val rest = 900f
    private val start = 300f
    private val dock = 80f

    @Test fun atFirstItMovesWithThePage() {
        assertEquals(HeroNameFrame(900f, 0f), heroNameFrame(0f, rest, start, dock))
        assertEquals(HeroNameFrame(500f, 0f), heroNameFrame(400f, rest, start, dock))
    }

    @Test fun itSettlesInTheBarAndStaysThere() {
        val end = (rest - start) + DOCK_SPAN * (start - dock)
        val settled = heroNameFrame(end, rest, start, dock)
        assertEquals(dock, settled.top, 0.01f)
        assertEquals(1f, settled.t, 0.0001f)
        assertEquals(dock, heroNameFrame(end + 5_000f, rest, start, dock).top, 0.01f)
    }

    @Test fun itNeverGoesBackDownNorPastTheBar() {
        var last = Float.MAX_VALUE
        var s = 0f
        while (s < 2_000f) {
            val top = heroNameFrame(s, rest, start, dock).top
            assertTrue("moved down at $s", top <= last + 0.001f)
            assertTrue("past the bar at $s", top >= dock - 0.001f)
            last = top
            s += 1f
        }
    }

    @Test fun noJoltWhereTheGlideStarts() {
        // Its speed just before the glide is the page's (1 px a px); just after, still about that.
        val s0 = rest - start
        val before = heroNameFrame(s0 - 1f, rest, start, dock).top - heroNameFrame(s0 - 2f, rest, start, dock).top
        val after = heroNameFrame(s0 + 2f, rest, start, dock).top - heroNameFrame(s0 + 1f, rest, start, dock).top
        assertEquals(-1f, before, 0.001f)
        assertTrue("speed jumped: $after", abs(after - before) < 0.05f)
    }

    @Test fun aNameThatStartsHighGlidesFromWhereItIs() {
        // A big font on a short screen: the name starts above the start line, and glides from where it is.
        val f = heroNameFrame(0f, restTop = 200f, startTop = start, dockTop = dock)
        assertEquals(200f, f.top, 0.01f)
        assertEquals(0f, f.t, 0.0001f)
    }
}
