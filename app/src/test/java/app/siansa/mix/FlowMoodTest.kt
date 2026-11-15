package app.siansa.mix

import app.siansa.data.Genre
import app.siansa.net.ClapHit
import app.siansa.net.Steer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Flow's mood pad (the owner's pick, 15 Nov): its names, colours and the sounds and genres picked with +. */
class FlowMoodTest {
    private fun at(energy: Int, tempo: Int) = FlowMood.name(FlowMood.at(energy), FlowMood.at(tempo))

    @Test fun everyPlaceOnThePadHasAName() {
        assertEquals("Balanced", at(0, 0))
        assertEquals("Lift me up", at(2, 0))
        assertEquals("Lift me up", at(2, 1))
        assertEquals("Wind down", at(-1, 0))
        assertEquals("Keep it moving", at(0, 2))
        assertEquals("Take it slow", at(-1, -2))
        assertEquals("Get moving", at(2, 2))
        assertEquals("Slow burn", at(1, -1))
        assertEquals("Easy groove", at(-1, 1))
        assertEquals("Late-night drift", at(-2, -2))
        // While a finger drags, between the steps: barely off the middle is still balanced.
        assertEquals("Balanced", FlowMood.name(0.1f, -0.1f))
    }

    @Test fun theMoodSaysWhatItDoesInPlainWords() {
        assertEquals("Right in the middle: not too calm, not too busy.", FlowMood.about(0f, 0f))
        assertEquals("Calmer and a bit faster", FlowMood.about(-0.5f, 0.5f))
        assertEquals("High energy and fast", FlowMood.feel(1f, 1f))
        assertEquals("Very calm and slow", FlowMood.feel(-1f, -1f))
        assertEquals("Livelier", FlowMood.feel(0.5f, 0f))
        assertEquals("A bit slower", FlowMood.feel(0f, -0.5f))
    }

    @Test fun theSummaryNamesTheMoodThePicksAndWhoseMusic() {
        val t = VibeTuning(
            energy = -1, tempo = 1, familiar = -2,
            sounds = listOf(SoundDial("Piano", 3), SoundDial("vocals", -3), SoundDial("Jazz", 3, genre = true), SoundDial("guitar", 0)),
        )
        assertEquals("Calmer and a bit faster · more piano, jazz · less vocals · new to you", FlowMood.summary(t))
        assertEquals("Balanced · your favourites", FlowMood.summary(VibeTuning(familiar = 2)))
    }

    @Test fun eachCornerHasItsColourAndTheMiddleItsOwn() {
        assertArrayEquals(intArrayOf(43, 58, 143), FlowMood.colour(-1f, -1f))
        assertArrayEquals(intArrayOf(31, 138, 138), FlowMood.colour(-1f, 1f))
        assertArrayEquals(intArrayOf(176, 64, 154), FlowMood.colour(1f, -1f))
        assertArrayEquals(intArrayOf(255, 138, 61), FlowMood.colour(1f, 1f))
        // Not the corners' muddy average (126, 100, 124): mostly the middle's own violet.
        val middle = FlowMood.colour(0f, 0f)
        assertArrayEquals(intArrayOf(110, 93, 196), middle)
        // Past the pad's edge is the edge.
        assertArrayEquals(FlowMood.colour(1f, 1f), FlowMood.colour(3f, 2f))
        // Dark ink only on the light, lively and fast corner.
        assertTrue(FlowMood.darkInk(FlowMood.colour(1f, 1f)))
        assertFalse(FlowMood.darkInk(middle))
        assertFalse(FlowMood.darkInk(FlowMood.colour(-1f, -1f)))
    }

    @Test fun aPickIsMoreThenLessThenGone() {
        var t = FlowMood.pick(VibeTuning(), "piano", genre = false)
        assertEquals(listOf(SoundDial("piano", FlowMood.MORE)), t.sounds)
        assertEquals(t, FlowMood.pick(t, "Piano", genre = true))  // picked already
        t = FlowMood.tap(t, "PIANO")
        assertEquals(FlowMood.LESS, t.sounds.single().value)
        t = FlowMood.tap(t, "piano")
        // Gone, but its dial stays in the middle: a vibe's last pick taken off doesn't bring the AI's sounds back.
        assertEquals(listOf(SoundDial("piano", 0)), t.sounds)
        assertTrue(FlowMood.picks(t).isEmpty())
        assertTrue(t.isNeutral)
        assertFalse(FlowMood.picked(t, "piano"))
        // Picked again, as a genre this time: one dial, not two.
        t = FlowMood.pick(t, "Piano", genre = true)
        assertEquals(listOf(SoundDial("Piano", FlowMood.MORE, genre = true)), t.sounds)
        assertTrue(FlowMood.drop(t, "piano").isNeutral)
    }

    @Test fun noMoreThanSixArePicked() {
        var t = VibeTuning()
        ('a'..'h').forEach { t = FlowMood.pick(t, it.toString(), genre = false) }
        assertEquals(FlowMood.MOST_PICKS, FlowMood.picks(t).size)
        assertFalse(FlowMood.hasRoom(t))
        // One taken off makes room for another.
        t = FlowMood.pick(FlowMood.drop(t, "a"), "z", genre = true)
        assertEquals(listOf("b", "c", "d", "e", "f", "z"), FlowMood.picks(t).map { it.term })
    }

    @Test fun aSoundSteersTheSearchAndAGenreReRanksIt() {
        val t = FlowMood.pick(FlowMood.pick(VibeTuning(), "piano", genre = false), "Jazz", genre = true)
        assertEquals(listOf(Steer("piano", true, 3)), t.steering())
        // With no words typed, both are searched for.
        assertEquals("piano Jazz music", sliderQuery(t))
        val hits = listOf("s1", "s2", "s3").map { ClapHit(it, "T$it", "A$it", null, 0.5) }
        val tags = mapOf("s3" to tagGenres("Jazz; Soul"), "s1" to tagGenres("Rock"))
        assertEquals("s3", rankVibe(hits, emptyMap(), emptyMap(), t, tags).first().id)
        assertEquals("s3", rankVibe(hits, emptyMap(), emptyMap(), FlowMood.tap(t, "jazz"), tags).last().id)
    }

    @Test fun genreTagsAreSplitIntoGenres() {
        assertEquals(listOf("Rock", "Indie", "Lo-Fi"), tagGenres("Rock; Indie / Lo-Fi, rock").map { it.name })
        assertTrue(tagGenres(null).isEmpty())
        assertTrue(tagGenres(" ; ").isEmpty())
    }

    @Test fun myPicksGoOverTheAisOwnSounds() {
        val ai = VibeTuning.start(listOf(Steer("piano", true, 5), Steer("rain", true)), emptyList())
        val mine = VibeTuning(energy = -2, familiar = 2, sounds = listOf(SoundDial("Piano", FlowMood.LESS), SoundDial("Jazz", FlowMood.MORE, genre = true), SoundDial("gone", 0)))
        val both = FlowMood.over(ai, mine)
        assertEquals(listOf(SoundDial("Piano", FlowMood.LESS), SoundDial("Jazz", FlowMood.MORE, genre = true), SoundDial("rain", 3)), both.sounds)
        assertEquals(-2, both.energy)
        assertEquals(2, both.familiar)
        // Nothing picked: the AI's sounds as they are.
        assertEquals(ai, FlowMood.over(ai, VibeTuning()))
    }

    @Test fun aMixWithoutWordsIsNamedAfterItsMood() {
        assertEquals("Balanced", FlowMood.title(VibeTuning()))
        assertEquals("Easy groove with jazz and piano", FlowMood.title(VibeTuning(energy = -1, tempo = 1, sounds = listOf(SoundDial("Jazz", 3, genre = true), SoundDial("vocals", -3), SoundDial("piano", 3), SoundDial("guitar", 3)))))
    }

    /** The owner, 15 Nov: "more progressive metal" on a calm search got a downtempo song. A picked genre comes first. */
    @Test fun aPickedGenreComesFirstAndALessOneIsLeftOut() {
        val hits = listOf("a", "b", "c", "d").map { ClapHit(it, "T$it", "A$it", null, 0.5) }
        val fill = listOf("x", "b", "y", "z").map { ClapHit(it, "T$it", "A$it", null, 0.0) }
        val id = { hit: ClapHit -> hit.id }
        // Only the genre's songs of what the search found, best first, then more of the genre the search didn't find.
        assertEquals(listOf("b", "d", "x", "y"), genreFirst(hits, id, wanted = setOf("b", "d", "x", "y", "z"), unwanted = emptySet(), fill = fill, most = 4).map { it.id })
        // None of the search's songs is of the genre: the mix is still the genre's.
        assertEquals(listOf("x", "y", "z"), genreFirst(hits, id, wanted = setOf("x", "y", "z"), unwanted = emptySet(), fill = fill, most = 10).map { it.id })
        // "Less" leaves the genre's songs out, of the search's and of what's added.
        assertEquals(listOf("a", "c"), genreFirst(hits, id, wanted = null, unwanted = setOf("b", "d"), fill = fill, most = 2).map { it.id })
        assertEquals(listOf("d", "x"), genreFirst(hits, id, wanted = setOf("b", "d", "x"), unwanted = setOf("b"), fill = fill, most = 5).map { it.id })
        // Enough of the genre among the search's: nothing is added.
        assertEquals(listOf("a", "b"), genreFirst(hits, id, wanted = setOf("a", "b", "c", "x"), unwanted = emptySet(), fill = fill, most = 2).map { it.id })
    }

    @Test fun theSoundsToPickLeaveTheSearchsGenreWordsOut() {
        val catalogue = app.siansa.net.ConceptCatalogue(
            listOf(
                app.siansa.net.ConceptCategory("genre", "Genre", listOf("progressive rock", "jazz")),
                app.siansa.net.ConceptCategory("instrument", "Instrument", listOf("piano", "guitar")),
                app.siansa.net.ConceptCategory("mood", "Mood", listOf("dreamy", "Piano")),
            ),
            10,
        )
        assertEquals(listOf("piano", "guitar", "dreamy"), soundsOf(catalogue))
    }

    @Test fun aFlowMixLivesUntilPlaybackStopsOrSomethingElsePlays() {
        assertTrue(FlowMix.isLive(started = 7, now = 7, hasMedia = true))
        // Another list was started, or playback was stopped (both count the player's list on).
        assertFalse(FlowMix.isLive(started = 7, now = 8, hasMedia = true))
        // Nothing in the player.
        assertFalse(FlowMix.isLive(started = 7, now = 7, hasMedia = false))
    }

    @Test fun steeringChangesOnlyWhatComesNext() {
        fun song(id: String) = app.siansa.data.Song(id = id, title = "T$id", artist = "A", album = "B")
        val heard = listOf(song("1"), song("2"))
        // What has played and what's playing aren't queued again, and a song comes once.
        assertEquals(listOf("5", "3", "4"), FlowMix.upcoming(listOf(song("2"), song("5"), song("3"), song("5"), song("1"), song("4")), heard).map { it.id })
        assertTrue(FlowMix.upcoming(heard, heard).isEmpty())
    }

    @Test fun artistsAreThreeChoices() {
        assertEquals(listOf(0, 1, 2), listOf(2, 0, -1).map { FlowMood.artists(it) })
        assertEquals(listOf(2, 0, -2), listOf(0, 1, 2).map { FlowMood.familiar(it) })
        for (choice in 0..2) assertEquals(choice, FlowMood.artists(FlowMood.familiar(choice)))
    }

    @Test fun theMainGenresAreOfferedAndSearchingReachesTheRest() {
        val all = (1..40).map { Genre("Genre $it", 1000 - it) } + Genre("Zydeco", 2) + Genre("Jazz Fusion", 1) + Genre("Acid Jazz", 1)
        assertEquals(FlowMood.MAIN_GENRES, FlowMood.genres(all, "").size)
        assertEquals("Genre 1", FlowMood.genres(all, " ").first().name)
        assertEquals(listOf("Zydeco"), FlowMood.genres(all, "zyd").map { it.name })
        // Those starting with what's typed first.
        assertEquals(listOf("Jazz Fusion", "Acid Jazz"), FlowMood.genres(all, "JAZZ").map { it.name })
        assertTrue(FlowMood.genres(all, "polka").isEmpty())
    }
}
