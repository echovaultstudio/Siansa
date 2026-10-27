package app.siansa.ui

import app.siansa.data.likeLiteral
import app.siansa.data.markLabels
import app.siansa.data.unmarkLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An album's genre and label tags (#558): how labels are kept, matched and opened. */
class AlbumTagsTest {
    private val mark = app.siansa.data.db.LABEL_MARK

    @Test fun labelsAreKeptWholeAndComeBackTheSame() {
        val stored = markLabels(listOf(" Warner ", "Reprise", "Warner", ""))
        assertEquals("${mark}Warner$mark${mark}Reprise$mark", stored)
        assertEquals(listOf("Warner", "Reprise"), unmarkLabels(stored))
        assertEquals(emptyList<String>(), unmarkLabels(""))
    }

    @Test fun aLabelMatchesWholeNotAsPartOfAnother() {
        // "Warner" must not match "Warner Music Japan": the marks either side make the LIKE match whole labels.
        val stored = markLabels(listOf("Warner Music Japan"))
        assertFalse(stored.contains(mark + "Warner" + mark))
        assertTrue(stored.contains(mark + "Warner Music Japan" + mark))
    }

    @Test fun wildcardsInALabelMatchOnlyThemselves() {
        assertEquals("100\\% Records", likeLiteral("100% Records"))
        assertEquals("a\\_b\\\\c", likeLiteral("a_b\\c"))
    }

    @Test fun theRouteCarriesTheNameEncoded() {
        assertEquals("albums/label?value=Warner%20Music%20Japan", albumTagRoute(AlbumTag.LABEL, "Warner Music Japan"))
        assertEquals("albums/genre?value=R%26B", albumTagRoute(AlbumTag.GENRE, "R&B"))
    }

    @Test fun anEmptyPageSaysWhy() {
        assertTrue(albumTagEmptyText(AlbumTag.LABEL, synced = false).startsWith("Sync your library"))
        assertTrue(albumTagEmptyText(AlbumTag.LABEL, synced = true).contains("next library sync"))
        assertEquals("No albums with this genre.", albumTagEmptyText(AlbumTag.GENRE, synced = true))
    }
}
