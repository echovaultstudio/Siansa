package app.siansa.mix

import app.siansa.data.ArtistRef
import app.siansa.data.HomeSection
import app.siansa.data.Song
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** Custom cards pinned to Home (issue #622): rules, picking, saving, and what a card needs. */
class PinnedCardsTest {
    private val song = Song(id = "1", title = "Blue in Green", artist = "Miles Davis", album = "Kind of Blue", year = 1959, genre = "Jazz", starred = true, artists = listOf(ArtistRef("a", "Miles Davis"), ArtistRef("b", "Bill Evans")))
    private val card = PinnedCard.new("Test")

    @Test fun aRuleThatIsNotSetMatchesEverything() {
        assertTrue(matchesRules(song, card))
        assertTrue(matchesRules(Song(id = "2", title = "Untagged"), card))
    }

    @Test fun genreArtistAlbumAndWordsMatchIgnoringCase() {
        assertTrue(matchesRules(song, card.copy(genre = "jazz")))
        assertFalse(matchesRules(song, card.copy(genre = "Rock")))
        assertTrue(matchesRules(song, card.copy(artist = "miles")))
        assertTrue(matchesRules(song, card.copy(artist = "Evans")))  // any artist on the song, not only the first
        assertFalse(matchesRules(song, card.copy(artist = "Coltrane")))
        assertTrue(matchesRules(song, card.copy(album = "kind of")))
        assertTrue(matchesRules(song, card.copy(words = "green blue")))  // every word, in any order
        assertFalse(matchesRules(song, card.copy(words = "green red")))
    }

    @Test fun yearsAndFavouritesAreChecked() {
        assertTrue(matchesRules(song, card.copy(fromYear = 1950, toYear = 1959)))
        assertFalse(matchesRules(song, card.copy(fromYear = 1960)))
        assertFalse(matchesRules(song, card.copy(toYear = 1958)))
        assertFalse(matchesRules(song.copy(year = null), card.copy(fromYear = 1900)))  // no year: can't be in a range
        assertTrue(matchesRules(song, card.copy(favourites = true)))
        assertFalse(matchesRules(song.copy(starred = false), card.copy(favourites = true)))
    }

    @Test fun pickingLimitsEachArtistAndKeepsTheSameSetForASeed() {
        val all = (1..200).map { i -> Song(id = "$i", title = "Song $i", artist = "Artist ${i % 4}", genre = "Rock") }
        val rock = card.copy(genre = "Rock")
        val a = pickRuleSongs(all, rock, Random(7), size = 50, perArtist = 5)
        val b = pickRuleSongs(all, rock, Random(7), size = 50, perArtist = 5)
        assertEquals(a, b)
        assertEquals(20, a.size)  // 4 artists × 5 songs each is all there is
        assertTrue(a.groupBy { it.artist }.values.all { it.size <= 5 })
        // Spread out where possible: the same artist twice in a row only in the last few, if at all.
        val repeats = a.zipWithNext().count { (x, y) -> x.artist == y.artist }
        assertTrue("$repeats artists twice in a row", repeats <= 2)
        assertTrue(pickRuleSongs(all, card.copy(genre = "Jazz"), Random(1)).isEmpty())
    }

    @Test fun aDaySeedChangesWithTheDayTheCardAndTheRound() {
        val day = LocalDate.of(2026, 10, 28)
        assertEquals(daySeed("x", day), daySeed("x", day))
        assertTrue(daySeed("x", day) != daySeed("x", day.plusDays(1)))
        assertTrue(daySeed("x", day) != daySeed("y", day))
        assertTrue(daySeed("x", day) != daySeed("x", day, round = 1))
    }

    @Test fun whatACardNeeds() {
        assertEquals("Give the card a name.", pinnedCardProblem(card.copy(title = " "), audioMuseReady = true))
        assertNotNull(pinnedCardProblem(card, audioMuseReady = true))  // from the library with no rule at all
        assertNull(pinnedCardProblem(card.copy(favourites = true), audioMuseReady = true))
        assertNull(pinnedCardProblem(card.copy(words = "love"), audioMuseReady = false))
        val sound = card.copy(source = CardSource.SOUND, sound = "rainy jazz piano")
        assertNull(pinnedCardProblem(sound, audioMuseReady = true))
        assertNotNull(pinnedCardProblem(sound, audioMuseReady = false))  // needs AudioMuse
        assertEquals("Describe the sound.", pinnedCardProblem(sound.copy(sound = ""), audioMuseReady = true))
        assertEquals("The first year comes before the second.", pinnedCardProblem(card.copy(fromYear = 2000, toYear = 1990), audioMuseReady = true))
        assertNotNull(pinnedCardProblem(card.copy(fromYear = 1850), audioMuseReady = true, thisYear = 2026))
    }

    @Test fun theDetailLineSaysWhatTheCardShows() {
        assertEquals("Every song", card.detail)
        assertEquals("Rock · 1990–1999 · favourites", card.copy(genre = "Rock", fromYear = 1990, toYear = 1999, favourites = true).detail)
        assertEquals("1980–…", card.copy(fromYear = 1980).detail)
        assertEquals("artist \"Nina\" · title \"feeling\"", card.copy(artist = "Nina", words = "feeling").detail)
        assertEquals("Sound: rainy jazz piano", card.copy(source = CardSource.SOUND, sound = " rainy jazz piano ").detail)
    }

    @Test fun albumsComeFromTheSongsInOrderWithoutRepeats() {
        val songs = listOf(song.copy(id = "1", albumId = "a"), song.copy(id = "2", albumId = "b"), song.copy(id = "3", albumId = "a"), song.copy(id = "4", albumId = null))
        assertEquals(listOf("a", "b"), albumIdsOf(songs))
    }

    @Test fun aCardSavesAndLoadsAsItWas() {
        val c = card.copy(look = CardLook.BANNER, source = CardSource.LIBRARY, genre = "Rock", fromYear = 1990, toYear = 1999, artist = "Nirvana", favourites = true, shown = false)
        val json = Json.encodeToString(PinnedCard.serializer(), c)
        assertEquals(c, Json.decodeFromString(PinnedCard.serializer(), json))
        // Fields added later get their defaults, so old saves still load.
        val old = Json { ignoreUnknownKeys = true }.decodeFromString(PinnedCard.serializer(), """{"id":"pin-1","title":"Old"}""")
        assertEquals(CardLook.TILES, old.look)
        assertTrue(old.shown)
    }

    @Test fun homeHasAPlaceForTheCardsAfterMadeForYou() {
        val order = HomeSection.parse("quick,today,made,rediscover")  // saved before the section existed
        assertEquals(HomeSection.MADE_FOR_YOU, order[order.indexOf(HomeSection.CARDS) - 1])
    }
}
