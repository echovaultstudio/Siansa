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
     * The card as last read, at once: what Home draws first when it comes back, so Your listening and On this day are
     * there from its first frame and Home lands where it was (the owner, 27 Nov: going back "randomly goes back to some
     * other spot").
     */
    val current: TimeCard? get() = file.state.value?.card

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
        val made = buildListeningCard(
            today, timeCardPlans(today),
            songsOf = { plan, limit ->
                // Only a finished month or year is kept for good: a day and the card's times move on every day.
                val lasting = plan.kind != TimeListKind.DAY && plan.kind != TimeListKind.RANGE
                runCatching { graph.insights.topOnce(plan.window, plan.key, limit, lasting) }
                    .onFailure { AppLog.w("TimeCard", "${plan.title} not made: ${it.message}") }
                    .getOrNull()?.takeIf { it.complete || it.songs.isNotEmpty() }
                    ?.tracks?.mapNotNull { t -> t.song?.let { PlayedSong(it, t.plays.toInt()) } }
            },
            artistsOf = { window ->
                runCatching { graph.insights.topArtistsOnce(window, CARD_ARTISTS * 2) }
                    .onFailure { AppLog.w("TimeCard", "The artists of ${window.label} not made: ${it.message}") }.getOrNull()
                    ?.map { CardArtist(it.name, it.plays.toInt(), it.artist?.id, it.artist?.coverArt, it.artist?.imageUrl) }
            },
            // Siansa Beats' genres by each song's sound where it has them, else the song's tag.
            genresOf = { songs ->
                val bySound = runCatching { graph.beatsGenres.ofSongs(songs.map { it.id }) }.getOrNull().orEmpty()
                val of: (app.siansa.data.Song) -> List<String> = { song -> bySound[song.id]?.map { it.name }?.takeIf { it.isNotEmpty() } ?: tagGenres(song) }
                of
            },
        )
        if (made == null) {
            AppLog.w("TimeCard", "Not made for $today: the history couldn't be reached; ${if (kept == null) "no card yet" else "keeping the one from ${kept.day}"}")
            return@withLock kept
        }
        file.update { TimeCardKept(made) }
        AppLog.i("TimeCard", "Made for $today in ${System.currentTimeMillis() - now}ms: " +
            made.lists.joinToString { "${it.title} (${it.songIds.size})" }.ifEmpty { "no look back has enough plays" } + " · " +
            made.ranges.joinToString { "${it.range}: ${it.songs?.songIds?.size ?: 0} songs, ${it.artists.size} artists, ${it.genres.size} genres" })
        made
    }

    private companion object {
        const val RETRY_MS = 15 * 60_000L
    }
}
