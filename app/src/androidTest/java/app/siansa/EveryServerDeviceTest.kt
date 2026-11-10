package app.siansa

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.siansa.data.Song
import app.siansa.log.AppLog
import app.siansa.net.SourceKind
import app.siansa.ui.browseGenres
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app itself on a phone, signed in to each of Navidrome, Emby and Jellyfin in turn (the owner, 10 Nov: the Dev build
 * against Emby and Jellyfin), each serving the same small library (scripts/servers/start.sh and library.py): the library
 * syncs, an artist's page has their albums and photo looked up, the genres to browse are the songs' tags (no Siansa
 * Beats here), search finds things, songs in each format play, and a DJ mix isn't offered without AudioMuse.
 *
 * Skipped unless the instrumentation argument `servers` names them (`navidrome=http://10.0.2.2:4533,emby=…,jellyfin=…`):
 * the Server devices workflow (.github/workflows/server-devices.yml) sets it.
 */
@RunWith(AndroidJUnit4::class)
class EveryServerDeviceTest {
    @get:Rule val notifications: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SiansaApp
    private val graph get() = app.graph
    private val servers: Map<String, String> = InstrumentationRegistry.getArguments().getString("servers").orEmpty().split(',')
        .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
        .toMap()

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { graph.player.stop() }
        println("---- Siansa log ----")
        AppLog.tail(150).forEach { println(it) }
    }

    @Test fun navidrome() = onServer("navidrome", SourceKind.OPENSUBSONIC)
    @Test fun emby() = onServer("emby", SourceKind.EMBY)
    @Test fun jellyfin() = onServer("jellyfin", SourceKind.JELLYFIN)

    private fun onServer(name: String, kind: SourceKind) {
        val url = servers[name]
        assumeTrue("$name isn't running (instrumentation argument servers)", url != null)
        graph.settings.setLogging(true)
        AppLog.init(app, true)
        graph.settings.save(graph.settings.current.copy(
            navidromeUrl = url!!, navidromeUser = "max", navidromePassword = "sesame", sourceKind = kind, audioMuseUrl = "",
        ))
        println("Server: $name ($kind) at $url")

        // The library syncs: every album and artist of the test library.
        graph.sync.start(full = true)
        runBlocking {
            withTimeout(180_000) {
                while (true) {
                    val p = graph.sync.progress.value
                    if (!p.running && graph.library.isSynced && graph.library.albums("name").isNotEmpty()) break
                    kotlinx.coroutines.delay(500)
                }
            }
        }
        val progress = graph.sync.progress.value
        assertEquals("$name: the sync went through", null, progress.error)
        val albums = runBlocking { graph.library.albums("name") }.map { it.name }
        listOf("First Light", "Two Discs", "Mixed Bag", "Loose Ends").forEach { album ->
            assertTrue("$name: $album in the library: $albums", album in albums)
        }

        // An artist's page: their albums, and their photo looked up (it may be none, but asking mustn't fail the page).
        val aurora = runBlocking { graph.library.artists() }.first { it.name == "Aurora Test" }
        val own = runBlocking { graph.library.albumsOf(aurora) }.map { it.name }
        assertTrue("$name: Aurora Test's albums $own", "First Light" in own)
        runBlocking { graph.artistPhotos.refresh(listOf(aurora)) }

        // The genres to browse (Search, Home): the songs' tags without Siansa Beats.
        val genres = runBlocking { graph.browseGenres(30) }.map { it.name }
        listOf("Electronic", "Rock", "Pop", "Jazz").forEach { g -> assertTrue("$name: genre $g in $genres", g in genres) }

        // Search through the server.
        val found = runBlocking { graph.source.search("Morning Tone") }
        assertTrue("$name: search finds Morning Tone", found.songs.any { it.title == "Morning Tone" })

        // No AudioMuse: a DJ mix isn't offered.
        assertEquals("$name: DJ mix without AudioMuse", "Needs AudioMuse", graph.djProblem())

        // Songs play, in each format the library has (FLAC, MP3, AAC, Opus).
        val songs = runBlocking { graph.source.songsPage(0, 100) }
        listOf("Morning Tone", "Upstairs", "Shared Room", "Loose Ends").forEach { title ->
            playsFor(name, songs.first { it.title == title })
        }
    }

    private fun playsFor(server: String, song: Song) {
        instrumentation.runOnMainSync { graph.player.play(listOf(song)) }
        val started = System.currentTimeMillis()
        var position = 0L
        while (System.currentTimeMillis() - started < 45_000) {
            val state = graph.player.state.value
            if (state.isPlaying && state.current?.id == song.id) {
                instrumentation.runOnMainSync { position = graph.player.positionMs() }
                if (position > 1_500) break
            }
            Thread.sleep(250)
        }
        println("$server: ${song.title} played to ${position} ms")
        assertTrue("$server: ${song.title} plays (reached $position ms)", position > 1_500)
        instrumentation.runOnMainSync { graph.player.stop() }
    }
}
