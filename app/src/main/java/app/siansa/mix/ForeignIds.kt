package app.siansa.mix

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Fetches songs from the server by [ids] that aren't in the synced library (beta testers #32, #35). When AudioMuse
 * analysed another server (Navidrome, while the app is on Emby), none of its ids exist here: the first [PROBE] are
 * asked one by one, and if the server knows none of them the rest aren't asked at all, instead of 150 requests
 * that all fail. [fetch] answers null for a song the server doesn't have. Returns the songs found, in [ids]' order.
 */
internal suspend fun <T : Any> fetchUnlessForeign(
    ids: List<String>,
    parallel: Int = 6,
    fetch: suspend (String) -> T?,
): FetchResult<T> = coroutineScope {
    val wanted = ids.distinct()
    val found = LinkedHashMap<String, T>()
    val asked = HashSet<String>()
    for (id in wanted.take(PROBE)) {
        asked += id
        fetch(id)?.let { found[id] = it }
        if (found.isNotEmpty()) break
    }
    if (found.isEmpty() && asked.size == PROBE && wanted.size > PROBE) {
        return@coroutineScope FetchResult(emptyList(), foreign = true)
    }
    val permits = Semaphore(parallel)
    wanted.filter { it !in asked }
        .map { id -> async { id to permits.withPermit { fetch(id) } } }
        .awaitAll()
        .forEach { (id, song) -> if (song != null) found[id] = song }
    FetchResult(wanted.mapNotNull { found[it] }, foreign = false)
}

internal data class FetchResult<T>(val songs: List<T>, val foreign: Boolean)

/** Songs asked one by one before deciding they're another server's. */
internal const val PROBE = 3
