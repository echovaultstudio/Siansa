package app.siansa.servers

import app.siansa.data.AppSettings
import app.siansa.data.Song
import app.siansa.net.EmbyApi
import app.siansa.net.MusicSource
import app.siansa.net.SourceKind
import app.siansa.net.SubsonicApi
import app.siansa.playback.songCacheKey
import app.siansa.playback.streamSongId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Siansa's own server code against real Navidrome, Emby and Jellyfin servers holding the same small library
 * (scripts/servers/: start.sh and library.py), so code written with one server in mind is checked on the others
 * (issue #903). Every test runs once per server. They're skipped unless SIANSA_TEST_SERVERS names the servers
 * (`navidrome=http://…,emby=…,jellyfin=…`) and SIANSA_TEST_LIBRARY points at library.json: CI's Servers workflow
 * sets both. To run them at home: `scripts/servers/start.sh`, then the environment it prints, then
 * `gradle :app:testDebugUnitTest --tests 'app.siansa.servers.RealServersTest'`.
 */
@RunWith(Parameterized::class)
class RealServersTest(private val server: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun servers() = listOf("navidrome", "emby", "jellyfin")

        private val addresses: Map<String, String> = System.getenv("SIANSA_TEST_SERVERS").orEmpty().split(',')
            .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
            .toMap()

        /** What library.py made: each song's title, tags, length and file size. */
        private val library: List<JsonObject> by lazy {
            Json.parseToJsonElement(File(System.getenv("SIANSA_TEST_LIBRARY")).readText())
                .jsonObject["songs"]!!.jsonArray.map { it.jsonObject }
        }

        private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

        /** One client per server for the whole run, as the app has: Emby and Jellyfin log in once. */
        private val clients = mutableMapOf<String, MusicSource>()
    }

    private fun source(): MusicSource {
        val url = addresses[server]
        assumeTrue("$server isn't running (set SIANSA_TEST_SERVERS)", url != null && System.getenv("SIANSA_TEST_LIBRARY") != null)
        return clients.getOrPut(server) {
            val kind = when (server) {
                "emby" -> SourceKind.EMBY
                "jellyfin" -> SourceKind.JELLYFIN
                else -> SourceKind.OPENSUBSONIC
            }
            val settings = AppSettings(navidromeUrl = url!!, navidromeUser = "max", navidromePassword = "sesame", sourceKind = kind)
            if (kind.embyLike) EmbyApi(http, { settings }, jellyfin = kind == SourceKind.JELLYFIN) else SubsonicApi(http) { settings }
        }
    }

    private fun expected(title: String) = library.first { it.text("title") == title }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.number(key: String) = this[key]!!.jsonPrimitive.int

    private suspend fun allSongs(): List<Song> = source().songsPage(0, 500)
    private suspend fun song(title: String): Song = allSongs().firstOrNull { it.title == title } ?: error("$server has no song \"$title\"")

    private fun get(url: String, range: String? = null) =
        http.newCall(Request.Builder().url(url).apply { range?.let { header("Range", it) } }.build()).execute()

    @Test fun answers() = runBlocking {
        source().ping()
        assertTrue("$server lists its music folders", source().musicFolders().isNotEmpty())
    }

    @Test fun everySongIsInTheLibraryWithItsTags() = runBlocking {
        val songs = allSongs()
        assertEquals("$server: songs", library.map { it.text("title") }.sorted(), songs.map { it.title }.sorted())
        songs.forEach { s ->
            val e = expected(s.title)
            assertEquals("$server ${s.title}: artist", e.text("artist"), s.artist)
            assertEquals("$server ${s.title}: album", e.text("album"), s.album)
            assertEquals("$server ${s.title}: track", e.number("track"), s.track)
            assertEquals("$server ${s.title}: disc", e.number("disc"), s.disc ?: 1)
            assertEquals("$server ${s.title}: year", e.number("year"), s.year)
            assertEquals("$server ${s.title}: genre", e.text("genre"), s.genre)
            assertTrue("$server ${s.title}: ${s.durationSec}s long, should be ${e.number("seconds")}s", abs(s.durationSec - e.number("seconds")) <= 1)
            assertNotNull("$server ${s.title}: album id", s.albumId)
        }
    }

    @Test fun albumsHoldTheirSongsInOrder() = runBlocking {
        val albums = source().albums("alphabeticalByName", 50, 0)
        assertEquals("$server: albums", library.map { it.text("album") }.distinct().sorted(), albums.map { it.name }.sorted())
        val discs = source().album(albums.first { it.name == "Two Discs" }.id)
        assertEquals("$server: Two Discs in order", listOf("Upstairs", "Landing", "Downstairs"), discs.songs.map { it.title })
        assertEquals("$server: Two Discs' discs", listOf(1, 1, 2), discs.songs.map { it.disc ?: 1 })
        val mixed = albums.first { it.name == "Mixed Bag" }
        assertEquals("$server: the compilation's album artist", "Various Artists", mixed.artist)
        assertEquals("$server: the compilation's songs", listOf("Aurora Test", "Cleo Sample"), source().album(mixed.id).songs.map { it.artist })
    }

    @Test fun artistsHaveTheirAlbums() = runBlocking {
        val artists = source().artists()
        listOf("Aurora Test", "Bram Example", "Cleo Sample").forEach { name ->
            assertTrue("$server lists $name: ${artists.map { it.name }}", artists.any { it.name == name })
        }
        val aurora = source().artist(artists.first { it.name == "Aurora Test" }.id)
        assertTrue("$server: Aurora Test's albums ${aurora.albums.map { it.name }}", aurora.albums.any { it.name == "First Light" })
    }

    @Test fun searchFindsSongsAlbumsAndArtists() = runBlocking {
        assertTrue("$server: search for a song", source().search("Morning Tone").songs.any { it.title == "Morning Tone" })
        assertTrue("$server: search for an album", source().search("First Light").albums.any { it.name == "First Light" })
        assertTrue("$server: search for an artist", source().search("Bram Example").artists.any { it.name == "Bram Example" })
    }

    @Test fun everySongStreamsWholeAndCanBeSeekedInto() = runBlocking {
        allSongs().forEach { s ->
            val url = source().streamUrl(s.id)
            // The player, downloads and the song cache all find the song from its address (issue #903).
            assertEquals("$server ${s.title}: the song in its address", s.id, streamSongId(url))
            assertTrue("$server ${s.title}: cache key ${songCacheKey(url)}", songCacheKey(url).startsWith("song:${s.id}:"))
            val size = get(url).use { r ->
                assertEquals("$server ${s.title}: stream", 200, r.code)
                r.body!!.bytes().size.toLong()
            }
            assertEquals("$server ${s.title}: the whole file", expected(s.title)["bytes"]!!.jsonPrimitive.long, size)
            // Seeking into a song that isn't loaded yet asks for part of the file.
            get(url, "bytes=1000-1999").use { r ->
                assertEquals("$server ${s.title}: part of the file", 206, r.code)
                assertEquals("$server ${s.title}: the part's size", 1000, r.body!!.bytes().size)
            }
        }
    }

    @Test fun theAlbumWithACoverShowsIt() = runBlocking {
        val album = source().albums("alphabeticalByName", 50, 0).first { it.name == "First Light" }
        get(source().coverUrl(album.coverArt ?: album.id, 300)).use { r ->
            assertEquals("$server: cover", 200, r.code)
            assertTrue("$server: the cover is a picture (${r.header("Content-Type")})", r.header("Content-Type").orEmpty().startsWith("image/"))
        }
    }

    @Test fun syncedLyricsAreRead() = runBlocking {
        val lyrics = source().lyrics(song("Second Glow"))
        assertNotNull("$server: lyrics for Second Glow (from its .lrc)", lyrics)
        assertTrue("$server: synced", lyrics!!.synced)
        assertEquals("$server: lines", listOf("The first line", "The second line", "The last line"), lyrics.lines.map { it.text })
        assertEquals("$server: timing", listOf(1000L, 10_000L, 20_000L), lyrics.lines.map { it.startMs })
    }

    @Test fun favouritesAreSavedAndRemoved() = runBlocking {
        val s = song("Late Bloom")
        source().star(s.id)
        assertTrue("$server: favourite song", source().starredSongs().any { it.id == s.id })
        source().unstar(s.id)
        assertFalse("$server: favourite song removed", source().starredSongs().any { it.id == s.id })
        val album = s.albumId!!
        source().starAlbum(album)
        assertTrue("$server: favourite album", source().starredAlbums().any { it.id == album })
        source().unstarAlbum(album)
        assertFalse("$server: favourite album removed", source().starredAlbums().any { it.id == album })
        val artist = source().artists().first { it.name == "Cleo Sample" }.id
        source().starArtist(artist)
        assertTrue("$server: favourite artist", source().starredArtists().any { it.id == artist })
        source().unstarArtist(artist)
        assertFalse("$server: favourite artist removed", source().starredArtists().any { it.id == artist })
    }

    @Test fun playlistsAreMadeAndChanged() = runBlocking {
        val a = song("Morning Tone")
        val b = song("Upstairs")
        val c = song("Open Door")
        val made = source().createPlaylist("CI list", listOf(a.id, b.id))
        val id = made?.id ?: source().playlists().first { it.name == "CI list" }.id
        assertEquals("$server: new playlist", listOf(a.id, b.id), source().playlist(id).songs.map { it.id })
        source().addToPlaylist(id, listOf(c.id))
        assertEquals("$server: song added", listOf(a.id, b.id, c.id), source().playlist(id).songs.map { it.id })
        source().removeFromPlaylist(id, listOf(0))
        assertEquals("$server: first song removed", listOf(b.id, c.id), source().playlist(id).songs.map { it.id })
        source().renamePlaylist(id, "CI renamed")
        assertEquals("$server: renamed", "CI renamed", source().playlist(id).playlist.name)
    }

    @Test fun playsAreReported() = runBlocking {
        val s = song("Landing")
        source().scrobble(s.id, submission = false)
        source().playbackProgress(s.id, 10_000, paused = false)
        source().playbackStopped(s.id, 30_000)
        source().scrobble(s.id, submission = true, timeMs = System.currentTimeMillis())
        assertEquals("$server: the song by its id", s.title, source().song(s.id).title)
    }

    @Test fun randomSongsAndDetails() = runBlocking {
        assertTrue("$server: random songs", source().randomSongs(5).isNotEmpty())
        source().songDetails(song("Loose Ends").id)
        Unit
    }
}
