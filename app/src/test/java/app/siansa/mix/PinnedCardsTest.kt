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

    // ---- Issue #663: rules to include or leave out, and album cards ----

    private val labelAlbum = app.siansa.data.Album(id = "al", name = "Selected Works", artist = "Bonobo", year = 2024, genre = "Electronic",
        labels = listOf("Ninja Tune"), releaseTypes = listOf("album"), created = "2026-10-20T10:00:00Z", playCount = 12, songCount = 11, durationSec = 3000)
    private val otherAlbum = labelAlbum.copy(id = "o", name = "Live at Home", year = 2023, labels = listOf("Warp"), releaseTypes = listOf("album", "live"), created = "2024-01-01T00:00:00Z")

    private fun rule(field: RuleField, op: RuleOp, value: String = "", value2: String = "") = CardRule(field, op, value, value2)

    @Test fun anAlbumCardOnALabelAndAYear() {
        val c = card.copy(contents = CardContents.ALBUMS, rules = listOf(rule(RuleField.LABEL, RuleOp.CONTAINS, "ninja tune"), rule(RuleField.YEAR, RuleOp.IS, "2024")))
        assertEquals(listOf("al"), pickRuleAlbums(listOf(labelAlbum, otherAlbum), c, Random(1)).map { it.id })
        assertNull(pinnedCardProblem(c, audioMuseReady = false))
        assertEquals("Albums · Record label contains ninja tune · Year is 2024", c.detail)
    }

    @Test fun leavingOutIsAsEasyAsKeeping() {
        val notLive = card.copy(contents = CardContents.ALBUMS, rules = listOf(rule(RuleField.RELEASE_TYPE, RuleOp.NOT_CONTAINS, "live, remix")))
        assertEquals(listOf("al"), pickRuleAlbums(listOf(labelAlbum, otherAlbum), notLive, Random(1)).map { it.id })
        val notWarp = card.copy(contents = CardContents.ALBUMS, rules = listOf(rule(RuleField.LABEL, RuleOp.IS_NOT, "Warp")))
        assertEquals(listOf("al"), pickRuleAlbums(listOf(labelAlbum, otherAlbum), notWarp, Random(1)).map { it.id })
    }

    @Test fun everyRuleOrAnyRule() {
        val rules = listOf(rule(RuleField.LABEL, RuleOp.IS, "Warp"), rule(RuleField.YEAR, RuleOp.IS, "2024"))
        val all = card.copy(contents = CardContents.ALBUMS, rules = rules)
        assertTrue(pickRuleAlbums(listOf(labelAlbum, otherAlbum), all, Random(1)).isEmpty())
        val any = all.copy(matchAll = false)
        assertEquals(setOf("al", "o"), pickRuleAlbums(listOf(labelAlbum, otherAlbum), any, Random(1)).map { it.id }.toSet())
        assertEquals("Albums · Record label is Warp or Year is 2024", any.detail)
    }

    @Test fun numbersAgesAndFlags() {
        val today = LocalDate.of(2026, 10, 29)
        val s = AlbumSubject(labelAlbum, favourite = true, today = today)
        assertTrue(rule(RuleField.YEAR, RuleOp.BETWEEN, "2020", "2029").matches(s))
        assertTrue(rule(RuleField.YEAR, RuleOp.BETWEEN, "2029", "2020").matches(s))  // either way round
        assertTrue(rule(RuleField.SONG_COUNT, RuleOp.AT_LEAST, "10").matches(s))
        assertFalse(rule(RuleField.LENGTH, RuleOp.AT_MOST, "45").matches(s))  // 50 minutes
        assertTrue(rule(RuleField.ADDED, RuleOp.WITHIN, "30").matches(s))  // 9 days ago
        assertFalse(rule(RuleField.ADDED, RuleOp.NOT_WITHIN, "30").matches(s))
        assertTrue(rule(RuleField.FAVOURITE, RuleOp.YES).matches(s))
        assertFalse(rule(RuleField.COMPILATION, RuleOp.YES).matches(s))
        // A missing value passes only a rule that leaves out.
        val noYear = AlbumSubject(labelAlbum.copy(year = null))
        assertFalse(rule(RuleField.YEAR, RuleOp.AT_LEAST, "1990").matches(noYear))
        assertTrue(rule(RuleField.YEAR, RuleOp.IS_NOT, "1990").matches(noYear))
    }

    @Test fun aSongTakesItsAlbumsDetails() {
        val s = song.copy(albumId = "al")
        val c = card.copy(rules = listOf(rule(RuleField.LABEL, RuleOp.CONTAINS, "Ninja")))
        assertTrue(matchesRules(s, c, labelAlbum))
        assertFalse(matchesRules(s, c, otherAlbum))
        assertFalse(matchesRules(s, c))  // album unknown: no label
        assertEquals(listOf("1"), pickRuleSongs(listOf(s), c, Random(1), albums = mapOf("al" to labelAlbum)).map { it.id })
    }

    @Test fun anOldCardsFieldsBecomeRules() {
        val old = card.copy(genre = "Rock", fromYear = 1990, toYear = 1999, artist = "Nirvana", words = "smells teen", favourites = true)
        val m = old.migrated()
        assertEquals(listOf(
            rule(RuleField.GENRE, RuleOp.CONTAINS, "Rock"), rule(RuleField.YEAR, RuleOp.BETWEEN, "1990", "1999"),
            rule(RuleField.ARTIST, RuleOp.CONTAINS, "Nirvana"), rule(RuleField.TITLE, RuleOp.CONTAINS, "smells"),
            rule(RuleField.TITLE, RuleOp.CONTAINS, "teen"), rule(RuleField.FAVOURITE, RuleOp.YES),
        ), m.rules)
        assertEquals("", m.genre)
        assertNull(m.fromYear)
        assertFalse(m.favourites)
        val nirvana = song.copy(title = "Smells Like Teen Spirit", artist = "Nirvana", genre = "Rock", year = 1991, starred = true)
        assertEquals(matchesRules(nirvana, old), matchesRules(nirvana, m))
        assertTrue(matchesRules(nirvana, m))
        assertEquals(listOf(rule(RuleField.YEAR, RuleOp.AT_LEAST, "1980")), card.copy(fromYear = 1980).allRules)
    }

    @Test fun whatARuleNeeds() {
        assertEquals("Give record label a value.", pinnedCardProblem(card.copy(rules = listOf(rule(RuleField.LABEL, RuleOp.CONTAINS))), true))
        assertEquals("Year takes a number.", pinnedCardProblem(card.copy(rules = listOf(rule(RuleField.YEAR, RuleOp.IS, "nineties"))), true))
        assertEquals("Give year the second number too.", pinnedCardProblem(card.copy(rules = listOf(rule(RuleField.YEAR, RuleOp.BETWEEN, "1990"))), true))
        assertEquals("Song title is for songs, not albums.", pinnedCardProblem(card.copy(contents = CardContents.ALBUMS, rules = listOf(rule(RuleField.TITLE, RuleOp.CONTAINS, "x"))), true))
        assertEquals("Albums come from your library: pick From your library.", pinnedCardProblem(card.copy(contents = CardContents.ALBUMS, source = CardSource.SOUND, sound = "x"), true))
        assertNull(pinnedCardProblem(card.copy(rules = listOf(rule(RuleField.FAVOURITE, RuleOp.NO))), true))
        assertTrue(RuleField.forContents(CardContents.ALBUMS).none { it == RuleField.TITLE })
        assertTrue(RuleOp.forKind(RuleKind.TEXT).containsAll(listOf(RuleOp.CONTAINS, RuleOp.NOT_CONTAINS, RuleOp.IS, RuleOp.IS_NOT)))
    }

    @Test fun ordersAndHowMany() {
        val albums = listOf(labelAlbum, otherAlbum, labelAlbum.copy(id = "z", name = "Another", year = 2010, created = "2025-01-01T00:00:00Z", playCount = 99))
        fun ids(sort: CardSort, size: Int = 10) = pickRuleAlbums(albums, card.copy(contents = CardContents.ALBUMS, sort = sort, size = size), Random(1)).map { it.id }
        assertEquals(listOf("al", "o", "z"), ids(CardSort.NEWEST))
        assertEquals(listOf("z", "o", "al"), ids(CardSort.OLDEST))
        assertEquals(listOf("al", "z", "o"), ids(CardSort.ADDED))
        assertEquals(listOf("z", "al", "o"), ids(CardSort.PLAYED))
        assertEquals(listOf("z", "o", "al"), ids(CardSort.NAME))
        assertEquals(2, ids(CardSort.NAME, size = 2).size)
    }

    @Test fun rulesSaveAndLoad() {
        val c = card.copy(contents = CardContents.ALBUMS, matchAll = false, sort = CardSort.ADDED, size = 30,
            rules = listOf(rule(RuleField.LABEL, RuleOp.IS, "Ninja Tune"), rule(RuleField.YEAR, RuleOp.BETWEEN, "2020", "2024")))
        assertEquals(c, Json.decodeFromString(PinnedCard.serializer(), Json.encodeToString(PinnedCard.serializer(), c)))
    }

    // ---- Issue #678: play count, last played and skips ----

    private val zone = java.time.ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 30)
    private fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000

    private fun event(id: String, day: LocalDate, skipped: Boolean = false) =
        app.siansa.data.PlayEvent(id = id, title = "", artist = "", at = at(day), skipped = skipped)

    @Test fun playStatsTakeTheHigherCountAndTheLatestPlay() {
        val history = listOf(
            event("1", today.minusDays(2)), event("1", today.minusDays(5)), event("1", today.minusDays(1), skipped = true),
            event("2", today.minusDays(3), skipped = true),
        )
        val server = mapOf("1" to ServerPlays(7, today.minusDays(10).toEpochDay()), "3" to ServerPlays(2, today.minusDays(40).toEpochDay()))
        val stats = playStats(history, server, zone)
        assertEquals(PlayStats(plays = 7, skips = 1, lastPlayed = today.minusDays(2).toEpochDay()), stats["1"])
        assertEquals(PlayStats(plays = 0, skips = 1, lastPlayed = null), stats["2"])  // only skipped: never really played
        assertEquals(PlayStats(plays = 2, skips = 0, lastPlayed = today.minusDays(40).toEpochDay()), stats["3"])
        val albums = albumPlayStats(stats, mapOf("1" to "al", "3" to "al", "2" to "o"))
        assertEquals(PlayStats(plays = 9, skips = 1, lastPlayed = today.minusDays(2).toEpochDay()), albums["al"])
    }

    @Test fun theDaySiansaFirstSawASongIsntWhenItWasPlayed() {
        val day = today.toEpochDay()
        // Seen once: its count, but not when it was played (it may have been years ago).
        assertEquals(ServerPlays(12, null), ServerPlays.of(listOf(CountPoint(day, 12))))
        // The count went up on a later day: played then.
        assertEquals(ServerPlays(13, day), ServerPlays.of(listOf(CountPoint(day - 5, 12), CountPoint(day, 13))))
        assertEquals(null, ServerPlays.of(emptyList()))
        // A song first seen today with plays long ago stays "not played lately".
        val stats = playStats(emptyList(), mapOf("x" to ServerPlays.of(listOf(CountPoint(day, 30)))!!), zone)
        assertTrue(rule(RuleField.PLAYED, RuleOp.NOT_WITHIN, "90").matches(SongSubject(song, null, stats["x"], today)))
        assertTrue(rule(RuleField.PLAYS, RuleOp.AT_LEAST, "30").matches(SongSubject(song, null, stats["x"], today)))
    }

    @Test fun songsHaveTheirOwnPlayCountLastPlayedAndSkips() {
        val played = SongSubject(song, labelAlbum, PlayStats(plays = 3, skips = 4, lastPlayed = today.minusDays(100).toEpochDay()), today)
        assertTrue(rule(RuleField.PLAYS, RuleOp.AT_LEAST, "3").matches(played))
        assertFalse(rule(RuleField.PLAYS, RuleOp.AT_LEAST, "12").matches(played))  // the album's 12 plays aren't the song's
        assertTrue(rule(RuleField.SKIPS, RuleOp.AT_LEAST, "3").matches(played))
        assertTrue(rule(RuleField.PLAYED, RuleOp.NOT_WITHIN, "90").matches(played))
        assertFalse(rule(RuleField.PLAYED, RuleOp.WITHIN, "90").matches(played))
        assertTrue(rule(RuleField.ADDED, RuleOp.WITHIN, "30").matches(played))  // added still comes from the album
        // Never played: no plays, and "not played lately" keeps it.
        val never = SongSubject(song, labelAlbum, null, today)
        assertTrue(rule(RuleField.PLAYS, RuleOp.AT_MOST, "0").matches(never))
        assertTrue(rule(RuleField.PLAYED, RuleOp.NOT_WITHIN, "30").matches(never))
        assertFalse(rule(RuleField.PLAYED, RuleOp.WITHIN, "30").matches(never))
        assertTrue(RuleField.PLAYS in RuleField.forContents(CardContents.SONGS))
        assertTrue(RuleField.PLAYS in RuleField.forContents(CardContents.ALBUMS))
        assertFalse(RuleField.SKIPS in RuleField.forContents(CardContents.ALBUMS))
        assertEquals("Play count at least 3", rule(RuleField.PLAYS, RuleOp.AT_LEAST, "3").text)
        assertEquals("Played not in the last 90 days", rule(RuleField.PLAYED, RuleOp.NOT_WITHIN, "90").text)
    }

    @Test fun albumsLastPlayedFromTheirSongsAndMostPlayedSongsFirst() {
        val s = AlbumSubject(labelAlbum, stats = PlayStats(plays = 20, lastPlayed = today.minusDays(3).toEpochDay()), today = today)
        assertTrue(rule(RuleField.PLAYS, RuleOp.AT_LEAST, "20").matches(s))  // the higher of the server's 12 and its songs' 20
        assertTrue(rule(RuleField.PLAYED, RuleOp.WITHIN, "7").matches(s))
        assertTrue(rule(RuleField.PLAYED, RuleOp.NOT_WITHIN, "7").matches(AlbumSubject(labelAlbum, today = today)))
        val songs = listOf(song.copy(id = "a"), song.copy(id = "b"), song.copy(id = "c"))
        val c = card.copy(sort = CardSort.PLAYED, rules = listOf(rule(RuleField.PLAYS, RuleOp.AT_LEAST, "1")))
        val stats = mapOf("a" to PlayStats(plays = 2), "b" to PlayStats(plays = 9))
        assertEquals(listOf("b", "a"), pickRuleSongs(songs, c, Random(1), stats = stats).map { it.id })
    }
}
