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
 * The settings are drawn in [PARTS], as its Mix settings page draws them (0.63).
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

        /**
         * The settings in parts of one line each, as Siansa Beats' Mix settings page has them (0.63; the owner, 22 Nov:
         * "100% organised and not bloated and easy to understand"): only the lines with a setting Siansa Beats has,
         * and any setting without a line last, under "More".
         */
        fun parts(): List<Part> {
            val known = settings.map { it.name }.toSet()
            val placed = PARTS.flatMap { it.lines }.flatMap { listOfNotNull(it.on) + it.more }.toSet()
            val rest = settings.map { it.name }.filter { it !in placed }
            val more = if (rest.isEmpty()) emptyList() else listOf(Part("More", "", listOf(Line("more", "More", null, rest))))
            return (PARTS + more).map { part ->
                part.copy(lines = part.lines.map { it.copy(on = it.on?.takeIf { n -> n in known }, more = it.more.filter { n -> n in known }) }
                    .filter { it.on != null || it.more.isNotEmpty() })
            }.filter { it.lines.isNotEmpty() }
        }

        /** The settings under [line], in its order. */
        fun under(line: Line): List<Setting> = line.more.mapNotNull { name -> settings.firstOrNull { it.name == name } }

        /** Whether anything under [line] isn't as it comes. */
        fun tuned(line: Line): Boolean = under(line).any { (values[it.name] ?: it.default) != it.default }

        /** [line] in a few words: what's chosen under it. */
        fun note(line: Line): String {
            fun n(name: String) = values[name]?.number ?: 0
            fun on(name: String) = values[name]?.on == true
            fun words(name: String) = values[name]?.words.orEmpty().size
            fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
            return when (line.key) {
                "now" -> "${count(n("now_count"), "mix", "mixes")} · for this part of the day"
                "daily" -> "${count(n("daily_count"), "mix", "mixes")} · the kinds of music you play most"
                "repeat" -> "Your most played · the last ${n("repeat_days")} days"
                "rediscover" -> "Old favourites · not played for ${n("forgotten_days")} days"
                "new" -> "Fresh finds near your taste"
                "in" -> "${n("size")} songs · ${n("familiar")}% you know · ${n("per_artist")} by one artist"
                "out" -> listOfNotNull(
                    "Played in the last ${n("fresh_days")} days".takeIf { n("fresh_days") > 0 },
                    count(words("leave_genres"), "genre", "genres").takeIf { words("leave_genres") > 0 },
                    count(words("leave_artists"), "artist", "artists").takeIf { words("leave_artists") > 0 },
                ).joinToString(" · ").ifEmpty { "Nothing" }
                "around" -> listOfNotNull("Time of day".takeIf { on("time_of_day") }, "weekends".takeIf { on("weekend_apart") },
                    "daylight".takeIf { on("daylight") }, "season".takeIf { on("season") }).joinToString(" · ").ifEmpty { "Your listening alone" }
                "siansa" -> (if (on("together")) "One row of ${n("together_count")}" else "Each group its own row") +
                    " · " + (if (on("ai_names")) "names from AI" else "Siansa Beats' own names")
                else -> count(line.more.size, "setting", "settings")
            }
        }
    }

    /** A named part of the settings: its lines. */
    data class Part(val title: String, val about: String, val lines: List<Line>)

    /** One line: a mix with its switch ([on]) or something every mix shares, and the settings that open under it. */
    data class Line(val key: String, val name: String, val on: String?, val more: List<String>)

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
        val PARTS = listOf(
            Part("Mixes", "Made for the moment", listOf(
                Line("now", "For now", "now", listOf("now_count")),
                Line("daily", "Made for you", "daily", listOf("daily_count")),
            )),
            Part("Your sound", "From your own listening", listOf(
                Line("repeat", "On repeat", "repeat", listOf("repeat_days")),
                Line("rediscover", "Rediscover", "rediscover", listOf("forgotten_days")),
                Line("new", "New for you", "new", emptyList()),
            )),
            Part("For all mixes", "", listOf(
                Line("in", "What's in a mix", null, listOf("size", "familiar", "per_artist")),
                Line("out", "Leave out", null, listOf("fresh_days", "leave_genres", "leave_artists")),
                Line("around", "Made around", null, listOf("time_of_day", "weekend_apart", "daylight", "season", "half_life")),
                Line("siansa", "In Siansa", null, listOf("together", "together_count", "ai_names")),
            )),
        )

        /** A setting's few words under its line (its line says the rest); without one here, Siansa Beats' own words. */
        val WORDS = mapOf(
            "now_count" to "How many", "daily_count" to "How many", "repeat_days" to "Looks back … days",
            "forgotten_days" to "Not played for … days", "size" to "Songs in a mix", "familiar" to "Songs you know, in %",
            "per_artist" to "By one artist, at most", "fresh_days" to "Songs played in the last … days",
            "leave_genres" to "Genres", "leave_artists" to "Artists", "time_of_day" to "The part of the day you listen in",
            "weekend_apart" to "Weekends apart from weekdays", "daylight" to "Calmer when it's dark outside",
            "season" to "This time of year counts more", "half_life" to "Listening counts half after … days",
            "together" to "All in one row on Home", "together_count" to "Cards in that row, at most",
            "ai_names" to "Names and lines from the AI service",
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
