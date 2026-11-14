package app.siansa.tvapp

import app.siansa.data.DefaultSelectors
import app.siansa.data.Mix
import app.siansa.data.SyncPhase
import app.siansa.data.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner's TV list of 14 Nov: Today's mixes, Home's Sync pill, a mix's reason, the lyrics alone. */
class TvHomeChangesTest {
    private val today = "2026-11-14"
    private fun mix(id: String, selector: String = id.substringAfter("$today-")) =
        Mix(id = id, selectorId = selector, date = today, title = selector, description = "", query = "", songs = emptyList(), createdAt = 0, aiNamed = false)

    @Test
    fun everyMixOfTheDayHasACardMadeOrToMake() {
        val tiles = todayTiles(DefaultSelectors.all, listOf(mix("$today-evening")), today)
        assertEquals(listOf("mv:morning", "mv:day", "mx:$today-evening", "mv:night"), tiles.map { it.key })
    }

    @Test
    fun withNoVibeOnTheDaysFourAreThere() {
        val off = DefaultSelectors.all.map { it.copy(enabled = false) }
        assertEquals(listOf("mv:morning", "mv:day", "mv:evening", "mv:night"), todayTiles(off, emptyList(), today).map { it.key })
        assertEquals(4, todayTiles(emptyList(), emptyList(), today).size)
    }

    @Test
    fun aVibeSwitchedOffHasNoCardAndOtherMixesOfTodayFollow() {
        val vibes = DefaultSelectors.all.map { if (it.id == "night") it.copy(enabled = false) else it }
        val tiles = todayTiles(vibes, listOf(mix("$today-carousel-1", "carousel-1")), today)
        assertEquals(listOf("mv:morning", "mv:day", "mv:evening", "mx:$today-carousel-1"), tiles.map { it.key })
    }

    @Test
    fun todaysMixesAreLandscapeCards() {
        assertTrue(TvRow("Today's mixes", emptyList(), landscape = true).landscape)
        assertFalse(TvRow("Recently added", emptyList()).landscape)
    }

    @Test
    fun theSyncPillTurnsWhileItRunsAndGoesRedWhenItFailed() {
        assertEquals(SyncPill.IDLE, syncPill(SyncProgress()))
        assertEquals(SyncPill.SYNCING, syncPill(SyncProgress(phase = SyncPhase.ALBUMS)))
        assertEquals(SyncPill.FAILED, syncPill(SyncProgress(error = "Couldn't reach the server")))
        // The quiet check at start says nothing when it fails.
        assertEquals(SyncPill.IDLE, syncPill(SyncProgress(error = "offline", quiet = true)))
        assertEquals(listOf("Sync", "Syncing…", "Sync failed"), SyncPill.entries.map { it.label })
    }

    @Test
    fun aMixThatCantBeMadeSaysWhy() {
        assertEquals("Evening couldn't be made: Set up your server in Settings first", noMixReason("Evening", "Set up your server in Settings first"))
        assertEquals("Today's mix has no songs yet. Try again once your library has synced.", noMixReason("Today's mix", null))
        assertEquals("Today's mix has no songs yet. Try again once your library has synced.", noMixReason("Today's mix", " "))
    }

    @Test
    fun theLyricsHaveTheScreenToThemselves() {
        assertFalse(showsGuide(Panel.LYRICS))
        assertTrue(showsGuide(Panel.NONE))
        assertTrue(showsGuide(Panel.QUEUE))
    }

    @Test
    fun similarSongsOpenTheTvsOwnPage() {
        assertTrue(TvRoutes.SIMILAR_SONGS in TvRoutes.own)
    }
}
