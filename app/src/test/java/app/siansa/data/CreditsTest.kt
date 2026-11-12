package app.siansa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Featured and compilation artists are found and their pages split own releases from "Appears on" (beta testers #34). */
class CreditsTest {
    private val pendulum = ArtistRef("p", "Pendulum")
    private val djFresh = ArtistRef("f", "DJ Fresh")
    private val tarantula = Song(
        "s1", "Tarantula", "Pendulum feat. DJ Fresh", "p", "Hold Your Colour", "a1",
        artists = listOf(pendulum, djFresh),
    )
    private val dropDat = Song("s2", "Drop Dat", "T95", "t95", "Heavyweight D&B", "va")

    @Test fun creditsAreKeptAndReadBack() {
        val stored = Credits.encode(tarantula)
        assertEquals(listOf(pendulum, djFresh), Credits.decode(stored))
        assertEquals("Pendulum DJ Fresh", Credits.names(stored))
        // A song with only its main artist keeps nothing extra.
        assertEquals("", Credits.encode(dropDat.copy(artists = listOf(ArtistRef("t95", "T95")))))
        assertTrue(Credits.decode("not json").isEmpty())
        assertTrue(Credits.decode("").isEmpty())
    }

    @Test fun aFeaturedArtistIsFound() {
        val found = Credits.matching(listOf(tarantula), listOf("dj", "fresh"), known = emptySet())
        assertEquals(listOf(djFresh to 1), found)
    }

    @Test fun aCompilationArtistIsFound() {
        val found = Credits.matching(listOf(dropDat), listOf("t95"), known = emptySet())
        assertEquals(listOf(ArtistRef("t95", "T95") to 1), found)
    }

    @Test fun artistsAlreadyListedAndCombinedNamesAreLeftOut() {
        assertTrue(Credits.matching(listOf(tarantula), listOf("fresh"), known = setOf("f")).isEmpty())
        // "Pendulum feat. DJ Fresh" is no artist of its own.
        val noCredits = tarantula.copy(artists = emptyList())
        assertTrue(Credits.matching(listOf(noCredits), listOf("fresh"), known = emptySet()).isEmpty())
    }

    @Test fun theExactNameComesFirst() {
        val names = listOf("Fresh Prince", "Freshlyground", "DJ Fresh", "Fresh")
        assertEquals(listOf("Fresh", "Fresh Prince", "Freshlyground", "DJ Fresh"), rankByName("fresh", names) { it })
        assertEquals(listOf("DJ Fresh", "Fresh Prince", "Freshlyground", "Fresh"), rankByName("dj fresh", names) { it })
    }

    @Test fun theSongCalledWhatWasTypedComesFirst() {
        // The owner's searches, 12 Nov.
        fun song(title: String, artist: String, album: String = "") = Song(id = title, title = title, artist = artist, album = album)
        val gita = listOf(
            song("Digital Bath (acoustic)", "Deftones"), song("bLeePs and NoiSes", "The Digital Kid"),
            song("Insane", "A-Star vs. Digital Cowboy"), song("Gita (sped up version)", "Moderat"), song("Gita", "Moderat", "II"),
        )
        assertEquals(listOf("Gita", "Gita (sped up version)"), bestFirst("Gita", gita, { it.title }) { "${it.artist} ${it.album}" }.take(2).map { it.title })
        val right = listOf(
            song("Why the Bright Stars Glow", "Valerie June"), song("Unchained Melody", "The Righteous Brothers"),
            song("Bright Eyes", "Lonely The Brave"), song("The Right Place (Live Mix)", "DJ Seinfeld & Teira"),
            song("The Right", "DJ Seinfeld • Confidence Man", "If This Is It"),
        )
        val found = SearchResults(emptyList(), emptyList(), right)
        assertEquals(listOf("The Right", "The Right Place (Live Mix)"), found.bestFirst("The right").songs.take(2).map { it.title })
        // With the artist typed too, the song of just those words is still before the longer one.
        assertEquals(listOf("The Right", "The Right Place (Live Mix)"), found.bestFirst("dj seinfeld the right").songs.take(2).map { it.title })
    }

    @Test fun howWellANameAnswersASearch() {
        assertEquals(0, searchRank("gita", "Gita"))
        assertEquals(1, searchRank("gita", "Gita (sped up version)"))
        assertEquals(2, searchRank("bath", "Digital Bath"))
        assertEquals(3, searchRank("dj seinfeld the right", "The Right", "DJ Seinfeld If This Is It"))
        assertEquals(4, searchRank("moderat gita", "Gita (sped up version)", "Moderat II"))
        assertEquals(5, searchRank("gita", "Digital Bath", "Deftones"))
        assertEquals(5, searchRank("the right", "Why the Bright Stars Glow", "Valerie June"))
        // Accents and case don't count; albums go by their name with their artist.
        assertEquals(0, searchRank("BJORK", "Björk"))
        val albums = listOf(Album("a1", "Digital Nowhere", "Profiler", null), Album("a2", "Gita", "Someone", null))
        assertEquals("Gita", SearchResults(emptyList(), albums, emptyList()).bestFirst("gita").albums.first().name)
    }

    @Test fun onlyTheArtistsOwnAlbumsAreReleases() {
        val artist = Artist("f", "DJ Fresh")
        val own = Album("al1", "Kryptonite", "DJ Fresh", "f")
        val byName = Album("al2", "Gold Dust", "DJ FRESH", null)
        val mixtape = Album("al3", "Flava D Mixtape", "Flava D", "fd")
        val (mine, others) = splitOwnAlbums(artist, listOf(own, mixtape, byName))
        assertEquals(listOf(own, byName), mine)
        assertEquals(listOf(mixtape), others)
    }

    @Test fun anArtistFromSongCreditsStillHasAPicture() {
        // Found through "feat. DJ Fresh" only: no cover from the server, so the artist's own picture address.
        assertEquals("ar-f", Artist("f", "DJ Fresh").picture)
        assertEquals("ar-f_abc", Artist("f", "DJ Fresh", coverArt = "ar-f_abc").picture)
    }
}
