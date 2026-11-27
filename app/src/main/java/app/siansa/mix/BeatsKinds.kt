package app.siansa.mix

import app.siansa.net.BeatsMixes

/**
 * The kinds of mix Siansa Beats makes, by their ids, for a Siansa Beats card (the owner, 26 Nov: "a siansa beats card
 * where you can toggle them on and off"). Forgotten artists isn't one: its artists are in Artist radios.
 */
enum class BeatsKind(val id: String, val label: String, val hint: String, private val ids: (String) -> Boolean) {
    NOW("now", "For now", "Mixes for this part of the day", { it.startsWith("now-") }),
    DAILY("daily", "Made for you", "The kinds of music you play most", { it.startsWith("daily-") }),
    REPEAT("repeat", "On repeat", "Your most played lately", { it == "repeat" }),
    REDISCOVER("rediscover", "Rediscover", "Old favourites you haven't played for a while", { it == "rediscover" }),
    NEW("new", "New for you", "Songs you've never played, near your taste", { it == "new" }),
    DEEP("deep", "Deep cuts", "Unplayed songs from albums you love", { it == "deep" });

    companion object {
        /** A new card's: your own sound, as More of you. */
        val FIRST = listOf(REPEAT, REDISCOVER, NEW, DEEP)

        fun of(mixId: String): BeatsKind? = entries.firstOrNull { it.ids(mixId) }

        fun byId(id: String): BeatsKind? = entries.firstOrNull { it.id == id }

        /** [kinds]' mixes in [answer], kind by kind in the order Siansa Beats lists them, each once. */
        fun mixes(answer: BeatsMixes.Shelves?, kinds: List<String>): List<BeatsMixes.BeatsMix> {
            val wanted = kinds.mapNotNull(::byId).toSet()
            val all = answer?.shelves.orEmpty().flatMap { it.mixes }.distinctBy { it.id }
            return entries.filter { it in wanted }.flatMap { kind -> all.filter { of(it.id) == kind } }
        }
    }
}
