package app.siansa.mix

import app.siansa.AppGraph
import app.siansa.data.JsonFile
import app.siansa.data.Users
import app.siansa.log.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate

/**
 * Home's Time Machine card, kept on the phone (issue #1341; the owner, 23 Nov: "make sure its properly cached on the
 * card. This day last year can be refetched once a day in the background"): Home draws it from its file at once, and
 * it's made again once a day, when Home opens or by the background work that prepares the day's mixes. Its plays come
 * from Siansa Beats' history when that's set up, else Koito or Last.fm, else Siansa's own history ([Insights.topOnce]).
 */
class TimeCards(private val graph: AppGraph) {
    private val file = JsonFile(File(graph.app.filesDir, Users.scoped("time_card.json")), TimeCardKept.serializer()) { TimeCardKept() }
    private val asking = Mutex()
    @Volatile private var triedAt = 0L

    /** The card on the phone: null until read ([refresh] reads it) or while there's none. */
    val card: Flow<TimeCard?> = file.state.map { it?.card }

    /**
     * Reads the card, and makes it again when it's from another day (or [force]). A try that couldn't reach the
     * history keeps the card there is, and isn't repeated for [RETRY_MS].
     */
    suspend fun refresh(today: LocalDate = LocalDate.now(), force: Boolean = false): TimeCard? = asking.withLock {
        val kept = file.read().card
        if (!force && !timeCardStale(kept, today)) return@withLock kept
        val now = System.currentTimeMillis()
        if (!force && now - triedAt < RETRY_MS) return@withLock kept
        if (!graph.settings.current.navidromeReady) return@withLock kept
        triedAt = now
        val made = buildTimeCard(today, timeCardPlans(today)) { plan ->
            runCatching { graph.insights.topOnce(plan.window, plan.key, TIME_LIST_SONGS, lasting = plan.kind != TimeListKind.DAY) }
                .onFailure { AppLog.w("TimeCard", "${plan.title} not made: ${it.message}") }
                .getOrNull()?.takeIf { it.complete || it.songs.isNotEmpty() }?.songs
        }
        if (made == null) {
            AppLog.w("TimeCard", "Not made for $today: the history couldn't be reached; ${if (kept == null) "no card yet" else "keeping the one from ${kept.day}"}")
            return@withLock kept
        }
        file.update { TimeCardKept(made) }
        AppLog.i("TimeCard", "Made for $today in ${System.currentTimeMillis() - now}ms: " +
            made.lists.joinToString { "${it.title} (${it.songIds.size})" }.ifEmpty { "no list has enough plays" })
        made
    }

    private companion object {
        const val RETRY_MS = 15 * 60_000L
    }
}
