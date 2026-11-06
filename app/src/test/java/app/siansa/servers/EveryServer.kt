package app.siansa.servers

import app.siansa.data.AppSettings
import app.siansa.net.EmbyApi
import app.siansa.net.MusicSource
import app.siansa.net.SourceKind
import app.siansa.net.SubsonicApi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * One kind of music server Siansa plays from, as a stand-in on the phone (issue #903). [songId] is a song id in that
 * server's own style. [source] is the app's client for it, logged in with login number [login]: a new number gives
 * new tokens in every address, as a server does from one app run to the next.
 */
class TestServer(val name: String, val kind: SourceKind, val songId: String, private val make: (AppSettings, Int) -> MusicSource) {
    val settings = AppSettings(navidromeUrl = "http://192.168.32.129:9096", navidromeUser = "max", navidromePassword = "sesame", sourceKind = kind)

    fun source(login: Int = 1): MusicSource = make(settings, login)

    override fun toString() = name
}

/**
 * Every kind of music server: Navidrome (OpenSubsonic), Emby and Jellyfin. A test of anything that takes a server's
 * addresses or answers (the player, downloads, the song cache, scrobbling, covers) runs over [all], so code written
 * with one server in mind is checked on the others too.
 */
object EveryServer {
    /** Answers every request with an empty success, like a server with nothing in it: enough for building addresses. */
    private val quiet: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("{}".toResponseBody("application/json".toMediaType())).build()
    }).build()

    private fun embyLike(jellyfin: Boolean) = { s: AppSettings, login: Int ->
        val key = s.navidromeUrl.trimEnd('/') + "|" + s.navidromeUser
        val token = (if (jellyfin) "jf" else "emby") + "-token-$login"
        EmbyApi(quiet, { s }, recall = { EmbyApi.Login(key, "u1", token) }, jellyfin = jellyfin)
    }

    val all: List<TestServer> = listOf(
        // Navidrome's tokens come from a salt that's new for each client.
        TestServer("Navidrome", SourceKind.OPENSUBSONIC, "tr-0a1b2c3d") { s, _ -> SubsonicApi(quiet) { s } },
        TestServer("Emby", SourceKind.EMBY, "52131", embyLike(jellyfin = false)),
        TestServer("Jellyfin", SourceKind.JELLYFIN, "6f1df514408a4c898b01a0b305887968", embyLike(jellyfin = true)),
    )
}
