package app.siansa.servers

import app.siansa.data.AppSettings
import app.siansa.data.PlaylistDuplicates
import app.siansa.data.Song
import app.siansa.net.EmbyApi
import app.siansa.net.MusicSource
import app.siansa.net.SourceKind
import app.siansa.net.SubsonicApi
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A song already in a playlist is counted from each server's own answer (issue #1235): Navidrome lists a playlist's
 * entries by song id, Emby and Jellyfin by item id beside a PlaylistItemId of their own. The badge counts by the
 * song's id on all three, never by the entry's.
 */
class PlaylistDuplicatesOnEveryServerTest {
    /** [server]'s answer for a playlist "p1" holding [ids] in order, in its own shape. */
    private fun answering(server: TestServer, ids: List<String>): MusicSource {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val path = chain.request().url.encodedPath
            val body = when (server.kind) {
                SourceKind.OPENSUBSONIC -> """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true,"playlist":{"id":"p1","name":"Mine","songCount":${ids.size},"entry":[""" +
                    ids.joinToString(",") { """{"id":"$it","title":"Song $it","artist":"A","album":"B","duration":200,"isDir":false}""" } + "]}}}"
                else -> when {
                    path.endsWith("/Playlists/p1/Items") -> """{"Items":[""" + ids.withIndex().joinToString(",") { (i, id) ->
                        """{"Id":"$id","Name":"Song $id","Type":"Audio","MediaType":"Audio","PlaylistItemId":"entry$i","RunTimeTicks":2000000000,"Artists":["A"],"Album":"B"}"""
                    } + "],\"TotalRecordCount\":${ids.size}}"
                    path.endsWith("/Items/p1") -> """{"Id":"p1","Name":"Mine","Type":"Playlist","ChildCount":${ids.size}}"""
                    else -> "{}"
                }
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val s: AppSettings = server.settings
        return when (server.kind) {
            SourceKind.OPENSUBSONIC -> SubsonicApi(http) { s }
            else -> EmbyApi(http, { s }, recall = { EmbyApi.Login(s.navidromeUrl.trimEnd('/') + "|" + s.navidromeUser, "u1", "token") },
                jellyfin = server.kind == SourceKind.JELLYFIN)
        }
    }

    @Test fun aSongTwiceInAPlaylistIsCountedTwiceOnEveryServer() = runBlocking {
        for (server in EveryServer.all) {
            val other = server.songId + "9"
            val playlist = answering(server, listOf(server.songId, other, server.songId)).playlist("p1")
            val ids = playlist.songs.map { it.id }
            assertEquals(server.name, listOf(server.songId, other, server.songId), ids)
            val adding = listOf(Song(id = server.songId, title = "Song"))
            assertEquals(server.name, 2, PlaylistDuplicates.count(ids, adding))
            assertEquals(server.name, "This song is in it twice already.", PlaylistDuplicates.question(ids, adding))
            assertEquals(server.name, 0, PlaylistDuplicates.count(ids, listOf(Song(id = server.songId + "7", title = "New"))))
            // Several songs: only the one not there yet is new.
            val several = adding + Song(id = other, title = "Other") + Song(id = "new", title = "New")
            assertEquals(server.name, listOf("new"), PlaylistDuplicates.missing(ids, several).map { it.id })
        }
    }
}
