package app.siansa.local

import app.siansa.data.Artist
import app.siansa.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Music files on the phone in the library: tags, artists, albums and matching to the server (issue #352). */
class LocalLibraryTest {
    private val mbA = "11111111-2222-3333-4444-555555555555"
    private val mbB = "66666666-7777-8888-9999-000000000000"
    private val rec = "7d8a3f4e-1c2b-4a5d-9e6f-0123456789ab"

    private fun track(
        file: String,
        title: String,
        artist: String,
        album: String = "Record",
        artists: List<String> = listOf(artist),
        artistMbids: List<String> = emptyList(),
        albumArtist: String = "",
        duration: Int = 200,
        recording: String? = null,
        hasCover: Boolean = false,
    ) = LocalTrack(
        uri = "content://tree/$file", folder = "content://tree", path = file, title = title, artist = artist, artists = artists,
        album = album, albumArtist = albumArtist, durationSec = duration, artistMbids = artistMbids, recordingMbid = recording, hasCover = hasCover,
    )

    @Test fun tagsBecomeATrack() {
        val t = LocalTags.track(
            "content://x/1", "content://x", "Album/01 Song.flac", 10, 20, "audio/flac",
            mapOf(
                "TITLE" to listOf("Song"), "ARTIST" to listOf("A", "B"), "TRACKNUMBER" to listOf("03/12"), "DISCNUMBER" to listOf("2"),
                "DATE" to listOf("2019-05-01"), "REPLAYGAIN_TRACK_GAIN" to listOf("-6,5 dB"), "REPLAYGAIN_TRACK_PEAK" to listOf("0.98"),
                "MUSICBRAINZ_TRACKID" to listOf(rec.uppercase()), "MUSICBRAINZ_ARTISTID" to listOf("$mbA/$mbB"), "COMPILATION" to listOf("1"),
            ),
            RetrieverTags(album = "From Android", durationMs = 201_400),
        )
        assertEquals("Song", t.title)
        assertEquals("A, B", t.artist)
        assertEquals(listOf("A", "B"), t.artists)
        assertEquals("From Android", t.album)
        assertEquals(3, t.track)
        assertEquals(2, t.disc)
        assertEquals(2019, t.year)
        assertEquals(201, t.durationSec)
        assertEquals(-6.5f, t.trackGain)
        assertEquals(0.98f, t.trackPeak)
        assertEquals(rec, t.recordingMbid)
        assertEquals(listOf(mbA, mbB), t.artistMbids)
        assertTrue(t.compilation)
        assertEquals("flac", t.suffix)
        assertFalse(t.wantsLookup)
    }

    @Test fun aFileWithoutTagsIsNamedAfterItself() {
        val t = LocalTags.track("u", "f", "Some Folder/Track 7.mp3", 0, 0, null, emptyMap(), RetrieverTags())
        assertEquals("Track 7", t.title)
        assertEquals("", t.artist)
        assertFalse(t.wantsLookup)
        assertNull(LocalTags.number("0"))
        assertEquals(listOf(mbA), LocalTags.mbids(listOf("not an id", mbA, mbA)))
    }

    @Test fun localIdsAreTheirOwn() {
        val id = LocalIds.song("content://tree/a.flac")
        assertTrue(LocalIds.isLocal(id))
        assertTrue(LocalIds.isLocal("al-" + LocalIds.album("A", "Record")))
        assertFalse(LocalIds.isLocal("al-123"))
        assertFalse(LocalIds.isLocal(null))
        assertEquals(LocalIds.album("Beyoncé", "Lemonade"), LocalIds.album("beyonce", "LEMONADE"))
        assertEquals("abc", LocalIds.coverKey(LocalIds.cover("abc")))
    }

    @Test fun theServersArtistsAreUsedByMusicBrainzIdThenName() {
        val server = listOf(Artist("ar1", "Artist A", mbid = mbA), Artist("ar2", "Band B"))
        val music = LocalLibrary.build(
            listOf(
                // A different spelling, but the same MusicBrainz ID.
                track("1.flac", "One", "Artist  A.", artistMbids = listOf(mbA)),
                track("2.flac", "Two", "band b"),
                track("3.flac", "Three", "Only Local"),
            ),
            server,
        )
        val byTitle = music.songs.associateBy { it.title }
        assertEquals("ar1", byTitle.getValue("One").artistId)
        assertEquals("ar2", byTitle.getValue("Two").artistId)
        val local = byTitle.getValue("Three").artistId!!
        assertTrue(LocalIds.isLocal(local))
        // Only the artist the server doesn't have is added.
        assertEquals(listOf("Only Local"), music.artists.map { it.name })
        assertEquals(1, music.artists.single().albumCount)
    }

    @Test fun aJoinedNameSplitsByItsMusicBrainzIds() {
        val server = listOf(Artist("ar1", "Artist A", mbid = mbA))
        val music = LocalLibrary.build(listOf(track("1.flac", "Duet", "Artist A feat. Singer B", artistMbids = listOf(mbA, mbB))), server)
        val song = music.songs.single()
        assertEquals(listOf("Artist A", "Singer B"), song.artists.map { it.name })
        assertEquals("ar1", song.artists.first().id)
        assertEquals(mbB, music.artists.single { it.name == "Singer B" }.mbid)
        // The song still reads as tagged.
        assertEquals("Artist A feat. Singer B", song.artist)
    }

    @Test fun aJoinedNameWithoutIdsStaysAsTagged() {
        val refs = LocalLibrary.artistRefs(track("1.flac", "Duet", "A & B"))
        assertEquals(listOf("A & B" to null), refs)
    }

    @Test fun songsMakeAlbumsWithTheirCover() {
        val music = LocalLibrary.build(
            listOf(
                track("1.flac", "One", "A", albumArtist = "A", hasCover = true),
                track("2.flac", "Two", "A feat. C", albumArtist = "A"),
                track("3.flac", "Elsewhere", "A", album = "Other"),
            ),
            emptyList(),
        )
        assertEquals(2, music.albums.size)
        val record = music.albums.single { it.name == "Record" }
        assertEquals(2, record.songCount)
        assertEquals(400, record.durationSec)
        assertTrue(record.coverArt!!.let { LocalIds.coverKey(it) != null })
        // Every song of the album shows its cover.
        assertTrue(music.songs.filter { it.albumId == record.id }.all { it.coverArt == record.coverArt })
        assertNull(music.albums.single { it.name == "Other" }.coverArt)
        // The local artist shows their album's cover.
        assertEquals(record.coverArt, music.artists.single { it.name == "A" }.coverArt)
    }

    @Test fun localSongsMatchTheServerByRecordingThenTitleArtistAndLength() {
        val local = listOf(
            Song(id = "lf-s1", title = "One", artist = "A", durationSec = 200, mbid = rec),
            Song(id = "lf-s2", title = "Two!", artist = "Á", durationSec = 181),
            Song(id = "lf-s3", title = "Three", artist = "A", durationSec = 300),
            Song(id = "lf-s4", title = "Four", artist = "A", durationSec = 100),
        )
        val server = listOf(
            ServerSong("srv1", "One (Remaster)", "A", 230, rec),
            ServerSong("srv2", "two", "a", 180, null),
            ServerSong("srv2b", "two", "a", 250, null),
            ServerSong("srv3", "Three", "A", 320, null),
            ServerSong("srv4", "Four", "B", 100, null),
        )
        val m = LocalMatch.match(local, server)
        assertEquals("srv1", m["lf-s1"])
        assertEquals("srv2", m["lf-s2"])
        // Too far apart in length, and another artist: only on the phone.
        assertNull(m["lf-s3"])
        assertNull(m["lf-s4"])
    }
}
