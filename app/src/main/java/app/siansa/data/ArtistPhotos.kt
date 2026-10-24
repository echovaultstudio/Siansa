package app.siansa.data

import app.siansa.log.AppLog
import app.siansa.net.MusicSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Artist photos for Home's artist radios (beta report 95a30366). Navidrome only fetches an artist's photo when the
 * artist is looked up (as their page does), and its picture id changes then; the library keeps the id from its last
 * sync, so Home showed the grey placeholder that id stands for while the artist's page had the photo. Once per
 * app run, each artist shown is looked up the same way and their new picture id kept.
 *
 * Home reads [covers] for the new ids at once, without reloading (a reload would pick its artists again).
 */
class ArtistPhotos(private val source: MusicSource, private val library: LibraryRepository) {
    private val done = ConcurrentHashMap.newKeySet<String>()
    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Artist id → their new picture id, for the artists refreshed this run. */
    val covers: StateFlow<Map<String, String>> = _covers.asStateFlow()

    /** Looks up [artists] not looked up yet this run; true when any picture id changed. */
    suspend fun refresh(artists: List<Artist>): Boolean = coroutineScope {
        val permits = Semaphore(3)
        val changed = artists.filter { done.add(it.id) }.map { artist ->
            async {
                permits.withPermit {
                    runCatching {
                        // Asking for the artist's info is what makes Navidrome fetch their photo.
                        runCatching { source.artistInfo(artist.id) }
                        val fresh = source.artist(artist.id).artist.coverArt
                        if (newCover(artist.coverArt, fresh)) {
                            library.setArtistCover(artist.id, fresh!!)
                            _covers.update { it + (artist.id to fresh) }
                            true
                        } else {
                            false
                        }
                    }.onFailure { AppLog.d("Covers", "Artist photo for ${artist.name} not refreshed: ${it.message}") }
                        .getOrDefault(false)
                }
            }
        }.awaitAll().count { it }
        if (changed > 0) {
            AppLog.i("Covers", "New photos for $changed artist radio(s)")
        }
        changed > 0
    }
}

/** Whether the server's [fresh] picture id for an artist differs from the one kept ([kept]). */
internal fun newCover(kept: String?, fresh: String?): Boolean = !fresh.isNullOrBlank() && fresh != kept
