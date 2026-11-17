package app.siansa.net

import app.siansa.data.AppSettings
import app.siansa.data.BeatsUse
import app.siansa.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder
import java.time.ZoneId

/**
 * Home's mixes made by Siansa Beats from your own listening (Siansa Beats 0.53, the owner, 17 Nov: "sunshine mixes in
 * Finland at 6:40 am, it's winter and dark outside"): by the part of the week you listen in, the kinds of music you play
 * then, and whether it's dark outside. Three shelves, as Siansa Beats' page has them: **For now**, **Made for you**
 * and **More of you**. Siansa Beats makes the next part of the day's ahead (0.54), so asking is quick; they're asked
 * again when the part of the day changes, and every [EVERY_MS] while Home shows them.
 */
class BeatsMixes(
    private val get: suspend (pathAndQuery: String) -> JsonElement,
    private val settings: () -> AppSettings,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val shown = MutableStateFlow<Shelves?>(null)

    /** The last shelves Siansa Beats gave; null before any. */
    val state: StateFlow<Shelves?> = shown.asStateFlow()

    /** Whether Home shows Siansa Beats' mixes: it makes them, and they're switched on under its card. */
    val on: Boolean get() = settings().beatsFor(BeatsUse.MIXES) && BeatsSound.mixes.value

    /** Asks for the shelves when there are none, they're from another part of the day, or [force]. */
    suspend fun refresh(force: Boolean = false): Shelves? {
        if (!on) return null
        val had = shown.value
        val part = partOf(java.time.Instant.ofEpochMilli(now()).atZone(zone()).hour)
        if (!force && had != null && had.part == part && now() - had.askedAt < EVERY_MS) return had
        val s = settings()
        val path = "/api/v1/mixes?user=" + URLEncoder.encode(BeatsListens.listener(s), "UTF-8") +
            "&tz=" + URLEncoder.encode(zone().id, "UTF-8") + "&size=$SIZE"
        return runCatching { parse(get(path) as? JsonObject ?: throw ApiException("Siansa Beats sent an unexpected answer"), now()) }
            .onSuccess {
                if (had == null || had.part != it.part) {
                    AppLog.i("Beats", "Mixes for the ${it.said}: ${it.shelves.sumOf { s -> s.mixes.size }} from ${it.basis}" +
                        (it.daylight?.let { d -> " · ${d.light}" } ?: ""))
                }
                shown.value = it
            }
            .onFailure { AppLog.w("Beats", "Siansa Beats' mixes weren't read: ${it.message}") }
            .getOrNull() ?: had
    }

    /** One mix: [songIds] are the music server's own. */
    data class BeatsMix(
        val id: String,
        val name: String,
        val why: String,
        val aiNamed: Boolean,
        val genres: List<String>,
        val tags: List<String>,
        val energy: Double?,
        val songIds: List<String>,
        val own: Int,
        /** A short line on what's in it (Siansa Beats 0.55): from its AI with the name, else from its sound. */
        val about: String = "",
        /** What Siansa Beats keeps it by, which no other mix has (0.55); "" from an older Siansa Beats. */
        val key: String = "",
    ) {
        /** What Home shows under it: what's in it, never the artists (they're on its own page; the owner, 17 Nov). */
        val caption: String get() = about.ifBlank { genres.take(2).joinToString(", ").ifBlank { why } }
    }

    data class Shelf(val key: String, val title: String, val note: String, val mixes: List<BeatsMix>)

    data class Daylight(val light: String, val said: String)

    data class Shelves(
        val part: String,
        val said: String,
        val based: String,
        val basis: String,
        val daylight: Daylight?,
        val shelves: List<Shelf>,
        val askedAt: Long,
        /** How many days Siansa Beats keeps a mix it gave (0.55, set on its page); 0 from an older one. */
        val keepDays: Int = 0,
    )

    companion object {
        const val SIZE = 40
        const val EVERY_MS = 15 * 60_000L

        /** The shelves' titles as Home shows them: Siansa Beats' page says "For now" for the part of the day's. */
        fun title(shelf: Shelf): String = if (shelf.key == "now") "For now" else shelf.title

        /** The part of the day [hour] is in, as Siansa Beats cuts it. */
        fun partOf(hour: Int): String = when (hour) {
            in 5..8 -> "early"
            in 9..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }

        internal fun parse(o: JsonObject, at: Long): Shelves {
            fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun JsonObject.texts(name: String) = (this[name] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            val daylight = o["daylight"] as? JsonObject
            return Shelves(
                part = o.text("part").orEmpty(),
                said = o.text("said").orEmpty(),
                based = o.text("based").orEmpty(),
                basis = o.text("basis").orEmpty(),
                daylight = daylight?.let { Daylight(it.text("light").orEmpty(), it.text("said").orEmpty()) },
                shelves = (o["shelves"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { shelf ->
                    Shelf(
                        key = shelf.text("key").orEmpty(),
                        title = shelf.text("title").orEmpty(),
                        note = shelf.text("note").orEmpty(),
                        mixes = (shelf["mixes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { m ->
                            val songs = (m["songs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("id") }
                            if (songs.isEmpty()) return@mapNotNull null
                            BeatsMix(
                                id = m.text("id") ?: return@mapNotNull null,
                                name = m.text("name").orEmpty(),
                                why = m.text("why").orEmpty(),
                                aiNamed = m.text("named") == "ai",
                                genres = m.texts("genres"),
                                tags = m.texts("tags"),
                                energy = (m["energy"] as? JsonPrimitive)?.doubleOrNull,
                                songIds = songs,
                                own = (m["own"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                                about = m.text("about").orEmpty(),
                                key = m.text("key").orEmpty(),
                            )
                        },
                    )
                }.filter { it.mixes.isNotEmpty() },
                askedAt = at,
                keepDays = (o["keep_days"] as? JsonPrimitive)?.content?.toIntOrNull()?.coerceIn(0, 90) ?: 0,
            )
        }
    }
}
