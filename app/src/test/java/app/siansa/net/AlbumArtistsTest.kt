package app.siansa.net

import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.ArtistRef
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every album artist, as each server lists them (the owner, 14 Nov: "9 months & 50 hours" by Fred again.. and LATIN
 * MAFIA opened only Fred again..). Navidrome, Emby and Jellyfin.
 */
class AlbumArtistsTest {
    private val both = listOf(ArtistRef("a1", "Fred again.."), ArtistRef("a2", "LATIN MAFIA"))

    @Test fun navidromeListsEachAlbumArtist() = runBlocking {
        val answer = """{"subsonic-response": {"status": "ok", "version": "1.16.1", "openSubsonic": true, "album": {
            "id": "al1", "name": "9 months & 50 hours", "artist": "Fred again.. • LATIN MAFIA", "artistId": "a1",
            "displayArtist": "Fred again.. • LATIN MAFIA",
            "artists": [{"id": "a1", "name": "Fred again.."}, {"id": "a2", "name": "LATIN MAFIA"}], "song": []}}}"""
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(answer.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val settings = AppSettings(navidromeUrl = "http://192.168.32.129:4533", navidromeUser = "max", navidromePassword = "sesame")
        val album = SubsonicApi(http) { settings }.album("al1").album
        assertEquals(both, album.artists)
        assertEquals("a1", album.artistId)
    }

    @Test fun embyAndJellyfinListEachAlbumArtist() {
        val item = AppJson.parseToJsonElement("""{"Id": "al1", "Name": "9 months & 50 hours", "AlbumArtist": "Fred again..",
            "AlbumArtists": [{"Id": "a1", "Name": "Fred again.."}, {"Id": "a2", "Name": "LATIN MAFIA"}]}""") as JsonObject
        for (jellyfin in listOf(false, true)) {
            val api = EmbyApi(OkHttpClient(), { AppSettings(sourceKind = if (jellyfin) SourceKind.JELLYFIN else SourceKind.EMBY) }, jellyfin = jellyfin)
            assertEquals(if (jellyfin) "Jellyfin" else "Emby", both, api.parseAlbum(item).artists)
        }
    }

    @Test fun anAlbumWithOneArtistListsOne() {
        val item = AppJson.parseToJsonElement("""{"Id": "al2", "Name": "Solo", "AlbumArtists": [{"Id": "a1", "Name": "Fred again.."}]}""") as JsonObject
        val api = EmbyApi(OkHttpClient(), { AppSettings(sourceKind = SourceKind.EMBY) }, jellyfin = false)
        assertEquals(listOf(ArtistRef("a1", "Fred again..")), api.parseAlbum(item).artists)
    }
}
