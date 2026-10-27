package app.siansa.net

import app.siansa.data.Album
import app.siansa.data.creditedNames
import app.siansa.data.splitArtists
import app.siansa.data.isVariousArtists
import app.siansa.ui.AlbumFoot
import app.siansa.ui.albumFoot
import app.siansa.ui.albumHeadline
import app.siansa.ui.tagRows
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Common tags from Navidrome (OpenSubsonic) and Emby are read and shown (issue #504). */
class TagsTest {
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun navidromeSongTags() {
        val t = Tags.subsonicSong(json("""
            {"id":"1","title":"Tarantula","artist":"Pendulum • Fresh","year":2005,"bpm":174,"explicitStatus":"clean",
             "artists":[{"id":"p","name":"Pendulum"},{"id":"f","name":"Fresh"}],
             "albumArtists":[{"id":"p","name":"Pendulum"}],
             "contributors":[{"role":"composer","artist":{"id":"r","name":"Rob Swire"}},
                             {"role":"composer","artist":{"id":"g","name":"Gareth McGrillen"}},
                             {"role":"performer","subRole":"vocals","artist":{"id":"s","name":"Spyda"}},
                             {"role":"djmixer","artist":{"id":"x","name":"Hype"}}],
             "genres":[{"name":"Drum & Bass"},{"name":"Electronic"}],"moods":["energetic"],
             "isrc":["GBCPZ0500123"],"comment":"Album version","musicBrainzId":"abc-123"}
        """))
        assertEquals(listOf("Pendulum", "Fresh"), t.artists)
        assertEquals(listOf("Pendulum"), t.albumArtists)
        assertEquals(
            listOf("Composer" to listOf("Rob Swire", "Gareth McGrillen"), "Performer (vocals)" to listOf("Spyda"), "DJ mixer" to listOf("Hype")),
            t.roles,
        )
        assertEquals(listOf("Drum & Bass", "Electronic"), t.genres)
        assertEquals(174, t.bpm)
        assertEquals(false, t.explicit)
        assertEquals(listOf("GBCPZ0500123"), t.isrc)
    }

    @Test fun olderServersStillGiveTheBasics() {
        val t = Tags.subsonicSong(json("""{"id":"1","title":"A","genre":"Jazz","displayComposer":"Miles Davis","explicitStatus":""}"""))
        assertEquals(listOf("Jazz"), t.genres)
        assertEquals(listOf("Composer" to listOf("Miles Davis")), t.roles)
        assertNull(t.explicit)
        assertTrue(t.artists.isEmpty())
    }

    @Test fun navidromeAlbumInfo() {
        val a = Tags.subsonicAlbum(json("""
            {"id":"a","name":"Rumours","version":"Super Deluxe","year":2013,"originalReleaseDate":{"year":1977,"month":2},
             "recordLabels":[{"name":"Warner"}],"genres":[{"name":"Rock"},{"name":"Soft Rock"}],"explicitStatus":"explicit"}
        """))
        assertEquals("Super Deluxe", a.version)
        assertEquals(1977, a.originalYear)
        assertEquals(listOf("Warner"), a.labels)
        assertEquals(true, a.explicit)
    }

    @Test fun embySongTags() {
        val t = Tags.embySong(json("""
            {"Id":"9","Name":"Song","Artists":["A","B"],"AlbumArtists":[{"Name":"A","Id":"1"}],"Genres":["Pop"],
             "ProductionYear":2019,"PremiereDate":"2019-05-17T00:00:00.0000000Z",
             "People":[{"Name":"C","Type":"Composer"},{"Name":"D","Type":"Lyricist"},{"Name":"E","Type":"Actor"}],
             "ProviderIds":{"MusicBrainzTrack":"mb-1"}}
        """))
        assertEquals(listOf("A", "B"), t.artists)
        assertEquals(listOf("Composer" to listOf("C"), "Lyricist" to listOf("D")), t.roles)
        assertEquals("2019-05-17", t.released)
        assertEquals("mb-1", t.musicBrainzId)
        // A date that's only the year says nothing more.
        assertNull(Tags.embySong(json("""{"PremiereDate":"2019-01-01T00:00:00Z"}""")).released)
    }

    @Test fun embyAlbumLabels() {
        val a = Tags.embyAlbum(json("""{"Id":"a","Studios":[{"Name":"Warp"}],"Genres":["IDM"]}"""))
        assertEquals(listOf("Warp"), a.labels)
        assertEquals(listOf("IDM"), a.genres)
    }

    @Test fun roleNames() {
        assertEquals("Composer", Tags.roleLabel("composer", null))
        assertEquals("Mixed by", Tags.roleLabel("mixer", ""))
        assertEquals("Guest conductor", Tags.roleLabel("GuestConductor", null))
    }

    @Test fun variousArtistsIsACompilation() {
        assertTrue(isVariousArtists("Various Artists"))
        assertTrue(isVariousArtists("VA"))
        assertTrue(isVariousArtists("Verschiedene Künstler"))
        assertFalse(isVariousArtists("Various Cruelties"))
        assertFalse(isVariousArtists(""))
    }

    @Test fun detailsShowOnlyTheTagsThereAre() {
        assertTrue(tagRows(SongTags()).isEmpty())
        val rows = tagRows(SongTags(artists = listOf("A"), genres = listOf("Pop", "Rock"), explicit = true, bpm = 120)).toMap()
        assertFalse("One artist is already on the page", "Artists" in rows)
        assertEquals("Pop, Rock", rows["Genres"])
        assertEquals("Explicit", rows["Lyrics"])
        assertEquals("120", rows["BPM"])
    }

    @Test fun albumHeadlineShowsOnlyTypeAndYear() {
        val album = Album("a", "Rumours", "Fleetwood Mac", year = 2013, genre = "Rock")
        val info = AlbumInfo(version = "Super Deluxe", originalYear = 1977, labels = listOf("Warner"), genres = listOf("Rock", "Soft Rock"))
        assertEquals("Album · 1977 · reissued 2013", albumHeadline(album, info))
        assertEquals("Album · 2013", albumHeadline(album, AlbumInfo()))
    }

    @Test fun albumFootHasGenresLabelsVersionAndLength() {
        val album = Album("a", "Rumours", "Fleetwood Mac", year = 2013, genre = "Rock", labels = listOf("Reprise"))
        val info = AlbumInfo(version = "Super Deluxe", labels = listOf("Warner"), genres = listOf("Rock", "Soft Rock"))
        assertEquals(AlbumFoot(listOf("Rock", "Soft Rock"), listOf("Warner"), "Super Deluxe", "38 songs · 2 h 40 min"), albumFoot(album, info, 38, "2 h 40 min"))
        // From the library alone: its one genre and the labels the sync read.
        assertEquals(AlbumFoot(listOf("Rock"), listOf("Reprise"), null, "1 song · 4 min"), albumFoot(album, AlbumInfo(), 1, "4 min"))
    }

    @Test fun navidromesOwnJoinerSplitsArtists() {
        assertEquals(listOf("Pendulum", "Fresh"), splitArtists("Pendulum • Fresh"))
        assertTrue("fresh" in creditedNames("Pendulum • Fresh"))
    }
}
