package app.siansa.net

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * What goes into your mixes from Siansa Beats (0.56; the owner, 17 Nov: "a settings page so the user can adjust what
 * should be contained or left out from the mixes. Make settings or toggles for everything that is currently
 * automatically chosen"). Kept by Siansa Beats for each listener, and changed the same from its own Mixes page. Each
 * setting comes described by Siansa Beats (what kind it is, its limits, its words), so a new one shows here by itself.
 */
class BeatsMixSettings(
    private val get: suspend (pathAndQuery: String) -> JsonElement,
    private val post: suspend (path: String, body: String) -> JsonElement,
) {
    /** One setting: a switch ([Kind.ON]), a number from [low] to [high], or names to leave out. */
    data class Setting(val name: String, val kind: Kind, val label: String, val low: Int, val high: Int, val default: Value)

    enum class Kind { ON, NUMBER, WORDS }

    /** A setting's value: whichever its kind has. */
    data class Value(val on: Boolean = false, val number: Int = 0, val words: List<String> = emptyList())

    data class Settings(val user: String, val settings: List<Setting>, val values: Map<String, Value>, val keepDays: Int) {
        fun value(name: String): Value? = values[name]

        /** The settings in groups, as Siansa Beats' page has them; any not in one come last, under "More". */
        fun grouped(): List<Pair<String, List<Setting>>> {
            val byName = settings.associateBy { it.name }
            val listed = GROUPS.flatMap { it.second }.toSet()
            return (GROUPS + ("More" to settings.map { it.name }.filter { it !in listed }))
                .map { (title, names) -> title to names.mapNotNull { byName[it] } }
                .filter { it.second.isNotEmpty() }
        }
    }

    suspend fun load(user: String): Settings =
        parse(get("/api/v1/mixes/settings?user=" + java.net.URLEncoder.encode(user, "UTF-8")) as? JsonObject
            ?: throw ApiException("Siansa Beats sent an unexpected answer"))

    /** Changes [changes] (by name) for [user]; [keepDays] how long every listener's mixes are kept. */
    suspend fun save(user: String, changes: Map<String, Value>, kinds: Map<String, Kind>, keepDays: Int? = null): Settings {
        val body = buildJsonObject {
            put("user", user)
            put("settings", buildJsonObject {
                changes.forEach { (name, v) -> put(name, json(v, kinds[name] ?: return@forEach)) }
                if (keepDays != null) put("keep_days", keepDays)
            })
        }
        return parse(post("/api/v1/mixes/settings", body.toString()) as? JsonObject
            ?: throw ApiException("Siansa Beats sent an unexpected answer"))
    }

    /**
     * [user]'s mixes made again now, a new pick from the same songs and settings (Siansa Beats 0.60.4, the owner, 20 Nov:
     * a button to make them again after changing how they're made): how many times today.
     */
    suspend fun again(user: String, zone: String): Int {
        val body = buildJsonObject { put("user", user); put("tz", zone) }
        val o = post("/api/v1/mixes/again", body.toString()) as? JsonObject ?: throw ApiException("Siansa Beats sent an unexpected answer")
        return o["times"]?.jsonPrimitive?.intOrNull ?: 1
    }

    companion object {
        val GROUPS = listOf(
            "Which mixes" to listOf("now", "now_count", "daily", "daily_count", "repeat", "rediscover", "new"),
            "What's in each" to listOf("size", "familiar", "per_artist", "fresh_days", "leave_genres", "leave_artists"),
            "What they're made from" to listOf("time_of_day", "weekend_apart", "daylight", "season", "half_life", "repeat_days", "forgotten_days"),
            "Names" to listOf("ai_names"),
        )

        internal fun json(v: Value, kind: Kind): JsonElement = when (kind) {
            Kind.ON -> JsonPrimitive(v.on)
            Kind.NUMBER -> JsonPrimitive(v.number)
            Kind.WORDS -> JsonArray(v.words.map(::JsonPrimitive))
        }

        private fun valueOf(e: JsonElement?, kind: Kind): Value = when (kind) {
            Kind.ON -> Value(on = (e as? JsonPrimitive)?.booleanOrNull ?: false)
            Kind.NUMBER -> Value(number = (e as? JsonPrimitive)?.intOrNull ?: 0)
            Kind.WORDS -> Value(words = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content })
        }

        internal fun parse(o: JsonObject): Settings {
            fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val settings = (o["described"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { d ->
                val kind = when (d.text("kind")) { "on" -> Kind.ON; "number" -> Kind.NUMBER; "words" -> Kind.WORDS; else -> return@mapNotNull null }
                val name = d.text("name") ?: return@mapNotNull null
                fun bound(n: String) = (d[n].takeIf { it != JsonNull } as? JsonPrimitive)?.intOrNull ?: 0
                Setting(name, kind, d.text("label") ?: name, bound("low"), bound("high"), valueOf(d["default"], kind))
            }
            val given = o["settings"] as? JsonObject ?: JsonObject(emptyMap())
            return Settings(
                user = o.text("user").orEmpty(),
                settings = settings,
                values = settings.associate { it.name to valueOf(given[it.name], it.kind) },
                keepDays = (o["keep_days"] as? JsonPrimitive)?.intOrNull ?: 7,
            )
        }
    }
}
