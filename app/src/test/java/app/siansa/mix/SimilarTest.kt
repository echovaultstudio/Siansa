package app.siansa.mix

import app.siansa.data.AppJson
import app.siansa.data.SimilarArtist
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Siansa plugin's similar albums and artists, and "Fans also like" (issue #223). */
class SimilarTest {
    private fun json(text: String) = AppJson.parseToJsonElement(text).jsonObject

    @Test fun readsSimilarAlbumsAsThePluginSendsThem() {
        // The layout of audiomuse-plugin/siansa_analysis/web.py's api/similar/albums.
        val root = json(
            """{"schema_version":1,"generation":3,"name":"Night","artist":"Ann","track_id":"s1","similar":[
              {"album":"Coast","artist":"Bea","track_id":"nd-7","score":0.93},
              {"album":"Day","artist":"Ann","track_id":"nd-9","score":0.81},
              {"artist":"No album","track_id":"x"},
              {"album":"No song","artist":"Cal"}]}""",
        )
        assertEquals(
            listOf(SimilarAlbumHit("Coast", "Bea", "nd-7", 0.93), SimilarAlbumHit("Day", "Ann", "nd-9", 0.81)),
            SimilarPlugin.parseAlbums(root),
        )
        assertTrue(SimilarPlugin.parseAlbums(json("""{"schema_version":1,"generation":0,"similar":[]}""")).isEmpty())
        assertTrue(SimilarPlugin.parseAlbums(json("""{"error":"AudioMuse doesn't know this song"}""")).isEmpty())
    }

    @Test fun readsSimilarArtists() {
        val root = json("""{"name":"ann","similar":[{"artist":"Bea","track_id":"nd-7","score":0.9},{"track_id":"x"}]}""")
        assertEquals(listOf(SimilarArtistHit("Bea", "nd-7", 0.9)), SimilarPlugin.parseArtists(root))
    }

    @Test fun queriesAreEncoded() {
        assertEquals("/plugins/siansa_beats/api/similar/albums?track_id=a%2Fb+c", SimilarPlugin.albumsQuery("a/b c"))
        assertEquals("/plugins/siansa_beats/api/similar/artists?artist=Simon+%26+Garfunkel", SimilarPlugin.artistsQuery("Simon & Garfunkel"))
    }

    @Test fun fansAlsoLikePutsSoundAlikesFirstWithoutDoublesOrTheArtistItself() {
        val plugin = listOf(SimilarArtist("2", "Bea", null, null), SimilarArtist("1", "Ann", null, null))
        val navidrome = listOf(
            SimilarArtist("3", "Cal", null, null),
            SimilarArtist("2", "bea", null, null), // the same artist, also from Navidrome
            SimilarArtist(null, "Dee", null, "https://img"),
            SimilarArtist("2", "Bea (other spelling)", null, null), // same id
        )
        val merged = SimilarPlugin.mergeArtists("Ann", plugin, navidrome)
        assertEquals(listOf("Bea", "Cal", "Dee"), merged.map { it.name })
        assertEquals(navidrome.take(1), SimilarPlugin.mergeArtists("Ann", emptyList(), navidrome.take(1)))
        assertEquals(2, SimilarPlugin.mergeArtists("Zed", plugin, navidrome, limit = 2).size)
    }

    /** Similar albums and artists are asked of AudioMuse-AI itself; Siansa Beats only passes them on (the owner, 11 Nov). */
    @Test fun audioMuseIsAskedFirstAndSiansaBeatsOnlyWhenItCant() {
        assertEquals(listOf(Similar.AskFrom.AUDIOMUSE, Similar.AskFrom.BEATS), Similar.askOrder(audioMuseReady = true, beatsReady = true))
        assertEquals(listOf(Similar.AskFrom.AUDIOMUSE), Similar.askOrder(audioMuseReady = true, beatsReady = false))
        assertEquals(listOf(Similar.AskFrom.BEATS), Similar.askOrder(audioMuseReady = false, beatsReady = true))
        assertEquals(emptyList<Similar.AskFrom>(), Similar.askOrder(audioMuseReady = false, beatsReady = false))
    }

    /** Siansa Beats 0.25 works similar albums and artists out itself: then it's asked first. */
    @Test fun siansaBeatsIsAskedFirstOnceItHasTheSongsSound() {
        assertEquals(listOf(Similar.AskFrom.BEATS, Similar.AskFrom.AUDIOMUSE), Similar.askOrder(audioMuseReady = true, beatsReady = true, beatsSound = true))
        assertEquals(listOf(Similar.AskFrom.BEATS), Similar.askOrder(audioMuseReady = false, beatsReady = true, beatsSound = true))
        // Its sound without it being set up here means nothing.
        assertEquals(listOf(Similar.AskFrom.AUDIOMUSE), Similar.askOrder(audioMuseReady = true, beatsReady = false, beatsSound = true))
    }

    /**
     * Report 40469175 (issue #1071): similar albums of an album added minutes before. Siansa Beats 0.35 had the sound of
     * the library but not of that album yet, asked AudioMuse-AI (switched off) and answered 502 with its trouble; the TV
     * showed "Siansa Beats answered HTTP 502: Couldn't reach AudioMuse-AI: [Errno 111] Connection refused".
     */
    @Test fun siansaBeatsNotKnowingAnAlbumYetIsSaidPlainly() {
        val reported = "Siansa Beats answered HTTP 502: Couldn't reach AudioMuse-AI: [Errno 111] Connection refused"
        assertTrue(Similar.beatsCouldntSay(reported, beatsSound = true))
        assertTrue(Similar.beatsCouldntSay("Siansa Beats answered HTTP 502: AudioMuse-AI isn't set up for Siansa Beats: add it on its page (Setup › AudioMuse-AI)", beatsSound = true))
        // A Siansa Beats without the songs' sound only passes questions on: then it is AudioMuse-AI's trouble.
        assertFalse(Similar.beatsCouldntSay(reported, beatsSound = false))
        // Anything else is said as it is.
        assertFalse(Similar.beatsCouldntSay("Siansa Beats wants its API key or password: check it in Settings › Connections", beatsSound = true))
        assertFalse(Similar.beatsCouldntSay("Failed to connect to /192.168.1.106:8090", beatsSound = true))
        assertFalse(Similar.beatsCouldntSay(null, beatsSound = true))
    }
}
