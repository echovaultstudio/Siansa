package app.siansa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What's new after an update (the owner, 16 Nov): when it shows, and what the summary holds. */
class UpdateNewsTest {
    private val changelog = parseChangelog(
        """
        # Changelog

        ## 1.6.0 — 2026-11-20

        ### Fixed
        - **Covers flashed a placeholder.** They now show straight away.
        - A small fix with no headline.

        ### Added
        - **Star ratings for songs.** Off by default, in About this song.

        ### Highlights
        - **Siansa Beats:** Siansa's own companion.

        ## 1.5.6 — 2026-11-15

        ### Fixed
        - **Download all covers gave up too soon.** It keeps asking now.

        ## 1.5.5 — 2026-11-15

        ### Changed
        - **Settings open a service's own page.** Manage still opens the list.

        ## 1.5.4 — 2026-11-15

        ### Fixed
        - **Animated covers were left out.** They're downloaded whole.
        """.trimIndent(),
    )

    @Test fun showsOnlyAfterAnUpdate() {
        // A build seen before: only a higher one is an update.
        assertTrue(UpdateNews.updated(seenCode = 41, code = 42, firstInstall = 1, lastUpdate = 1))
        assertFalse(UpdateNews.updated(seenCode = 42, code = 42, firstInstall = 1, lastUpdate = 9))
        assertFalse(UpdateNews.updated(seenCode = 43, code = 42, firstInstall = 1, lastUpdate = 9))
        // Never settled: a fresh install shows nothing, an update from a version before this one does.
        assertFalse(UpdateNews.updated(seenCode = null, code = 42, firstInstall = 5, lastUpdate = 5))
        assertTrue(UpdateNews.updated(seenCode = null, code = 42, firstInstall = 5, lastUpdate = 9))
    }

    @Test fun theNewestVersionAloneWhenTheLastOneIsntKnown() {
        val summary = UpdateNews.summary(changelog, installed = "1.6.0", seen = null)!!
        assertEquals("1.6.0", summary.version)
        // New things before fixes; only the headlines, without their full stop or colon.
        assertEquals(listOf("Siansa Beats", "Star ratings for songs", "Covers flashed a placeholder"), summary.lines)
        // The change with no headline isn't a big one: it's counted, not shown.
        assertEquals(1, summary.more)
    }

    @Test fun everyVersionSinceTheLastOneSeen() {
        val summary = UpdateNews.summary(changelog, installed = "1.6.0", seen = "1.5.4")!!
        assertEquals("1.6.0", summary.version)
        assertEquals(
            listOf("Siansa Beats", "Star ratings for songs", "Settings open a service's own page", "Covers flashed a placeholder", "Download all covers gave up too soon"),
            summary.lines,
        )
        assertEquals(1, summary.more)
        // 1.5.4 itself was seen before.
        assertFalse("Animated covers were left out" in summary.lines)
    }

    @Test fun atMostFiveHeadlines() {
        val summary = UpdateNews.summary(changelog, installed = "1.6.0", seen = "1.5.0")!!
        assertEquals(UpdateNews.MAX, summary.lines.size)
        assertEquals(2, summary.more)
    }

    @Test fun aVersionAheadOfTheAppIsLeftOut() {
        // Siansa Dev ("1.5.6-dev") with a changelog that's one version ahead; the same version seen: the newest reached.
        val summary = UpdateNews.summary(changelog, installed = "1.5.6-dev", seen = "1.5.6-dev")!!
        assertEquals("1.5.6", summary.version)
        assertEquals(listOf("Download all covers gave up too soon"), summary.lines)
        assertEquals(0, summary.more)
    }

    @Test fun nothingWithoutABigChange() {
        val plain = parseChangelog("## 1.6.1 — 2026-11-21\n\n### Fixed\n- A small fix.\n")
        assertNull(UpdateNews.summary(plain, installed = "1.6.1", seen = "1.6.0"))
        assertNull(UpdateNews.summary(emptyList(), installed = "1.6.1", seen = null))
    }

    @Test fun headlines() {
        assertEquals("Covers flashed", UpdateNews.headline("**Covers flashed.** They now show."))
        assertEquals("New in 1.5, in short", UpdateNews.headline("**New in 1.5, in short:** Siansa Beats."))
        assertNull(UpdateNews.headline("Settings › Sound: **Test** says it works."))
        assertNull(UpdateNews.headline("A plain change."))
    }
}
