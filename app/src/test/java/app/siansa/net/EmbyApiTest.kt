package app.siansa.net

import app.siansa.data.AppSettings
import app.siansa.data.AppJson
import app.siansa.data.Song
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Emby as a music source (issue #113): what is asked of the server and how its answers are read. */
class EmbyApiTest {
    private data class Seen(val method: String, val path: String, val query: Map<String, String>, val token: String?, val body: String)

    /** Answers like an Emby server; [answer] gets each request after the login, which is handled here. */
    private class FakeEmby(val tokens: MutableList<String> = mutableListOf("t1", "t2"), val answer: (Seen) -> Pair<Int, String>) {
        val seen = mutableListOf<Seen>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { respond(it.request()) }).build()

        private fun respond(request: Request): Response {
            val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            val query = (0 until request.url.querySize).associate { request.url.queryParameterName(it) to request.url.queryParameterValue(it).orEmpty() }
            val one = Seen(request.method, request.url.encodedPath, query, request.header("X-Emby-Token"), body)
            seen += one
            val (code, text) = when (one.path) {
                "/Users/AuthenticateByName" -> 200 to """{"AccessToken":"${tokens.removeAt(0)}","User":{"Id":"u1"}}"""
                else -> answer(one)
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(text.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private val settings = AppSettings(navidromeUrl = "http://emby.local:8096", navidromeUser = "max", navidromePassword = "sesame", sourceKind = SourceKind.EMBY)

    private val songJson = """{"Id":"s1","Name":"Shadow Box","Type":"Audio","Album":"Untrue","AlbumId":"a1",
        "Artists":["Burial","Kode9"],"ArtistItems":[{"Id":"ar1","Name":"Burial"},{"Id":"ar2","Name":"Kode9"}],
        "AlbumArtists":[{"Id":"ar1","Name":"Burial"}],"RunTimeTicks":2450000000,"IndexNumber":3,"ParentIndexNumber":1,
        "ProductionYear":2007,"Genres":["Dubstep"],"Container":"FLAC","UserData":{"IsFavorite":true,"PlayCount":4}}"""

    @Test fun logsInOnceAndAsksAsTheUser() = runBlocking {
        val server = FakeEmby { 200 to """{"Items":[$songJson]}""" }
        val api = EmbyApi(server.client, { settings })
        api.starredSongs(); api.songsPage(0, 50, "lib1")
        val login = server.seen.filter { it.path == "/Users/AuthenticateByName" }
        assertEquals(1, login.size)
        assertTrue(login[0].body.contains("\"Username\":\"max\"") && login[0].body.contains("\"Pw\":\"sesame\""))
        val page = server.seen.last()
        assertEquals("/Users/u1/Items", page.path)
        assertEquals("t1", page.token)
        assertEquals("Audio", page.query["IncludeItemTypes"])
        assertEquals("lib1", page.query["ParentId"])
        assertEquals("50", page.query["Limit"])
    }

    @Test fun theUserGoesIntoParametersToo() = runBlocking {
        val server = FakeEmby { 200 to """{"Id":"ar1","Name":"Burial"}""" }
        EmbyApi(server.client, { settings }).topSongs("Burial", 5)
        val lookup = server.seen.first { it.path.startsWith("/Artists/") }
        assertEquals("u1", lookup.query["UserId"])
    }

    @Test fun aRefusedTokenLogsInAgainOnce() = runBlocking {
        var refused = false
        val server = FakeEmby { seen -> if (seen.token == "t1") { refused = true; 401 to "" } else 200 to """{"Items":[]}""" }
        EmbyApi(server.client, { settings }).starredAlbums()
        assertTrue(refused)
        assertEquals(2, server.seen.count { it.path == "/Users/AuthenticateByName" })
        assertEquals("t2", server.seen.last().token)
    }

    @Test fun aWrongPasswordSaysSo() = runBlocking {
        val server = FakeEmby { 200 to "{}" }
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401).message("x")
                .body("".toResponseBody("text/plain".toMediaType())).build()
        }).build()
        val message = runCatching { EmbyApi(client, { settings }).starredSongs() }.exceptionOrNull()?.message
        assertEquals("Emby refused the user name or password", message)
        assertTrue(server.seen.isEmpty())
    }

    @Test fun songsAreRead() {
        val api = EmbyApi(OkHttpClient(), { settings })
        val song: Song = api.parseSong(app.siansa.data.AppJson.parseToJsonElement(songJson) as kotlinx.serialization.json.JsonObject)
        assertEquals("Shadow Box", song.title)
        assertEquals("Burial, Kode9", song.artist)
        assertEquals("ar1", song.artistId)
        assertEquals(245, song.durationSec)
        assertEquals(3, song.track)
        assertEquals(1, song.disc)
        assertEquals(2007, song.year)
        assertEquals("Dubstep", song.genre)
        assertTrue(song.starred)
        assertEquals(listOf("ar1", "ar2"), song.artists.map { it.id })
        // No picture of its own: the album's.
        assertEquals("a1", song.coverArt)
        assertEquals("flac", song.suffix)
    }

    @Test fun addressesCarryTheTokenAndPlayTheOriginal() = runBlocking {
        val server = FakeEmby { 200 to """{"Items":[]}""" }
        val api = EmbyApi(server.client, { settings })
        api.starredSongs()
        val stream = api.streamUrl("s 1")
        assertTrue(stream, stream.startsWith("http://emby.local:8096/Audio/s+1/stream?"))
        assertTrue(stream.contains("Static=true") && stream.contains("api_key=t1"))
        // Covers by item id; Navidrome-style album ids from older data work too.
        assertEquals(api.coverUrl("a1", 300), api.coverUrl("al-a1", 300))
        assertTrue(api.coverUrl("a1", 300).contains("/Items/a1/Images/Primary?maxWidth=300"))
    }

    @Test fun aLoginFromTheLastRunIsUsedForAddresses() {
        val kept = EmbyApi.Login("http://emby.local:8096|max", "u1", "kept")
        val api = EmbyApi(OkHttpClient(), { settings }, recall = { kept })
        assertTrue(api.streamUrl("s1").contains("api_key=kept"))
        // Another server or user: not that login.
        val other = EmbyApi(OkHttpClient(), { settings.copy(navidromeUser = "ann") }, recall = { kept })
        assertFalse(other.streamUrl("s1").contains("kept"))
    }

    @Test fun favouritesAreEmbyFavourites() = runBlocking {
        val server = FakeEmby { 200 to "" }
        val api = EmbyApi(server.client, { settings })
        api.star("s1"); api.unstarAlbum("a1"); api.starArtist("ar1")
        val calls = server.seen.filter { it.path.contains("FavoriteItems") }.map { it.method + " " + it.path }
        assertEquals(listOf("POST /Users/u1/FavoriteItems/s1", "DELETE /Users/u1/FavoriteItems/a1", "POST /Users/u1/FavoriteItems/ar1"), calls)
    }

    @Test fun playsAreMarkedPlayed() = runBlocking {
        val server = FakeEmby { 200 to "" }
        val api = EmbyApi(server.client, { settings })
        api.scrobble("s1", submission = true, timeMs = 0)
        api.scrobble("s1", submission = false)
        assertEquals("1970-01-01T00:00:00Z", server.seen.first { it.path == "/Users/u1/PlayedItems/s1" }.query["DatePlayed"])
        assertTrue(server.seen.last { it.path == "/Sessions/Playing" }.body.contains("\"ItemId\":\"s1\""))
    }

    @Test fun playbackIsFollowedOnEmbysTimeline() = runBlocking {
        val server = FakeEmby { 200 to "" }
        val api = EmbyApi(server.client, { settings })
        api.playbackProgress("s1", 90_000, paused = true)
        api.playbackStopped("s1", 245_000)
        val progress = server.seen.single { it.path == "/Sessions/Playing/Progress" }.body
        assertTrue(progress, progress.contains("\"PositionTicks\":900000000") && progress.contains("\"IsPaused\":true") && progress.contains("\"EventName\":\"Pause\""))
        val stopped = server.seen.single { it.path == "/Sessions/Playing/Stopped" }.body
        assertTrue(stopped, stopped.contains("\"ItemId\":\"s1\"") && stopped.contains("\"PositionTicks\":2450000000"))
    }

    @Test fun navidromeHasNoTimeline() = runBlocking {
        // The defaults do nothing (and ask nothing of the server).
        val subsonic: MusicSource = SubsonicApi(OkHttpClient()) { AppSettings() }
        subsonic.playbackProgress("s1", 1000, paused = false)
        subsonic.playbackStopped("s1", 1000)
    }

    @Test fun removingFromAPlaylistUsesItsEntryIds() = runBlocking {
        val server = FakeEmby { seen ->
            if (seen.method == "GET") 200 to """{"Items":[{"Id":"s1","PlaylistItemId":"e1"},{"Id":"s2","PlaylistItemId":"e2"},{"Id":"s3","PlaylistItemId":"e3"}]}"""
            else 200 to ""
        }
        EmbyApi(server.client, { settings }).removeFromPlaylist("p1", listOf(0, 2))
        val delete = server.seen.single { it.method == "DELETE" }
        assertEquals("/Playlists/p1/Items", delete.path)
        assertEquals("e1,e3", delete.query["EntryIds"])
    }

    @Test fun lyricsComeFromTheSongsLyricsStreams() = runBlocking {
        val server = FakeEmby { seen ->
            when {
                seen.path == "/Users/u1/Items/s1" -> 200 to """{"Id":"s1","MediaSources":[{"Id":"ms1","MediaStreams":[
                    {"Type":"Audio","Index":0},{"Type":"Subtitle","Index":1,"Codec":"lrc","Language":"eng"}]}]}"""
                seen.path == "/Items/s1/ms1/Subtitles/1/Stream.lrc" -> 200 to "[00:01.00]Hello\n[00:03.50]World"
                else -> 404 to ""
            }
        }
        val lyrics = EmbyApi(server.client, { settings }).lyrics(Song("s1", "T"))!!
        assertTrue(lyrics.synced)
        assertEquals(listOf(1000L, 3500L), lyrics.lines.map { it.startMs })
        assertEquals("World", lyrics.lines[1].text)
    }

    @Test fun noLyricsStreamMeansNoLyrics() = runBlocking {
        val server = FakeEmby { 200 to """{"Id":"s1","MediaSources":[{"Id":"ms1","MediaStreams":[{"Type":"Audio","Index":0}]}]}""" }
        assertNull(EmbyApi(server.client, { settings }).lyrics(Song("s1", "T")))
    }

    @Test fun plainLyricsStayPlain() {
        val plain = EmbyApi.readLyrics("Line one\nLine two")!!
        assertFalse(plain.synced)
        assertEquals(listOf("Line one", "Line two"), plain.lines.map { it.text })
        assertNull(EmbyApi.readLyrics("  "))
        assertEquals("lrc", EmbyApi.lyricsFormat("LRC"))
        assertEquals("srt", EmbyApi.lyricsFormat("subrip"))
        assertEquals("txt", EmbyApi.lyricsFormat(null))
    }

    @Test fun albumSongsAreInDiscAndTrackOrder() = runBlocking {
        val server = FakeEmby { seen ->
            if (seen.path == "/Users/u1/Items/a1") 200 to """{"Id":"a1","Name":"Untrue"}"""
            else 200 to """{"Items":[{"Id":"x","Name":"B","IndexNumber":1,"ParentIndexNumber":2},
                {"Id":"y","Name":"A","IndexNumber":2,"ParentIndexNumber":1},{"Id":"z","Name":"C","IndexNumber":1,"ParentIndexNumber":1}]}"""
        }
        assertEquals(listOf("z", "y", "x"), EmbyApi(server.client, { settings }).album("a1").songs.map { it.id })
    }

    @Test fun anArtistsPageShowsTheirOwnAlbums() = runBlocking {
        val server = FakeEmby { seen ->
            if (seen.path == "/Users/u1/Items/ar1") 200 to """{"Id":"ar1","Name":"Burial"}"""
            else 200 to """{"Items":[{"Id":"a1","Name":"Untrue","AlbumArtists":[{"Id":"ar1","Name":"Burial"}]},
                {"Id":"a2","Name":"Compilation","AlbumArtists":[{"Id":"va","Name":"Various Artists"}]}]}"""
        }
        val detail = EmbyApi(server.client, { settings }).artist("ar1")
        assertEquals(listOf("a1"), detail.albums.map { it.id })
        assertEquals("ar1", server.seen.last().query["ArtistIds"])
    }

    @Test fun artistRadioIsTheArtistsInstantMix() = runBlocking {
        val server = FakeEmby { 200 to """{"Items":[]}""" }
        EmbyApi(server.client, { settings }).artistRadio("ar1", 20)
        assertEquals("/Items/ar1/InstantMix", server.seen.last().path)
    }

    @Test fun albumListsMapToEmbySorts() {
        assertEquals("DateCreated,SortName" to "Descending", EmbyApi.albumSort("newest"))
        assertEquals("PlayCount,SortName" to "Descending", EmbyApi.albumSort("frequent"))
        assertEquals("Random" to "Ascending", EmbyApi.albumSort("random"))
        assertEquals("SortName" to "Ascending", EmbyApi.albumSort("alphabeticalByName"))
        assertEquals(0, EmbyApi.ticksToSec(null))
    }

    @Test fun whatEmbyHasNotIsEmpty() = runBlocking {
        val api = EmbyApi(OkHttpClient(), { settings })
        assertFalse(api.hasSonicMatch())
        assertTrue(api.sonicSimilar("s1", 10).isEmpty())
        assertNull(api.lastScan())
    }

    @Test fun nowPlayingNamesTheMediaSourceAndOneSessionPerSong() {
        val api = EmbyApi(OkHttpClient(), { settings })
        val start = AppJson.parseToJsonElement(api.timeline("886400", 0, paused = false, event = null)) as JsonObject
        val progress = AppJson.parseToJsonElement(api.timeline("886400", 5000, paused = false, event = "TimeUpdate")) as JsonObject
        val next = AppJson.parseToJsonElement(api.timeline("887221", 0, paused = false, event = null)) as JsonObject
        assertEquals("mediasource_886400", start["MediaSourceId"]!!.jsonPrimitive.content)
        assertEquals(start["PlaySessionId"], progress["PlaySessionId"])
        assertTrue(start["PlaySessionId"] != next["PlaySessionId"])
    }

    @Test fun aPlayIsMarkedWithTheDateEmbyReads() = runBlocking {
        // Beta #15: "DatePlayed=2026-10-25T06:41:34Z" made Emby answer HTTP 500, so plays never reached it.
        assertEquals("20261025064134", embyDate(java.time.Instant.parse("2026-10-25T06:41:34Z").toEpochMilli()))
        val server = FakeEmby { 200 to "{}" }
        EmbyApi(server.client, { settings }).scrobble("s1", submission = true, timeMs = java.time.Instant.parse("2026-10-25T06:41:34Z").toEpochMilli())
        val played = server.seen.last()
        assertEquals("/Users/u1/PlayedItems/s1", played.path)
        assertEquals("20261025064134", played.query["DatePlayed"])
    }

    @Test fun aRefusedDateStillCountsThePlay() = runBlocking {
        val server = FakeEmby { seen -> if (seen.query.containsKey("DatePlayed")) 500 to "" else 200 to "{}" }
        EmbyApi(server.client, { settings }).scrobble("s1", submission = true, timeMs = 0L)
        val played = server.seen.last()
        assertEquals("/Users/u1/PlayedItems/s1", played.path)
        assertTrue(played.query.isEmpty())
    }
}
