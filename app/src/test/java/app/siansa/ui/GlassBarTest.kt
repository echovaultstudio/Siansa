package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glass title bar (the owner, 15 Nov): it comes in as the page's title goes under it, in one step; the colour runs
 * out below it with no edge; what scrolls dissolves into it.
 */
class GlassBarTest {
    private val bar = 84f
    private val lead = 16f
    private val height = 36f

    @Test fun theBarComesInAsTheTitleGoesUnderIt() {
        // Well below the bar: nothing. A lead above its lower edge: it starts.
        assertEquals(0f, GlassBar.step(titleTop = 400f, titleHeight = height, barBottom = bar, lead = lead), 0f)
        assertEquals(0f, GlassBar.step(bar + lead, height, bar, lead), 0f)
        // All of the title under the bar: all of it, and it stays.
        assertEquals(1f, GlassBar.step(bar - height, height, bar, lead), 0f)
        assertEquals(1f, GlassBar.step(-500f, height, bar, lead), 0f)
        // Half way: half, and never backwards on the way.
        assertEquals(0.5f, GlassBar.step(bar + lead - (height + lead) / 2, height, bar, lead), 1e-4f)
        var last = 0f
        for (top in 110 downTo 40) {
            val now = GlassBar.step(top.toFloat(), height, bar, lead)
            assertTrue("At $top: $now after $last", now >= last)
            last = now
        }
        // A title not measured yet (no height): in or out, no division by nothing.
        assertEquals(1f, GlassBar.step(10f, 0f, bar, 0f), 0f)
        assertEquals(0f, GlassBar.step(200f, 0f, bar, 0f), 0f)
    }

    @Test fun itStartsAndStopsWithoutAJolt() {
        // Eased: the first and last tenth of the way move it less than a tenth of the way in the middle does.
        val span = height + lead
        fun at(share: Float) = GlassBar.step(bar + lead - span * share, height, bar, lead)
        assertTrue(at(0.1f) - at(0f) < (at(0.55f) - at(0.45f)) / 2)
        assertTrue(at(1f) - at(0.9f) < (at(0.55f) - at(0.45f)) / 2)
    }

    @Test fun theNameIsNeverThereTwice() {
        for (i in 0..100) {
            val step = i / 100f
            val page = GlassBar.pageTitle(step)
            val inBar = GlassBar.barTitle(step)
            assertTrue("At $step the page's title shows $page and the bar's $inBar", page < 0.02f || inBar < 0.02f)
        }
        assertEquals(1f, GlassBar.pageTitle(0f), 0f)
        assertEquals(0f, GlassBar.barTitle(0f), 0f)
        assertEquals(0f, GlassBar.pageTitle(1f), 0f)
        assertEquals(1f, GlassBar.barTitle(1f), 0f)
    }

    @Test fun theColourRunsOutWithNoEdge() {
        assertEquals(1f, GlassBar.colour(0f), 0f)
        assertEquals(0f, GlassBar.colour(1f), 0f)
        // Always less on the way down, and never a step a screen could show as a line: the largest change between two
        // of the wash's colours is small, and the last ones before the end are the smallest (it levels out).
        var last = 1f
        var largest = 0f
        for (i in 1..GlassBar.STEPS) {
            val now = GlassBar.colour(i / GlassBar.STEPS.toFloat())
            assertTrue("Step $i: $now after $last", now <= last)
            largest = maxOf(largest, last - now)
            last = now
        }
        assertTrue("The largest step is $largest", largest < 0.07f)
        val end = GlassBar.colour((GlassBar.STEPS - 1) / GlassBar.STEPS.toFloat())
        assertTrue("The last step before nothing is $end", end < 0.01f)
        // Behind the bar's title (about half way down) most of the colour is still there.
        assertTrue(GlassBar.colour(0.48f) > 0.7f)
    }

    @Test fun whatScrollsDissolvesBelowTheTitleOnly() {
        // Nothing shows down to the solid part; all of it at the end; level at both ends.
        assertEquals(0f, GlassBar.shows(0f, 0.5f), 0f)
        assertEquals(0f, GlassBar.shows(0.5f, 0.5f), 0f)
        assertEquals(1f, GlassBar.shows(1f, 0.5f), 0f)
        assertTrue(GlassBar.shows(0.55f, 0.5f) < 0.03f)
        assertTrue(GlassBar.shows(0.95f, 0.5f) > 0.97f)
        var last = 0f
        for (i in 0..100) {
            val now = GlassBar.shows(i / 100f, 0.5f)
            assertTrue(now >= last)
            last = now
        }
        // With tabs pinned under the bar the solid part is longer; with none left to dissolve in, it's all solid.
        assertEquals(0f, GlassBar.shows(0.7f, 0.75f), 0f)
        assertEquals(0f, GlassBar.shows(0.9f, 1f), 0f)
        assertEquals(1f, GlassBar.shows(1f, 1f), 0f)
    }
}
