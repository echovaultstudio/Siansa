package app.musix.ui

import app.musix.ui.ChangelogBlock.Bullet
import app.musix.ui.ChangelogBlock.Paragraph
import app.musix.ui.ChangelogBlock.Section
import app.musix.ui.ChangelogBlock.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The What's new page's reading of CHANGELOG.md. */
class ChangelogTextTest {
    private val sample = """
        # Changelog

        Every version of Musix, newest first.
        Changes add a note in `changelog.d/` instead of editing this file.

        ## 0.0.29beta — 2026-10-17

        ### Added
        - **Backup.** Settings → Backup saves your settings, vibes and mixes to a
          file, and restores them.

        ## 0.0.26beta — 2026-10-17

        ### Fixed
        - **Grey square before the cover.** Two things changed:
          - the cover is loaded at full size;
          - any cover that isn't ready shows a smaller one
            until the sharper one is ready.
        <!-- release: minor -->

        ### Changed
        Alphabetical sorting now puts names with accents with their letter.
    """.trimIndent()

    @Test fun readsVersionsSectionsAndBullets() {
        val expected = listOf(
            Version("0.0.29beta", "2026-10-17"),
            Section("Added"),
            Bullet("**Backup.** Settings → Backup saves your settings, vibes and mixes to a file, and restores them.", 0),
            Version("0.0.26beta", "2026-10-17"),
            Section("Fixed"),
            Bullet("**Grey square before the cover.** Two things changed:", 0),
            Bullet("the cover is loaded at full size;", 1),
            Bullet("any cover that isn't ready shows a smaller one until the sharper one is ready.", 1),
            Section("Changed"),
            Paragraph("Alphabetical sorting now puts names with accents with their letter."),
        )
        assertEquals(expected, parseChangelog(sample))
    }

    @Test fun titleAndIntroductionAreLeftOut() {
        val blocks = parseChangelog(sample)
        assertTrue(blocks.first() is Version)
        assertTrue(blocks.none { it is Paragraph && it.text.contains("newest first") })
    }

    @Test fun versionWithoutDate() {
        assertEquals(listOf(Version("Unreleased", "")), parseChangelog("## Unreleased"))
        assertEquals(listOf(Version("1.0.0", "2027-01-02")), parseChangelog("## 1.0.0 - 2027-01-02"))
    }

    @Test fun multiLineCommentsAreSkipped() {
        val text = "## 1.0\n<!--\n- hidden\n-->\n- shown"
        assertEquals(listOf(Version("1.0", ""), Bullet("shown", 0)), parseChangelog(text))
    }

    @Test fun emptyTextHasNoBlocks() {
        assertTrue(parseChangelog("").isEmpty())
        assertTrue(parseChangelog("# Changelog\n\nNothing yet.").isEmpty())
    }

    @Test fun boldPiecesAreSplit() {
        assertEquals(
            listOf(ChangelogSpan("Backup.", true), ChangelogSpan(" Saves your settings ", false), ChangelogSpan("daily", true)),
            changelogSpans("**Backup.** Saves your settings **daily**"),
        )
    }

    @Test fun unmatchedBoldMarkerStaysAsText() {
        assertEquals(
            listOf(ChangelogSpan("a ", false), ChangelogSpan("b", true), ChangelogSpan(" c **d", false)),
            changelogSpans("a **b** c **d"),
        )
        assertEquals(listOf(ChangelogSpan("2 ** 3", false)), changelogSpans("2 ** 3"))
    }

    @Test fun linksAndCodeBecomePlainText() {
        assertEquals(
            listOf(ChangelogSpan("See the issue and changelog.d/ notes", false)),
            changelogSpans("See [the issue](https://github.com/x/y/issues/3) and `changelog.d/` notes"),
        )
        assertEquals(listOf(ChangelogSpan("Docs", true)), changelogSpans("**[Docs](https://x.y)**"))
    }

    @Test fun releaseLinks() {
        assertEquals("https://github.com/Th3K1ngP1n/musix/releases/tag/v0.0.29beta", releaseUrl("0.0.29beta"))
        assertEquals("https://github.com/Th3K1ngP1n/musix/releases", releaseUrl(""))
    }
}
