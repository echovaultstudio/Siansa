package app.siansa.mix

import app.siansa.net.BeatsMixes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A Siansa Beats card's kinds of mix (the owner, 26 Nov: "a siansa beats card where you can toggle them on and off"). */
class BeatsKindsTest {
    private fun mix(id: String) = BeatsMixes.BeatsMix(id, id, "", false, emptyList(), emptyList(), null, emptyList(), 0)

    @Test fun eachMixIsKnownByItsId() {
        assertEquals(BeatsKind.NOW, BeatsKind.of("now-2"))
        assertEquals(BeatsKind.DAILY, BeatsKind.of("daily-5"))
        assertEquals(BeatsKind.REPEAT, BeatsKind.of("repeat"))
        // Forgotten artists' artists are in Artist radios; Your energy and Smooth run were taken out (the owner, 27 Nov);
        // a kind Siansa Beats adds later is none of these.
        assertNull(BeatsKind.of("forgotten"))
        assertNull(BeatsKind.of("energy-low"))
        assertNull(BeatsKind.of("smooth-1"))
        assertNull(BeatsKind.of("newer-thing"))
    }

    /** The card's mixes from every shelf, kind by kind in the order Siansa Beats lists them, each once. */
    @Test fun aCardHasItsKindsFromEveryShelf() {
        val answer = BeatsMixes.Shelves(part = "evening", said = "evening", based = "", basis = "slot", daylight = null, askedAt = 1L, shelves = listOf(
            BeatsMixes.Shelf("now", "Your evening", "", listOf(mix("now-1"), mix("now-2"))),
            BeatsMixes.Shelf("daily", "Made for you", "", listOf(mix("daily-1"))),
            BeatsMixes.Shelf("more", "More of you", "", listOf(mix("repeat"), mix("deep"), mix("forgotten"), mix("new"))),
        ))
        assertEquals(listOf("now-1", "now-2", "repeat", "deep"),
            BeatsKind.mixes(answer, listOf("deep", "repeat", "now", "smooth", "gone")).map { it.id })
        assertEquals(emptyList<String>(), BeatsKind.mixes(answer, emptyList()).map { it.id })
        assertEquals(emptyList<String>(), BeatsKind.mixes(null, listOf("now")).map { it.id })
    }

    @Test fun aBeatsCardSaysItsMixes() {
        val card = PinnedCard("p", "Daily", source = CardSource.BEATS, beats = listOf("now", "deep"), look = CardLook.CAROUSEL)
        assertEquals("Siansa Beats: For now, Deep cuts", card.detail)
        // A card saved with a kind since taken out says only the ones still made.
        assertEquals("Siansa Beats: For now", card.copy(beats = listOf("now", "smooth", "energy")).detail)
        assertEquals("Siansa Beats: no mixes yet", card.copy(beats = emptyList()).detail)
        // Saved and read back, as any card.
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        assertEquals(card, json.decodeFromString(PinnedCard.serializer(), json.encodeToString(PinnedCard.serializer(), card)))
    }

    @Test fun aNewCardStartsWithYourOwnSound() {
        assertEquals(listOf("repeat", "rediscover", "new", "deep"), BeatsKind.FIRST.map { it.id })
    }
}
