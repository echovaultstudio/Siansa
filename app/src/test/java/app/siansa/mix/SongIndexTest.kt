package app.siansa.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The time machine matches Koito and Last.fm songs to the library through one index (issue #186). */
class SongIndexTest {
    private val index = SongIndex(
        listOf(
            SongIndex.Entry("1", "Left Hand Free", "alt-J"),
            SongIndex.Entry("2", "Ghost", "Mystery Skulls"),
            SongIndex.Entry("3", "Ghost", "Justin Bieber"),
            SongIndex.Entry("4", "Déjà Vu", "Beyoncé feat. Jay-Z"),
            SongIndex.Entry("5", "Intro", ""),
        )
    )

    @Test fun sameTitleAndArtistInAnyCase() = assertEquals("1", index.find("ALT-J", "left hand free"))

    @Test fun theRightArtistAmongTheSameTitles() {
        assertEquals("3", index.find("Justin Bieber", "Ghost"))
        assertEquals("2", index.find("Mystery Skulls", "Ghost"))
    }

    @Test fun accentsAndFeaturedArtists() = assertEquals("4", index.find("Beyonce", "Deja vu"))

    @Test fun noArtistTakesTheTitle() = assertEquals("2", index.find("", "Ghost"))

    @Test fun wrongArtistIsNoMatch() = assertNull(index.find("Radiohead", "Ghost"))

    @Test fun aSongWithoutArtistOnlyMatchesWithoutArtist() {
        // An empty artist in the library is "contained" in every name; it mustn't match any artist.
        assertNull(index.find("Bonobo", "Intro"))
        assertEquals("5", index.find("", "Intro"))
    }

    @Test fun unknownTitle() = assertNull(index.find("alt-J", "Breezeblocks"))

    @Test fun exactArtistIsPreferred() {
        val i = SongIndex(listOf(SongIndex.Entry("a", "Kerala", "Bonobo feat. Someone"), SongIndex.Entry("b", "Kerala", "Bonobo")))
        assertEquals("b", i.find("Bonobo", "Kerala"))
    }

    // ---- Issue #661: songs in the library showed as not in it ----

    private val beatles = SongIndex(
        listOf(
            SongIndex.Entry("lt", "I'm Looking Through You - Remastered 2009", "The Beatles"),
            SongIndex.Entry("sly", "She Loves You (Mono)", "The Beatles"),
            SongIndex.Entry("hj", "Hey Jude", "The Beatles"),
            SongIndex.Entry("dv", "Déjà Vu (feat. Jay-Z)", "Beyoncé"),
            SongIndex.Entry("cw", "Wonderwall", "Oasis"),
            SongIndex.Entry("cwl", "Wonderwall (Live at Knebworth)", "Oasis"),
            SongIndex.Entry("dont", "Don't Stop Me Now", "Queen"),
        ),
        versions = true,
    )

    @Test fun aRemasteredOrMonoVersionIsTheSameSong() {
        assertEquals("lt", beatles.find("The Beatles", "I'm Looking Through You"))
        assertEquals("sly", beatles.find("The Beatles", "She Loves You"))
        assertEquals("sly", beatles.find("The Beatles", "She Loves You - 2009 Remaster"))
    }

    @Test fun featuredArtistsInTheTitleDontCount() {
        assertEquals("dv", beatles.find("Beyoncé", "Deja Vu"))
        assertEquals("dv", beatles.find("Beyonce", "Déjà Vu feat. Jay-Z"))
    }

    @Test fun apostrophesAndTheDontCount() {
        assertEquals("lt", beatles.find("Beatles", "Im Looking Through You"))
        assertEquals("dont", beatles.find("Queen", "Dont Stop Me Now"))
        assertEquals("hj", beatles.find("Beatles", "Hey Jude"))
    }

    @Test fun theClosestVersionWinsAndOtherVersionsAreTheLastResort() {
        assertEquals("cw", beatles.find("Oasis", "Wonderwall"))
        assertEquals("cwl", beatles.find("Oasis", "Wonderwall (Live at Knebworth)"))
        assertEquals("cw", beatles.find("Oasis", "Wonderwall (Remastered)"))
        val onlyLive = SongIndex(listOf(SongIndex.Entry("live", "Wonderwall (Live at Knebworth)", "Oasis")), versions = true)
        assertEquals("live", onlyLive.find("Oasis", "Wonderwall"))
    }

    @Test fun withoutVersionsAnotherVersionIsAnotherSong() {
        // Finding the very song AudioMuse means: an acoustic or remastered version isn't it.
        val strict = SongIndex(listOf(SongIndex.Entry("o", "Ignorance (Acoustic Version)", "Paramore"), SongIndex.Entry("im", "I'm Yours", "The Jason Mraz")))
        assertNull(strict.find("Paramore", "Ignorance"))
        assertEquals("im", strict.find("Jason Mraz", "Im Yours"))  // apostrophes and "The" still don't count
    }

    @Test fun anotherArtistIsStillNoMatch() {
        assertNull(beatles.find("The Rolling Stones", "Hey Jude"))
        assertNull(beatles.find("Radiohead", "Wonderwall"))
    }

    @Test fun versionsAreLeftOutOnlyWhenTheyAreVersions() {
        assertEquals("Song", SongIndex.withoutVersion("Song - Remastered 2011"))
        assertEquals("Song", SongIndex.withoutVersion("Song (Radio Edit) [Explicit]"))
        assertEquals("Song (Interlude)", SongIndex.withoutVersion("Song (Interlude)"))
        assertEquals("Song - Part 2", SongIndex.withoutVersion("Song - Part 2"))
        assertEquals("Song", SongIndex.baseTitle("Song - Part 2"))
        assertEquals("(Untitled)", SongIndex.baseTitle("(Untitled)"))
    }
}
