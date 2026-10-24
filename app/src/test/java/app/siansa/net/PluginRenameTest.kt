package app.siansa.net

import app.siansa.data.AppSettings
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AudioMuse plugin was renamed with the app (issue #360): an older install keeps working until it's replaced. */
class PluginRenameTest {
    private val settings = AppSettings(audioMuseUrl = "http://am.local:8000")

    private fun server(installed: Set<String>, seen: MutableList<String>) = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val path = chain.request().url.encodedPath
        seen += path
        val there = installed.any { path.startsWith(it) }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(if (there) 200 else 404).message("x")
            .body((if (there) """{"plugin":"x","status":"ok"}""" else """{"error":"not found"}""").toResponseBody("application/json".toMediaType()))
            .build()
    }).build()

    @Test fun theNewPluginIsUsedWhenItIsThere() = runBlocking {
        val seen = mutableListOf<String>()
        val api = AudioMuseApi(server(setOf(PLUGIN_PREFIX, LEGACY_PLUGIN_PREFIX), seen)) { settings }
        api.plugin(PLUGIN_PREFIX + "api/profiles?ids=1")
        assertEquals(PLUGIN_PREFIX + "api/profiles", seen.last())
        assertTrue(seen.none { it.startsWith(LEGACY_PLUGIN_PREFIX) })
    }

    @Test fun anOlderInstallIsUsedUntilItIsReplaced() = runBlocking {
        val seen = mutableListOf<String>()
        val api = AudioMuseApi(server(setOf(LEGACY_PLUGIN_PREFIX), seen)) { settings }
        api.plugin(PLUGIN_PREFIX + "api/profiles?ids=1")
        api.plugin(PLUGIN_PREFIX + "api/similar/albums?track_id=1")
        assertEquals(LEGACY_PLUGIN_PREFIX + "api/similar/albums", seen.last())
        // Worked out once: one look at each plugin's health, then straight to the older one.
        assertEquals(1, seen.count { it == PLUGIN_PREFIX + "api/health" })
        assertEquals(1, seen.count { it == LEGACY_PLUGIN_PREFIX + "api/health" })
    }

    @Test fun otherAddressesAreLeftAlone() = runBlocking {
        val seen = mutableListOf<String>()
        val api = AudioMuseApi(server(setOf("/api/"), seen)) { settings }
        api.plugin("/api/something")
        assertEquals(listOf("/api/something"), seen)
    }
}
