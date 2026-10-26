package app.siansa.data

import app.siansa.net.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Connections page saves (beta: the Audiobookshelf login cleared on Save). */
class ConnectionsSaveTest {
    private val page = AppSettings(
        navidromeUrl = "http://music", navidromeUser = "max", navidromePassword = "pw",
        proxyUser = "p", proxyPassword = "pp",
        audioMuseUrl = "http://am", audioMuseToken = "t", audioMuseUser = "u", audioMusePassword = "ap", audioMuseServer = "Emby",
        openRouterKey = "k", openRouterModel = "m", aiTitles = false,
        audiobookshelfUrl = "http://abs:13378", audiobookshelfToken = "abs-token",
        audiobookshelfUser = "reader", audiobookshelfPassword = "abs-pw",
    )

    @Test fun theAudiobookshelfLoginIsSaved() {
        val saved = AppSettings().withConnectionsFrom(page)
        assertEquals("http://abs:13378", saved.audiobookshelfUrl)
        assertEquals("abs-token", saved.audiobookshelfToken)
        assertEquals("reader", saved.audiobookshelfUser)
        assertEquals("abs-pw", saved.audiobookshelfPassword)
        assertTrue(saved.booksReady)
    }

    @Test fun everyConnectionFieldOnThePageIsSaved() {
        // Every field the page sets differs from its default above, so one left out of the save shows up here.
        assertEquals(page.copy(sourceKind = SourceKind.EMBY), AppSettings().withConnectionsFrom(page, SourceKind.EMBY))
    }

    @Test fun otherSettingsKeepTheirValue() {
        val current = AppSettings(bookSkipBackSec = 20, bookSkipForwardSec = 60)
        val saved = current.withConnectionsFrom(page)
        assertEquals(20, saved.bookSkipBackSec)
        assertEquals(60, saved.bookSkipForwardSec)
    }
}
