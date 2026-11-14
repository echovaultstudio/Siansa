package app.siansa.net

import app.siansa.data.AppSettings
import app.siansa.data.AppJson
import app.siansa.data.Song
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
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
        // Requests can come in at once (album covers are looked up a few at a time): kept in a list that's safe for that.
        val seen: MutableList<Seen> = java.util.Collections.synchronizedList(mutableListOf())
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
        assertEquals("19700101000000", server.seen.first { it.path == "/Users/u1/PlayedItems/s1" }.query["DatePlayed"])
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

    /** Emby 4.8 answers a song's .lrc file as `.lrc` with nothing at all; its SRT has the lines and times. */
    @Test fun anEmptyLrcAnswerIsReadAsSrtInstead() = runBlocking {
        val server = FakeEmby { seen ->
            when (seen.path) {
                "/Users/u1/Items/s1" -> 200 to """{"Id":"s1","MediaSources":[{"Id":"ms1","MediaStreams":[
                    {"Type":"Audio","Index":0},{"Type":"Subtitle","Index":1,"Codec":"lrc","IsExternal":true}]}]}"""
                "/Items/s1/ms1/Subtitles/1/Stream.lrc" -> 200 to ""
                "/Items/s1/ms1/Subtitles/1/Stream.srt" ->
                    200 to "﻿1\n00:00:01,000 --> 00:00:10,000\nThe first line\n\n2\n00:00:10,000 --> 00:00:20,000\nThe second line\n"
                else -> 404 to ""
            }
        }
        val lyrics = EmbyApi(server.client, { settings }).lyrics(Song("s1", "T"))!!
        assertTrue(lyrics.synced)
        assertEquals(listOf("The first line", "The second line"), lyrics.lines.map { it.text })
        assertEquals(listOf(1000L, 10_000L), lyrics.lines.map { it.startMs })
        assertEquals(listOf("lrc", "srt"), EmbyApi.lyricsFormats("lrc"))
        assertEquals(listOf("srt"), EmbyApi.lyricsFormats("subrip"))
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
        assertEquals("ar1", server.seen.first { it.query["IncludeItemTypes"] == "MusicAlbum" }.query["ArtistIds"])
    }

    /**
     * Emby makes albums from tags, so a cover.jpg or folder.jpg is its folder's picture, never the album's (found by
     * the real-server tests): songs and albums with their cover only there showed none.
     */
    @Test fun aSongWithoutAPictureShowsItsAlbumsElseItsFolders() {
        val api = EmbyApi(OkHttpClient(), { settings })
        fun cover(json: String) = api.parseSong(app.siansa.data.AppJson.parseToJsonElement(json) as JsonObject).coverArt
        assertEquals("s1", cover("""{"Id":"s1","AlbumId":"a1","ParentId":"f1","ImageTags":{"Primary":"t"}}"""))
        assertEquals("a1", cover("""{"Id":"s1","AlbumId":"a1","ParentId":"f1","AlbumPrimaryImageTag":"t"}"""))
        assertEquals("f1", cover("""{"Id":"s1","AlbumId":"a1","ParentId":"f1"}"""))
        assertEquals("a1", cover("""{"Id":"s1","AlbumId":"a1","ParentId":"a1"}"""))
        assertEquals("a1", cover("""{"Id":"s1","AlbumId":"a1"}"""))
    }

    /** A cover replaced on Emby or Jellyfin keeps its address: its tag is what tells (the owner, 11 Nov). */
    @Test fun aReplacedAlbumCoverIsNoticedByItsTagOnEmbyAndJellyfin() {
        for (jellyfin in listOf(false, true)) {
            val covers = app.siansa.covers.CoverVersions
            covers.forgetTags()
            val changed = mutableListOf<String>()
            covers.onChanged = { changed += it }
            try {
                val api = EmbyApi(OkHttpClient(), { settings }, jellyfin = jellyfin)
                fun read(tag: String) = api.parseSong(
                    app.siansa.data.AppJson.parseToJsonElement("""{"Id":"s1","Type":"Audio","AlbumId":"a1","AlbumPrimaryImageTag":"$tag"}""") as JsonObject,
                )
                read("one")
                read("one")
                assertEquals(emptyList<String>(), changed)
                read("two")
                assertEquals("jellyfin=$jellyfin", listOf("a1"), changed)
                // A song's own picture isn't followed (one tag a song would be tens of thousands).
                api.parseSong(app.siansa.data.AppJson.parseToJsonElement("""{"Id":"s2","Type":"Audio","ImageTags":{"Primary":"x"}}""") as JsonObject)
                api.parseSong(app.siansa.data.AppJson.parseToJsonElement("""{"Id":"s2","Type":"Audio","ImageTags":{"Primary":"y"}}""") as JsonObject)
                assertEquals(listOf("a1"), changed)
            } finally {
                covers.onChanged = null
                covers.forgetTags()
            }
        }
    }

    @Test fun anAlbumWithItsCoverOnlyInItsFolderShowsTheFolders() = runBlocking {
        val server = FakeEmby { seen ->
            when {
                seen.query["ParentId"] == "a1" -> 200 to """{"Items":[{"Id":"s1","Name":"One","Type":"Audio","AlbumId":"a1","ParentId":"f1"},
                    {"Id":"s2","Name":"Two","Type":"Audio","AlbumId":"a1","ParentId":"f1"}]}"""
                seen.query["ParentId"] == "a2" -> 200 to """{"Items":[{"Id":"s3","Name":"Three","Type":"Audio","AlbumId":"a2","ParentId":"f2"}]}"""
                seen.path == "/Users/u1/Items/f1" -> 200 to """{"Id":"f1","Type":"Folder","ImageTags":{"Primary":"x"}}"""
                seen.path == "/Users/u1/Items/f2" -> 200 to """{"Id":"f2","Type":"Folder"}"""
                else -> 200 to """{"Items":[{"Id":"a1","Name":"First Light"},{"Id":"a2","Name":"Bare"}]}"""
            }
        }
        assertEquals(listOf("f1", null), EmbyApi(server.client, { settings }).albums("newest", 20, 0).map { it.coverArt })
        assertEquals("f1", EmbyApi.folderOf(listOf(buildJsonObject { put("ParentId", "f1") }, buildJsonObject { put("ParentId", "f1") }), "a1"))
        assertNull(EmbyApi.folderOf(listOf(buildJsonObject { put("ParentId", "f1") }, buildJsonObject { put("ParentId", "f9") }), "a1"))
        assertNull(EmbyApi.folderOf(listOf(buildJsonObject { put("ParentId", "a1") }), "a1"))
    }

    /** Issue #811 (beta report 80dcb26b): album lists showed nothing where the player showed the song's art. */
    @Test fun anAlbumListShowsSongCoversForAlbumsWithoutTheirOwn() = runBlocking {
        val server = FakeEmby { seen ->
            when (seen.query["ParentId"]) {
                "a1" -> 200 to """{"Items":[{"Id":"s0","Name":"Intro","Type":"Audio","AlbumId":"a1"},
                    {"Id":"s1","Name":"Shadow Box","Type":"Audio","AlbumId":"a1","ImageTags":{"Primary":"t"}}]}"""
                "a3" -> 200 to """{"Items":[{"Id":"s3","Name":"Bare","Type":"Audio","AlbumId":"a3"}]}"""
                else -> 200 to """{"Items":[{"Id":"a1","Name":"Untrue"},{"Id":"a2","Name":"Own","ImageTags":{"Primary":"x"}},{"Id":"a3","Name":"Bare"}]}"""
            }
        }
        val api = EmbyApi(server.client, { settings })
        val albums = api.albums("newest", 20, 0)
        assertEquals(listOf("s1", "a2", null), albums.map { it.coverArt })  // the first song with a picture; its own; none
        // Only the albums without a picture were asked, each once: a second list asks nothing more.
        val lookups = { server.seen.count { it.query["IncludeItemTypes"] == "Audio" } }
        assertEquals(2, lookups())
        assertEquals(listOf("s1", "a2", null), api.albums("newest", 20, 0).map { it.coverArt })
        assertEquals(2, lookups())
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

    /**
     * Beta report 0f576bc5 (issue #792): Emby had no image on the album item, but served each song's cover from its
     * file. The player showed art; the album page asked for the album's own image and got 404.
     */
    @Test fun anAlbumWithoutItsOwnImageShowsItsFirstSongs() {
        val api = EmbyApi(OkHttpClient(), { settings })
        val album = app.siansa.data.Album(id = "53640", name = "Bossanova", artist = "Pixies", coverArt = null)
        val songs = listOf(
            app.siansa.data.Song(id = "53593", title = "Bone Machine", artist = "Pixies", album = "Bossanova", albumId = "53640", coverArt = "53593"),
            app.siansa.data.Song(id = "53594", title = "Rock Music", artist = "Pixies", album = "Bossanova", albumId = "53640", coverArt = "53594"),
        )
        assertEquals("53593", api.albumWithSongCover(album, songs).coverArt)
        // A song without its own picture points at the album's, which is the one that isn't there: skipped.
        val bare = app.siansa.data.Song(id = "1", title = "x", artist = "y", album = "z", albumId = "53640", coverArt = "53640")
        assertEquals("53594", api.albumWithSongCover(album, listOf(bare, songs[1])).coverArt)
        assertEquals(null, api.albumWithSongCover(album, listOf(bare)).coverArt)
        // An album with its own image keeps it.
        assertEquals("own", api.albumWithSongCover(album.copy(coverArt = "own"), songs).coverArt)
        // An album's id stands in for a cover on Subsonic servers only.
        assertTrue(SourceKind.OPENSUBSONIC.albumIdIsCover)
        assertTrue(!SourceKind.EMBY.albumIdIsCover && !SourceKind.JELLYFIN.albumIdIsCover)
    }
}
